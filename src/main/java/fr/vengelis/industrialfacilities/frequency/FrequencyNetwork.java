package fr.vengelis.industrialfacilities.frequency;

import java.util.*;
import fr.vengelis.industrialfacilities.access.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** One inventory per physical device, with an index for frequency lookups. */
public final class FrequencyNetwork extends SavedData {
    private static final Comparator<FrequencyEndpoint> ORDER = Comparator.comparing(FrequencyEndpoint::id);
    public record Port(int slot, FrequencyChip frequency, FrequencyChip.Mode mode) {
        public Port(int slot, FrequencyChip frequency) { this(slot, frequency, FrequencyChip.Mode.BOTH); }
    }
    private enum Flow { SEND, RECEIVE, VISIBLE }
    private record RouteKey(UUID id, Flow flow) {}
    private record Reach(List<FrequencyEndpoint> peers, Map<UUID, UUID> publicPaths, Map<UUID, UUID> ownPaths) {
        static final Reach EMPTY = new Reach(List.of(), Map.of(), Map.of());
    }

    private static final class Device {
        final FrequencyEndpoint endpoint;
        final SimpleContainer inventory;
        final Set<FrequencyChip> frequencies = new LinkedHashSet<>();
        final Set<FrequencyChip> sending = new HashSet<>(), receiving = new HashSet<>();
        boolean observed;

        Device(FrequencyEndpoint endpoint) {
            this.endpoint = endpoint;
            this.inventory = new SimpleContainer(endpoint.slots());
        }
    }

    private final Map<UUID, Device> devices = new LinkedHashMap<>();
    private final Map<FrequencyChip, Set<Device>> byFrequency = new HashMap<>();
    private final Set<UUID> migrated = new HashSet<>();
    private final Map<UUID, Integer> users = new HashMap<>();
    private final Map<RouteKey, Reach> routes = new HashMap<>();
    private long routeTick = Long.MIN_VALUE;

