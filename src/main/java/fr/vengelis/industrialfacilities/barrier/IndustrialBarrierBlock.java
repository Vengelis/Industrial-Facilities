package fr.vengelis.industrialfacilities.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
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
    private static final VoxelShape[] SHAPES = makeBarrierShapes();
    private static final VoxelShape[] GRID_SHAPES = makeGridShapes();

    public IndustrialBarrierBlock(MapColor color) {
        super(BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F)
            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(defaultBlockState().setValue(STACKED, false));
    }

    private static VoxelShape[] makeBarrierShapes() {
        VoxelShape[] shapes = new VoxelShape[32];
        VoxelShape post = Shapes.or(Block.box(3, 0, 5, 13, 3, 11), Block.box(5, 0, 3, 11, 3, 13),
            Block.box(4, 3, 5.6, 12, 14, 10.4), Block.box(5.6, 3, 4, 10.4, 14, 12),
            Block.box(3, 14, 5, 13, 16, 11), Block.box(5, 14, 3, 11, 16, 13));
        VoxelShape north = Shapes.or(Block.box(4.75, 0, 0, 11.25, 3, 8),
            Block.box(6.12, 3, 0, 9.88, 14.5, 8));
        VoxelShape east = Shapes.or(Block.box(8, 0, 4.75, 16, 3, 11.25),
            Block.box(8, 3, 6.12, 16, 14.5, 9.88));
        VoxelShape south = Shapes.or(Block.box(4.75, 0, 8, 11.25, 3, 16),
            Block.box(6.12, 3, 8, 9.88, 14.5, 16));
        VoxelShape west = Shapes.or(Block.box(0, 0, 4.75, 8, 3, 11.25),
            Block.box(0, 3, 6.12, 8, 14.5, 9.88));
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

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STACKED);
    }

    private BlockState stackedState(BlockState state, BlockGetter level, BlockPos pos) {
        return junctionPost(state).setValue(STACKED, level.getBlockState(pos.below()).is(this));
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
        return state == null ? null : stackedState(state, context.getLevel(), context.getClickedPos());
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return stackedState(super.updateShape(state, direction, neighbor, level, pos, neighborPos), level, pos);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(STACKED) ? GRID_SHAPES : SHAPES)[shapeIndex(state)];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(STACKED) ? GRID_SHAPES : SHAPES)[shapeIndex(state)];
    }
}
