package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

abstract class IndustrialJawDoorBlock extends IndustrialDoorFaceMountedBlock {
    protected IndustrialJawDoorBlock(boolean controller) {
        super(controller, IndustrialDoorStyle.JAW);
        registerDefaultState(defaultBlockState().setValue(FRAME_ROTATED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FRAME_ROTATED);
    }
}
