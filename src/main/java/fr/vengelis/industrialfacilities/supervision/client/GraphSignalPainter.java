package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.frequency.FrequencyChip;
import fr.vengelis.industrialfacilities.supervision.*;
import fr.vengelis.industrialfacilities.supervision.client.GraphInk.Corner;
import java.util.*;

/** Reuses the displayed geometry, including manual bends and shared route trunks. */
final class GraphSignalPainter {
    @FunctionalInterface interface Paint { void rect(int x, int y, int width, int height, int color); }
    record Travel(List<Corner> path, int color, long tick, NetworkView.SignalKind kind, long until) {}

    static List<Travel> plan(GraphLayout.Page page, List<GraphInk.Trace> traces) {
        if (page.signals().isEmpty()) return List.of();
        Map<GraphRoutes.Key, GraphInk.Trace> ink = new HashMap<>();
        traces.forEach(trace -> ink.put(trace.key(), trace));
        var docks = GraphRouting.docks(page);
        Map<GraphRoutes.SocketKey, NetworkView.Port> ports = new HashMap<>();
        page.points().forEach(point -> point.node().ports().forEach(port ->
            ports.put(new GraphRoutes.SocketKey(point.node().id(), port.slot(), port.channel()), port)));
        Map<GraphRoutes.SocketKey, List<GraphRoutes.SocketKey>> neighbors = new HashMap<>();
        for (var wire : page.wires()) {
            var key = GraphRoutes.key(wire); var from = GraphRoutes.from(key); var to = GraphRoutes.to(key);
            neighbors.computeIfAbsent(from, id -> new ArrayList<>()).add(to);
            neighbors.computeIfAbsent(to, id -> new ArrayList<>()).add(from);
        }
        List<Travel> result = new ArrayList<>();
        for (var signal : page.signals()) {
            var from = new GraphRoutes.SocketKey(signal.from(), signal.fromSlot(), signal.channel());
            var to = new GraphRoutes.SocketKey(signal.to(), signal.toSlot(), signal.channel());
            if (!docks.containsKey(from) || !docks.containsKey(to)) continue;
            if (signal.fromSlot() >= 0) {
                var source = ports.get(from); var target = ports.get(to);
                if (source == null || target == null || !FrequencyChip.Mode.fromId(source.mode()).sends()
                        || !FrequencyChip.Mode.fromId(target.mode()).receives()) continue;
            }
            List<GraphRoutes.SocketKey> route = signal.fromSlot() < 0 ? List.of(from, to) : busPath(neighbors, from, to);
            if (route.isEmpty()) continue;
            List<Corner> path = new ArrayList<>();
            int color = GraphInk.CYAN;
            boolean complete = true;
            for (int i = 1; i < route.size(); i++) {
                var a = route.get(i - 1); var b = route.get(i);
                var key = new GraphRoutes.Key(a.node(), a.slot(), b.node(), b.slot(), signal.channel());
                var trace = ink.get(key);
                if (trace == null) { complete = false; break; }
                color = trace.color();
                // A shared bus joins outside intermediate devices; only the real TX/RX ends touch their ports.
                if (i == 1) append(path, docks.get(a).edge());
                var segment = List.of(trace.corners());
                if (!GraphRoutes.from(key).equals(a)) segment = segment.reversed();
                for (var point : segment) append(path, point);
                if (i == route.size() - 1) append(path, docks.get(b).edge());
            }
            color = switch (signal.kind()) {
                case OPEN -> 0xFF55E681;
                case CLOSE -> 0xFFFF6464;
                case LOCKDOWN -> 0xFFFFA547;
                default -> color;
            };
            if (complete && path.size() >= 2) result.add(new Travel(List.copyOf(path), color, signal.tick(), signal.kind(), signal.until()));
        }
        return List.copyOf(result);
    }

    /** The forest is a drawing of a shared frequency bus; intermediate sockets are passive junctions. */
    private static List<GraphRoutes.SocketKey> busPath(Map<GraphRoutes.SocketKey, List<GraphRoutes.SocketKey>> neighbors,
                                                       GraphRoutes.SocketKey from, GraphRoutes.SocketKey to) {
        Map<GraphRoutes.SocketKey, GraphRoutes.SocketKey> previous = new HashMap<>();
        Deque<GraphRoutes.SocketKey> queue = new ArrayDeque<>(); previous.put(from, from); queue.add(from);
        while (!queue.isEmpty() && !previous.containsKey(to)) {
            var next = queue.removeFirst();
            for (var neighbor : neighbors.getOrDefault(next, List.of()))
                if (!previous.containsKey(neighbor)) { previous.put(neighbor, next); queue.addLast(neighbor); }
        }
        if (!previous.containsKey(to)) return List.of();
        List<GraphRoutes.SocketKey> route = new ArrayList<>(); var step = to;
        while (!step.equals(from)) { route.add(step); step = previous.get(step); }
        route.add(from); return route.reversed();
    }
    private static void append(List<Corner> path, Corner point) {
        if (!path.isEmpty() && path.getLast().equals(point)) return;
        // Joined routes can share a trunk in opposite order. Cancel the retraced part.
        while (path.size() >= 2) {
            var before = path.get(path.size() - 2); var last = path.getLast();
            if (!(before.x() == last.x() && last.x() == point.x() || before.y() == last.y() && last.y() == point.y())) break;
            path.removeLast();
            if (path.getLast().equals(point)) return;
        }
        path.add(point);
    }

    static void draw(Paint out, List<Travel> traffic, double clock, GraphLayout.Canvas canvas) {
        for (var pulse : traffic) {
            double age = clock - pulse.tick;
            if (age < 0 || clock >= pulse.until) continue;
            double length = 0;
            for (int i = 1; i < pulse.path.size(); i++) length += distance(pulse.path.get(i - 1), pulse.path.get(i));
            double offset = age * 3 % 24;
            int alpha = (int) (255 * Math.min(1, (pulse.until - clock) / 10));
            int head = (alpha << 24) | (pulse.kind == NetworkView.SignalKind.GENERIC ? 0xEAFBFF : pulse.color & 0xFFFFFF);
            int count = 0;
            for (double position = offset; position <= length && count++ < 96; position += 24) {
                var tip = at(pulse.path, position);
                rect(out, canvas, tip.x() - 1, tip.y() - 1, 3, 3, head);
            }
        }
    }
    static Corner at(List<Corner> path, double position) {
        for (int i = 1; i < path.size(); i++) {
            var a = path.get(i - 1); var b = path.get(i); double size = distance(a, b);
            if (position <= size && size > 0) return new Corner((int) Math.round(a.x() + (b.x() - a.x()) * position / size),
                (int) Math.round(a.y() + (b.y() - a.y()) * position / size));
            position -= size;
        }
        return path.getLast();
    }
    private static int distance(Corner a, Corner b) { return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()); }
    private static void rect(Paint out, GraphLayout.Canvas canvas, int x, int y, int w, int h, int color) {
        int left = Math.max(0, x), top = Math.max(17, y), right = Math.min(canvas.width(), x + w), bottom = Math.min(canvas.height(), y + h);
        if (right > left && bottom > top) out.rect(left, top, right - left, bottom - top, color);
    }
    private GraphSignalPainter() {}
}
