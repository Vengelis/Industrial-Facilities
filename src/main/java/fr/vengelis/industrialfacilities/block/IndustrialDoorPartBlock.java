package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;

/** Invisible reserved aperture cells, with collision matching the moving halves. */
public class IndustrialDoorPartBlock extends Block implements EntityBlock {
    public IndustrialDoorPartBlock() { super(Properties.of().strength(-1,3600000).noOcclusion().noLootTable().dynamicShape()); }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new IndustrialDoorEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type) {
        return type == IndustrialDoorContent.TYPE ? (world,pos,state,be) -> IndustrialDoorEntity.tick(world,pos,state,(IndustrialDoorEntity)be) : null;
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return collision(l,p); }
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return collision(l,p); }
    private VoxelShape collision(BlockGetter l,BlockPos p) {
        if (!(l.getBlockEntity(p) instanceof IndustrialDoorEntity part) || part.owner == null) return Shapes.empty();
        if (!(l.getBlockEntity(part.owner) instanceof IndustrialDoorEntity door) || door.size==0) return Shapes.empty();
        double height=door.size-2, half=height/2, travel=half*door.progress;
        double row=p.getY()-door.doorOrigin().getY()-1;
        VoxelShape shape=Shapes.empty();
        double bottom=Math.min(1,half-travel-row);
        double top=Math.max(0,half+travel-row);
        boolean z=door.getBlockState().getValue(IndustrialDoorBlock.FACING).getAxis()==net.minecraft.core.Direction.Axis.Z;
        if(bottom>0) shape=Shapes.or(shape,z ? Block.box(0,0,6,16,bottom*16,10) : Block.box(6,0,0,10,bottom*16,16));
        if(top<1) shape=Shapes.or(shape,z ? Block.box(0,top*16,6,16,16,10) : Block.box(6,top*16,0,10,16,16));
        return shape;
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if (!s.is(next.getBlock()) && l instanceof net.minecraft.server.level.ServerLevel server && l.getBlockEntity(p) instanceof IndustrialDoorEntity be)
            IndustrialDoorManager.breakMember(server,be);
        super.onRemove(s,l,p,next,moving);
    }
    @Override public PushReaction getPistonPushReaction(BlockState s) { return PushReaction.BLOCK; }
}
