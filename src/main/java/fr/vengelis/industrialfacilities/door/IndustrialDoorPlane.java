package fr.vengelis.industrialfacilities.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Local X follows the columns, Y follows the rows and Z faces out of the door. */
public enum IndustrialDoorPlane {
    NORTH(Direction.NORTH, Direction.WEST, Direction.UP),
    SOUTH(Direction.SOUTH, Direction.EAST, Direction.UP),
    EAST(Direction.EAST, Direction.NORTH, Direction.UP),
    WEST(Direction.WEST, Direction.SOUTH, Direction.UP),
    UP(Direction.UP, Direction.EAST, Direction.NORTH),
    DOWN(Direction.DOWN, Direction.EAST, Direction.SOUTH),
    UP_ROTATED(Direction.UP, Direction.NORTH, Direction.WEST),
    DOWN_ROTATED(Direction.DOWN, Direction.SOUTH, Direction.WEST);

    private final Direction facing, across, up;

    IndustrialDoorPlane(Direction facing, Direction across, Direction up) {
        this.facing = facing;
        this.across = across;
        this.up = up;
    }

    public static IndustrialDoorPlane of(Direction facing) {
        return of(facing, false);
    }

    public static IndustrialDoorPlane of(Direction facing, boolean rotated) {
        return switch (facing) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> rotated ? UP_ROTATED : UP;
            case DOWN -> rotated ? DOWN_ROTATED : DOWN;
        };
    }

    public Direction facing() { return facing; }
    public Direction across() { return across; }
    public Direction up() { return up; }
    public boolean rotated() { return this == UP_ROTATED || this == DOWN_ROTATED; }

    public BlockPos at(BlockPos origin, int column, int row) {
        return origin.relative(across, column).relative(up, row);
    }

    public AABB bounds(BlockPos origin, int width, int height) {
        return new AABB(origin).minmax(new AABB(at(origin, width - 1, height - 1)));
    }

    public int column(BlockPos origin, BlockPos pos) { return offset(origin, pos, across); }
    public int row(BlockPos origin, BlockPos pos) { return offset(origin, pos, up); }

    public static int offset(BlockPos origin, BlockPos pos, Direction axis) {
        return (pos.getX() - origin.getX()) * axis.getStepX() + (pos.getY() - origin.getY()) * axis.getStepY()
            + (pos.getZ() - origin.getZ()) * axis.getStepZ();
    }

    public AABB worldBox(BlockPos origin, AABB box) {
        return new AABB(worldPoint(origin, box.minX, box.minY, box.minZ),
            worldPoint(origin, box.maxX, box.maxY, box.maxZ));
    }

    public AABB localBox(BlockPos origin, AABB box) {
        return new AABB(localPoint(origin, box.minX, box.minY, box.minZ),
            localPoint(origin, box.maxX, box.maxY, box.maxZ));
    }

    private Vec3 worldPoint(BlockPos origin, double x, double y, double z) {
        return new Vec3(origin.getX() + .5 + across.getStepX() * (x - .5) + up.getStepX() * (y - .5) + facing.getStepX() * (z - .5),
            origin.getY() + .5 + across.getStepY() * (x - .5) + up.getStepY() * (y - .5) + facing.getStepY() * (z - .5),
            origin.getZ() + .5 + across.getStepZ() * (x - .5) + up.getStepZ() * (y - .5) + facing.getStepZ() * (z - .5));
    }

    private Vec3 localPoint(BlockPos origin, double x, double y, double z) {
        x -= origin.getX() + .5;
        y -= origin.getY() + .5;
        z -= origin.getZ() + .5;
        return new Vec3(project(x, y, z, across) + .5, project(x, y, z, up) + .5, project(x, y, z, facing) + .5);
    }

    private static double project(double x, double y, double z, Direction axis) {
        return x * axis.getStepX() + y * axis.getStepY() + z * axis.getStepZ();
    }

}
