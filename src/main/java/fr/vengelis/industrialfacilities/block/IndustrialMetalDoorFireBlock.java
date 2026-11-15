package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class IndustrialMetalDoorFireBlock extends IndustrialMetalDoorBlock {
    private static final int CHECK_INTERVAL = 5;

    public IndustrialMetalDoorFireBlock() {
        super(IndustrialDoorStyle.FIRE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (!previous.is(this) && level instanceof ServerLevel && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        super.tick(state, level, pos, random);
        updatePower(level, pos);
        if (level.getBlockState(pos).is(this)) level.scheduleTick(pos, this, CHECK_INTERVAL);
    }
}