    public static FrequencyNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FrequencyNetwork::new, FrequencyNetwork::load), "industrial_frequencies");
    }

    public boolean installed(UUID id) { return devices.containsKey(id) && !devices.get(id).frequencies.isEmpty(); }
    public boolean relay(UUID id) {
        Device device = devices.get(id);
        return device != null && device.endpoint.type().terminal() && device.frequencies.size() > 1;
    }
    public boolean migrated(UUID id) { return migrated.contains(id); }
    public boolean legacy(UUID source, UUID target) { return !migrated.contains(source) && !migrated.contains(target); }

    public List<FrequencyEndpoint> peers(ServerLevel level, UUID id) {
        return reach(level, id, Flow.SEND).peers;
    }

    /** Devices able to deliver a signal to this endpoint, including directed relay paths. */
    public List<FrequencyEndpoint> senders(ServerLevel level, UUID id) {
        return reach(level, id, Flow.RECEIVE).peers;
    }

    /** Physical subscriptions for read-only supervision and diagnostic overlays. */
    public List<FrequencyEndpoint> visiblePeers(ServerLevel level, UUID id) {
        return reach(level, id, Flow.VISIBLE).peers;
    }

    public boolean visible(ServerLevel level, UUID source, UUID target) {
        Reach reach = reach(level, source, Flow.VISIBLE);
        return !Objects.equals(source, target) && (reach.publicPaths.containsKey(target) || reach.ownPaths.containsKey(target));
    }

    private Reach reach(ServerLevel level, UUID id, Flow flow) {
        if (!installed(id)) return Reach.EMPTY;
        if (routeTick != level.getGameTime()) {
            routes.clear();
            routeTick = level.getGameTime();
        }
        return routes.computeIfAbsent(new RouteKey(id, flow), ignored -> reachable(level, devices.get(id), flow));
    }

    private Reach reachable(ServerLevel level, Device source, Flow flow) {
        Map<Device, Boolean> valid = new HashMap<>();
        if (!valid(level, source)) return Reach.EMPTY;
        var publicPaths = collectReachable(level, source, false, valid, flow);
        // Any path that uses a private channel stays within the owner's equipment.
        var ownPaths = collectReachable(level, source, true, valid, flow);
        var matches = new HashSet<>(publicPaths.keySet());
        matches.addAll(ownPaths.keySet());
        matches.remove(source.endpoint.id());
        return new Reach(matches.stream().map(id -> devices.get(id).endpoint).sorted(ORDER).toList(), publicPaths, ownPaths);
    }

    private Map<UUID, UUID> collectReachable(ServerLevel level, Device source, boolean ownNetwork, Map<Device, Boolean> valid, Flow flow) {
        Map<UUID, UUID> previous = new HashMap<>();
        Deque<Device> pending = new ArrayDeque<>();
        previous.put(source.endpoint.id(), source.endpoint.id());
        pending.add(source);
        while (!pending.isEmpty()) {
            Device relay = pending.removeFirst();
            Set<FrequencyChip> outgoing = flow == Flow.VISIBLE ? relay.frequencies : flow == Flow.SEND ? relay.sending : relay.receiving;
            if (flow == Flow.VISIBLE && relay == source
                    && (source.endpoint.type() == FrequencyEndpoint.Type.CONSOLE || source.endpoint.type() == FrequencyEndpoint.Type.DISPLAY))
                outgoing = source.receiving;
            for (FrequencyChip frequency : outgoing) {
                if (!ownNetwork && frequency.issuer() != null) continue;
                for (Device other : byFrequency.get(frequency)) {
                    if (previous.containsKey(other.endpoint.id()) || (ownNetwork && !Objects.equals(source.endpoint.owner(), other.endpoint.owner()))
                            || flow == Flow.SEND && !other.receiving.contains(frequency)
                            || flow == Flow.RECEIVE && !other.sending.contains(frequency)
                            || !TerminalLinks.inRange(relay.endpoint.pos(), other.endpoint.pos())
                            || !valid.computeIfAbsent(other, device -> valid(level, device))) continue;
                    previous.put(other.endpoint.id(), relay.endpoint.id());
                    if (relay(other.endpoint.id())) pending.addLast(other);
                }
            }
        }
        return previous;
    }

    public List<FrequencyEndpoint> path(ServerLevel level, UUID source, UUID target) {
        return path(level, source, target, Flow.SEND);
    }

    public List<FrequencyEndpoint> visualPath(ServerLevel level, UUID source, UUID target) {
        return path(level, source, target, Flow.VISIBLE);
    }

    private List<FrequencyEndpoint> path(ServerLevel level, UUID source, UUID target, Flow flow) {
        if (Objects.equals(source, target) || !installed(target)) return List.of();
        Reach reach = reach(level, source, flow);
        var shared = path(source, target, reach.publicPaths);
        var owned = path(source, target, reach.ownPaths);
        return owned.isEmpty() || (!shared.isEmpty() && shared.size() <= owned.size()) ? shared : owned;
    }

    private List<FrequencyEndpoint> path(UUID source, UUID target, Map<UUID, UUID> previous) {
        if (!previous.containsKey(target)) return List.of();
        List<FrequencyEndpoint> path = new ArrayList<>();
        UUID step = target;
        while (!step.equals(source)) {
            path.add(devices.get(step).endpoint);
            step = previous.get(step);
        }
        path.add(devices.get(source).endpoint);
        return path.reversed();
    }

    private static boolean valid(ServerLevel level, Device device) {
        return !TerminalLinks.loaded(level, device.endpoint.pos()) || device.endpoint.online(level);
    }

    List<FrequencyChip> privateFrequencies(UUID owner) {
        return byFrequency.keySet().stream().filter(chip -> owner.equals(chip.issuer())).toList();
    }

    public FrequencyEndpoint endpoint(UUID id) {
        Device device = devices.get(id);
        return device == null ? null : device.endpoint;
    }

    public List<Port> ports(UUID id) {
        Device device = devices.get(id);
        if (device == null) return List.of();
        List<Port> ports = new ArrayList<>();
        for (int slot = 0; slot < device.inventory.getContainerSize(); slot++) {
            FrequencyChip chip = FrequencyChip.read(device.inventory.getItem(slot));
            if (chip != null && device.frequencies.contains(chip)) ports.add(new Port(slot, chip, FrequencyChip.mode(device.inventory.getItem(slot))));
        }
        return List.copyOf(ports);
    }

    public boolean connected(ServerLevel level, UUID source, UUID target) {
        if (Objects.equals(source, target) || !installed(target)) return false;
        Reach reach = reach(level, source, Flow.SEND);
        return reach.publicPaths.containsKey(target) || reach.ownPaths.containsKey(target);
    }

    private SimpleContainer inventory(ServerLevel level, FrequencyEndpoint endpoint) {
        Device device = devices.computeIfAbsent(endpoint.id(), id -> new Device(endpoint));
        if (!device.observed) {
            device.inventory.addListener(container -> changed(level, device));
            device.observed = true;
        }
        return device.inventory;
    }

    void close(FrequencyEndpoint endpoint, SimpleContainer inventory) {
        Device device = devices.get(endpoint.id());
        if (device != null && device.inventory == inventory && inventory.isEmpty()) {
            // The inventory can be shared by several open menus. Retain it until the last closes.
            if (users.merge(endpoint.id(), -1, Integer::sum) <= 0) {
                users.remove(endpoint.id());
                devices.remove(endpoint.id());
            }
        } else users.computeIfPresent(endpoint.id(), (id, count) -> count <= 1 ? null : count - 1);
    }

    SimpleContainer open(ServerLevel level, FrequencyEndpoint endpoint) {
        users.merge(endpoint.id(), 1, Integer::sum);
        return inventory(level, endpoint);
    }

    public static void remove(ServerLevel level, UUID id, BlockPos dropAt) {
        if (id == null) return;
        FrequencyNetwork network = get(level);
        Device device = network.devices.remove(id);
        if (device == null && !network.migrated.remove(id)) return;
        network.migrated.remove(id);
        network.users.remove(id);
        if (device != null) {
            network.unindex(device);
            for (int slot = 0; slot < device.inventory.getContainerSize(); slot++) {
                ItemStack stack = device.inventory.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) Containers.dropItemStack(level, dropAt.getX() + .5, dropAt.getY() + .5, dropAt.getZ() + .5, stack);
            }
        }
        network.updated(level);
    }

    private void changed(ServerLevel level, Device device) {
        if (devices.get(device.endpoint.id()) != device) return;
        // Stacks restored from disk acquire their listener when a player opens the slots.
        unindex(device);
        index(device);
        if (!device.frequencies.isEmpty()) {
            migrated.add(device.endpoint.id());
            if (device.endpoint.type().reader()) {
                var terminal = AccessTerminalEntity.resolve(level, device.endpoint.pos());
                if (terminal != null) terminal.ensureCode();
            }
        }
        updated(level);
    }

    private void updated(ServerLevel level) {
        routes.clear();
        setDirty();
        fr.vengelis.industrialfacilities.security.AutomationOutputs.frequencyChanged(level);
        TerminalControlData.get(level).frequencyChanged(level);
    }

    private void unindex(Device device) {
        for (FrequencyChip chip : device.frequencies) {
            Set<Device> members = byFrequency.get(chip);
            if (members != null && members.remove(device) && members.isEmpty()) byFrequency.remove(chip);
        }
        device.frequencies.clear();
        device.sending.clear();
        device.receiving.clear();
    }

    private void index(Device device) {
        for (int i = 0; i < device.inventory.getContainerSize(); i++) {
            FrequencyChip chip = FrequencyChip.read(device.inventory.getItem(i));
            if (chip == null || !chip.permits(device.endpoint.owner())) continue;
            if (device.frequencies.add(chip))
                byFrequency.computeIfAbsent(chip, key -> new TreeSet<>(Comparator.comparing(member -> member.endpoint, ORDER))).add(device);
            FrequencyChip.Mode mode = FrequencyChip.mode(device.inventory.getItem(i));
            if (mode.sends()) device.sending.add(chip);
            if (mode.receives()) device.receiving.add(chip);
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag rows = new ListTag();
        for (Device device : devices.values()) {
            if (device.inventory.isEmpty()) continue;
            FrequencyEndpoint endpoint = device.endpoint;
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", endpoint.id());
            row.putUUID("Owner", endpoint.owner());
            row.putLong("Pos", endpoint.pos().asLong());
            row.putString("Kind", endpoint.type().name());
            ListTag items = new ListTag();
            for (int i = 0; i < device.inventory.getContainerSize(); i++) {
                ItemStack stack = device.inventory.getItem(i);
                if (stack.isEmpty()) continue;
                CompoundTag item = new CompoundTag();
                item.putInt("Slot", i);
                item.put("Stack", stack.save(provider));
                items.add(item);
            }
            row.put("Chips", items);
            rows.add(row);
        }
        tag.put("Devices", rows);
        ListTag old = new ListTag();
        for (UUID id : migrated) { CompoundTag row = new CompoundTag(); row.putUUID("Id", id); old.add(row); }
        tag.put("Migrated", old);
        return tag;
    }

    static FrequencyNetwork load(CompoundTag tag, HolderLookup.Provider provider) {
        FrequencyNetwork network = new FrequencyNetwork();
        for (Tag item : tag.getList("Devices", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (!row.hasUUID("Id") || !row.hasUUID("Owner")) continue;
            FrequencyEndpoint.Type type;
            try { type = FrequencyEndpoint.Type.valueOf(row.getString("Kind")); }
            catch (IllegalArgumentException exception) { continue; }
            var endpoint = new FrequencyEndpoint(row.getUUID("Id"), type, BlockPos.of(row.getLong("Pos")), row.getUUID("Owner"));
            Device device = new Device(endpoint);
            for (Tag saved : row.getList("Chips", Tag.TAG_COMPOUND)) {
                CompoundTag chip = (CompoundTag) saved;
                int slot = chip.getInt("Slot");
                if (slot >= 0 && slot < endpoint.slots())
                    device.inventory.setItem(slot, ItemStack.parseOptional(provider, chip.getCompound("Stack")));
            }
            if (!device.inventory.isEmpty()) {
                network.devices.put(endpoint.id(), device);
                network.index(device);
                network.migrated.add(endpoint.id());
            }
        }
        for (Tag item : tag.getList("Migrated", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (row.hasUUID("Id")) network.migrated.add(row.getUUID("Id"));
        }
        return network;
    }
}

