package fr.vengelis.industrialfacilities.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Equal plates retract in alternating directions along the axis selected by the controller's edge. */
public final class IndustrialJawDoorMotion {
    public static final double BACK = 5.5 / 16;
    public static final double FRONT = 10.5 / 16;

    public static int teeth(int width) {
        // The minimum aperture is 3 blocks, with 4 teeth. Each additional block adds one more.
        return width + 1;
    }

    public static double column(int width, int index) {
        return (double) width * index / teeth(width);
    }

    public static Layout layout(IndustrialDoorEntity door, IndustrialDoorPlane plane) {
        BlockPos origin = door.doorOrigin(), controller = door.getBlockPos();
        int column = plane.column(origin, controller);
        int row = plane.row(origin, controller);
        int width = door.size - 2, height = door.height() - 2;
        // The top/bottom edge takes precedence at the four corners.
        boolean sideways = row > 0 && row < door.height() - 1;
        int span = sideways ? height : width;
        int travel = sideways ? width : height;
        int controllerTooth = Math.clamp((int) Math.floor(((sideways ? row : column) - .5) * teeth(span) / span),
            0, teeth(span) - 1);
        boolean towardsPositive = sideways ? column == 0 : row == door.height() - 1;
        return new Layout(span, travel, sideways, towardsPositive == ((controllerTooth & 1) == 0));
    }

    public record Layout(int span, int travel, boolean sideways, boolean evenPositive) {
        public int toothCount() {
            return teeth(span);
        }

        public double start(int tooth) {
            return column(span, tooth);
        }

        public boolean opensPositive(int tooth) {
            return ((tooth & 1) == 0) == evenPositive;
        }

        public double offset(int tooth, double progress) {
            return (opensPositive(tooth) ? travel : -travel) * progress;
        }

        public VoxelShape collision(double progress, IndustrialDoorPlane plane, int column, int row) {
            int spanCell = sideways ? row : column;
            int travelCell = sideways ? travel - 1 - column : row;
            if (progress >= 1 || spanCell < 0 || travelCell < 0 || spanCell >= span || travelCell >= travel)
                return Shapes.empty();
            if (progress <= 0) return panel(plane, 0, 0, 1, 1);
            VoxelShape result = Shapes.empty();
            for (int tooth = 0; tooth < toothCount(); tooth++) {
                double x0 = Math.max(0, start(tooth) - spanCell);
                double x1 = Math.min(1, start(tooth + 1) - spanCell);
                if (x0 >= x1) continue;
                double shift = offset(tooth, progress) - travelCell;
                double bottom = Math.max(0, shift);
                double top = Math.min(1, travel + shift);
                if (bottom < top) result = Shapes.or(result, panel(plane, x0, bottom, x1, top));
            }
            return result;
        }

        public boolean isClear(double progress, AABB entity) {
            if (progress >= 1) return true;
            double spanMin = sideways ? entity.minY : entity.minX, spanMax = sideways ? entity.maxY : entity.maxX;
            double travelMin = sideways ? travel - entity.maxX : entity.minY;
            double travelMax = sideways ? travel - entity.minX : entity.maxY;
            for (int tooth = 0; tooth < toothCount(); tooth++) {
                if (spanMax <= start(tooth) || spanMin >= start(tooth + 1)) continue;
                double shift = offset(tooth, progress);
                if (travelMin < Math.min(travel, travel + shift) && travelMax > Math.max(0, shift)) return false;
            }
            return true;
        }

        private VoxelShape panel(IndustrialDoorPlane plane, double x0, double y0, double x1, double y1) {
            AABB surface = sideways ? new AABB(1 - y1, x0, BACK, 1 - y0, x1, FRONT)
                : new AABB(x0, y0, BACK, x1, y1, FRONT);
            return Shapes.create(plane.worldBox(BlockPos.ZERO, surface));
        }
    }

    public static AABB slab(int width, int height) {
        return new AABB(0, 0, BACK, width, height, FRONT);
    }

    private IndustrialJawDoorMotion() {}
}
