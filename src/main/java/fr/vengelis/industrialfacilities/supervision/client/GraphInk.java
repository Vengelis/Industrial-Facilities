package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class GraphInk {
    static final int INK = 0xFFE4F2F9, MUTED = 0xFFABBDCB, CYAN = 0xFF84D6FF;
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
            case "open", "running", "online", "active" -> 0xFF76E6A9;
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
    static void draw(Surface out, Font font, String sector, GraphLayout.Page page) {
        draw(out, font, sector, page, "no_sector");
    }
    static void draw(Surface out, Font font, String sector, GraphLayout.Page page, String emptySectorKey) {
        int width = page.canvas().width(), height = page.canvas().height();
        out.rect(0, 0, width, height, 0xFF0D1724);
        for (int x = 7; x < width - 2; x += 12) out.rect(x, 16, 1, height - 16, 0xFF142237);
        for (int y = 16; y < height - 2; y += 12) out.rect(0, y, width, 1, 0xFF142237);
        String header = font.plainSubstrByWidth(tr("sector_title", sector.isEmpty() ? "—" : sector), 190);
        out.text(header, 6, 4, CYAN);
        String count = (page.index() + 1) + "/" + page.total();
        out.text(count, width - font.width(count) - 6, 4, MUTED);
        if (page.points().isEmpty()) {
            String text = tr(sector.isEmpty() ? emptySectorKey : "no_nodes");
            out.text(text, (width - font.width(text)) / 2, height / 2 - 7, MUTED);
            return;
        }
        var points = new HashMap<UUID, GraphLayout.Point>();
        page.points().forEach(p -> points.put(p.node().id(), p));
        for (var edge : page.edges()) {
            var from = points.get(edge.source());
            var to = points.get(edge.target());
            int color = kind(from.node());
            var path = route(page.points(), from, to, width, height);
            for (int i = 1; i < path.length; i++)
                line(out, path[i - 1].x(), path[i - 1].y(), path[i].x(), path[i].y(), color);
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
            out.text(font.plainSubstrByWidth(tr("state." + node.status()) + more, 69), x + 3, y + 12, color(node.status()));
        }
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
    private record Corner(int x, int y) {}
    private static Corner[] route(List<GraphLayout.Point> points, GraphLayout.Point from, GraphLayout.Point to,
                                  int width, int height) {
        var start = new Corner(from.x(), from.y());
        var end = new Corner(to.x(), to.y());
        var obstacles = points.stream().filter(point -> point != from && point != to).toList();
        var rows = new LinkedHashSet<Integer>();
        var columns = new LinkedHashSet<Integer>();
        rows.add((start.y + end.y)/2);
        columns.add((start.x + end.x)/2);
        rows.add(17); rows.add(height - 1);
        columns.add(1); columns.add(width - 2);
        for (var obstacle : obstacles) {
            rows.add(Math.max(17, obstacle.y()-12)); rows.add(Math.min(height - 1, obstacle.y()+13));
            columns.add(Math.max(1, obstacle.x()-39)); columns.add(Math.min(width - 2, obstacle.x()+39));
        }
        var candidates = new ArrayList<Corner[]>();
        if (start.y != end.y) for (int row : rows)
            candidates.add(new Corner[]{start, new Corner(start.x,row), new Corner(end.x,row), end});
        for (int col : columns)
            candidates.add(new Corner[]{start, new Corner(col,start.y), new Corner(col,end.y), end});
        int facing = start.y <= end.y ? 1 : -1;
        int fromPort = Math.clamp(start.y + facing * 13, 17, height - 1);
        int toPort = Math.clamp(end.y - facing * 12, 17, height - 1);
        for (int col : columns)
            candidates.add(new Corner[]{start,new Corner(start.x,fromPort),new Corner(col,fromPort),
                new Corner(col,toPort),new Corner(end.x,toPort),end});
        return candidates.stream().min(Comparator.comparingInt(c -> routeCost(c, obstacles))).orElseThrow();
    }
    private static int routeCost(Corner[] corners,List<GraphLayout.Point> obstacles) {
        int cost = corners.length * 2;
        for (int i = 1; i < corners.length; i++) {
            var a = corners[i - 1];
            var b = corners[i];
            cost += Math.abs(a.x-b.x) + Math.abs(a.y-b.y);
            int left = Math.min(a.x,b.x), right = Math.max(a.x,b.x);
            int top = Math.min(a.y,b.y), bottom = Math.max(a.y,b.y);
            for (var p : obstacles) {
                if (left <= p.x()+37 && right >= p.x()-37 && top <= p.y()+11 && bottom >= p.y()-10)
                    cost += 4096;
            }
        }
        return cost;
    }
    private static void line(Surface out, int x1, int y1, int x2, int y2, int color) {
        out.rect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1) + 1, Math.abs(y2 - y1) + 1, color);
    }
}
