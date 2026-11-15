package fr.vengelis.industrialfacilities.alarm;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/** Active fire alarms, indexed by level without retaining unloaded block entities. */
public final class FireAlarmControls {
    private static final Map<ServerLevel, Map<BlockPos, Integer>> ACTIVE = new WeakHashMap<>();

    static synchronized void update(ServerLevel level, BlockPos position, int radius, boolean active) {
        if (active) {
            ACTIVE.computeIfAbsent(level, key -> new HashMap<>()).put(position.immutable(), radius);
        } else {
            var positions = ACTIVE.get(level);
            if (positions == null) return;
            positions.remove(position);
            if (positions.isEmpty()) ACTIVE.remove(level);
        }
    }

    /** The closest part of the door must lie within the alarm's configured radius. */
    public static synchronized boolean blocksOpening(ServerLevel level, BlockPos first, BlockPos last) {
        var positions = ACTIVE.get(level);
        if (positions == null) return false;
        for (var alarm : positions.entrySet()) {
            BlockPos p = alarm.getKey();
            double dx = p.getX() - Mth.clamp(p.getX(), Math.min(first.getX(), last.getX()), Math.max(first.getX(), last.getX()));
            double dy = p.getY() - Mth.clamp(p.getY(), Math.min(first.getY(), last.getY()), Math.max(first.getY(), last.getY()));
            double dz = p.getZ() - Mth.clamp(p.getZ(), Math.min(first.getZ(), last.getZ()), Math.max(first.getZ(), last.getZ()));
            if (dx * dx + dy * dy + dz * dz <= (double) alarm.getValue() * alarm.getValue()) return true;
        }
        return false;
    }

    private FireAlarmControls() {}
}
