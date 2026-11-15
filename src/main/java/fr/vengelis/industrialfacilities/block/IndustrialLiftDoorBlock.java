package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import fr.vengelis.industrialfacilities.door.IndustrialLiftDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialMetalDoorContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A two-block iron door with a single panel that retracts through its lintel. */
public abstract class IndustrialLiftDoorBlock extends IndustrialMetalDoorBlock {
    protected IndustrialLiftDoorBlock(IndustrialDoorStyle style) {
        super(style, properties(style.blastResistance()).dynamicShape());
    }

    public static boolean isLower(BlockState state) {
        return state.getBlock() instanceof IndustrialLiftDoorBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isLower(state) ? new IndustrialLiftDoorEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == IndustrialMetalDoorContent.LIFT_TYPE && isLower(state)
            ? (world, pos, current, be) -> ((IndustrialLiftDoorEntity)be).tick(world, current) : null;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return isLower(state) && world.getBlockState(pos).is(this) || super.canSurvive(state, world, pos);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape collision = getCollisionShape(state, level, pos, context);
        if (!collision.isEmpty() || isLower(state)) return collision;
        // The upper guide remains targetable when the panel is retracted.
        return panelShape(state.getValue(FACING), .875);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean lower = isLower(state);
        var base = lower ? pos : pos.below();
        float progress = level.getBlockEntity(base) instanceof IndustrialLiftDoorEntity lift ? lift.progress : state.getValue(OPEN) ? 1 : 0;
        double bottom = Math.max(0, progress * 2 - (lower ? 0 : 1));
        return bottom >= 1 ? Shapes.empty() : panelShape(state.getValue(FACING), bottom);
    }

    @Override public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return getCollisionShape(state, level, pos, CollisionContext.empty()).isEmpty() ? 0 : getExplosionResistance();
    }

    public static VoxelShape panelShape(Direction facing, double bottom) {
        double y = bottom * 16;
        return switch (facing) {
            case EAST -> box(0, y, 0, 3, 16, 16);
            case WEST -> box(13, y, 0, 16, 16, 16);
            case SOUTH -> box(0, y, 0, 16, 16, 3);
            default -> box(0, y, 13, 16, 16, 16);
        };
    }
}
