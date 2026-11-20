package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.client.CountdownText;
import fr.vengelis.industrialfacilities.supervision.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class GraphInk {
    static final int INK = 0xFFE4F2F9, MUTED = 0xFFABBDCB, CYAN = 0xFF84D6FF;
    private static final int[] CHANNEL_COLORS = {
        CYAN, 0xFFC49AFF, 0xFF76E6A9, 0xFFEAC875, 0xFFFFA49C, 0xFF7CDDD4, 0xFFAFB9FF, 0xFFFFACD6
    };
    interface Surface {
        void rect(int x, int y, int w, int h, int color);
        void text(String text, int x, int y, int color);
        void scaledText(String text, int x, int y, int color, float scale);
    }
    static String tr(String key, Object... args) { return Component.translatable(NetworkView.UI + key, args).getString(); }
    static String display(NetworkView.Node node) {
        if (!node.name().isBlank()) return node.name();
        if (node.kind().equals("door")) return tr("kind.door") + " " + node.position();
        return Component.translatable(node.titleKey()).getString();
    }
    static int color(String state) {
        return switch (state) {
            case "alert", "closed", "denied" -> 0xFFFF7874;
            case "moving", "partial", "tracking" -> 0xFFF4CC6B;
            case "lockdown", "obstructed" -> 0xFFFFA65C;
            case "active" -> 0xFFFFD166;
            case "open", "running", "online" -> 0xFF76E6A9;
            default -> 0xFFA5B4C1;
        };
    }
    static int kind(NetworkView.Node node) {
        return switch (node.kind()) {
            case "automation", "camera" -> 0xFFC49AFF;
            case "emitter", "alarm" -> 0xFFEAC875;
            case "door" -> color(node.status());
            default -> CYAN;
        };
    }
    static List<Trace> draw(Surface out, Font font, String sector, GraphLayout.Page page) {
        return draw(out, font, sector, page, "no_sector");
    }
    static List<Trace> draw(Surface out, Font font, String sector, GraphLayout.Page page, String emptySectorKey) {
        return draw(out, font, sector, page, emptySectorKey, null);
    }

    static List<Trace> draw(Surface out, Font font, String sector, GraphLayout.Page page, String emptySectorKey, GraphRoutes.Key selected) {
        int width = page.canvas().width(), height = page.canvas().height();
        out.rect(0, 0, width, height, 0xFF0D1724);
        for (int x = GraphRoutes.GRID_X; x < width - 1; x += GraphRoutes.GRID)
            out.rect(x, GraphRoutes.GRID_Y, 1, height - GraphRoutes.GRID_Y, 0xFF142237);
        for (int y = GraphRoutes.GRID_Y; y < height; y += GraphRoutes.GRID)
            out.rect(0, y, width, 1, 0xFF142237);
        String header = font.plainSubstrByWidth(tr("sector_title", sector.isEmpty() ? "—" : sector), 190);
        out.text(header, 6, 4, CYAN);
        String count = (page.index() + 1) + "/" + page.total();
        out.text(count, width - font.width(count) - 6, 4, MUTED);
        if (page.points().isEmpty()) {
            String text = tr(sector.isEmpty() ? emptySectorKey : "no_nodes");
            out.text(text, (width - font.width(text)) / 2, height / 2 - 7, MUTED);
            return List.of();
        }
        var docks = GraphRouting.docks(page);
        var traces = traces(page, docks);
        for (var trace : traces)
            drawPath(out, trace.corners(), trace.key().equals(selected) ? INK : trace.color());
        for (var trace : traces) {
            var dot = trace.junction();
            if (dot != null) out.rect(dot.x - 2, dot.y - 2, 5, 5, trace.color());
        }
        for (var point : page.points()) {
            int x = point.x() - 37, y = point.y() - 10;
            var node = point.node();
            out.rect(x, y, 75, 22, kind(node));
            out.rect(x + 1, y + 1, 73, 20, 0xFF152339);
            String title = shortLabel(node);
            float scale = Math.clamp(69f / Math.max(1, font.width(title)), .58f, 1f);
            if (font.width(title) * scale > 69) title = font.plainSubstrByWidth(title, (int)(63 / scale)) + "…";
            out.scaledText(title, x + 3, y + 3 + Math.round((1 - scale) * 3), INK, scale);
            String more = point.remoteLinks() > 0 ? " +" + point.remoteLinks() : "";
            if (node.holdUntil() <= 0)
                out.text(font.plainSubstrByWidth(tr("state." + node.status()) + more, 69), x + 3, y + 12, color(node.status()));
        }
        docks.forEach((key, dock) -> {
            var edge = dock.edge();
            var exit = dock.exit();
            int color = key.slot() < 0 ? kind(dock.point().node()) : channelColor(key.channel());
            line(out, edge.x, edge.y, exit.x, exit.y, color);
            out.rect(Math.clamp(edge.x - 2, 0, width - 5), Math.clamp(edge.y - 2, 17, height - 5), 5, 5, color);
            if (key.slot() >= 0) {
                int mode = dock.point().node().ports().stream().filter(port -> port.slot() == key.slot())
                    .mapToInt(NetworkView.Port::mode).findFirst().orElse(0);
                if (mode != 0) {
                    int dx = Integer.signum(exit.x - edge.x), dy = Integer.signum(exit.y - edge.y);
                    int direction = mode == 1 ? 1 : -1;
                    int tipX = edge.x + dx * (mode == 1 ? 7 : 0), tipY = edge.y + dy * (mode == 1 ? 7 : 0);
                    for (int i = 0; i < 3; i++) {
                        int x1 = tipX - direction * dx * i + dy * i, y1 = tipY - direction * dy * i - dx * i;
                        int x2 = tipX - direction * dx * i - dy * i, y2 = tipY - direction * dy * i + dx * i;
                        if (x1 >= 0 && x1 < width && y1 >= 17 && y1 < height) out.rect(x1, y1, 1, 1, color);
                        if (x2 >= 0 && x2 < width && y2 >= 17 && y2 < height) out.rect(x2, y2, 1, 1, color);
                    }
                }
            }
        });
        return traces;
    }

    static List<GraphLayout.Point> timedNodes(GraphLayout.Page page) {
        return page.points().stream().filter(point -> point.node().holdUntil() > 0).toList();
    }

    /** Only these glyphs change with the clock; route geometry and static text stay cached. */
    static List<WorldGraphSurface.Glyph> countdowns(Font font, List<GraphLayout.Point> timedNodes, long now) {
        var glyphs = new ArrayList<WorldGraphSurface.Glyph>();
        for (var point : timedNodes) {
            var node = point.node();
            int x = point.x() - 34, y = point.y() + 2;
            String state = tr("state." + node.status()) + (point.remoteLinks() > 0 ? " +" + point.remoteLinks() : "");
            String time = CountdownText.remaining(node.holdUntil(), now);
            int timeWidth = font.width(time), available = time.isEmpty() ? 69 : 65 - timeWidth;
            float scale = Math.clamp((float) available / Math.max(1, font.width(state)), .58f, 1f);
            String label = font.plainSubstrByWidth(state, (int) (available / scale));
            glyphs.add(new WorldGraphSurface.Glyph(label, x, y + Math.round((1 - scale) * 3), color(node.status()), scale));
            if (!time.isEmpty()) glyphs.add(new WorldGraphSurface.Glyph(time, x + 69 - timeWidth, y, CYAN, 1));
        }
        return glyphs;
    }

    private static int channelColor(int channel) {
        return CHANNEL_COLORS[Math.floorMod(channel, CHANNEL_COLORS.length)];
    }

    private static void drawPath(Surface out, Corner[] path, int color) {
        for (int i = 1; i < path.length; i++)
            line(out, path[i - 1].x, path[i - 1].y, path[i].x, path[i].y, color);
    }
    private static String shortLabel(NetworkView.Node node) {
        if (!node.name().isBlank()) return node.name();
        if (node.kind().equals("automation")) return tr("kind.camera");
        if (node.kind().equals("door")) {
            String[] coordinates = node.position().split(", ");
            if (coordinates.length == 3) return tr("kind.door") + " " + coordinates[0] + "/" + coordinates[2];
        }
        return display(node);
    }
    record Corner(int x, int y) {}
    record Trace(GraphRoutes.Key key, Corner[] corners, int color, Corner junction) {
        Trace(GraphRoutes.Key key, Corner[] corners, int color) { this(key, corners, color, null); }
    }

    static List<Trace> traces(GraphLayout.Page page) {
        return traces(page, GraphRouting.docks(page));
    }

    static List<Trace> traces(GraphLayout.Page page, Map<GraphRoutes.SocketKey, GraphRouting.Dock> docks) {
        Map<GraphRoutes.Key, Integer> colors = new LinkedHashMap<>();
        for (var edge : page.edges()) {
            var key = GraphRoutes.key(edge);
            var source = docks.get(new GraphRoutes.SocketKey(edge.source(), -1, 0));
            colors.put(key, kind(source.point().node()));
        }
        for (var wire : page.wires()) {
            var key = GraphRoutes.key(wire);
            colors.put(key, channelColor(wire.channel()));
        }
        var order = new ArrayList<>(colors.keySet());
        order.sort(Comparator.<GraphRoutes.Key, Boolean>comparing(key -> !page.routes().containsKey(key))
            .thenComparingInt(GraphRoutes.Key::channel).thenComparing(GraphRoutes.Key::from)
            .thenComparingInt(GraphRoutes.Key::fromSlot).thenComparing(GraphRoutes.Key::to).thenComparingInt(GraphRoutes.Key::toSlot));
        Map<GraphRoutes.Key, Trace> traces = new LinkedHashMap<>();
        List<Trace> occupied = new ArrayList<>();
        docks.forEach((key, dock) -> {
            colors.keySet().stream().filter(route -> GraphRoutes.from(route).equals(key) || GraphRoutes.to(route).equals(key))
                .findFirst().ifPresent(route -> occupied.add(new Trace(route, new Corner[]{dock.edge(), dock.exit()}, 0)));
        });
        var remaining = new LinkedHashSet<>(order);
        while (!remaining.isEmpty()) {
            boolean progress = false;
            for (var key : new ArrayList<>(remaining)) {
                var parent = page.joins().get(key);
                if (parent != null && remaining.contains(parent)) continue;
                Trace trace;
                var trunk = traces.get(parent);
                var common = trunk == null ? null : GraphRoutes.common(key, parent);
                if (common == null || page.routes().containsKey(key)) {
                    trace = new Trace(key, path(page, key, docks, occupied), colors.get(key));
                } else {
                    var destination = docks.get(common.equals(GraphRoutes.from(key)) ? GraphRoutes.to(key) : GraphRoutes.from(key));
                    var joined = GraphJunctions.connect(page, key, trunk, destination.exit(), occupied);
                    trace = new Trace(key, joined.path(), colors.get(key), joined.junction());
                }
                traces.put(key, trace);
                occupied.add(trace);
                remaining.remove(key);
                progress = true;
            }
            if (!progress) {
                // A malformed cyclic snapshot falls back to independent automatic paths.
                for (var key : remaining) {
                    var trace = new Trace(key, path(page, key, docks, occupied), colors.get(key));
                    traces.put(key, trace);
                    occupied.add(trace);
                }
                break;
            }
        }
        return List.copyOf(traces.values());
    }

    private static Corner[] path(GraphLayout.Page page, GraphRoutes.Key key,
                                 Map<GraphRoutes.SocketKey, GraphRouting.Dock> docks, List<Trace> occupied) {
        var from = docks.get(GraphRoutes.from(key));
        var to = docks.get(GraphRoutes.to(key));
        var start = from.exit();
        var end = to.exit();
        var bends = page.routes().getOrDefault(key, List.of());
        if (bends.isEmpty()) return GraphRouting.automatic(page, start, end, occupied, key.channel());
        var points = new ArrayList<Corner>();
        points.add(start);
        for (int i = 0; i < bends.size(); i++) {
            var bend = bends.get(i);
            addCorner(points, new Corner(bend.x(), bend.y()), page, i == 0 ? from.point() : null);
        }
        addCorner(points, end, page, to.point());
        return GraphRouting.simplified(points).toArray(Corner[]::new);
    }

    private static void addCorner(List<Corner> points, Corner next, GraphLayout.Page page, GraphLayout.Point card) {
        Corner last = points.getLast();
        Corner elbow = new Corner(next.x, last.y);
        if (card != null && (GraphRouting.intersects(last, elbow, card) || GraphRouting.intersects(elbow, next, card))) {
            var path = GraphRouting.automatic(page, last, next);
            for (int i = 1; i < path.length; i++) points.add(path[i]);
            return;
        }
        if (last.x != next.x && last.y != next.y) points.add(elbow);
        points.add(next);
    }
    private static void line(Surface out, int x1, int y1, int x2, int y2, int color) {
        out.rect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1) + 1, Math.abs(y2 - y1) + 1, color);
    }
}
