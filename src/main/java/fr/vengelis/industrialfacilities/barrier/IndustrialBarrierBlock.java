package fr.vengelis.industrialfacilities.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Solid base barriers support a fine textured wire mesh of the same variant. */
public class IndustrialBarrierBlock extends WallBlock {
    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");
    public static final BooleanProperty PANELLED = BooleanProperty.create("panelled");
    private static final VoxelShape[] SHAPES = makeBarrierShapes();
    private static final VoxelShape[] GRID_SHAPES = makeGridShapes();

    public IndustrialBarrierBlock(MapColor color) {
        super(BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().dynamicShape().noOcclusion());
        registerDefaultState(defaultBlockState().setValue(STACKED, false).setValue(PANELLED, false)
            .setValue(NORTH_EAST, false).setValue(SOUTH_EAST, false).setValue(SOUTH_WEST, false).setValue(NORTH_WEST, false));
    }

    private static VoxelShape[] makeBarrierShapes() {
        VoxelShape[] shapes = new VoxelShape[32];
        VoxelShape post = Shapes.or(Block.box(3, 0, 5, 13, 3, 11), Block.box(5, 0, 3, 11, 3, 13),
            Block.box(4, 3, 5.6, 12, 14, 10.4), Block.box(5.6, 3, 4, 10.4, 14, 12),
            Block.box(3, 14, 5, 13, 16, 11), Block.box(5, 14, 3, 11, 16, 13));
        VoxelShape north = Shapes.or(Block.box(4.75, 0, 0, 11.25, 3, 8),
            Block.box(6.12, 3, 0, 9.88, 16, 8));
        VoxelShape east = Shapes.or(Block.box(8, 0, 4.75, 16, 3, 11.25),
            Block.box(8, 3, 6.12, 16, 16, 9.88));
        VoxelShape south = Shapes.or(Block.box(4.75, 0, 8, 11.25, 3, 16),
            Block.box(6.12, 3, 8, 9.88, 16, 16));
        VoxelShape west = Shapes.or(Block.box(0, 0, 4.75, 8, 3, 11.25),
            Block.box(0, 3, 6.12, 8, 16, 9.88));
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = (mask & 16) != 0 ? post : Shapes.empty();
            if ((mask & 1) != 0) shape = Shapes.or(shape, north);
            if ((mask & 2) != 0) shape = Shapes.or(shape, east);
            if ((mask & 4) != 0) shape = Shapes.or(shape, south);
            if ((mask & 8) != 0) shape = Shapes.or(shape, west);
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    // A continuous thin collision panel makes the fine textured mesh easy to target.
    private static VoxelShape gridArm(Direction direction) {
        return switch (direction) {
            case EAST -> Block.box(8, 0, 7.25, 16, 16, 8.75);
            case SOUTH -> Block.box(7.25, 0, 8, 8.75, 16, 16);
            case WEST -> Block.box(0, 0, 7.25, 8, 16, 8.75);
            default -> Block.box(7.25, 0, 0, 8.75, 16, 8);
        };
    }

    private static VoxelShape[] makeGridShapes() {
        VoxelShape[] shapes = new VoxelShape[32];
        VoxelShape post = Shapes.or(Block.box(4, 0, 5.6, 12, 14, 10.4),
            Block.box(5.6, 0, 4, 10.4, 14, 12),
            Block.box(3, 14, 5, 13, 16, 11), Block.box(5, 14, 3, 11, 16, 13));
        VoxelShape north = gridArm(Direction.NORTH), east = gridArm(Direction.EAST),
            south = gridArm(Direction.SOUTH), west = gridArm(Direction.WEST);
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = (mask & 16) != 0 ? post : Shapes.empty();
            if ((mask & 1) != 0) shape = Shapes.or(shape, north);
            if ((mask & 2) != 0) shape = Shapes.or(shape, east);
            if ((mask & 4) != 0) shape = Shapes.or(shape, south);
            if ((mask & 8) != 0) shape = Shapes.or(shape, west);
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    public static final BooleanProperty NORTH_EAST = BooleanProperty.create("north_east");
    public static final BooleanProperty SOUTH_EAST = BooleanProperty.create("south_east");
    public static final BooleanProperty SOUTH_WEST = BooleanProperty.create("south_west");
    public static final BooleanProperty NORTH_WEST = BooleanProperty.create("north_west");
    private static final BooleanProperty[] DIAGONALS = {NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST};
    private static final int[][] DIAGONAL_OFFSETS = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
    private static final VoxelShape[] DIAGONAL_SOLID = makeDiagonalShapes(false);
    private static final VoxelShape[] DIAGONAL_GRID = makeDiagonalShapes(true);
    private static final java.util.concurrent.ConcurrentMap<Integer, VoxelShape> CONNECTED_SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    private static VoxelShape diagonalRibbon(int dx, int dz, int width) {
        // Four broad boxes on integer coordinates avoid hundreds of tiny outline edges.
        // Both infill styles use full-height collision; no rib or bevel enters the shape.
        VoxelShape result = Shapes.empty();
        double half = width / 2.0;
        for (int i = 0; i < 4; i++) {
            double x = 8 + dx * (1 + i * 2), z = 8 + dz * (1 + i * 2);
            result = Shapes.or(result, Block.box(x-half, 0, z-half, x+half, 16, z+half));
        }
        return result.optimize();
    }

    private static VoxelShape[] makeDiagonalShapes(boolean mesh) {
        VoxelShape[] result = new VoxelShape[4];
        for (int i = 0; i < 4; i++)
            result[i] = diagonalRibbon(DIAGONAL_OFFSETS[i][0], DIAGONAL_OFFSETS[i][1], mesh ? 4 : 6);
        return result;
    }

    private static VoxelShape simpleDiagonalCore(int cardinal, boolean grid) {
        // Keep every component on the same coarse grid: detailed octagonal posts would
        // otherwise subdivide the diagonal ribbon again during voxel-shape unions.
        VoxelShape shape = (cardinal & 16) != 0 ? Block.box(3,0,3,13,16,13) : Shapes.empty();
        int low = grid ? 7 : 5, high = 16-low;
        if ((cardinal & 1) != 0) shape = Shapes.or(shape,Block.box(low,0,0,high,16,8));
        if ((cardinal & 2) != 0) shape = Shapes.or(shape,Block.box(8,0,low,16,16,high));
        if ((cardinal & 4) != 0) shape = Shapes.or(shape,Block.box(low,0,8,high,16,16));
        if ((cardinal & 8) != 0) shape = Shapes.or(shape,Block.box(0,0,low,8,16,high));
        return shape;
    }

    private static int diagonalMask(BlockState state) {
        int mask = 0;
        for (int i = 0; i < DIAGONALS.length; i++) if (state.getValue(DIAGONALS[i])) mask |= 1 << i;
        return mask;
    }

    @Override protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        BlockState result = super.rotate(state, rotation);
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        for (int i = 0; i < 4; i++) result = result.setValue(DIAGONALS[(i + turns) % 4], state.getValue(DIAGONALS[i]));
        return result;
    }

    @Override protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        BlockState result = super.mirror(state, mirror);
        for (int i = 0; i < 4; i++) {
            int target = switch (mirror) {
                case LEFT_RIGHT -> i ^ 1;
                case FRONT_BACK -> 3 - i;
                default -> i;
            };
            result = result.setValue(DIAGONALS[target], state.getValue(DIAGONALS[i]));
        }
        return result;
    }

