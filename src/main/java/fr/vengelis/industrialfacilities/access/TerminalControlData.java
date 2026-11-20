package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.frequency.*;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorManager;
import fr.vengelis.industrialfacilities.security.SignalEmitterEntity;
import fr.vengelis.industrialfacilities.supervision.SignalTraffic;
import fr.vengelis.industrialfacilities.supervision.NetworkView.SignalKind;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local control links, latched lockdowns and timed button requests. */
public final class TerminalControlData extends SavedData {
    static final int MAX_LINKS = 64;

    public record Target(UUID id, BlockPos position, boolean emitter, boolean door) {
        public Target(UUID id, BlockPos position, boolean emitter) { this(id, position, emitter, false); }
        public boolean terminal() { return !emitter && !door; }
        public String kind() { return door ? "door" : emitter ? "emitter" : "terminal"; }
    }
    private record Pulse(UUID button, Target target, UUID code) {}
    private record ReaderSite(BlockPos position, UUID owner) {}

    private static final class Circuit {
        final BlockPos position;
        final UUID owner;
        final boolean lockdown;
        final Set<Target> links = new LinkedHashSet<>();
        boolean on;
        long until;

        Circuit(BlockPos position, UUID owner, boolean lockdown) {
            this.position = position.immutable();
            this.owner = owner;
            this.lockdown = lockdown;
        }
    }

    private boolean frequencyInitialized;
    private final Map<UUID, Circuit> circuits = new HashMap<>();
    private final Map<UUID, Set<Pulse>> requests = new HashMap<>();
    private final Map<UUID, Target> locked = new HashMap<>();
    private final Set<Target> emitters = new HashSet<>();
    private final Map<ReaderSite, Set<UUID>> lockedReaderSites = new HashMap<>();
    private final Map<UUID, Set<UUID>> cameraDoors = new HashMap<>();
    private final Map<UUID, Set<UUID>> camerasByDoor = new HashMap<>();

