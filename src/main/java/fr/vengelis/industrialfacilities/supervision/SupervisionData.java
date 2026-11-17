package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.security.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SupervisionData extends SavedData {
    private static final int HISTORY_LIMIT = 64;
    record Sector(UUID owner, String name) {}
    record Console(UUID id, BlockPos pos, Sector sector, List<String> sectors, GraphLayout.Canvas canvas) {
        Console(UUID id, BlockPos pos, Sector sector, List<String> sectors) {
            this(id, pos, sector, sectors, GraphLayout.Canvas.DEFAULT);
        }
        Console showing(String name) { return new Console(id, pos, new Sector(sector.owner, name), sectors, canvas); }

        Console renameSector(Sector previous, Sector next) {
            if (!sectors.contains(previous.name)) return this;
            return new Console(id, pos, sector.equals(previous) ? next : sector,
                sectors.stream().map(name -> name.equals(previous.name) ? next.name : name).toList(), canvas);
        }
    }
    private record Target(UUID id, BlockPos pos, String kind, String name) {
        CompoundTag write() {
            var tag = new CompoundTag();
            tag.putUUID("Id", id); tag.putLong("Pos", pos.asLong());
            tag.putString("Kind", kind); tag.putString("Name", name);
            return tag;
        }
        static Target read(CompoundTag n) {
            return new Target(n.getUUID("Id"), BlockPos.of(n.getLong("Pos")), n.getString("Kind"), n.getString("Name"));
        }
    }
    private record Terminal(Target target, Sector sector, UUID reader, List<Target> links) {}
    public record DisplayLocation(ResourceLocation dimension, BlockPos position) {
        void write(CompoundTag tag) {
            tag.putString("Dimension", dimension.toString());
            tag.putLong("Position", position.asLong());
        }
        static DisplayLocation read(CompoundTag tag) {
            if (!tag.contains("Position", Tag.TAG_LONG) || tag.getString("Dimension").isEmpty()) return null;
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            return dimension == null ? null : new DisplayLocation(dimension, BlockPos.of(tag.getLong("Position")));
        }
    }
    record Display(UUID source, String sector, String name, DisplayLocation location) {
        Display withSector(String value) { return new Display(source, value, name, location); }
    }
    public record LinkedDisplay(UUID id, String sector, String name, DisplayLocation location, boolean online) {
        CompoundTag write() {
            var tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Sector", sector);
            tag.putString("Name", name);
            if (location != null) location.write(tag);
            tag.putBoolean("Online", online);
            return tag;
        }
        static LinkedDisplay read(CompoundTag tag) {
            return new LinkedDisplay(tag.getUUID("Id"), tag.getString("Sector"), tag.getString("Name"),
                DisplayLocation.read(tag), tag.getBoolean("Online"));
        }
    }
    private final Map<UUID, Terminal> terminals = new HashMap<>();
    private final Map<UUID, Console> consoles = new HashMap<>();
    private final Map<UUID, Display> displays = new HashMap<>();
    private final Map<UUID, Map<String, Map<UUID, GraphLayout.Placement>>> layouts = new HashMap<>();
    private final Map<UUID, Map<UUID, String>> namesByOwner = new HashMap<>();
    private final Map<Sector, Deque<NetworkView.Notice>> history = new HashMap<>();
    private final Map<Sector, Map<UUID, String>> states = new HashMap<>();

    public static SupervisionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(SupervisionData::new, SupervisionData::load), "industrial_supervision");
    }

    static String cleanSector(String text) {
        String clean = text.strip().toUpperCase(Locale.ROOT);
        return clean.length() <= 24 && clean.matches("[\\p{L}\\p{N} _-]*") ? clean : null;
    }

    static void writeSectorNames(CompoundTag tag, List<String> names) {
        var sectors = new ListTag();
        names.forEach(name -> sectors.add(StringTag.valueOf(name)));
        tag.put("Sectors", sectors);
    }

    static List<String> readSectorNames(CompoundTag tag, String active) {
        var names = new LinkedHashSet<String>();
        for (Tag value : tag.getList("Sectors", Tag.TAG_STRING)) {
            String name = cleanSector(value.getAsString());
            if (name != null && !name.isEmpty()) names.add(name);
        }
        if (!active.isEmpty()) names.add(active);
        return List.copyOf(names);
    }

    Console console(UUID id) { return consoles.get(id); }
    void register(Console console) {
        var before = consoles.put(console.id, console);
        if (console.equals(before)) return;
        var positions = layouts.get(console.id);
        if (before != null && positions != null && !before.canvas.equals(console.canvas)) {
            for (var layout : positions.values())
                layout.replaceAll((id, place) -> place.resized(before.canvas, console.canvas));
        }
        setDirty();
    }
    void removeConsole(UUID id) {
        boolean changed = consoles.remove(id) != null;
        changed |= displays.values().removeIf(display -> display.source.equals(id));
        if (layouts.remove(id) != null || changed) setDirty();
    }

    Display display(UUID id) { return displays.get(id); }

    void bindDisplay(UUID id, Console source, String name, DisplayLocation location) {
        var existing = displays.get(id);
        String selection = existing != null && existing.source.equals(source.id) ? existing.sector : source.sector.name;
        saveDisplay(id, new Display(source.id, selection, name, location));
    }

    void locateDisplay(UUID id, DisplayLocation location) {
        var display = displays.get(id);
        if (display != null && !location.equals(display.location))
            saveDisplay(id, new Display(display.source, display.sector, display.name, location));
    }

    boolean renameDisplay(UUID id, String value) {
        var display = displays.get(id);
        String name = cleanName(value);
        if (display == null || name == null) return false;
        saveDisplay(id, new Display(display.source, display.sector, name, display.location));
        return true;
    }

    private void saveDisplay(UUID id, Display display) {
        if (!display.equals(displays.put(id, display))) setDirty();
    }

    List<LinkedDisplay> linkedDisplays(ServerLevel level, Console source) {
        return displays.entrySet().stream()
            .filter(entry -> entry.getValue().source.equals(source.id))
            .map(entry -> {
                var link = entry.getValue();
                var sector = source.sectors.contains(link.sector) ? link.sector : source.sector.name;
                return new LinkedDisplay(entry.getKey(), sector, link.name, link.location,
                    loadedDisplay(level, entry.getKey(), link) != null);
            })
            .sorted(Comparator.comparing((LinkedDisplay display) -> display.location == null ? "" : display.location.dimension.toString())
                .thenComparingLong(display -> display.location == null ? 0 : display.location.position.asLong())
                .thenComparing(LinkedDisplay::id))
            .toList();
    }

    static SupervisionConsoleEntity loadedDisplay(ServerLevel source, UUID id, Display display) {
        if (display.location == null) return null;
        var location = display.location;
        var level = source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, location.dimension));
        if (level == null || !TerminalLinks.loaded(level, location.position)) return null;
        return level.getBlockEntity(location.position) instanceof SupervisionConsoleEntity entity
                && entity.isDisplay() && entity.assembled() && id.equals(entity.consoleId) && entity.displaysSourceIn(source)
            ? entity : null;
    }

    boolean selectDisplaySector(UUID id, String sector) {
        var link = displays.get(id);
        var source = link == null ? null : consoles.get(link.source);
        if (source == null || !(source.sectors.contains(sector) || sector.isEmpty() && source.sectors.isEmpty()))
            return false;
        if (!link.sector.equals(sector)) saveDisplay(id, link.withSector(sector));
        return true;
    }

    void removeDisplay(UUID id) {
        if (displays.remove(id) != null) setDirty();
    }

    boolean renameSector(ServerLevel level, Sector previous, String name) {
        var next = new Sector(previous.owner, name);
        if (previous.equals(next)) return true;
        if (sectorNameInUse(next)) return false;

        terminals.replaceAll((id, terminal) -> terminal.sector.equals(previous)
            ? new Terminal(terminal.target, next, terminal.reader, terminal.links) : terminal);
        moveEntry(history, previous, next);
        moveEntry(states, previous, next);

        var affected = new ArrayList<Console>();
        consoles.replaceAll((id, console) -> {
            if (!console.sector.owner.equals(previous.owner)) return console;
            var positions = layouts.get(id);
            if (positions != null) moveEntry(positions, previous.name, next.name);
            var renamed = console.renameSector(previous, next);
            if (renamed != console) affected.add(renamed);
            return renamed;
        });
        displays.replaceAll((id, display) -> {
            var source = consoles.get(display.source);
            return source != null && source.sector.owner.equals(previous.owner) && display.sector.equals(previous.name)
                && source.sectors.contains(next.name) ? display.withSector(next.name) : display;
        });
        setDirty();

        for (Console console : affected) {
            if (TerminalLinks.loaded(level, console.pos)
                    && level.getBlockEntity(console.pos) instanceof SupervisionConsoleEntity entity
                    && entity.assembled() && console.id.equals(entity.consoleId)) entity.update(level);
        }
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof SupervisionMenu menu)
                menu.sectorRenamed(player, level, previous, next);
        }
        return true;
    }

    private boolean sectorNameInUse(Sector sector) {
        if (history.containsKey(sector) || terminals.values().stream().anyMatch(t -> t.sector.equals(sector))) return true;
        for (Console console : consoles.values()) {
            if (console.sector.owner.equals(sector.owner) && (console.sectors.contains(sector.name)
                    || layouts.getOrDefault(console.id, Map.of()).containsKey(sector.name))) return true;
        }
        return false;
    }

    private static <K, V> void moveEntry(Map<K, V> map, K previous, K next) {
        V value = map.remove(previous);
        if (value != null) map.put(next, value);
    }

    public static final int MAX_NAME = 32;

    public String customName(UUID owner, UUID device) {
        var names = namesByOwner.get(owner);
        return names == null ? "" : names.getOrDefault(device, "");
    }

    private static String cleanName(String text) {
        String name = text.strip();
        return name.length() > MAX_NAME || name.codePoints().anyMatch(ch -> Character.isISOControl(ch) || ch == '§')
            ? null : name;
    }

    boolean rename(Sector sector, NetworkView.Node node, String text) {
        String name = cleanName(text);
        if (!node.canRename() || name == null) return false;
        var names = namesByOwner.computeIfAbsent(sector.owner, owner -> new HashMap<>());
        if (name.isEmpty()) names.remove(node.id());
        else names.put(node.id(), name);
        if (names.isEmpty()) namesByOwner.remove(sector.owner);
        setDirty();
        return true;
    }

    void place(Console console, UUID node, GraphLayout.Placement placement) {
        layouts.computeIfAbsent(console.id, id -> new HashMap<>())
            .computeIfAbsent(console.sector.name, sector -> new HashMap<>())
            .put(node, placement);
        setDirty();
    }

    void resetLayout(Console console) {
        var bySector = layouts.get(console.id);
        if (bySector == null || bySector.remove(console.sector.name) == null) return;
        if (bySector.isEmpty()) layouts.remove(console.id);
        setDirty();
    }

    NetworkView view(ServerLevel level, Console console) {
        NetworkView view = snapshot(level, console.sector).withCanvas(console.canvas);
        var saved = layouts.getOrDefault(console.id, Map.of()).getOrDefault(console.sector.name, Map.of());
        if (saved.isEmpty()) return view;
        var relevant = new HashMap<UUID, GraphLayout.Placement>();
        for (var node : view.nodes()) {
            var p = saved.get(node.id());
            if (p != null) relevant.put(node.id(), p);
        }
        return view.withLayout(Map.copyOf(relevant));
    }
    public void removeTerminal(UUID id) { if (terminals.remove(id) != null) setDirty(); }
    String sector(AccessTerminalEntity terminal) {
        Terminal saved = terminals.get(terminal.instanceId);
        return saved == null ? "" : saved.sector.name;
    }

    void assign(ServerLevel level, AccessTerminalEntity terminal, String name) {
        if (name.isEmpty()) {
            removeTerminal(terminal.instanceId);
            return;
        }
        String kind = terminal instanceof CameraTerminalEntity ? "automation" : "terminal";
        var target = new Target(terminal.instanceId, terminal.getBlockPos(), kind,
            terminal.getBlockState().getBlock().getDescriptionId());
        var row = new Terminal(target, new Sector(terminal.creator, name), terminal.terminalId, List.of());
        terminals.put(terminal.instanceId, row);
        refresh(level, terminal);
        setDirty();
    }

    public void refresh(ServerLevel level, AccessTerminalEntity terminal) {
        Terminal row = terminals.get(terminal.instanceId);
        if (row == null || !row.target.pos.equals(terminal.getBlockPos()) || !row.sector.owner.equals(terminal.creator)) return;
        var links = new ArrayList<Target>();
        if (terminal instanceof CameraTerminalEntity camera) {
            for (var link : camera.linkViews()) {
                if (TerminalLinks.inRange(row.target.pos, link.pos()))
                    links.add(new Target(link.id(), link.pos(), link.kind(), link.kind().equals("door") ? "" : link.name()));
            }
        } else if (terminal instanceof ControlTerminalEntity control) {
            for (var link : TerminalControlData.get(level).links(control))
                links.add(new Target(link.id(), link.position(), link.emitter() ? "emitter" : "terminal", ""));
        } else if (terminal instanceof KeypassTerminalEntity manager && manager.manager()) {
            for (var reader : KeypassData.get(level).readers(manager))
                links.add(new Target(reader.id(), reader.position(), "terminal", reader.name()));
        }
        var updated = new Terminal(row.target, row.sector, terminal.terminalId, List.copyOf(links));
        if (!updated.equals(row)) {
            terminals.put(terminal.instanceId, updated);
            setDirty();
        }
    }

    NetworkView snapshot(ServerLevel level, Sector sector) {
        if (sector.name.isEmpty()) return NetworkView.EMPTY;
        var nodes = new LinkedHashMap<UUID, NetworkView.Node>();
        var edges = new LinkedHashSet<NetworkView.Edge>();
        var registry = AccessRegistry.get(level.getServer());
        String dimension = level.dimension().location().toString();
        var physicalReaders = new HashMap<UUID, AccessRegistry.Entry>();
        for (var reader : registry.entries())
            if (reader.instanceId() != null && reader.owner.equals(sector.owner) && reader.dimension.equals(dimension))
                physicalReaders.put(reader.instanceId(), reader);
        var members = terminals.values().stream().filter(t -> t.sector.equals(sector))
            .sorted(Comparator.comparingLong(t -> t.target.pos.asLong())).toList();
        for (Terminal source : members) {
            nodes.putIfAbsent(source.target.id, inspect(level, source.target));
            var reader = source.reader == null ? null : registry.find(source.reader);
            if (reader != null) {
                for (var link : reader.doors()) {
                    if (TerminalLinks.admissible(level, source.target.pos, link))
                        connect(level, nodes, edges, source.target.id, new Target(link.instance(), link.position(), "door", ""));
                }
            }
            for (Target target : source.links) {
                if (target.kind.equals("door") && registry.obsoleteDoorLink(dimension, target.pos, target.id)) continue;
                if (target.kind.equals("terminal") || target.kind.equals("automation")) {
                    Terminal member = terminals.get(target.id);
                    if (member != null && member.sector.equals(sector)) {
                        connect(level, nodes, edges, source.target.id, member.target);
                    } else if (source.target.name.equals("block.industrial_facilities.terminal_button")) {
                        var linkedReader = physicalReaders.get(target.id);
                        if (linkedReader == null || !linkedReader.position.equals(target.pos)) continue;
                        String key = linkedReader.keypass ? "block.industrial_facilities.keypass_reader" : "block.industrial_facilities.terminal_reader";
                        connect(level, nodes, edges, source.target.id, new Target(target.id, target.pos, "terminal", key));
                        for (var link : linkedReader.doors())
                            if (TerminalLinks.admissible(level, target.pos, link))
                                connect(level, nodes, edges, target.id, new Target(link.instance(),link.position(),"door",""));
                    }
                } else {
                    connect(level, nodes, edges, source.target.id, target);
                }
            }
        }
        Map<UUID, String> previous = states.getOrDefault(sector, Map.of());
        var current = new HashMap<UUID, String>();
        for (var node : nodes.values()) {
            current.put(node.id(), node.status());
            String old = previous.get(node.id());
            if (old != null && !old.equals(node.status())) log(sector, new NetworkView.Notice(level.getGameTime(), node));
        }
        states.put(sector, current);
        Collection<NetworkView.Notice> entries = history.get(sector);
        var names = namesByOwner.getOrDefault(sector.owner, Map.of());
        var named = nodes.values().stream().map(node -> applyName(names, node)).toList();
        var events = entries == null ? List.<NetworkView.Notice>of() : entries.stream()
            .map(e -> new NetworkView.Notice(e.tick(), applyName(names, e.node()))).toList();
        return new NetworkView(named, List.copyOf(edges), events);
    }

    private static NetworkView.Node applyName(Map<UUID, String> names, NetworkView.Node node) {
        String name = names.get(node.id());
        return name == null ? node : node.withName(name);
    }

    private static void connect(ServerLevel level, Map<UUID, NetworkView.Node> nodes,
                                Set<NetworkView.Edge> edges, UUID source, Target target) {
        nodes.computeIfAbsent(target.id, id -> inspect(level, target));
        edges.add(new NetworkView.Edge(source, target.id));
    }

    private static NetworkView.Node inspect(ServerLevel level, Target target) {
        String name = target.name;
        String title = NetworkView.UI + "kind." + target.kind;
        String state = "offline";
        var be = TerminalLinks.loaded(level, target.pos) ? level.getBlockEntity(target.pos) : null;
        if (target.kind.equals("terminal") || target.kind.equals("automation")) {
            title = target.name;
            name = "";
        }
        if (be instanceof AccessTerminalEntity terminal && target.id.equals(terminal.instanceId)) {
            title = terminal.getBlockState().getBlock().getDescriptionId();
            if (terminal instanceof KeypassTerminalEntity keypass) name = KeypassData.get(level).readerName(keypass);
            state = terminal.isLockedDown() ? "lockdown"
                : terminal instanceof CameraTerminalEntity camera ? (camera.isRunning() ? "running" : "stopped")
                : terminal.accessGranted ? "active" : "online";
        } else if (be instanceof IndustrialDoorEntity door && target.id.equals(door.doorInstanceId)) {
            title = door.getBlockState().getBlock().getDescriptionId();
            state = TerminalControlData.get(level).blocksDoor(level, door) ? "lockdown"
                : door.paused ? "obstructed" : door.progress != door.targetProgress() ? "moving"
                : door.progress >= 1 ? "open" : door.progress > 0 ? "partial" : "closed";
        } else if (be instanceof SecurityCameraEntity camera && target.id.equals(camera.instanceId)) {
            state = camera.stage >= 3 ? "alert" : camera.stage == 2 ? "tracking" : "online";
        } else if (be instanceof SignalEmitterEntity emitter && target.id.equals(emitter.instanceId)) {
            state = emitter.getBlockState().getValue(SignalEmitterBlock.POWERED) ? "active" : "online";
        } else if (be instanceof AlarmBlockEntity alarm && target.id.equals(alarm.automationInstanceId)) {
            state = alarm.powered() ? "active" : "online";
        } else if (be != null && target.kind.equals("panel")) {
            state = "online";
        }
        return new NetworkView.Node(target.id, target.kind, name, title, state, target.pos.toShortString());
    }

    public void denied(ServerLevel level, AccessTerminalEntity terminal) {
        Terminal row = terminals.get(terminal.instanceId);
        if (row == null) return;
        var node = inspect(level, row.target);
        log(row.sector, new NetworkView.Notice(level.getGameTime(),
            new NetworkView.Node(node.id(), node.kind(), node.name(), node.titleKey(), "denied", node.position())));
    }

    private void log(Sector sector, NetworkView.Notice notice) {
        var entries = history.computeIfAbsent(sector, key -> new ArrayDeque<>());
        if (entries.size() == HISTORY_LIMIT) entries.removeLast();
        entries.addFirst(notice);
        setDirty();
    }

    private static void writeSector(CompoundTag tag, Sector sector) {
        tag.putUUID("Owner", sector.owner);
        tag.putString("Sector", sector.name);
    }
    private static Sector readSector(CompoundTag tag) { return new Sector(tag.getUUID("Owner"), tag.getString("Sector")); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var savedTerminals = new ListTag();
        terminals.values().forEach(t -> {
            var entry = t.target.write();
            writeSector(entry, t.sector);
            if (t.reader != null) entry.putUUID("Reader", t.reader);
            var linked = new ListTag();
            t.links.forEach(target -> linked.add(target.write()));
            entry.put("Links", linked);
            savedTerminals.add(entry);
        });
        tag.put("Terminals", savedTerminals);
        var savedConsoles = new ListTag();
        consoles.values().forEach(c -> {
            var entry = new CompoundTag();
            entry.putUUID("Id", c.id); entry.putLong("Pos", c.pos.asLong());
            c.canvas.write(entry);
            writeSector(entry, c.sector);
            writeSectorNames(entry, c.sectors);
            var positions = new ListTag();
            layouts.getOrDefault(c.id, Map.of()).forEach((sector, layout) -> {
                var page = new CompoundTag();
                page.putString("Sector", sector);
                page.put("Positions", GraphLayout.write(layout));
                positions.add(page);
            });
            if (!positions.isEmpty()) entry.put("Layouts", positions);
            savedConsoles.add(entry);
        });
        tag.put("Consoles", savedConsoles);
        var savedDisplays = new ListTag();
        displays.forEach((id, display) -> {
            var entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putUUID("Source", display.source);
            entry.putString("Sector", display.sector);
            if (!display.name.isEmpty()) entry.putString("Name", display.name);
            if (display.location != null) display.location.write(entry);
            savedDisplays.add(entry);
        });
        if (!savedDisplays.isEmpty()) tag.put("Displays", savedDisplays);
        var savedNames = new ListTag();
        namesByOwner.forEach((owner, names) -> names.forEach((id, name) -> {
            var value = new CompoundTag();
            value.putUUID("Owner", owner);
            value.putUUID("Id", id);
            value.putString("Name", name);
            savedNames.add(value);
        }));
        tag.put("Names", savedNames);
        var journals = new ListTag();
        history.forEach((sector, entries) -> {
            var row = new CompoundTag();
            writeSector(row, sector);
            var notices = new ListTag();
            entries.forEach(event -> notices.add(event.write()));
            row.put("Events", notices);
            journals.add(row);
        });
        tag.put("History", journals);
        return tag;
    }

    static SupervisionData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SupervisionData();
        for (var raw : tag.getList("Terminals", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue;
            var target = Target.read(entry);
            var links = new ArrayList<Target>();
            for (var link : entry.getList("Links", Tag.TAG_COMPOUND))
                if (((CompoundTag) link).hasUUID("Id")) links.add(Target.read((CompoundTag) link));
            data.terminals.put(target.id, new Terminal(target, readSector(entry),
                entry.hasUUID("Reader") ? entry.getUUID("Reader") : null, List.copyOf(links)));
        }
        for (var raw : tag.getList("Consoles", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue;
            UUID id = entry.getUUID("Id");
            var sector = readSector(entry);
            var names = readSectorNames(entry, sector.name);
            if (sector.name.isEmpty() && !names.isEmpty()) sector = new Sector(sector.owner, names.getFirst());
            var canvas = GraphLayout.Canvas.read(entry);
            data.consoles.put(id, new Console(id, BlockPos.of(entry.getLong("Pos")), sector, names, canvas));
            for (var rawLayout : entry.getList("Layouts", Tag.TAG_COMPOUND)) {
                var layout = (CompoundTag) rawLayout;
                var positions = GraphLayout.read(layout.getList("Positions", Tag.TAG_COMPOUND), canvas);
                if (!positions.isEmpty()) data.layouts.computeIfAbsent(id, key -> new HashMap<>())
                    .put(layout.getString("Sector"), new HashMap<>(positions));
            }
        }
        for (var raw : tag.getList("Displays", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (entry.hasUUID("Id") && entry.hasUUID("Source") && data.consoles.containsKey(entry.getUUID("Source")))
                data.displays.put(entry.getUUID("Id"), new Display(entry.getUUID("Source"), entry.getString("Sector"),
                    Objects.requireNonNullElse(cleanName(entry.getString("Name")), ""), DisplayLocation.read(entry)));
        }
        for (var raw : tag.getList("Names", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            String name = entry.getString("Name");
            if (entry.hasUUID("Owner") && entry.hasUUID("Id") && !name.isEmpty() && name.length() <= MAX_NAME)
                data.namesByOwner.computeIfAbsent(entry.getUUID("Owner"), owner -> new HashMap<>()).put(entry.getUUID("Id"), name);
        }
        for (var raw : tag.getList("History", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Owner")) continue;
            var events = new ArrayDeque<NetworkView.Notice>();
            for (var notice : entry.getList("Events", Tag.TAG_COMPOUND))
                if (((CompoundTag) notice).hasUUID("Id") && events.size() < HISTORY_LIMIT)
                    events.add(NetworkView.Notice.read((CompoundTag) notice));
            data.history.put(readSector(entry), events);
        }
        return data;
    }
}
