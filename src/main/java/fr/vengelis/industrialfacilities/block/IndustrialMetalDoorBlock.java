package fr.vengelis.industrialfacilities.block;

import javax.annotation.Nullable;
import fr.vengelis.industrialfacilities.alarm.FireAlarmControls;
import fr.vengelis.industrialfacilities.door.DoorMotionConfig;
import fr.vengelis.industrialfacilities.door.IndustrialDoorSounds;
import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import fr.vengelis.industrialfacilities.door.IndustrialHingedDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialMetalDoorContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class IndustrialMetalDoorBlock extends DoorBlock implements EntityBlock {
    public final IndustrialDoorStyle style;

    public IndustrialMetalDoorBlock() {
        this(IndustrialDoorStyle.LIGHT);
    }

    protected IndustrialMetalDoorBlock(IndustrialDoorStyle style) {
        this(style, properties(style.blastResistance()));
    }

    protected IndustrialMetalDoorBlock(IndustrialDoorStyle style, BlockBehaviour.Properties properties) {
        super(BlockSetType.IRON, properties);
        this.style = style;
    }

    public static boolean isMetalDoorBase(BlockState state) {
        return state.getBlock() instanceof IndustrialMetalDoorBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMetalDoorBase(state) ? new IndustrialHingedDoorEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != IndustrialMetalDoorContent.HINGE_TYPE || !isMetalDoorBase(state)) return null;
        return (world, pos, current, be) -> ((IndustrialHingedDoorEntity) be).tick(server, current);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Doors already in a save may not have a movement block entity yet.
        if (isMetalDoorBase(state)) level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(OPEN, false).setValue(POWERED, false);
    }

    protected static BlockBehaviour.Properties properties(float blastResistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(5.0F, blastResistance).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .noOcclusion().pushReaction(PushReaction.DESTROY);
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

    protected static void updatePower(Level level, BlockPos pos) {
        BlockState lower = lowerHalf(level, pos);
        if (lower == null) return;
        BlockPos other = partner(level, pos, lower);
        // Read the redstone inputs directly so an open partner cannot keep the pair powered.
        boolean powered = receivesSignal(level, pos) || other != null && receivesSignal(level, other);
        if (powered && level instanceof ServerLevel server
            && (lockedByAlarm(server, pos, lower) || other != null && lockedByAlarm(server, other, level.getBlockState(other)))) {
            powered = false;
        }
        int ticks = motionTicks(level, pos, lower, powered);
        applyPower(level, pos, powered, ticks);
        if (other != null) applyPower(level, other, powered, ticks);
    }

    public static int motionTicks(Level level, BlockPos pos, BlockState state, boolean opening) {
        var block = (IndustrialMetalDoorBlock) state.getBlock();
        int own = DoorMotionConfig.ticks(block.style, true, opening);
        BlockPos adjacent = partner(level, pos, state);
        if (adjacent == null) return own;
        var other = (IndustrialMetalDoorBlock) level.getBlockState(adjacent).getBlock();
        return Math.max(own, DoorMotionConfig.ticks(other.style, true, opening));
    }

    public static boolean finishPivot(Level level, BlockPos pos, boolean opening) {
        BlockState lower = lowerHalf(level, pos);
        if (lower == null || lower.getValue(POWERED) != opening) return false;
        BlockState updated = lower.setValue(OPEN, opening);
        if (!level.setBlock(pos, updated, Block.UPDATE_CLIENTS)) return false;
        doorGameEvent(level, pos, updated, opening);
        return true;
    }

    private static boolean lockedByAlarm(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof IndustrialMetalDoorFireBlock
            && FireAlarmControls.blocksOpening(level, pos, pos.above());
    }

    private static boolean receivesSignal(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
    }

    private static void applyPower(Level level, BlockPos pos, boolean powered, int ticks) {
        BlockState lower = level.getBlockState(pos);
        if (!(lower.getBlock() instanceof IndustrialMetalDoorBlock door) || lower.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        boolean moved = lower.getValue(OPEN) != powered;
        boolean changesTarget = lower.getValue(POWERED) != powered;
        if (!changesTarget) return;

        // DoorBlock.updateShape copies the lower state to the upper half.
        BlockState updated = lower.setValue(POWERED, powered);
        if (door.style.lifts() || ticks == 0) updated = updated.setValue(OPEN, powered);
        if (level.setBlock(pos, updated, Block.UPDATE_CLIENTS) && moved) {
            if (ticks > 0 && level.getBlockEntity(pos) instanceof IndustrialHingedDoorEntity hinge) hinge.begin(powered, ticks);
            level.playSound(null, pos, IndustrialDoorSounds.event(door.style, true, powered), SoundSource.BLOCKS, .8F, 1F);
            if (updated.getValue(OPEN) == powered) doorGameEvent(level, pos, updated, powered);
        }
    }

    private static void doorGameEvent(Level level, BlockPos pos, BlockState state, boolean opening) {
        level.gameEvent(opening ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos, GameEvent.Context.of(state));
    }
}
