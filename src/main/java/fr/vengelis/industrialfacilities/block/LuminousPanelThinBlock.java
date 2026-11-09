package fr.vengelis.industrialfacilities.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Half-block housing, with its back flush against the supporting wall. */
public class LuminousPanelThinBlock extends LuminousPanelBlock {
    public LuminousPanelThinBlock() { super(true); }
    @Override public boolean halfDepth() { return true; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch(s.getValue(FACING)) {
            case NORTH -> box(0,0,8,16,16,16);
            case SOUTH -> box(0,0,0,16,16,8);
            case WEST -> box(8,0,0,16,16,16);
            case EAST -> box(0,0,0,8,16,16);
            default -> Shapes.block();
        };
    }
    @Override protected VoxelShape getOcclusionShape(BlockState s, BlockGetter l, BlockPos p) {
        return getShape(s,l,p,CollisionContext.empty());
    }
}
