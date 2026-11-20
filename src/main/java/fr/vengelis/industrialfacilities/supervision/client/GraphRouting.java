package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.GraphLayout;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.Side;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.Socket;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.SocketKey;
import fr.vengelis.industrialfacilities.supervision.client.GraphInk.Corner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.TreeSet;

final class GraphRouting {
    record Dock(GraphLayout.Point point, Socket socket, Corner edge, Corner exit) {}

    static Map<SocketKey, Dock> docks(GraphLayout.Page page) {
        Map<UUID, GraphLayout.Point> points = new LinkedHashMap<>();
        Map<SocketKey, List<GraphLayout.Point>> neighbors = new LinkedHashMap<>();
        for (var point : page.points()) {
            points.put(point.node().id(), point);
            for (var port : point.node().ports())
                neighbors.put(new SocketKey(point.node().id(), port.slot(), port.channel()), new ArrayList<>());
        }
        for (var wire : page.wires()) link(neighbors, points, GraphRoutes.key(wire));
        for (var edge : page.edges()) link(neighbors, points, GraphRoutes.key(edge));

        Map<SocketKey, Dock> docks = new LinkedHashMap<>();
        neighbors.forEach((key, others) -> {
            var point = points.get(key.node());
            var socket = page.sockets().get(key);
            docks.put(key, socket == null ? automaticDock(page, point, key.slot(), others) : dock(point, socket, page.canvas()));
        });
        return docks;
    }

    private static void link(Map<SocketKey, List<GraphLayout.Point>> neighbors, Map<UUID, GraphLayout.Point> points,
                             GraphRoutes.Key key) {
        neighbors.computeIfAbsent(GraphRoutes.from(key), ignored -> new ArrayList<>()).add(points.get(key.to()));
        neighbors.computeIfAbsent(GraphRoutes.to(key), ignored -> new ArrayList<>()).add(points.get(key.from()));
    }

    private static Dock automaticDock(GraphLayout.Page page, GraphLayout.Point point, int slot, List<GraphLayout.Point> neighbors) {
        boolean terminal = point.node().kind().equals("terminal") || point.node().kind().equals("automation");
        int x = slot < 0 || !terminal ? 0 : -24 + Math.floorMod(slot, 4) * 16;
        int y = slot < 0 || !terminal ? 0 : -7 + Math.floorMod(slot, 4) * 5;
        if (neighbors.isEmpty()) return dock(point, new Socket(Side.BOTTOM, x), page.canvas());
        Dock best = null;
        int lowest = Integer.MAX_VALUE;
        for (Side side : Side.values()) {
            var candidate = dock(point, new Socket(side, side.horizontal() ? x : y), page.canvas());
            int cost = candidate.edge.equals(candidate.exit) ? 4096 : 0;
            for (var other : neighbors) {
                int dx = other.x() - candidate.edge.x(), dy = other.y() - candidate.edge.y();
                cost += Math.abs(dx) + Math.abs(dy);
                if (dx * side.dx + dy * side.dy <= 0) cost += 45;
                if (side.horizontal() ? Math.abs(other.y() - point.y()) <= 21 : Math.abs(other.x() - point.x()) <= 74)
                    cost += 32;
            }
            for (var obstacle : page.points())
                if (obstacle != point && intersects(candidate.edge, candidate.exit, obstacle)) cost += 8192;
            if (cost < lowest) {
                lowest = cost;
                best = candidate;
            }
        }
        return best;
    }

    static Dock dock(GraphLayout.Point point, Socket socket, GraphLayout.Canvas canvas) {
        var side = socket.side();
        int x = point.x() + (side.horizontal() ? socket.offset() : 37 * side.dx);
        int y = point.y() + (side.horizontal() ? (side == Side.TOP ? -10 : 11) : socket.offset());
        var edge = new Corner(x, y);
        var exit = new Corner(Math.clamp(x + side.dx * 4, 0, canvas.width() - 1),
            Math.clamp(y + side.dy * 4, 17, canvas.height() - 1));
        return new Dock(point, socket, edge, exit);
    }

    static Socket closestSocket(GraphLayout.Point point, double x, double y) {
        var relativeX = x - point.x();
        var relativeY = y - point.y();
        return java.util.Arrays.stream(Side.values()).map(side -> {
            double offset = side.horizontal() ? relativeX : relativeY;
            int limit = side.horizontal() ? 32 : 7;
            return new Socket(side, Math.clamp((int) Math.round(offset), -limit, limit));
        }).min(Comparator.comparingDouble(socket -> {
            var side = socket.side();
            double dx = relativeX - (side.horizontal() ? socket.offset() : side.dx * 37);
            double dy = relativeY - (side.horizontal() ? side == Side.TOP ? -10 : 11 : socket.offset());
            return dx * dx + dy * dy;
        })).orElseThrow();
    }

