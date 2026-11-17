package fr.vengelis.industrialfacilities.supervision;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class GraphLayout {
    public static final int WIDTH = 256, HEIGHT = 136;
    public static final int MIN_X = 38, MAX_X = 218, MIN_Y = 28, MAX_Y = 123;
    public static final int MIN_SIZE = 3, MAX_SIZE = 7;
    public record Canvas(int columns, int rows, boolean fullHeight) {
        public static final Canvas DEFAULT = new Canvas(MIN_SIZE, MIN_SIZE);

        public Canvas(int columns, int rows) { this(columns, rows, false); }

        public Canvas {
            columns = Math.clamp(columns, MIN_SIZE, MAX_SIZE);
            rows = Math.clamp(rows, MIN_SIZE, MAX_SIZE);
        }

        public int width() { return WIDTH + (columns - MIN_SIZE) * 96; }
        public int height() { return HEIGHT + (rows - MIN_SIZE + (fullHeight ? 1 : 0)) * 76; }
        int slotsAcross() { return width() / 85; }
        int slotsDown() { return (height() - 20) / 28; }
        int firstRow(int layer) { return layer * slotsDown() / 4; }

        void write(CompoundTag tag) {
            tag.putInt("Columns", columns);
            tag.putInt("Rows", rows);
            if (fullHeight) tag.putBoolean("FullHeight", true);
        }

        static Canvas read(CompoundTag tag) {
            return new Canvas(tag.getInt("Columns"), tag.getInt("Rows"), tag.getBoolean("FullHeight"));
        }
    }
    public record Placement(int page, int x, int y) {
        public static Placement within(int page, int x, int y) {
            return within(Canvas.DEFAULT, page, x, y);
        }
        public static Placement within(Canvas canvas, int page, int x, int y) {
            return new Placement(page, Math.clamp(x, MIN_X, canvas.width() - (WIDTH - MAX_X)),
                Math.clamp(y, MIN_Y, canvas.height() - (HEIGHT - MAX_Y)));
        }

        Placement resized(Canvas before, Canvas after) {
            int x = MIN_X + (int) Math.round((this.x - MIN_X) * (after.width() - 2.0 * MIN_X) / (before.width() - 2.0 * MIN_X));
            int marginY = MIN_Y + HEIGHT - MAX_Y;
            int y = MIN_Y + (int) Math.round((this.y - MIN_Y) * (after.height() - (double) marginY) / (before.height() - marginY));
            return within(after, page, x, y);
        }
    }
    public record Point(NetworkView.Node node, int x, int y, int remoteLinks) {}
    public record Page(List<Point> points, List<NetworkView.Edge> edges, int index, int total, Canvas canvas) {
        public Page(List<Point> points, List<NetworkView.Edge> edges, int index, int total) {
            this(points, edges, index, total, Canvas.DEFAULT);
        }
    }

    private static int layer(NetworkView.Node node) {
        return switch (node.kind()) {
            case "door" -> 0;
            case "terminal" -> 1;
            case "automation" -> 2;
            default -> 3;
        };
    }

    private static List<List<NetworkView.Node>> pages(NetworkView view) {
        var canvas = view.canvas();
        int[] capacity = new int[4];
        for (int i = 0; i < capacity.length; i++)
            capacity[i] = (canvas.firstRow(i + 1) - canvas.firstRow(i)) * canvas.slotsAcross();
        var left = new LinkedHashMap<UUID, NetworkView.Node>();
        view.nodes().forEach(n -> left.put(n.id(), n));
        Map<UUID, List<UUID>> neighbors = new HashMap<>();
        for (var link : view.edges()) {
            neighbors.computeIfAbsent(link.source(), id -> new ArrayList<>()).add(link.target());
            neighbors.computeIfAbsent(link.target(), id -> new ArrayList<>()).add(link.source());
        }
        List<List<NetworkView.Node>> result = new ArrayList<>();
        while (!left.isEmpty()) {
            var sheet = new ArrayList<NetworkView.Node>();
            var queue = new LinkedHashSet<UUID>();
            int[] rows = new int[4];
            while (true) {
                if (queue.isEmpty()) {
                    var next = left.values().stream().filter(n -> rows[layer(n)] < capacity[layer(n)]).findFirst();
                    if (next.isEmpty()) break;
                    queue.add(next.get().id());
                }
                UUID id = queue.removeFirst();
                var node = left.get(id);
                if (node == null || rows[layer(node)] >= capacity[layer(node)]) continue;
                left.remove(id);
                rows[layer(node)]++;
                sheet.add(node);
                for (UUID neighbor : neighbors.getOrDefault(id, List.of())) if (left.containsKey(neighbor)) queue.add(neighbor);
            }
            result.add(sheet);
        }
        return result.isEmpty() ? List.of(List.of()) : result;
    }

    private static List<List<Point>> arranged(NetworkView view) {
        var canvas = view.canvas();
        int columns = canvas.slotsAcross();
        var automatic = pages(view);
        List<List<Point>> result = new ArrayList<>();
        for (int i = 0; i < automatic.size(); i++) result.add(new ArrayList<>());
        for (var node : view.nodes()) {
            var place = view.layout().get(node.id());
            if (place == null) continue;
            sheet(result, place.page()).add(new Point(node, place.x(), place.y(), 0));
        }
        for (int page = 0; page < automatic.size(); page++) {
            var entries = automatic.get(page);
            for (int row = 0; row < 4; row++) {
                int layer = row;
                var line = entries.stream().filter(n -> layer(n) == layer).toList();
                for (int i = 0; i < line.size(); i++) {
                    var node = line.get(i);
                    if (view.layout().containsKey(node.id())) continue;
                    int count = Math.min(columns, line.size() - i / columns * columns);
                    int x = (2 * (i % columns) + 1) * canvas.width() / (2 * count);
                    int y = 29 + (canvas.firstRow(row) + i / columns) * 28;
                    addAutomatic(result, node, page, x, y, canvas);
                }
            }
        }
        while (result.size() > 1 && result.getLast().isEmpty()) result.removeLast();
        return result;
    }

    private static List<Point> sheet(List<List<Point>> sheets, int i) {
        while (sheets.size() <= i) sheets.add(new ArrayList<>());
        return sheets.get(i);
    }

    private static void addAutomatic(List<List<Point>> sheets, NetworkView.Node node, int page, int x, int y, Canvas canvas) {
        var preferred = sheet(sheets, page);
        if (vacant(preferred, x, y)) {
            preferred.add(new Point(node, x, y, 0));
            return;
        }
        for (int p = page; ; p++) {
            var target = sheet(sheets, p);
            for (int row = 0; row < canvas.slotsDown(); row++) for (int col = 0; col < canvas.slotsAcross(); col++) {
                int px = 42 + col * 85, py = 29 + row * 28;
                if (vacant(target, px, py)) {
                    target.add(new Point(node, px, py, 0));
                    return;
                }
            }
        }
    }

    private static boolean vacant(List<Point> points, int x, int y) {
        return points.stream().noneMatch(p -> Math.abs(x - p.x()) < 77 && Math.abs(y - p.y()) < 24);
    }

    public static int pageCount(NetworkView view) { return arranged(view).size(); }

    public static Page page(NetworkView view, int page) {
        var sheets = arranged(view);
        int index = Math.floorMod(page, sheets.size());
        var sheet = sheets.get(index);
        Set<UUID> ids = new HashSet<>();
        sheet.forEach(p -> ids.add(p.node().id()));
        List<NetworkView.Edge> edges = new ArrayList<>();
        Map<UUID, Integer> elsewhere = new HashMap<>();
        for (var e : view.edges()) {
            boolean a = ids.contains(e.source()), b = ids.contains(e.target());
            if (a && b) edges.add(e);
            else if (a || b) elsewhere.merge(a ? e.source() : e.target(), 1, Integer::sum);
        }
        var points = sheet.stream().map(p -> new Point(p.node(), p.x(), p.y(), elsewhere.getOrDefault(p.node().id(), 0))).toList();
        return new Page(points, List.copyOf(edges), index, sheets.size(), view.canvas());
    }

    static ListTag write(Map<UUID, Placement> layout) {
        var tag = new ListTag();
        layout.forEach((id, p) -> {
            var row = new CompoundTag();
            row.putUUID("Id", id);
            row.putInt("Page", p.page());
            row.putInt("X", p.x());
            row.putInt("Y", p.y());
            tag.add(row);
        });
        return tag;
    }

    static Map<UUID, Placement> read(ListTag tag, Canvas canvas) {
        var layout = new HashMap<UUID, Placement>();
        for (var raw : tag) {
            var row = (CompoundTag) raw;
            if (row.hasUUID("Id") && row.getInt("Page") >= 0 && row.getInt("Page") <= 4096)
                layout.put(row.getUUID("Id"), Placement.within(canvas, row.getInt("Page"), row.getInt("X"), row.getInt("Y")));
        }
        return Map.copyOf(layout);
    }
    private GraphLayout() {}
}
