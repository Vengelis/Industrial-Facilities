package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.IndustrialDoorBlock;
import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public abstract class IndustrialDoorFaceMountedBlock extends IndustrialDoorBlock {
    protected IndustrialDoorFaceMountedBlock(boolean controller, IndustrialDoorStyle style) {
        super(controller, style);
    }

    @Override protected DirectionProperty facingProperty() {
        return BlockStateProperties.FACING;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(facingProperty(), context.getClickedFace());
    }
}
