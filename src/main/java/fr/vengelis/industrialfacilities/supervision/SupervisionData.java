package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.security.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import fr.vengelis.industrialfacilities.frequency.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SupervisionData extends SavedData {
    static final String DEFAULT_FREQUENCY_SECTOR = "Radio";
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
    record Display(UUID source, String sector, String name, DisplayLocation location, boolean manual) {
        Display(UUID source, String sector, String name, DisplayLocation location) { this(source, sector, name, location, false); }
        Display withSector(String value) { return new Display(source, value, name, location, manual); }
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
    private final Map<UUID, Map<String, Map<GraphRoutes.Key, List<GraphRoutes.Bend>>>> routes = new HashMap<>();
    private final Map<UUID, Map<String, Map<GraphRoutes.SocketKey, GraphRoutes.Socket>>> sockets = new HashMap<>();
    private final Map<UUID, Map<String, Map<GraphRoutes.Key, GraphRoutes.Key>>> joins = new HashMap<>();
    private final Map<UUID, Map<UUID, String>> namesByOwner = new HashMap<>();
    private final Map<Sector, Deque<NetworkView.Notice>> history = new HashMap<>();
    private final Map<Sector, Map<UUID, String>> states = new HashMap<>();
    private final Map<UUID, Map<String, Map<UUID, String>>> frequencyStates = new HashMap<>();

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
        if (before != null && !before.canvas.equals(console.canvas)) {
            if (positions != null) for (var layout : positions.values())
                layout.replaceAll((id, place) -> place.resized(before.canvas, console.canvas));
            for (var sector : routes.getOrDefault(console.id, Map.of()).values())
                sector.replaceAll((key, points) -> points.stream().map(point -> point.resized(before.canvas, console.canvas)).toList());
        }
        setDirty();
    }
    void removeConsole(UUID id) {
        frequencyStates.remove(id);
        boolean changed = consoles.remove(id) != null;
        changed |= displays.values().removeIf(display -> display.source.equals(id));
        changed |= layouts.remove(id) != null;
        changed |= routes.remove(id) != null;
        changed |= sockets.remove(id) != null;
        changed |= joins.remove(id) != null;
        if (changed) setDirty();
    }

    Display display(UUID id) { return displays.get(id); }

    void bindDisplay(UUID id, Console source, String name, DisplayLocation location) {
        bindDisplay(id, source, name, location, true);
    }

    void bindDisplay(UUID id, Console source, String name, DisplayLocation location, boolean manual) {
        var existing = displays.get(id);
        String selection = existing != null && existing.source.equals(source.id) ? existing.sector : source.sector.name;
        saveDisplay(id, new Display(source.id, selection, name, location, manual));
    }

    void locateDisplay(UUID id, DisplayLocation location) {
        var display = displays.get(id);
        if (display != null && !location.equals(display.location))
            saveDisplay(id, new Display(display.source, display.sector, display.name, location, display.manual));
    }

    boolean renameDisplay(UUID id, String value) {
        var display = displays.get(id);
        String name = cleanName(value);
        if (display == null || name == null) return false;
        saveDisplay(id, new Display(display.source, display.sector, name, display.location, display.manual));
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
            var paths = routes.get(id);
            if (paths != null) moveEntry(paths, previous.name, next.name);
            var attachments = sockets.get(id);
            if (attachments != null) moveEntry(attachments, previous.name, next.name);
            var junctions = joins.get(id);
            if (junctions != null) moveEntry(junctions, previous.name, next.name);
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
                    || layouts.getOrDefault(console.id, Map.of()).containsKey(sector.name)
                    || routes.getOrDefault(console.id, Map.of()).containsKey(sector.name)
                    || sockets.getOrDefault(console.id, Map.of()).containsKey(sector.name)
                    || joins.getOrDefault(console.id, Map.of()).containsKey(sector.name))) return true;
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

    void placeRoute(Console console, GraphRoutes.Key key, List<GraphRoutes.Bend> points) {
        joinRoutes(console, key, null);
        if (points.isEmpty()) {
            var bySector = routes.get(console.id);
            if (bySector == null) return;
            var saved = bySector.get(console.sector.name);
            if (saved == null || saved.remove(key) == null) return;
            if (saved.isEmpty()) bySector.remove(console.sector.name);
            if (bySector.isEmpty()) routes.remove(console.id);
        } else {
            var saved = routes.computeIfAbsent(console.id, id -> new HashMap<>())
                .computeIfAbsent(console.sector.name, sector -> new HashMap<>());
            if (!saved.containsKey(key) && saved.size() >= GraphRoutes.MAX_ROUTES) return;
            saved.put(key, points.stream().map(point -> point.within(console.canvas)).toList());
        }
        setDirty();
    }

    void joinRoutes(Console console, GraphRoutes.Key child, GraphRoutes.Key parent) {
        var bySector = joins.get(console.id);
        if (parent == null) {
            if (bySector == null) return;
            var saved = bySector.get(console.sector.name);
            if (saved == null || saved.remove(child) == null) return;
            if (saved.isEmpty()) bySector.remove(console.sector.name);
            if (bySector.isEmpty()) joins.remove(console.id);
        } else {
            var existing = bySector == null ? Map.<GraphRoutes.Key, GraphRoutes.Key>of()
                : bySector.getOrDefault(console.sector.name, Map.of());
            if (!GraphRoutes.canJoin(child, parent, existing)
                    || !existing.containsKey(child) && existing.size() >= GraphRoutes.MAX_ROUTES) return;
            joins.computeIfAbsent(console.id, id -> new HashMap<>())
                .computeIfAbsent(console.sector.name, sector -> new HashMap<>()).put(child, parent);
            var paths = routes.getOrDefault(console.id, Map.of()).get(console.sector.name);
            if (paths != null) paths.remove(child);
        }
        setDirty();
    }

    void placeSocket(Console console, GraphRoutes.SocketKey key, GraphRoutes.Socket socket) {
        if (socket == null) {
            var bySector = sockets.get(console.id);
            if (bySector == null) return;
            var saved = bySector.get(console.sector.name);
            if (saved == null || saved.remove(key) == null) return;
            if (saved.isEmpty()) bySector.remove(console.sector.name);
            if (bySector.isEmpty()) sockets.remove(console.id);
        } else {
            var saved = sockets.computeIfAbsent(console.id, id -> new HashMap<>())
                .computeIfAbsent(console.sector.name, sector -> new HashMap<>());
            if (!saved.containsKey(key) && saved.size() >= GraphRoutes.MAX_ROUTES) return;
            saved.put(key, socket);
        }
        setDirty();
    }

    NetworkView view(ServerLevel level, Console console) {
        var network = FrequencyNetwork.get(level);
        NetworkView view = (network.migrated(console.id)
            ? frequencySnapshot(level, console) : snapshot(level, console.sector)).withCanvas(console.canvas);
        view = view.withSignals(SignalTraffic.snapshot(level, view.nodes()));
        var saved = layouts.getOrDefault(console.id, Map.of()).getOrDefault(console.sector.name, Map.of());
        var paths = routes.getOrDefault(console.id, Map.of()).getOrDefault(console.sector.name, Map.of());
        if (!paths.isEmpty()) {
            var relevantPaths = new HashMap<GraphRoutes.Key, List<GraphRoutes.Bend>>();
            view.wires().forEach(wire -> copyRoute(paths, relevantPaths, GraphRoutes.key(wire)));
            view.edges().stream().filter(edge -> !edge.frequency())
                .forEach(edge -> copyRoute(paths, relevantPaths, GraphRoutes.key(edge)));
            view = view.withRoutes(Map.copyOf(relevantPaths));
        }
        var attachments = sockets.getOrDefault(console.id, Map.of()).getOrDefault(console.sector.name, Map.of());
        if (!attachments.isEmpty()) {
            var ids = view.nodes().stream().map(NetworkView.Node::id).collect(java.util.stream.Collectors.toSet());
            var relevantSockets = new HashMap<GraphRoutes.SocketKey, GraphRoutes.Socket>();
            attachments.forEach((key, socket) -> { if (ids.contains(key.node())) relevantSockets.put(key, socket); });
            view = view.withSockets(Map.copyOf(relevantSockets));
        }
        var junctions = joins.getOrDefault(console.id, Map.of()).getOrDefault(console.sector.name, Map.of());
        if (!junctions.isEmpty()) {
            var keys = view.wires().stream().map(GraphRoutes::key).collect(java.util.stream.Collectors.toSet());
            var relevantJoins = new HashMap<GraphRoutes.Key, GraphRoutes.Key>();
            junctions.forEach((child, parent) -> {
                if (keys.contains(child) && keys.contains(parent)) relevantJoins.put(child, parent);
            });
            view = view.withJoins(Map.copyOf(relevantJoins));
        }
        if (saved.isEmpty()) return view;
        var relevant = new HashMap<UUID, GraphLayout.Placement>();
        for (var node : view.nodes()) {
            var p = saved.get(node.id());
            if (p != null) relevant.put(node.id(), p);
        }
        return view.withLayout(Map.copyOf(relevant));
    }
    void refreshTrafficViews(ServerLevel level) {
        for (var source : List.copyOf(consoles.values())) {
            if (TerminalLinks.loaded(level, source.pos) && level.getBlockEntity(source.pos) instanceof SupervisionConsoleEntity entity
                    && !entity.isDisplay() && source.id.equals(entity.consoleId) && entity.assembled()) entity.update(level);
        }
        for (var entry : List.copyOf(displays.entrySet())) {
            var display = loadedDisplay(level, entry.getKey(), entry.getValue());
            if (display != null && display.getLevel() instanceof ServerLevel targetLevel) display.update(targetLevel);
        }
    }
    private static void copyRoute(Map<GraphRoutes.Key, List<GraphRoutes.Bend>> paths,
                                  Map<GraphRoutes.Key, List<GraphRoutes.Bend>> relevant, GraphRoutes.Key key) {
        var points = paths.get(key);
        if (points != null) relevant.put(key, points);
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
                links.add(new Target(link.id(), link.position(), link.kind(), ""));
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

    Set<String> frequencySectors(ServerLevel level, UUID console) {
        Set<String> result = new LinkedHashSet<>();
        for (var peer : FrequencyNetwork.get(level).visiblePeers(level, console)) {
            var row = terminals.get(peer.id());
            if (row != null) result.add(row.sector.name);
        }
        return result;
    }

    private NetworkView frequencySnapshot(ServerLevel level, Console console) {
        var network = FrequencyNetwork.get(level);
        var peers = network.visiblePeers(level, console.id);
        var nearby = new LinkedHashMap<UUID, FrequencyEndpoint>();
        var nodes = new LinkedHashMap<UUID, NetworkView.Node>();
        var edges = new LinkedHashSet<NetworkView.Edge>();
        Set<UUID> doorsWithTerminals = new HashSet<>();
        peers.forEach(peer -> nearby.put(peer.id(), peer));
        for (var peer : peers) {
            if (!peer.type().terminal()) continue;
            Terminal row = terminals.get(peer.id());
            String sector = row == null ? console.sectors.stream().findFirst().orElse("") : row.sector.name;
            var candidates = new ArrayList<>(network.peers(level, peer.id()));
            if (peer.type() == FrequencyEndpoint.Type.CAMERA_TERMINAL) {
                candidates.removeIf(target -> target.type() == FrequencyEndpoint.Type.CAMERA);
                for (var input : network.senders(level, peer.id()))
                    if (input.type() == FrequencyEndpoint.Type.CAMERA) candidates.add(input);
            }
            List<FrequencyEndpoint> connected = candidates.stream()
                .filter(target -> nearby.containsKey(target.id()) && supports(peer.type(), target.type()))
                .filter(target -> peer.type() != FrequencyEndpoint.Type.KEYPASS_MANAGER
                    || KeypassData.get(level).frequencyBinding(level, peer.id(), target.id())).toList();
            for (var target : connected) if (target.type() == FrequencyEndpoint.Type.DOOR) doorsWithTerminals.add(target.id());
            if (!console.sector.name.equals(sector)) continue;
            nodes.put(peer.id(), inspect(level, target(level, peer)));
            for (var target : connected) {
                var targetRow = terminals.get(target.id());
                if (targetRow != null && !targetRow.sector.name.equals(sector)) continue;
                connect(level, nodes, edges, peer.id(), target(level, target));
            }
        }
        if (console.sector.name.equals(console.sectors.stream().findFirst().orElse(""))) {
            for (var peer : peers)
                if (!peer.type().terminal() && peer.type() != FrequencyEndpoint.Type.DISPLAY
                        && peer.type() != FrequencyEndpoint.Type.CONSOLE && !doorsWithTerminals.contains(peer.id()))
                    nodes.putIfAbsent(peer.id(), inspect(level, target(level, peer)));
        }
        var lastBySector = frequencyStates.computeIfAbsent(console.id, id -> new HashMap<>());
        var before = lastBySector.getOrDefault(console.sector.name, Map.of());
        var current = new HashMap<UUID, String>();
        for (var node : nodes.values()) {
            current.put(node.id(), node.status());
            if (before.containsKey(node.id()) && !node.status().equals(before.get(node.id())))
                log(console.sector, new NetworkView.Notice(level.getGameTime(), node));
        }
        lastBySector.put(console.sector.name, current);
        var names = namesByOwner.getOrDefault(console.sector.owner, Map.of());
        var results = nodes.values().stream().map(node -> applyName(names, node)).toList();
        var events = history.getOrDefault(console.sector, new ArrayDeque<>()).stream().filter(entry -> nodes.containsKey(entry.node().id()))
            .map(entry -> new NetworkView.Notice(entry.tick(), applyName(names, entry.node()))).toList();
        return FrequencyTopology.view(network, console.sector.owner, results, List.copyOf(edges), events);
    }

    private Target target(ServerLevel level, FrequencyEndpoint device) {
        Terminal row = terminals.get(device.id());
        if (row != null) return row.target;
        String kind = device.type().cameraKind();
        if (kind.isEmpty()) kind = device.type() == FrequencyEndpoint.Type.CAMERA_TERMINAL ? "automation" : "terminal";
        String label = TerminalLinks.loaded(level, device.pos()) ? level.getBlockState(device.pos()).getBlock().getDescriptionId() : "";
        return new Target(device.id(), device.pos(), kind, kind.equals("door") ? "" : label);
    }

    private static boolean supports(FrequencyEndpoint.Type source, FrequencyEndpoint.Type target) {
        return switch (source) {
            case READER, KEYPASS -> target == FrequencyEndpoint.Type.DOOR;
            case CAMERA_TERMINAL -> !target.cameraKind().isEmpty();
            case KEYPASS_MANAGER -> target == FrequencyEndpoint.Type.KEYPASS;
            case BUTTON -> target.reader() || target == FrequencyEndpoint.Type.DOOR;
            case LOCKDOWN -> target.terminal() && target != FrequencyEndpoint.Type.LOCKDOWN
                || target == FrequencyEndpoint.Type.EMITTER || target == FrequencyEndpoint.Type.DOOR;
            default -> false;
        };
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
                for (var link : AccessDoorService.doors(level, reader))
                    if (TerminalLinks.admissible(level, source.target.pos, source.target.id, link))
                        connect(level, nodes, edges, source.target.id, new Target(link.instance(), link.position(), "door", ""));
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
                        for (var link : AccessDoorService.doors(level, linkedReader))
                            if (TerminalLinks.admissible(level, target.pos, target.id, link))
                                connect(level, nodes, edges, target.id, new Target(link.instance(), link.position(), "door", ""));
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
        return FrequencyTopology.view(FrequencyNetwork.get(level), sector.owner, named, List.copyOf(edges), events);
    }

    private static NetworkView.Node applyName(Map<UUID, String> names, NetworkView.Node node) {
        String name = names.get(node.id());
        return name == null ? node : node.withName(name);
    }

    private static void connect(ServerLevel level, Map<UUID, NetworkView.Node> nodes,
                                Set<NetworkView.Edge> edges, UUID source, Target target) {
        nodes.computeIfAbsent(target.id, id -> inspect(level, target));
        edges.add(new NetworkView.Edge(source, target.id, FrequencyNetwork.get(level).visible(level, source, target.id)));
    }

    private static NetworkView.Node inspect(ServerLevel level, Target target) {
        String name = target.name;
        String title = NetworkView.UI + "kind." + target.kind;
        String state = "offline";
        long holdUntil = 0;
        var be = TerminalLinks.loaded(level, target.pos) ? level.getBlockEntity(target.pos) : null;
        if (target.kind.equals("terminal") || target.kind.equals("automation")) {
            title = target.name;
            name = "";
        }
        if (be instanceof AccessTerminalEntity terminal && target.id.equals(terminal.instanceId)) {
            title = terminal.getBlockState().getBlock().getDescriptionId();
            if (terminal instanceof ControlTerminalEntity control)
                holdUntil = TerminalControlData.get(level).activationUntil(control, level.getGameTime());
            if (terminal instanceof KeypassTerminalEntity keypass) name = KeypassData.get(level).readerName(keypass);
            state = terminal.isLockedDown() ? "lockdown"
                : terminal instanceof CameraTerminalEntity camera ? (camera.isRunning() ? "running" : "stopped")
                : terminal.accessGranted ? "active" : "online";
        } else if (be instanceof IndustrialDoorEntity door && target.id.equals(door.doorInstanceId)) {
            title = door.getBlockState().getBlock().getDescriptionId();
            holdUntil = AccessDoorService.holdOpenUntil(level, door);
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
        return new NetworkView.Node(target.id, target.kind, name, title, state, target.pos.toShortString(), List.of(), holdUntil);
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
            var paths = new ListTag();
            routes.getOrDefault(c.id, Map.of()).forEach((sector, saved) -> {
                var row = new CompoundTag();
                row.putString("Sector", sector);
                row.put("Paths", GraphRoutes.write(saved));
                paths.add(row);
            });
            if (!paths.isEmpty()) entry.put("Routes", paths);
            var attachments = new ListTag();
            sockets.getOrDefault(c.id, Map.of()).forEach((sector, saved) -> {
                var row = new CompoundTag();
                row.putString("Sector", sector);
                row.put("Attachments", GraphRoutes.writeSockets(saved));
                attachments.add(row);
            });
            if (!attachments.isEmpty()) entry.put("Sockets", attachments);
            var junctions = new ListTag();
            joins.getOrDefault(c.id, Map.of()).forEach((sector, saved) -> {
                var row = new CompoundTag();
                row.putString("Sector", sector);
                row.put("Junctions", GraphRoutes.writeJoins(saved));
                junctions.add(row);
            });
            if (!junctions.isEmpty()) entry.put("Joins", junctions);
            savedConsoles.add(entry);
        });
        tag.put("Consoles", savedConsoles);
        var savedDisplays = new ListTag();
        displays.forEach((id, display) -> {
            var entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putUUID("Source", display.source);
            if (display.manual) entry.putBoolean("Manual", true);
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
            for (var rawRoute : entry.getList("Routes", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) rawRoute;
                var paths = GraphRoutes.read(row.getList("Paths", Tag.TAG_COMPOUND), canvas);
                if (!paths.isEmpty()) data.routes.computeIfAbsent(id, key -> new HashMap<>())
                    .put(row.getString("Sector"), new HashMap<>(paths));
            }
            for (var rawSocket : entry.getList("Sockets", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) rawSocket;
                var saved = GraphRoutes.readSockets(row.getList("Attachments", Tag.TAG_COMPOUND));
                if (!saved.isEmpty()) data.sockets.computeIfAbsent(id, key -> new HashMap<>())
                    .put(row.getString("Sector"), new HashMap<>(saved));
            }
            for (var rawJoin : entry.getList("Joins", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) rawJoin;
                var saved = GraphRoutes.readJoins(row.getList("Junctions", Tag.TAG_COMPOUND));
                if (!saved.isEmpty()) data.joins.computeIfAbsent(id, key -> new HashMap<>())
                    .put(row.getString("Sector"), new HashMap<>(saved));
            }
        }
        for (var raw : tag.getList("Displays", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (entry.hasUUID("Id") && entry.hasUUID("Source") && data.consoles.containsKey(entry.getUUID("Source")))
                data.displays.put(entry.getUUID("Id"), new Display(entry.getUUID("Source"), entry.getString("Sector"),
                    Objects.requireNonNullElse(cleanName(entry.getString("Name")), ""), DisplayLocation.read(entry), entry.getBoolean("Manual")));
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
