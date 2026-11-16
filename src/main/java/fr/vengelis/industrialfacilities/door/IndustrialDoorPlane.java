package fr.vengelis.industrialfacilities.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local X follows the columns, Y follows the rows and Z faces out of the door. */
public final class IndustrialDoorPlane {
    public static Direction across(Direction facing) {
        return facing.getAxis().isVertical() ? Direction.EAST : facing.getCounterClockWise();
    }

    public static Direction up(Direction facing) {
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    public static int offset(BlockPos origin, BlockPos pos, Direction axis) {
        return (pos.getX() - origin.getX()) * axis.getStepX() + (pos.getY() - origin.getY()) * axis.getStepY()
            + (pos.getZ() - origin.getZ()) * axis.getStepZ();
    }

    public static AABB worldBox(BlockPos origin, Direction facing, AABB box) {
        return new AABB(worldPoint(origin, facing, box.minX, box.minY, box.minZ),
            worldPoint(origin, facing, box.maxX, box.maxY, box.maxZ));
    }

    public static AABB localBox(BlockPos origin, Direction facing, AABB box) {
        return new AABB(localPoint(origin, facing, box.minX, box.minY, box.minZ),
            localPoint(origin, facing, box.maxX, box.maxY, box.maxZ));
    }

    private static Vec3 worldPoint(BlockPos origin, Direction facing, double x, double y, double z) {
        Direction across = across(facing), up = up(facing);
        return new Vec3(origin.getX() + .5 + across.getStepX() * (x - .5) + up.getStepX() * (y - .5) + facing.getStepX() * (z - .5),
            origin.getY() + .5 + across.getStepY() * (x - .5) + up.getStepY() * (y - .5) + facing.getStepY() * (z - .5),
            origin.getZ() + .5 + across.getStepZ() * (x - .5) + up.getStepZ() * (y - .5) + facing.getStepZ() * (z - .5));
    }

    private static Vec3 localPoint(BlockPos origin, Direction facing, double x, double y, double z) {
        x -= origin.getX() + .5;
        y -= origin.getY() + .5;
        z -= origin.getZ() + .5;
        return new Vec3(project(x, y, z, across(facing)) + .5, project(x, y, z, up(facing)) + .5, project(x, y, z, facing) + .5);
    }

    private static double project(double x, double y, double z, Direction axis) {
        return x * axis.getStepX() + y * axis.getStepY() + z * axis.getStepZ();
    }

    private IndustrialDoorPlane() {}
}