    private static boolean clearForDiagonal(LevelAccessor level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean connectsDiagonally(LevelAccessor level, BlockPos pos, int dx, int dz) {
        BlockPos other = pos.offset(dx, 0, dz);
        return level.hasChunkAt(other) && level.getBlockState(other).getBlock() instanceof IndustrialBarrierBlock
            && clearForDiagonal(level, pos.offset(dx, 0, 0))
            && clearForDiagonal(level, pos.offset(0, 0, dz));
    }

    private BlockState connectedState(BlockState state, LevelAccessor level, BlockPos pos) {
        state = state.setValue(STACKED, level.getBlockState(pos.below()).is(this));
        for (int i = 0; i < DIAGONALS.length; i++) {
            int[] offset = DIAGONAL_OFFSETS[i];
            state = state.setValue(DIAGONALS[i], connectsDiagonally(level, pos, offset[0], offset[1]));
        }
        int diagonal = diagonalMask(state);
        if (diagonal == 0) return junctionPost(state);
        boolean straight = (shapeIndex(state) & 15) == 0 && (diagonal == 5 || diagonal == 10);
        BlockState above = level.getBlockState(pos.above());
        boolean supportsPost = above.getBlock() instanceof WallBlock && above.getValue(UP);
        return state.setValue(UP, !straight || supportsPost);
    }

    /** Recomputes legacy wall flags as well as diagonals; also used when loading older worlds. */
    public void refreshConnections(Level level, BlockPos pos, BlockState state) {
        if (!state.is(this)) return;
        BlockState vanilla = super.updateShape(state, Direction.UP, level.getBlockState(pos.above()), level, pos, pos.above());
        BlockState updated = connectedState(vanilla, level, pos);
        if (state != updated) level.setBlock(pos, updated, Block.UPDATE_ALL);
    }

    public static void refreshDiagonalNeighbors(Level level, BlockPos pos) {
        for (int[] offset : DIAGONAL_OFFSETS) {
            BlockPos other = pos.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(other)) continue;
            BlockState neighbor = level.getBlockState(other);
            if (neighbor.getBlock() instanceof IndustrialBarrierBlock barrier) barrier.refreshConnections(level, other, neighbor);
        }
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide() && !oldState.is(this)) {
            refreshConnections(level, pos, level.getBlockState(pos));
            refreshDiagonalNeighbors(level, pos);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide() && !newState.is(this)) refreshDiagonalNeighbors(level, pos);
    }

    private static VoxelShape connectedShape(BlockState state) {
        int diagonal = diagonalMask(state), cardinal = shapeIndex(state);
        boolean grid = state.getValue(STACKED);
        if (diagonal == 0) return (grid ? GRID_SHAPES : SHAPES)[cardinal];
        int key = cardinal | (diagonal << 5) | (grid ? 512 : 0);
        return CONNECTED_SHAPES.computeIfAbsent(key, ignored -> {
            VoxelShape shape = simpleDiagonalCore(cardinal, grid);
            for (int i = 0; i < 4; i++) if ((diagonal & (1 << i)) != 0)
                shape = Shapes.or(shape, (grid ? DIAGONAL_GRID : DIAGONAL_SOLID)[i]);
            return shape.optimize();
        });
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STACKED, PANELLED, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST);
    }

    private static int shapeIndex(BlockState state) {
        return (state.getValue(UP) ? 16 : 0)
            | (state.getValue(NORTH_WALL) != WallSide.NONE ? 1 : 0)
            | (state.getValue(EAST_WALL) != WallSide.NONE ? 2 : 0)
            | (state.getValue(SOUTH_WALL) != WallSide.NONE ? 4 : 0)
            | (state.getValue(WEST_WALL) != WallSide.NONE ? 8 : 0);
    }

    private static BlockState junctionPost(BlockState state) {
        // Vanilla walls omit the post at four-way crossings; these metal frames need it.
        return (shapeIndex(state) & 15) == 15 ? state.setValue(UP, true) : state;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : connectedState(state, context.getLevel(), context.getClickedPos());
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connectedState(super.updateShape(state, direction, neighbor, level, pos, neighborPos), level, pos);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return connectedShape(state);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return connectedShape(state);
    }
}
