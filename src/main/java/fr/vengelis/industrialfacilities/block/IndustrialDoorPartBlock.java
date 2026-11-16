package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Invisible reserved aperture cells, with collision matching the moving halves. */
public class IndustrialDoorPartBlock extends Block implements EntityBlock {
    public IndustrialDoorPartBlock() { super(Properties.of().strength(-1,IndustrialDoorStyle.NORMAL_BLAST_RESISTANCE).noOcclusion().noLootTable().dynamicShape()); }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new IndustrialDoorEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type) {
        return type == IndustrialDoorContent.TYPE ? (world,pos,state,be) -> IndustrialDoorEntity.tick(world,pos,state,(IndustrialDoorEntity)be) : null;
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return collision(l,p); }
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return collision(l,p); }
    public static IndustrialDoorEntity controller(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IndustrialDoorEntity part) || part.owner == null) return null;
        if (level instanceof Level world && !world.hasChunkAt(part.owner)) return null;
        return level.getBlockEntity(part.owner) instanceof IndustrialDoorEntity door && door.size > 0 ? door : null;
    }
    @Override public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        var door = controller(level, pos);
        if (door == null || collision(door, pos).isEmpty()) return 0;
        return door.style().blastResistance();
    }
    private VoxelShape collision(BlockGetter l,BlockPos p) {
        var door = controller(l, p);
        return door == null ? Shapes.empty() : collision(door,p);
    }
    private static VoxelShape collision(IndustrialDoorEntity door, BlockPos p) {
        int height = door.height() - 2;
        double half=height/2.0, travel=half*door.progress;
        double row=p.getY()-door.doorOrigin().getY()-1;
        var facing = IndustrialDoorBlock.facing(door.getBlockState());
        var style = door.style();
        if (style == IndustrialDoorStyle.IRIS || style == IndustrialDoorStyle.JAW) {
            IndustrialDoorPlane plane = door.plane();
            BlockPos origin = plane.at(door.doorOrigin(), 1, 1);
            int column = plane.column(origin, p);
            int line = plane.row(origin, p);
            return style == IndustrialDoorStyle.IRIS
                ? IndustrialIrisDoorMotion.collision(height, door.progress, facing, column, line)
                : IndustrialJawDoorMotion.layout(door, plane).collision(door.progress, plane, column, line);
        }
        if (door.style() == IndustrialDoorStyle.MESH) {
            var lower = IndustrialMeshDoorMotion.lower(height, door.progress);
            return Shapes.or(meshShape(lower.bounds(1, height, facing), row),
                meshShape(IndustrialMeshDoorMotion.upper(height, door.progress).bounds(1, height, facing), row),
                meshShape(IndustrialMeshDoorMotion.hazardBeam(lower).bounds(1, height, facing), row));
        }
        boolean z = facing.getAxis() == net.minecraft.core.Direction.Axis.Z;
        if (door.lifts()) {
            double lower = Math.max(0, height*door.progress-row);
            if (lower >= 1) return Shapes.empty();
            return z ? Block.box(0,lower*16,6,16,16,10) : Block.box(6,lower*16,0,10,16,16);
        }
        VoxelShape shape=Shapes.empty();
        double bottom=Math.min(1,half-travel-row);
        double top=Math.max(0,half+travel-row);
        if(bottom>0) shape=Shapes.or(shape,z ? Block.box(0,0,6,16,bottom*16,10) : Block.box(6,0,0,10,bottom*16,16));
        if(top<1) shape=Shapes.or(shape,z ? Block.box(0,top*16,6,16,16,10) : Block.box(6,top*16,0,10,16,16));
        return shape;
    }

    private static VoxelShape meshShape(AABB panel, double row) {
        if (panel == null) return Shapes.empty();
        double bottom = Math.max(0, panel.minY - row);
        double top = Math.min(1, panel.maxY - row);
        if (bottom >= top) return Shapes.empty();
        return Shapes.create(new AABB(panel.minX, bottom, panel.minZ, panel.maxX, top, panel.maxZ));
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if (!s.is(next.getBlock()) && l instanceof net.minecraft.server.level.ServerLevel server && l.getBlockEntity(p) instanceof IndustrialDoorEntity be)
            IndustrialDoorManager.breakMember(server,be);
        super.onRemove(s,l,p,next,moving);
    }
    @Override public PushReaction getPistonPushReaction(BlockState s) { return PushReaction.BLOCK; }
}
