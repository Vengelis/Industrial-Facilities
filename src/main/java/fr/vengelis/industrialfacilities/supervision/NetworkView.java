package fr.vengelis.industrialfacilities.supervision;

import java.util.*;
import net.minecraft.nbt.*;

public record NetworkView(List<Node> nodes, List<Edge> edges, List<Wire> wires, List<Notice> events,
                          Map<UUID, GraphLayout.Placement> layout, GraphLayout.Canvas canvas,
                          Map<GraphRoutes.Key, List<GraphRoutes.Bend>> routes,
                          Map<GraphRoutes.SocketKey, GraphRoutes.Socket> sockets, Map<GraphRoutes.Key, GraphRoutes.Key> joins,
                          List<Signal> signals) {
    public static final NetworkView EMPTY = new NetworkView(List.of(), List.of(), List.of());
    public static final String UI = "gui.industrial_facilities.supervision.";

    public NetworkView(List<Node> nodes, List<Edge> edges, List<Wire> wires, List<Notice> events,
                       Map<UUID, GraphLayout.Placement> layout, GraphLayout.Canvas canvas,
                       Map<GraphRoutes.Key, List<GraphRoutes.Bend>> routes,
                       Map<GraphRoutes.SocketKey, GraphRoutes.Socket> sockets, Map<GraphRoutes.Key, GraphRoutes.Key> joins) {
        this(nodes, edges, wires, events, layout, canvas, routes, sockets, joins, List.of());
    }

    public enum SignalKind {
        GENERIC, OPEN, CLOSE, LOCKDOWN;
        public static SignalKind fromId(int id) { return id >= 0 && id < values().length ? values()[id] : GENERIC; }
    }
    public record Signal(UUID from, int fromSlot, UUID to, int toSlot, int channel, long tick, SignalKind kind, long until) {
        public Signal(UUID from, int fromSlot, UUID to, int toSlot, int channel, long tick, SignalKind kind) {
            this(from, fromSlot, to, toSlot, channel, tick, kind, tick + SignalTraffic.LIFETIME);
        }
        public Signal(UUID from, int fromSlot, UUID to, int toSlot, int channel, long tick) {
            this(from, fromSlot, to, toSlot, channel, tick, SignalKind.GENERIC);
        }
    }

    NetworkView withSignals(List<Signal> value) {
        return new NetworkView(nodes, edges, wires, events, layout, canvas, routes, sockets, joins, value);
    }

    public NetworkView(List<Node> nodes, List<Edge> edges, List<Notice> events) {
        this(nodes, edges, List.of(), events);
    }
    public NetworkView(List<Node> nodes, List<Edge> edges, List<Wire> wires, List<Notice> events) {
        this(nodes, edges, wires, events, Map.of(), GraphLayout.Canvas.DEFAULT, Map.of(), Map.of(), Map.of());
    }
    NetworkView withLayout(Map<UUID, GraphLayout.Placement> placements) {
        return new NetworkView(nodes, edges, wires, events, placements, canvas, routes, sockets, joins, signals);
    }
    NetworkView withCanvas(GraphLayout.Canvas size) {
        return new NetworkView(nodes, edges, wires, events, layout, size, routes, sockets, joins, signals);
    }
    NetworkView withRoutes(Map<GraphRoutes.Key, List<GraphRoutes.Bend>> value) {
        return new NetworkView(nodes, edges, wires, events, layout, canvas, value, sockets, joins, signals);
    }
    NetworkView withSockets(Map<GraphRoutes.SocketKey, GraphRoutes.Socket> value) {
        return new NetworkView(nodes, edges, wires, events, layout, canvas, routes, value, joins, signals);
    }
    NetworkView withJoins(Map<GraphRoutes.Key, GraphRoutes.Key> value) {
        return new NetworkView(nodes, edges, wires, events, layout, canvas, routes, sockets, value, signals);
    }
    NetworkView resized(GraphLayout.Canvas size) {
        if (size.equals(canvas)) return this;
        var placements = new HashMap<UUID, GraphLayout.Placement>();
        layout.forEach((id, place) -> placements.put(id, place.resized(canvas, size)));
        var bends = new HashMap<GraphRoutes.Key, List<GraphRoutes.Bend>>();
        routes.forEach((key, points) -> bends.put(key, points.stream().map(point -> point.resized(canvas, size)).toList()));
        return new NetworkView(nodes, edges, wires, events, Map.copyOf(placements), size, Map.copyOf(bends), sockets, joins, signals);
    }

    public record Port(int slot, int channel, int mode) {
        public Port(int slot, int channel) { this(slot, channel, 0); }
    }
    public record Wire(UUID source, int sourceSlot, UUID target, int targetSlot, int channel) {}
    public record Node(UUID id, String kind, String name, String titleKey, String status, String position, List<Port> ports, long holdUntil) {
        public Node(UUID id, String kind, String name, String titleKey, String status, String position, List<Port> ports) {
            this(id, kind, name, titleKey, status, position, ports, 0);
        }
        public Node(UUID id, String kind, String name, String titleKey, String status, String position) {
            this(id, kind, name, titleKey, status, position, List.of());
        }
        CompoundTag write() {
            var n = new CompoundTag();
            n.putUUID("Id", id); n.putString("Kind", kind); n.putString("Name", name);
            n.putString("Title", titleKey); n.putString("Status", status); n.putString("Pos", position);
            if (holdUntil > 0) n.putLong("HoldUntil", holdUntil);
            if (!ports.isEmpty()) {
                var list = new ListTag();
                for (var port : ports) {
                    var p = new CompoundTag();
                    p.putByte("Slot", (byte) port.slot);
                    p.putInt("Channel", port.channel);
                    if (port.mode != 0) p.putByte("Mode", (byte) port.mode);
                    list.add(p);
                }
                n.put("Ports", list);
            }
            return n;
        }
        static Node read(CompoundTag n) {
            var ports = new ArrayList<Port>();
            for (Tag value : n.getList("Ports", Tag.TAG_COMPOUND)) {
                var p = (CompoundTag) value;
                int slot = p.getByte("Slot");
                if (validSlot(slot)) ports.add(new Port(slot, p.getInt("Channel"),
                    fr.vengelis.industrialfacilities.frequency.FrequencyChip.Mode.fromId(p.getByte("Mode")).ordinal()));
            }
            return new Node(n.getUUID("Id"), n.getString("Kind"), n.getString("Name"), n.getString("Title"),
                n.getString("Status"), n.getString("Pos"), List.copyOf(ports), Math.max(0, n.getLong("HoldUntil")));
        }
        Node withName(String value) { return new Node(id, kind, value, titleKey, status, position, ports, holdUntil); }
        Node withPorts(List<Port> value) { return new Node(id, kind, name, titleKey, status, position, value, holdUntil); }
        public boolean canRename() {
            return switch (kind) {
                case "door", "terminal", "automation", "camera", "panel", "alarm", "emitter" -> true;
                default -> false;
            };
        }
    }
    public record Edge(UUID source, UUID target, boolean frequency) {
        public Edge(UUID source, UUID target) { this(source, target, false); }
    }
    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < fr.vengelis.industrialfacilities.frequency.FrequencyEndpoint.CONSOLE_SLOTS;
    }
    public record Notice(long tick, Node node) {
        CompoundTag write() {
            var tag = node.write();
            tag.putLong("Tick", tick);
            return tag;
        }
        static Notice read(CompoundTag tag) { return new Notice(tag.getLong("Tick"), Node.read(tag)); }
    }

    public CompoundTag write(boolean history) {
        var tag = new CompoundTag();
        canvas.write(tag);
        var nodeTags = new ListTag();
        nodes.forEach(n -> nodeTags.add(n.write()));
        tag.put("Nodes", nodeTags);
        var edgeTags = new ListTag();
        for (var edge : edges) {
            var e = new CompoundTag();
            e.putUUID("From", edge.source); e.putUUID("To", edge.target);
            if (edge.frequency) e.putBoolean("Frequency", true);
            edgeTags.add(e);
        }
        tag.put("Edges", edgeTags);
        if (!signals.isEmpty()) {
            var list = new ListTag();
            for (var signal : signals) {
                if (list.size() >= SignalTraffic.MAX_SIGNALS) break;
                var s = new CompoundTag();
                s.putUUID("From", signal.from); s.putByte("FromSlot", (byte) signal.fromSlot);
                s.putUUID("To", signal.to); s.putByte("ToSlot", (byte) signal.toSlot);
                s.putInt("Channel", signal.channel); s.putLong("Tick", signal.tick);
                if (signal.kind != SignalKind.GENERIC) s.putByte("Kind", (byte) signal.kind.ordinal());
                s.putLong("Until", signal.until);
                list.add(s);
            }
            tag.put("Signals", list);
        }
        if (!wires.isEmpty()) {
            var list = new ListTag();
            for (var wire : wires) {
                var w = new CompoundTag();
                w.putUUID("From", wire.source); w.putByte("FromSlot", (byte) wire.sourceSlot);
                w.putUUID("To", wire.target); w.putByte("ToSlot", (byte) wire.targetSlot);
                w.putInt("Channel", wire.channel);
                list.add(w);
            }
            tag.put("Wires", list);
        }
        if (!layout.isEmpty()) tag.put("Layout", GraphLayout.write(layout));
        if (!routes.isEmpty()) tag.put("Routes", GraphRoutes.write(routes));
        if (!sockets.isEmpty()) tag.put("Sockets", GraphRoutes.writeSockets(sockets));
        if (!joins.isEmpty()) tag.put("Joins", GraphRoutes.writeJoins(joins));
        if (history) {
            var list = new ListTag();
            events.forEach(e -> list.add(e.write()));
            tag.put("Events", list);
        }
        return tag;
    }

    public static NetworkView read(CompoundTag tag) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        List<Wire> wires = new ArrayList<>();
        List<Notice> events = new ArrayList<>();
        List<Signal> signals = new ArrayList<>();
        for (Tag value : tag.getList("Signals", Tag.TAG_COMPOUND)) {
            if (signals.size() >= SignalTraffic.MAX_SIGNALS) break;
            var s = (CompoundTag) value;
            int from = s.getByte("FromSlot"), to = s.getByte("ToSlot");
            if (s.hasUUID("From") && s.hasUUID("To") && !s.getUUID("From").equals(s.getUUID("To")) && s.getLong("Tick") >= 0
                    && (validSlot(from) && validSlot(to) || from == -1 && to == -1 && s.getInt("Channel") == 0))
                signals.add(new Signal(s.getUUID("From"), from, s.getUUID("To"), to, s.getInt("Channel"), s.getLong("Tick"), SignalKind.fromId(s.getByte("Kind")),
                    s.contains("Until") ? Math.max(s.getLong("Tick"), s.getLong("Until")) : s.getLong("Tick") + SignalTraffic.LIFETIME));
        }
        for (Tag value : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag) value;
            if (n.hasUUID("Id")) nodes.add(Node.read(n));
        }
        for (Tag value : tag.getList("Edges", Tag.TAG_COMPOUND)) {
            var e = (CompoundTag) value;
            if (e.hasUUID("From") && e.hasUUID("To"))
                edges.add(new Edge(e.getUUID("From"), e.getUUID("To"), e.getBoolean("Frequency")));
        }
        for (Tag value : tag.getList("Wires", Tag.TAG_COMPOUND)) {
            var w = (CompoundTag) value;
            int from = w.getByte("FromSlot"), to = w.getByte("ToSlot");
            if (w.hasUUID("From") && w.hasUUID("To") && validSlot(from) && validSlot(to))
                wires.add(new Wire(w.getUUID("From"), from, w.getUUID("To"), to, w.getInt("Channel")));
        }
        for (Tag value : tag.getList("Events", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag) value;
            if (n.hasUUID("Id")) events.add(Notice.read(n));
        }
        var canvas = GraphLayout.Canvas.read(tag);
        return new NetworkView(List.copyOf(nodes), List.copyOf(edges), List.copyOf(wires), List.copyOf(events),
            GraphLayout.read(tag.getList("Layout", Tag.TAG_COMPOUND), canvas), canvas,
            GraphRoutes.read(tag.getList("Routes", Tag.TAG_COMPOUND), canvas),
            GraphRoutes.readSockets(tag.getList("Sockets", Tag.TAG_COMPOUND)),
            GraphRoutes.readJoins(tag.getList("Joins", Tag.TAG_COMPOUND)), List.copyOf(signals));
    }
}