    static Corner[] automatic(GraphLayout.Page page, Corner start, Corner end) {
        return automatic(page, start, end, List.of(), 0);
    }

    static Corner[] automatic(GraphLayout.Page page, Corner start, Corner end, List<GraphInk.Trace> occupied, int channel) {
        var rows = new LinkedHashSet<Integer>();
        var columns = new LinkedHashSet<Integer>();
        rows.add((start.y() + end.y()) / 2);
        rows.add(start.y());
        rows.add(end.y());
        columns.add((start.x() + end.x()) / 2);
        columns.add(start.x());
        columns.add(end.x());
        rows.add(17);
        rows.add(page.canvas().height() - 1);
        columns.add(0);
        columns.add(page.canvas().width() - 1);
        for (var obstacle : page.points()) {
            rows.add(Math.max(17, obstacle.y() - 12));
            rows.add(Math.min(page.canvas().height() - 1, obstacle.y() + 13));
            columns.add(Math.max(0, obstacle.x() - 39));
            columns.add(Math.min(page.canvas().width() - 1, obstacle.x() + 39));
        }
        for (var trace : occupied) {
            if (trace.key().channel() == channel) continue;
            for (var point : trace.corners()) {
                rows.add(GraphRoutes.snapY(point.y() - GraphRoutes.GRID, page.canvas()));
                rows.add(GraphRoutes.snapY(point.y() + GraphRoutes.GRID, page.canvas()));
                columns.add(GraphRoutes.snapX(point.x() - GraphRoutes.GRID, page.canvas()));
                columns.add(GraphRoutes.snapX(point.x() + GraphRoutes.GRID, page.canvas()));
            }
        }
        var best = new Candidate(page.points(), occupied, channel);
        for (int row : rows) best.offer(start, new Corner(start.x(), row), new Corner(end.x(), row), end);
        for (int col : columns) best.offer(start, new Corner(col, start.y()), new Corner(col, end.y()), end);
        if (best.cost >= 1024 && page.points().stream().noneMatch(point -> intersects(start, start, point)
                || intersects(end, end, point))) {
            var nearbyRows = rows.stream().sorted(Comparator.comparingInt(row -> Math.abs(row - (start.y() + end.y()) / 2)))
                .limit(20).toList();
            var nearbyColumns = columns.stream().sorted(Comparator.comparingInt(col -> Math.abs(col - (start.x() + end.x()) / 2)))
                .limit(20).toList();
            for (int row : nearbyRows) for (int col : nearbyColumns) {
                best.offer(start, new Corner(start.x(), row), new Corner(col, row), new Corner(col, end.y()), end);
                best.offer(start, new Corner(col, start.y()), new Corner(col, row), new Corner(end.x(), row), end);
            }
            if (best.cost >= 1024) {
                var path = search(page, start, end, rows, columns, occupied, channel);
                if (path != null) best.offer(path);
            }
        }
        return best.path;
    }

    private static final class Candidate {
        private final List<GraphLayout.Point> obstacles;
        private final List<GraphInk.Trace> occupied;
        private final int channel;
        int cost = Integer.MAX_VALUE;
        Corner[] path;

        Candidate(List<GraphLayout.Point> obstacles, List<GraphInk.Trace> occupied, int channel) {
            this.obstacles = obstacles;
            this.occupied = occupied;
            this.channel = channel;
        }

        void offer(Corner... trial) {
            var points = simplified(List.of(trial));
            int score = points.size() * 4;
            for (int i = 1; i < points.size() && score < cost; i++) {
                var a = points.get(i - 1);
                var b = points.get(i);
                score += segmentCost(a, b, obstacles, occupied, channel);
            }
            if (score < cost) {
                cost = score;
                path = points.toArray(Corner[]::new);
            }
        }
    }

    static int conflict(Corner a, Corner b, Corner c, Corner d) {
        boolean horizontal = a.y() == b.y(), otherHorizontal = c.y() == d.y();
        if (horizontal == otherHorizontal) {
            if (horizontal ? a.y() != c.y() : a.x() != c.x()) return 0;
            int first = horizontal ? a.x() : a.y(), last = horizontal ? b.x() : b.y();
            int otherFirst = horizontal ? c.x() : c.y(), otherLast = horizontal ? d.x() : d.y();
            int overlap = Math.min(Math.max(first, last), Math.max(otherFirst, otherLast))
                - Math.max(Math.min(first, last), Math.min(otherFirst, otherLast));
            return overlap > 0 ? 2048 + overlap * 32 : 0;
        }
        Corner h1 = horizontal ? a : c, h2 = horizontal ? b : d;
        Corner v1 = horizontal ? c : a, v2 = horizontal ? d : b;
        return between(h1.x(), v1.x(), h2.x()) && between(v1.y(), h1.y(), v2.y()) ? 1024 : 0;
    }

