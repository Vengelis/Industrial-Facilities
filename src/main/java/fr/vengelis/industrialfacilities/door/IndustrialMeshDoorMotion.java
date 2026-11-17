package fr.vengelis.industrialfacilities.door;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Positions of the two sliding panels and safety beam relative to the clear opening. */
public final class IndustrialMeshDoorMotion {
    public static final double HAZARD_HEIGHT = 3 / 16.0;
    private static final double HALF_THICKNESS = 1 / 16.0;

    public record Panel(double start, double end, double rise, double depth, double halfThickness) {
        public double bottom() {
            return start + rise;
        }

        public double nearDepth() {
            return depth - halfThickness;
        }

        public double farDepth() {
            return depth + halfThickness;
        }

        /** Includes the vertical travel between the previous and next closing positions. */
        public Panel sweptFrom(Panel previous) {
            return new Panel(start, end + (previous.rise - rise), rise, depth, halfThickness);
        }

        public @Nullable AABB bounds(double width, double height, Direction facing) {
            double minY = bottom();
            double maxY = Math.min(height, end + rise);
            if (minY >= maxY) return null;
            double center = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? depth : 1 - depth;
            double near = center - halfThickness;
            double far = center + halfThickness;
            return facing.getAxis() == Direction.Axis.Z
                ? new AABB(0, minY, near, width, maxY, far)
                : new AABB(near, minY, 0, far, maxY, width);
        }
    }

    public static Panel lower(double height, double progress) {
        return new Panel(0, height / 2, height * progress, 7 / 16.0, HALF_THICKNESS);
    }

    public static Panel upper(double height, double progress) {
        double half = height / 2;
        // The upper panel stays at the seam until the lower one has caught up.
        return new Panel(half, height, Math.max(0, height * progress - half), 9 / 16.0, HALF_THICKNESS);
    }

    public static Panel hazardBeam(Panel lower) {
        // The beam travels with the lower panel and wraps across both tracks.
        return new Panel(0, HAZARD_HEIGHT, lower.rise(), .5, 2 * HALF_THICKNESS);
    }

    private IndustrialMeshDoorMotion() {}
}
