package fr.vengelis.industrialfacilities.block;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** Reinforced floor/ceiling screen, facing outward from a half-height slab. */
public class LuminousPanelSurfaceBlock extends LuminousPanelBlock {
    public static final BooleanProperty CEILING=BooleanProperty.create("ceiling");
    public LuminousPanelSurfaceBlock() { super(true); registerDefaultState(defaultBlockState().setValue(CEILING,false)); }
    @Override public boolean halfDepth() { return true; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { super.createBlockStateDefinition(b); b.add(CEILING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        boolean ceiling=c.getClickedFace()==Direction.DOWN || (c.getClickedFace().getAxis().isHorizontal()
            && c.getClickLocation().y-c.getClickedPos().getY()>.5);
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection()).setValue(CEILING,ceiling);
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        return s.getValue(CEILING)?box(0,8,0,16,16,16):box(0,0,0,16,8,16);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p) { return getShape(s,l,p,CollisionContext.empty()); }
}
