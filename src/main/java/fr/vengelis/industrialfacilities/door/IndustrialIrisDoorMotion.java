package fr.vengelis.industrialfacilities.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The octagonal opening, shared by the renderer and collision checks. */
public final class IndustrialIrisDoorMotion {
    public static final double BACK = 6 / 16.0;
    public static final double FRONT = 10 / 16.0;
    private static final int STEPS = 8;
    private static final double SQRT_2 = Math.sqrt(2);
    private static final double MAX_PRE_DIAGONAL_PROGRESS = 2 - SQRT_2;
    private static final double EPSILON = 1e-8;

    public record Pivot(double cos, double sin) {}

    private record Normal(double x, double y) {}

    public static Pivot pivot(double progress) {
        // The hinge sits one aperture width from the centre, along the blade's trailing edge.
        // Keep the apothem at the configured fraction of the square's support at this angle.
        double angle = Math.atan(progress <= MAX_PRE_DIAGONAL_PROGRESS
            ? progress / SQRT_2 : progress / (2 - progress));
        return new Pivot(Math.cos(angle), Math.sin(angle));
    }

    private static Normal[] axes(Pivot pivot) {
        double cos = pivot.cos(), sin = pivot.sin();
        double adjacentX = (cos + sin) / SQRT_2, adjacentY = (cos - sin) / SQRT_2;
        return new Normal[]{new Normal(cos, -sin), new Normal(adjacentX, adjacentY),
            new Normal(sin, cos), new Normal(adjacentY, -adjacentX)};
    }

    public static AABB slab(int size) {
        return new AABB(0, 0, BACK, size, size, FRONT);
    }

    public static VoxelShape collision(int size, double progress, Direction facing, int column, int row) {
        if (progress >= 1 || column < 0 || row < 0 || column >= size || row >= size) return Shapes.empty();
        if (progress <= 0) return strip(facing, 0, 0, 1, 1);
        double center = size * .5;
        Pivot pivot = pivot(progress);
        double span = size * pivot.sin();
        Normal[] axes = axes(pivot);
        boolean cellClear = true;
        for (Normal normal : axes) {
            double distance = Math.abs(normal.x() * (column + .5 - center) + normal.y() * (row + .5 - center));
            double reach = (normal.x() + Math.abs(normal.y())) * .5;
            if (distance - reach >= span) return strip(facing, 0, 0, 1, 1);
            cellClear &= distance + reach <= span + EPSILON;
        }
        if (cellClear) return Shapes.empty();

        VoxelShape result = Shapes.empty();
        for (int strip = 0; strip < STEPS; strip++) {
            double y0 = (double) strip / STEPS, y1 = (double) (strip + 1) / STEPS;
            double left = 0, right = 1;
            for (Normal normal : axes) {
                double n0 = normal.y() * (row + y0 - center), n1 = normal.y() * (row + y1 - center);
                double low = Math.min(n0, n1), high = Math.max(n0, n1);
                if (normal.x() <= EPSILON) {
                    if (low < -span - EPSILON || high > span + EPSILON) {
                        right = left;
                        break;
                    }
                } else {
                    double offset = center - column;
                    left = Math.max(left, roundUp((-span - low) / normal.x() + offset));
                    right = Math.min(right, roundDown((span - high) / normal.x() + offset));
                }
            }
            if (left >= right) {
                result = Shapes.or(result, strip(facing, 0, y0, 1, y1));
            } else {
                if (left > 0) result = Shapes.or(result, strip(facing, 0, y0, left, y1));
                if (right < 1) result = Shapes.or(result, strip(facing, right, y0, 1, y1));
            }
        }
        return result;
    }

    private static VoxelShape strip(Direction facing, double x0, double y0, double x1, double y1) {
        return Shapes.create(IndustrialDoorPlane.of(facing).worldBox(BlockPos.ZERO, new AABB(x0, y0, BACK, x1, y1, FRONT)));
    }

    public static boolean isClear(int size, double progress, AABB localEntity) {
        if (progress >= 1) return true;
        if (progress <= 0) return false;
        Pivot pivot = pivot(progress);
        double span = size * pivot.sin();
        double x0 = Math.max(0, roundDown(localEntity.minX)), x1 = Math.min(size, roundUp(localEntity.maxX));
        double y0 = Math.max(0, roundDown(localEntity.minY)), y1 = Math.min(size, roundUp(localEntity.maxY));
        double x = (x0 + x1 - size) * .5, y = (y0 + y1 - size) * .5;
        double halfWidth = (x1 - x0) * .5, halfHeight = (y1 - y0) * .5;
        for (Normal normal : axes(pivot)) {
            double distance = Math.abs(normal.x() * x + normal.y() * y);
            double reach = normal.x() * halfWidth + Math.abs(normal.y()) * halfHeight;
            if (distance + reach > span + EPSILON) return false;
        }
        return true;
    }

    private static double roundDown(double x) {
        return Math.floor(x * STEPS + EPSILON) / STEPS;
    }

    private static double roundUp(double x) {
        return Math.ceil(x * STEPS - EPSILON) / STEPS;
    }

    private IndustrialIrisDoorMotion() {}
}