    public static TerminalControlData get(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TerminalControlData::new, TerminalControlData::load), "industrial_terminal_controls");
        if (!data.frequencyInitialized) {
            data.frequencyInitialized = true;
            data.rebuildLocks(level);
        }
        return data;
    }

    public boolean locked(UUID terminal) {
        return locked.containsKey(terminal);
    }

    public boolean blocksDoor(ServerLevel level, IndustrialDoorEntity door) {
        UUID doorId = door.doorInstanceId;
        if (doorId == null || locked.isEmpty()) return false;
        if (locked(doorId)) return true;
        var network = FrequencyNetwork.get(level);
        for (UUID camera : camerasByDoor.getOrDefault(doorId, Set.of())) {
            if (locked(camera) && network.legacy(camera, doorId)) return true;
        }
        for (var peer : network.senders(level, doorId)) {
            if (!peer.type().reader() && peer.type() != FrequencyEndpoint.Type.CAMERA_TERMINAL) continue;
            if (locked(peer.id()) || lockedReaderSites.getOrDefault(new ReaderSite(peer.pos(), peer.owner()), Set.of()).contains(peer.id()))
                return true;
        }
        if (lockedReaderSites.isEmpty()) return false;
        String dimension = level.dimension().location().toString();
        return AccessDoorService.readers(level, doorId).stream().anyMatch(reader -> {
            if (!dimension.equals(reader.dimension) || !TerminalLinks.inRange(reader.position, door.getBlockPos())) return false;
            Set<UUID> instances = lockedReaderSites.get(new ReaderSite(reader.position, reader.owner));
            // Older registrations had a site and owner but no physical instance UUID.
            return instances != null && (reader.instanceId == null || instances.contains(reader.instanceId));
        });
    }

    /** Maintain the inverse of the camera terminal's saved links, even when its chunk is unloaded. */
    public void recordCameraDoors(UUID camera, Set<UUID> doors) {
        Set<UUID> previous = cameraDoors.getOrDefault(camera, Set.of());
        if (previous.equals(doors)) return;
        for (UUID door : previous) {
            Set<UUID> cameras = camerasByDoor.get(door);
            if (cameras != null && cameras.remove(camera) && cameras.isEmpty()) camerasByDoor.remove(door);
        }
        if (doors.isEmpty()) cameraDoors.remove(camera);
        else {
            cameraDoors.put(camera, Set.copyOf(doors));
            for (UUID door : doors) camerasByDoor.computeIfAbsent(door, id -> new HashSet<>()).add(camera);
        }
        setDirty();
    }

    public boolean powers(SignalEmitterEntity emitter) {
        return emitters.contains(new Target(emitter.instanceId, emitter.getBlockPos(), true));
    }

    public Set<Target> links(ControlTerminalEntity terminal) {
        if (!(terminal.getLevel() instanceof ServerLevel level)) return Set.of();
        Circuit circuit = circuit(terminal);
        return targets(level, terminal.instanceId, terminal.lockdown(), circuit == null ? Set.of() : circuit.links);
    }

    private Set<Target> targets(ServerLevel level, UUID id, boolean lockdown, Set<Target> existing) {
        var result = new LinkedHashSet<Target>();
        if (level == null) { result.addAll(existing); return result; }
        var network = FrequencyNetwork.get(level);
        for (Target target : existing) if (network.legacy(id, target.id)) result.add(target);
        for (var peer : network.peers(level, id)) {
            var type = peer.type();
            if (type == FrequencyEndpoint.Type.DOOR) {
                result.add(new Target(peer.id(), peer.pos(), false, true));
                continue;
            }
            // Lockdowns share a channel without disabling their peers; this avoids a cyclic shutdown.
            if (lockdown ? (type.terminal() && type != FrequencyEndpoint.Type.LOCKDOWN) || type == FrequencyEndpoint.Type.EMITTER : type.reader())
                result.add(new Target(peer.id(), peer.pos(), type == FrequencyEndpoint.Type.EMITTER));
        }
        return result;
    }

    private Set<Target> targets(ServerLevel level, UUID id, Circuit circuit) {
        return targets(level, id, circuit.lockdown, circuit.links);
    }

    public void frequencyChanged(ServerLevel level) { changed(level); }

    public boolean on(ControlTerminalEntity terminal, long now) {
        Circuit circuit = circuit(terminal);
        return circuit != null && !locked(terminal.instanceId)
            && (circuit.lockdown ? circuit.on : circuit.until > now);
    }

    public long activationUntil(ControlTerminalEntity terminal, long now) {
        Circuit circuit = circuit(terminal);
        return circuit != null && !circuit.lockdown && !locked(terminal.instanceId) && circuit.until > now ? circuit.until : 0;
    }

    private Circuit circuit(ControlTerminalEntity terminal) {
        Circuit circuit = circuits.get(terminal.instanceId);
        return circuit != null && circuit.position.equals(terminal.getBlockPos())
            && circuit.owner.equals(terminal.creator) ? circuit : null;
    }

    public static boolean reader(AccessTerminalEntity terminal) {
        return terminal != null && !terminal.encoder && !terminal.bank && !(terminal instanceof ControlTerminalEntity);
    }

    public static AccessTerminalEntity terminalAt(ServerLevel level, Target target, UUID owner) {
        if (!target.terminal() || !TerminalLinks.loaded(level, target.position)) return null;
        var terminal = AccessTerminalEntity.resolve(level, target.position);
        return terminal != null && terminal.getBlockPos().equals(target.position)
            && target.id.equals(terminal.instanceId) && owner.equals(terminal.creator) ? terminal : null;
    }

    public static SignalEmitterEntity emitterAt(ServerLevel level, Target target, UUID owner) {
        return target.emitter && TerminalLinks.loaded(level, target.position)
            && level.getBlockEntity(target.position) instanceof SignalEmitterEntity emitter
            && target.id.equals(emitter.instanceId) && owner.equals(emitter.creator) ? emitter : null;
    }

    static boolean available(ServerLevel level, ControlTerminalEntity source, Target target) {
        var peer = FrequencyEndpoint.resolve(level, target.position);
        if (peer != null && peer.id().equals(target.id)
            && FrequencyNetwork.get(level).connected(level, source.instanceId, target.id)) return true;
        if (!TerminalLinks.inRange(source.getBlockPos(), target.position)) return false;
        return target.emitter ? emitterAt(level, target, source.creator) != null : terminalAt(level, target, source.creator) != null;
    }

    String unlinkOffline(ServerLevel level, ControlTerminalEntity terminal, UUID id, BlockPos position) {
        Circuit circuit = circuit(terminal);
        if (circuit != null) {
            var targets = circuit.links.iterator();
            while (targets.hasNext()) {
                Target target = targets.next();
                if (!target.id.equals(id) || !target.position.equals(position)) continue;
                if (available(level, terminal, target)) return "device_back_online";
                targets.remove();
                changed(level);
                return "device_unlinked";
            }
        }
        return "device_not_linked";
    }

    public String link(ServerLevel level, ControlTerminalEntity source, BlockPos position) {
        Target target;
        var terminal = AccessTerminalEntity.resolve(level, position);
        if (terminal != null && source.creator.equals(terminal.creator) && (source.lockdown() || reader(terminal))) {
            target = new Target(terminal.instanceId, terminal.getBlockPos(), false);
        } else if (source.lockdown() && level.getBlockEntity(position) instanceof SignalEmitterEntity emitter
                && source.creator.equals(emitter.creator)) {
            target = new Target(emitter.instanceId, position.immutable(), true);
        } else {
            return source.lockdown() ? "invalid_lock_target" : "invalid_button_target";
        }
        if (target.id.equals(source.instanceId)) return "self_link";
        if (!TerminalLinks.inRange(source.getBlockPos(), target.position)) return "too_far";

        Circuit circuit = circuits.computeIfAbsent(source.instanceId,
            id -> new Circuit(source.getBlockPos(), source.creator, source.lockdown()));
        if (circuit.links.remove(target)) {
            changed(level);
            return "unlinked";
        }
        if (circuit.links.size() >= MAX_LINKS) return "too_many";
        if (source.lockdown() && terminal instanceof ControlTerminalEntity control && control.lockdown()
                && reaches(target.id, source.instanceId)) return "loop";
        circuit.links.add(target);
        changed(level);
        return "linked";
    }

    private boolean reaches(UUID start, UUID end) {
        Set<UUID> seen = new HashSet<>();
        var queue = new ArrayDeque<UUID>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            if (id.equals(end)) return true;
            Circuit circuit = circuits.get(id);
            if (seen.add(id) && circuit != null && circuit.lockdown)
                for (Target link : circuit.links) if (!link.emitter) queue.add(link.id);
        }
        return false;
    }

    public boolean toggle(ServerLevel level, ControlTerminalEntity terminal) {
        Circuit circuit = circuits.computeIfAbsent(terminal.instanceId,
            id -> new Circuit(terminal.getBlockPos(), terminal.creator, true));
        circuit.on = !circuit.on;
        changed(level);
        return circuit.on;
    }

    public boolean press(ServerLevel level, ControlTerminalEntity terminal) {
        var linked = links(terminal);
        if (linked.isEmpty() || locked(terminal.instanceId)) return false;
        Circuit circuit = circuits.computeIfAbsent(terminal.instanceId,
            id -> new Circuit(terminal.getBlockPos(), terminal.creator, false));
        long now = level.getGameTime();
        removeExpired(level, now);

        var registry = AccessRegistry.get(level.getServer());
        boolean opened = false;
        for (Target target : linked) {
            if (locked(target.id)) continue;
            if (target.door) {
                var door = AccessDoorService.resolveDoor(level, target.position);
                if (door != null && target.position.equals(door.getBlockPos()) && target.id.equals(door.doorInstanceId))
                    opened |= requestDoor(level, terminal.instanceId, target, null, door);
                continue;
            }
            var peer = FrequencyEndpoint.resolve(level, target.position);
            UUID owner = peer != null && FrequencyNetwork.get(level).connected(level, terminal.instanceId, target.id)
                ? peer.owner() : circuit.owner;
            var reader = terminalAt(level, target, owner);
            if (!reader(reader) || reader.terminalId == null) continue;
            var entry = registry.find(reader.terminalId);
            if (TerminalLinks.loadedReader(level, entry) != reader) continue;
            for (var link : AccessDoorService.doors(level, entry)) {
                if (!TerminalLinks.admissible(level, target.position, target.id, link)) continue;
                var door = TerminalLinks.loadedDoor(level, link);
                if (door != null) opened |= requestDoor(level, terminal.instanceId, target, entry.id, door);
            }
        }
        if (opened) {
            circuit.until = now + terminal.seconds() * 20L;
            setDirty();
            refreshSignalTraffic(level);
        }
        return opened;
    }

    private boolean requestDoor(ServerLevel level, UUID button, Target target, UUID readerCode, IndustrialDoorEntity door) {
        if (blocksDoor(level, door) || IndustrialDoorManager.fireAlarmActive(level, door)) return false;
        requests.computeIfAbsent(door.doorInstanceId, id -> new HashSet<>()).add(new Pulse(button, target, readerCode));
        return true;
    }

    /** Derive traffic from live requests rather than replaying the initial button press. */
    public void refreshSignalTraffic(ServerLevel level) {
        long now = level.getGameTime();
        var registry = AccessRegistry.get(level.getServer());
        String dimension = level.dimension().location().toString();
        for (var circuitEntry : circuits.entrySet()) {
            UUID id = circuitEntry.getKey(); Circuit circuit = circuitEntry.getValue();
            var emissions = new LinkedHashSet<SignalTraffic.Emission>();
            if (!locked(id) && (circuit.lockdown ? circuit.on : circuit.until > now)) {
                var linked = targets(level, id, circuit);
                if (circuit.lockdown) {
                    for (Target target : linked)
                        emissions.add(new SignalTraffic.Emission(id, target.id, SignalKind.LOCKDOWN));
                } else for (var request : requests.entrySet()) {
                    for (Pulse pulse : request.getValue()) {
                        Target target = pulse.target;
                        if (!id.equals(pulse.button) || !linked.contains(target) || locked(target.id)) continue;
                        AccessRegistry.DoorLink link = null;
                        if (target.door) {
                            if (target.id.equals(request.getKey()))
                                link = new AccessRegistry.DoorLink(dimension, target.position, target.id);
                        } else {
                            var reader = registry.find(pulse.code);
                            if (reader != null && reader.position.equals(target.position) && reader.dimension.equals(dimension)
                                && (reader.owner.equals(circuit.owner) || FrequencyNetwork.get(level).connected(level, id, target.id)))
                                link = AccessDoorService.doors(level, reader).stream()
                                    .filter(door -> door.instance().equals(request.getKey())
                                        && TerminalLinks.admissible(level, reader.position, target.id, door))
                                    .findFirst().orElse(null);
                        }
                        if (link == null) continue;
                        if (TerminalLinks.loaded(level, link.position())) {
                            var door = TerminalLinks.loadedDoor(level, link);
                            if (door == null || blocksDoor(level, door) || IndustrialDoorManager.fireAlarmActive(level, door)) continue;
                        }
                        emissions.add(new SignalTraffic.Emission(id, target.id, SignalKind.OPEN));
                        if (target.terminal())
                            emissions.add(new SignalTraffic.Emission(target.id, link.instance(), SignalKind.OPEN));
                    }
                }
            }
            SignalTraffic.maintainAll(level, id, emissions, circuit.lockdown ? Long.MAX_VALUE : circuit.until);
        }
    }

    public boolean buttonRequests(ServerLevel level, IndustrialDoorEntity door) {
        return buttonOpenUntil(level, door) > level.getGameTime();
    }

    /** Latest valid timed request: any remaining button can still hold the door open. */
    public long buttonOpenUntil(ServerLevel level, IndustrialDoorEntity door) {
        Set<Pulse> candidates = requests.get(door.doorInstanceId);
        if (candidates == null) return 0;
        if (candidates.removeIf(pulse -> {
            Circuit source = circuits.get(pulse.button);
            return source == null || source.until <= level.getGameTime() || !targets(level, pulse.button, source).contains(pulse.target);
        })) {
            if (candidates.isEmpty()) requests.remove(door.doorInstanceId);
            setDirty();
        }
        long until = 0;
        for (Pulse pulse : candidates) {
            Circuit source = circuits.get(pulse.button);
            if (locked(pulse.button) || locked(pulse.target.id)) continue;
            if (pulse.target.door) {
                if (pulse.target.id.equals(door.doorInstanceId) && pulse.target.position.equals(door.getBlockPos()))
                    until = Math.max(until, source.until);
                continue;
            }
            var entry = AccessRegistry.get(level.getServer()).find(pulse.code);
            if (entry != null && entry.position.equals(pulse.target.position)
                    && (entry.owner.equals(source.owner) || FrequencyNetwork.get(level).connected(level, pulse.button, pulse.target.id))
                    && entry.dimension.equals(level.dimension().location().toString())
                    && AccessDoorService.doors(level, entry).stream().anyMatch(link -> link.instance().equals(door.doorInstanceId)
                        && link.position().equals(door.getBlockPos())
                        && TerminalLinks.admissible(level, entry.position, pulse.target.id, link))) {
                until = Math.max(until, source.until);
            }
        }
        return until;
    }

    void cancelDoorRequests(UUID door) {
        if (requests.remove(door) != null) setDirty();
    }

    public void forgetDoor(UUID door) {
        cancelDoorRequests(door);
        Set<UUID> cameras = camerasByDoor.remove(door);
        if (cameras == null) return;
        for (UUID camera : cameras) {
            Set<UUID> remaining = new HashSet<>(cameraDoors.getOrDefault(camera, Set.of()));
            remaining.remove(door);
            if (remaining.isEmpty()) cameraDoors.remove(camera);
            else cameraDoors.put(camera, Set.copyOf(remaining));
        }
        setDirty();
    }

    public void clear(ServerLevel level, ControlTerminalEntity terminal) {
        Circuit circuit = circuit(terminal);
        if (circuit == null) return;
        circuit.links.clear();
        circuit.until = 0;
        changed(level);
    }

    public void remove(ServerLevel level, UUID endpoint) {
        SignalTraffic.stopOrigin(level, endpoint);
        recordCameraDoors(endpoint, Set.of());
        boolean changed = circuits.remove(endpoint) != null;
        for (Circuit circuit : circuits.values()) changed |= circuit.links.removeIf(target -> target.id.equals(endpoint));
        if (changed) changed(level);
    }

    private void removeExpired(ServerLevel level, long now) {
        for (var pulses : requests.values()) {
            pulses.removeIf(pulse -> {
                Circuit source = circuits.get(pulse.button);
                return source == null || source.until <= now || !targets(level, pulse.button, source).contains(pulse.target);
            });
        }
        requests.values().removeIf(Collection::isEmpty);
    }

    private void changed(ServerLevel level) {
        Set<Target> refresh = new HashSet<>(emitters);
        Set<Target> previouslyLocked = new HashSet<>(locked.values());
        rebuildLocks(level);
        for (Target target : locked.values())
            if (!previouslyLocked.remove(target)) syncTerminal(level, target);
        for (Target target : previouslyLocked) syncTerminal(level, target);
        refresh.addAll(emitters);
        for (Target target : refresh)
            if (TerminalLinks.loaded(level, target.position)
                && level.getBlockEntity(target.position) instanceof SignalEmitterEntity emitter && target.id.equals(emitter.instanceId))
                emitter.updatePower();
        removeExpired(level, level.getGameTime());
        refreshSignalTraffic(level);
        setDirty();
    }

    private static void syncTerminal(ServerLevel level, Target target) {
        var terminal = AccessTerminalEntity.resolve(level, target.position);
        if (terminal != null && target.id.equals(terminal.instanceId)) terminal.sync();
    }

    private void rebuildLocks(ServerLevel level) {
        locked.clear();
        emitters.clear();
        lockedReaderSites.clear();
        Map<UUID, Integer> incoming = new HashMap<>();
        circuits.forEach((id, circuit) -> { if (circuit.lockdown) incoming.put(id, 0); });
        Map<UUID, Set<Target>> linked = new HashMap<>();
        circuits.forEach((id, circuit) -> linked.put(id, targets(level, id, circuit)));
        incoming.keySet().forEach(id -> {
            for (Target target : linked.get(id)) if (target.terminal())
                incoming.computeIfPresent(target.id, (targetId, count) -> count + 1);
        });
        var ready = new ArrayDeque<UUID>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });
        while (!ready.isEmpty()) {
            UUID id = ready.removeFirst();
            Circuit circuit = circuits.get(id);
            boolean engaged = circuit.on && !locked(id);
            for (Target target : linked.get(id)) {
                if (engaged && target.emitter) emitters.add(target);
                if (engaged && !target.emitter) {
                    locked.put(target.id, target);
                    if (target.terminal()) blockReaderSite(level, id, target, circuit.owner);
                }
                if (target.terminal()) {
                    Integer remaining = incoming.computeIfPresent(target.id, (targetId, count) -> count - 1);
                    if (remaining != null && remaining == 0) ready.add(target.id);
                }
            }
        }
        // A disabled button or reader loses its current press. Resetting a lockdown won't replay it.
        for (UUID id : locked.keySet()) {
            Circuit circuit = circuits.get(id);
            if (circuit != null && !circuit.lockdown) {
                circuit.until = 0;
                for (Target reader : linked.get(id)) if (reader.terminal()) blockReaderSite(level, id, reader, circuit.owner);
            }
        }
        for (var pulses : requests.values()) pulses.removeIf(pulse -> locked(pulse.target.id));
    }

    private void blockReaderSite(ServerLevel level, UUID source, Target target, UUID owner) {
        if (level != null) {
            var peer = FrequencyNetwork.get(level).peers(level, source).stream()
                .filter(device -> device.id().equals(target.id)).findFirst();
            if (peer.isPresent()) owner = peer.get().owner();
        }
        lockedReaderSites.computeIfAbsent(new ReaderSite(target.position, owner), site -> new HashSet<>()).add(target.id);
    }

    private static CompoundTag writeTarget(Target target) {
        var tag = new CompoundTag();
        tag.putUUID("Id", target.id);
        tag.putLong("Position", target.position.asLong());
        tag.putBoolean("Emitter", target.emitter);
        if (target.door) tag.putBoolean("DirectDoor", true);
        return tag;
    }

    private static Target readTarget(CompoundTag tag) {
        return new Target(tag.getUUID("Id"), BlockPos.of(tag.getLong("Position")), tag.getBoolean("Emitter"), tag.getBoolean("DirectDoor"));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var rows = new ListTag();
        circuits.forEach((id, circuit) -> {
            var row = new CompoundTag();
            row.putUUID("Id", id);
            row.putUUID("Owner", circuit.owner);
            row.putLong("Position", circuit.position.asLong());
            row.putBoolean("Lockdown", circuit.lockdown);
            row.putBoolean("On", circuit.on);
            row.putLong("Until", circuit.until);
            var links = new ListTag();
            for (Target target : circuit.links) links.add(writeTarget(target));
            row.put("Links", links);
            rows.add(row);
        });
        tag.put("Circuits", rows);
        var pulses = new ListTag();
        requests.forEach((door, entries) -> {
            for (Pulse pulse : entries) {
                var row = writeTarget(pulse.target);
                row.putUUID("Door", door);
                row.putUUID("Button", pulse.button);
                if (pulse.code != null) row.putUUID("Code", pulse.code);
                pulses.add(row);
            }
        });
        tag.put("Requests", pulses);
        var cameraRows = new ListTag();
        cameraDoors.forEach((camera, doors) -> {
            var row = new CompoundTag();
            row.putUUID("Terminal", camera);
            var linked = new ListTag();
            for (UUID door : doors) {
                var item = new CompoundTag();
                item.putUUID("Door", door);
                linked.add(item);
            }
            row.put("Doors", linked);
            cameraRows.add(row);
        });
        tag.put("CameraDoors", cameraRows);
        return tag;
    }

    static TerminalControlData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new TerminalControlData();
        for (Tag item : tag.getList("Circuits", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) item;
            if (!row.hasUUID("Id") || !row.hasUUID("Owner")) continue;
            var circuit = new Circuit(BlockPos.of(row.getLong("Position")), row.getUUID("Owner"), row.getBoolean("Lockdown"));
            circuit.on = row.getBoolean("On");
            circuit.until = row.getLong("Until");
            for (Tag entry : row.getList("Links", Tag.TAG_COMPOUND)) {
                var target = (CompoundTag) entry;
                if (target.hasUUID("Id") && circuit.links.size() < MAX_LINKS) {
                    Target link = readTarget(target);
                    if (!link.id.equals(row.getUUID("Id")) && TerminalLinks.inRange(circuit.position, link.position))
                        circuit.links.add(link);
                }
            }
            data.circuits.put(row.getUUID("Id"), circuit);
        }
        for (Tag item : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) item;
            if (row.hasUUID("Door") && row.hasUUID("Button") && row.hasUUID("Id")
                    && (row.getBoolean("DirectDoor") || row.hasUUID("Code"))) {
                data.requests.computeIfAbsent(row.getUUID("Door"), id -> new HashSet<>())
                    .add(new Pulse(row.getUUID("Button"), readTarget(row), row.hasUUID("Code") ? row.getUUID("Code") : null));
            }
        }
        for (Tag item : tag.getList("CameraDoors", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) item;
            if (!row.hasUUID("Terminal")) continue;
            Set<UUID> doors = new HashSet<>();
            for (Tag raw : row.getList("Doors", Tag.TAG_COMPOUND)) {
                var door = (CompoundTag) raw;
                if (door.hasUUID("Door")) doors.add(door.getUUID("Door"));
            }
            data.recordCameraDoors(row.getUUID("Terminal"), doors);
        }
        data.rebuildLocks(null);
        return data;
    }
}