    private static int segmentCost(Corner a, Corner b, List<GraphLayout.Point> obstacles,
                                   List<GraphInk.Trace> occupied, int channel) {
        int cost = Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
        for (var obstacle : obstacles) if (intersects(a, b, obstacle)) cost += 1000000;
        for (var trace : occupied) {
            if (trace.key().channel() == channel) continue;
            var path = trace.corners();
            for (int i = 1; i < path.length; i++) cost += conflict(a, b, path[i - 1], path[i]);
        }
        return cost;
    }

    private record Step(int node, int cost, int estimate) {}

    private static Corner[] search(GraphLayout.Page page, Corner start, Corner end, java.util.Set<Integer> rows,
                                   java.util.Set<Integer> columns, List<GraphInk.Trace> occupied, int channel) {
        var xx = new TreeSet<>(columns);
        var yy = new TreeSet<>(rows);
        for (int x = GraphRoutes.GRID_X; x < page.canvas().width(); x += GraphRoutes.GRID) xx.add(x);
        for (int y = GraphRoutes.GRID_Y + GraphRoutes.GRID; y < page.canvas().height(); y += GraphRoutes.GRID) yy.add(y);
        int[] xs = xx.stream().mapToInt(Integer::intValue).toArray(), ys = yy.stream().mapToInt(Integer::intValue).toArray();
        int width = xs.length, count = width * ys.length;
        int source = Arrays.binarySearch(ys, start.y()) * width + Arrays.binarySearch(xs, start.x());
        int target = Arrays.binarySearch(ys, end.y()) * width + Arrays.binarySearch(xs, end.x());
        int[] costs = new int[count], previous = new int[count];
        Arrays.fill(costs, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        var queue = new PriorityQueue<Step>(Comparator.comparingInt(Step::estimate).thenComparingInt(Step::node));
        costs[source] = 0;
        queue.add(new Step(source, 0, 0));
        while (!queue.isEmpty()) {
            var step = queue.remove();
            if (step.cost != costs[step.node]) continue;
            if (step.node == target) {
                var points = new ArrayList<Corner>();
                for (int node = target; node >= 0; node = previous[node]) points.add(new Corner(xs[node % width], ys[node / width]));
                return simplified(points.reversed()).toArray(Corner[]::new);
            }
            int x = step.node % width, y = step.node / width;
            var a = new Corner(xs[x], ys[y]);
            int[] next = {x > 0 ? step.node - 1 : -1, x + 1 < width ? step.node + 1 : -1,
                y > 0 ? step.node - width : -1, y + 1 < ys.length ? step.node + width : -1};
            for (int node : next) {
                if (node < 0) continue;
                var b = new Corner(xs[node % width], ys[node / width]);
                int cost = step.cost + segmentCost(a, b, page.points(), occupied, channel) + 1;
                if (cost >= costs[node]) continue;
                costs[node] = cost;
                previous[node] = step.node;
                queue.add(new Step(node, cost, cost + Math.abs(b.x() - end.x()) + Math.abs(b.y() - end.y())));
            }
        }
        return null;
    }

    static boolean intersects(Corner a, Corner b, GraphLayout.Point point) {
        return Math.min(a.x(), b.x()) <= point.x() + 37 && Math.max(a.x(), b.x()) >= point.x() - 37
            && Math.min(a.y(), b.y()) <= point.y() + 11 && Math.max(a.y(), b.y()) >= point.y() - 10;
    }

    static List<Corner> simplified(List<Corner> path) {
        List<Corner> result = new ArrayList<>();
        for (var next : path) {
            if (!result.isEmpty() && result.getLast().equals(next)) continue;
            if (result.size() >= 2) {
                var before = result.get(result.size() - 2);
                var middle = result.getLast();
                if (before.x() == middle.x() && middle.x() == next.x() && between(before.y(), middle.y(), next.y())
                        || before.y() == middle.y() && middle.y() == next.y() && between(before.x(), middle.x(), next.x()))
                    result.removeLast();
            }
            result.add(next);
        }
        return result;
    }

    private static boolean between(int a, int b, int c) { return Math.min(a, c) <= b && b <= Math.max(a, c); }

    private GraphRouting() {}
}
