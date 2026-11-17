package fr.vengelis.industrialfacilities.supervision;

import java.util.*;
import net.minecraft.nbt.*;

public record NetworkView(List<Node> nodes, List<Edge> edges, List<Notice> events, Map<UUID, GraphLayout.Placement> layout,
                          GraphLayout.Canvas canvas) {
    public static final NetworkView EMPTY = new NetworkView(List.of(), List.of(), List.of());
    public static final String UI = "gui.industrial_facilities.supervision.";

    public NetworkView(List<Node> nodes, List<Edge> edges, List<Notice> events) {
        this(nodes, edges, events, Map.of(), GraphLayout.Canvas.DEFAULT);
    }
    NetworkView withLayout(Map<UUID, GraphLayout.Placement> placements) {
        return new NetworkView(nodes, edges, events, placements, canvas);
    }
    NetworkView withCanvas(GraphLayout.Canvas size) {
        return new NetworkView(nodes, edges, events, layout, size);
    }
    NetworkView resized(GraphLayout.Canvas size) {
        if (size.equals(canvas)) return this;
        var placements = new HashMap<UUID, GraphLayout.Placement>();
        layout.forEach((id, place) -> placements.put(id, place.resized(canvas, size)));
        return new NetworkView(nodes, edges, events, Map.copyOf(placements), size);
    }

    public record Node(UUID id, String kind, String name, String titleKey, String status, String position) {
        CompoundTag write() {
            var n = new CompoundTag();
            n.putUUID("Id", id); n.putString("Kind", kind); n.putString("Name", name);
            n.putString("Title", titleKey); n.putString("Status", status); n.putString("Pos", position);
            return n;
        }
        static Node read(CompoundTag n) {
            return new Node(n.getUUID("Id"), n.getString("Kind"), n.getString("Name"), n.getString("Title"),
                n.getString("Status"), n.getString("Pos"));
        }
        Node withName(String value) { return new Node(id, kind, value, titleKey, status, position); }
        public boolean canRename() {
            return switch (kind) {
                case "door", "terminal", "automation", "camera", "panel", "alarm", "emitter" -> true;
                default -> false;
            };
        }
    }
    public record Edge(UUID source, UUID target) {}
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
            edgeTags.add(e);
        }
        tag.put("Edges", edgeTags);
        if (!layout.isEmpty()) tag.put("Layout", GraphLayout.write(layout));
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
        List<Notice> events = new ArrayList<>();
        for (Tag value : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag) value;
            if (n.hasUUID("Id")) nodes.add(Node.read(n));
        }
        for (Tag value : tag.getList("Edges", Tag.TAG_COMPOUND)) {
            var e = (CompoundTag) value;
            if (e.hasUUID("From") && e.hasUUID("To")) edges.add(new Edge(e.getUUID("From"), e.getUUID("To")));
        }
        for (Tag value : tag.getList("Events", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag) value;
            if (n.hasUUID("Id")) events.add(Notice.read(n));
        }
        var canvas = GraphLayout.Canvas.read(tag);
        return new NetworkView(List.copyOf(nodes), List.copyOf(edges), List.copyOf(events),
            GraphLayout.read(tag.getList("Layout", Tag.TAG_COMPOUND), canvas), canvas);
    }
}
