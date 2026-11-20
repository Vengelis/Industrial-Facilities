package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.frequency.FrequencyEndpoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class GraphRoutes {
    public static final int MAX_POINTS = 24;
    public static final int GRID = 12, GRID_X = 7, GRID_Y = 16;
    static final int MAX_ROUTES = 1024;

    /** Points are stored in order from the lower UUID to the higher UUID. */
    public record Key(UUID from, int fromSlot, UUID to, int toSlot, int channel) {
        public Key {
            if (from.compareTo(to) > 0) {
                UUID oldFrom = from;
                int oldSlot = fromSlot;
                from = to;
                fromSlot = toSlot;
                to = oldFrom;
                toSlot = oldSlot;
            }
        }

        public boolean valid() {
            if (from.equals(to)) return false;
            if (fromSlot == -1 && toSlot == -1) return channel == 0;
            return fromSlot >= 0 && fromSlot < FrequencyEndpoint.CONSOLE_SLOTS
                && toSlot >= 0 && toSlot < FrequencyEndpoint.CONSOLE_SLOTS;
        }
    }

    public record Bend(int x, int y) {
        public Bend within(GraphLayout.Canvas canvas) {
            return new Bend(Math.clamp(x, 1, canvas.width() - 2), Math.clamp(y, 17, canvas.height() - 1));
        }

        public Bend snapped(GraphLayout.Canvas canvas) {
            return new Bend(snapX(x, canvas), snapY(y, canvas));
        }

        Bend resized(GraphLayout.Canvas before, GraphLayout.Canvas after) {
            int xx = 1 + (int) Math.round((x - 1) * (after.width() - 3.0) / (before.width() - 3));
            int yy = 17 + (int) Math.round((y - 17) * (after.height() - 18.0) / (before.height() - 18));
            if (Math.floorMod(x - GRID_X, GRID) == 0) xx = snapX(xx, after);
            if (Math.floorMod(y - GRID_Y, GRID) == 0) yy = snapY(yy, after);
            return new Bend(xx, yy).within(after);
        }
    }

    public enum Side {
        TOP(0, -1), RIGHT(1, 0), BOTTOM(0, 1), LEFT(-1, 0);

        public final int dx, dy;
        Side(int dx, int dy) { this.dx = dx; this.dy = dy; }
        public boolean horizontal() { return dy != 0; }
    }

    public record SocketKey(UUID node, int slot, int channel) {
        public boolean valid() {
            return slot == -1 ? channel == 0 : slot >= 0 && slot < FrequencyEndpoint.CONSOLE_SLOTS;
        }
    }

    public record Socket(Side side, int offset) {
        public Socket {
            int limit = side.horizontal() ? 32 : 7;
            offset = Math.clamp(offset, -limit, limit);
        }
    }

    public static int snapX(double x, GraphLayout.Canvas canvas) { return snap(x, GRID_X, 1, canvas.width() - 2); }
    public static int snapY(double y, GraphLayout.Canvas canvas) { return snap(y, GRID_Y, 17, canvas.height() - 1); }

    private static int snap(double value, int origin, int minimum, int maximum) {
        int first = Math.ceilDiv(minimum - origin, GRID), last = Math.floorDiv(maximum - origin, GRID);
        long step = Math.clamp(Math.round((value - origin) / GRID), first, last);
        return origin + (int) step * GRID;
    }

    public static SocketKey from(Key key) { return new SocketKey(key.from, key.fromSlot, key.channel); }
    public static SocketKey to(Key key) { return new SocketKey(key.to, key.toSlot, key.channel); }

    public static SocketKey common(Key a, Key b) {
        if (a.equals(b) || a.fromSlot < 0 || b.fromSlot < 0 || a.channel != b.channel) return null;
        if (from(a).equals(from(b)) || from(a).equals(to(b))) return from(a);
        return to(a).equals(from(b)) || to(a).equals(to(b)) ? to(a) : null;
    }

    public static boolean canJoin(Key child, Key parent, Map<Key, Key> joins) {
        if (common(child, parent) == null) return false;
        Set<Key> seen = new HashSet<>();
        seen.add(child);
        for (Key key = parent; key != null; key = joins.get(key)) if (!seen.add(key)) return false;
        return true;
    }

    public static Key key(NetworkView.Edge edge) { return new Key(edge.source(), -1, edge.target(), -1, 0); }
    public static Key key(NetworkView.Wire wire) {
        return new Key(wire.source(), wire.sourceSlot(), wire.target(), wire.targetSlot(), wire.channel());
    }

    static ListTag write(Map<Key, List<Bend>> routes) {
        var list = new ListTag();
        routes.forEach((key, points) -> {
            var row = new CompoundTag();
            row.putUUID("From", key.from);
            row.putUUID("To", key.to);
            row.putInt("FromSlot", key.fromSlot);
            row.putInt("ToSlot", key.toSlot);
            row.putInt("Channel", key.channel);
            var bends = new ListTag();
            for (var point : points) {
                var bend = new CompoundTag();
                bend.putInt("X", point.x);
                bend.putInt("Y", point.y);
                bends.add(bend);
            }
            row.put("Points", bends);
            list.add(row);
        });
        return list;
    }

    static Map<Key, List<Bend>> read(ListTag list, GraphLayout.Canvas canvas) {
        Map<Key, List<Bend>> routes = new HashMap<>();
        for (Tag raw : list) {
            if (routes.size() >= MAX_ROUTES) break;
            var row = (CompoundTag) raw;
            if (!row.hasUUID("From") || !row.hasUUID("To")) continue;
            var key = new Key(row.getUUID("From"), row.getInt("FromSlot"), row.getUUID("To"), row.getInt("ToSlot"), row.getInt("Channel"));
            var points = row.getList("Points", Tag.TAG_COMPOUND);
            if (!key.valid() || points.isEmpty() || points.size() > MAX_POINTS) continue;
            List<Bend> bends = new ArrayList<>();
            for (Tag value : points) {
                var point = (CompoundTag) value;
                bends.add(new Bend(point.getInt("X"), point.getInt("Y")).within(canvas));
            }
            routes.put(key, List.copyOf(bends));
        }
        return Map.copyOf(routes);
    }

    static ListTag writeSockets(Map<SocketKey, Socket> sockets) {
        var list = new ListTag();
        sockets.forEach((key, socket) -> {
            var row = new CompoundTag();
            row.putUUID("Node", key.node);
            row.putInt("Slot", key.slot);
            row.putInt("Channel", key.channel);
            row.putString("Side", socket.side.name());
            row.putInt("Offset", socket.offset);
            list.add(row);
        });
        return list;
    }

    static Map<SocketKey, Socket> readSockets(ListTag list) {
        Map<SocketKey, Socket> sockets = new HashMap<>();
        for (Tag raw : list) {
            if (sockets.size() >= MAX_ROUTES) break;
            var row = (CompoundTag) raw;
            if (!row.hasUUID("Node")) continue;
            var key = new SocketKey(row.getUUID("Node"), row.getInt("Slot"), row.getInt("Channel"));
            if (!key.valid()) continue;
            try {
                var socket = new Socket(Side.valueOf(row.getString("Side")), row.getInt("Offset"));
                sockets.put(key, socket);
            } catch (IllegalArgumentException ignored) {
                // Unknown attachment sides are ignored when loading older or edited data.
            }
        }
        return Map.copyOf(sockets);
    }

    static ListTag writeJoins(Map<Key, Key> joins) {
        var list = new ListTag();
        joins.forEach((child, parent) -> {
            var row = new CompoundTag();
            row.put("Child", writeKey(child));
            row.put("Parent", writeKey(parent));
            list.add(row);
        });
        return list;
    }

    static Map<Key, Key> readJoins(ListTag list) {
        Map<Key, Key> joins = new HashMap<>();
        for (Tag raw : list) {
            if (joins.size() >= MAX_ROUTES) break;
            var row = (CompoundTag) raw;
            Key child = readKey(row.getCompound("Child")), parent = readKey(row.getCompound("Parent"));
            if (child != null && parent != null && canJoin(child, parent, joins)) joins.put(child, parent);
        }
        return Map.copyOf(joins);
    }

    private static CompoundTag writeKey(Key key) {
        var tag = new CompoundTag();
        tag.putUUID("From", key.from);
        tag.putUUID("To", key.to);
        tag.putInt("FromSlot", key.fromSlot);
        tag.putInt("ToSlot", key.toSlot);
        tag.putInt("Channel", key.channel);
        return tag;
    }

    private static Key readKey(CompoundTag tag) {
        if (!tag.hasUUID("From") || !tag.hasUUID("To")) return null;
        var key = new Key(tag.getUUID("From"), tag.getInt("FromSlot"), tag.getUUID("To"), tag.getInt("ToSlot"), tag.getInt("Channel"));
        return key.valid() ? key : null;
    }

    private GraphRoutes() {}
}
