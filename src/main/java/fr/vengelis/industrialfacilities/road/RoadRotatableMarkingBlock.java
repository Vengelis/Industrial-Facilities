package fr.vengelis.industrialfacilities.road;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Sixteen visible 22.5 degree headings, selected by aim or adjusted with an empty hand. */
public class RoadRotatableMarkingBlock extends RoadMarkingBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public RoadRotatableMarkingBlock() {
        super();
        registerDefaultState(defaultBlockState().setValue(ROTATION, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Keep FACING for the shared base constructor; the visible heading uses ROTATION.
        super.createBlockStateDefinition(builder);
        builder.add(ROTATION);
    }
    public static int heading(float yaw) { return Math.floorMod(Math.round((yaw + 270F) / 22.5F), 16); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(ROTATION, heading(context.getRotation()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
        if (!world.isClientSide) world.setBlock(pos, state.setValue(ROTATION,
            Math.floorMod(state.getValue(ROTATION) + (player.isShiftKeyDown() ? -1 : 1), 16)), Block.UPDATE_CLIENTS);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        // Base artwork points west. Convert to vanilla's south-origin segment before mirroring.
        int southOrigin = Math.floorMod(state.getValue(ROTATION) + 4, 16);
        return state.setValue(ROTATION, Math.floorMod(mirror.mirror(southOrigin, 16) - 4, 16));
    }
}
