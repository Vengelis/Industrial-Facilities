package fr.vengelis.industrialfacilities.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class IndustrialMetalDoorBlock extends DoorBlock {
    public IndustrialMetalDoorBlock() {
        super(BlockSetType.IRON, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(5.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .noOcclusion().pushReaction(PushReaction.DESTROY));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        BlockState lower = lowerHalf(level, pos);
        if (lower == null) return;

        if (partner(level, pos, lower) == null) {
            Direction side = openingSide(lower);
            if (!join(level, pos, lower, side)) join(level, pos, lower, side.getOpposite());
        }
        updatePower(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide()) {
            updatePower(level, state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below());
        }
    }

    private static @Nullable BlockState lowerHalf(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState lower = level.getBlockState(pos);
        if (!(lower.getBlock() instanceof IndustrialMetalDoorBlock) || lower.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        BlockState upper = level.getBlockState(pos.above());
        return upper.is(lower.getBlock()) && upper.getValue(HALF) == DoubleBlockHalf.UPPER ? lower : null;
    }

    // The latch edge faces the other leaf and the hinge stays on the outside.
    private static Direction openingSide(BlockState lower) {
        Direction facing = lower.getValue(FACING);
        return lower.getValue(HINGE) == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private static @Nullable BlockPos partner(Level level, BlockPos pos, BlockState lower) {
        Direction side = openingSide(lower);
        BlockPos adjacent = pos.relative(side);
        BlockState other = lowerHalf(level, adjacent);
        return other != null && other.getValue(FACING) == lower.getValue(FACING) && openingSide(other) == side.getOpposite()
            ? adjacent : null;
    }

    private static boolean join(Level level, BlockPos pos, BlockState lower, Direction side) {
        BlockPos adjacent = pos.relative(side);
        BlockState other = lowerHalf(level, adjacent);
        Direction facing = lower.getValue(FACING);
        if (other == null || other.getValue(FACING) != facing || partner(level, adjacent, other) != null) return false;

        DoorHingeSide hinge = side == facing.getClockWise() ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
        level.setBlock(pos, lower.setValue(HINGE, hinge), Block.UPDATE_CLIENTS);
        level.setBlock(adjacent, other.setValue(HINGE, hinge == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT), Block.UPDATE_CLIENTS);
        return true;
    }

    private static void updatePower(Level level, BlockPos pos) {
        BlockState lower = lowerHalf(level, pos);
        if (lower == null) return;
        BlockPos other = partner(level, pos, lower);
        // Read the redstone inputs directly so an open partner cannot keep the pair powered.
        boolean powered = receivesSignal(level, pos) || other != null && receivesSignal(level, other);
        applyPower(level, pos, powered);
        if (other != null) applyPower(level, other, powered);
    }

    private static boolean receivesSignal(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
    }

    private static void applyPower(Level level, BlockPos pos, boolean powered) {
        BlockState lower = level.getBlockState(pos);
        if (!(lower.getBlock() instanceof IndustrialMetalDoorBlock) || lower.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        boolean moved = lower.getValue(OPEN) != powered;
        if (!moved && lower.getValue(POWERED) == powered) return;

        // DoorBlock.updateShape copies the lower state to the upper half.
        BlockState updated = lower.setValue(OPEN, powered).setValue(POWERED, powered);
        if (level.setBlock(pos, updated, Block.UPDATE_CLIENTS) && moved) {
            BlockSetType metal = BlockSetType.IRON;
            level.playSound(null, pos, powered ? metal.doorOpen() : metal.doorClose(), SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(powered ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos, GameEvent.Context.of(updated));
        }
    }
}
