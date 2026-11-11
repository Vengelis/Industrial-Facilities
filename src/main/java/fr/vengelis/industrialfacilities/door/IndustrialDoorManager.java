package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import fr.vengelis.industrialfacilities.access.AccessDoorService;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.*;

public final class IndustrialDoorManager {
    public static BlockPos at(BlockPos origin,Direction facing,int x,int y) {
        return origin.relative(facing.getCounterClockWise(),x).above(y);
    }
    public static boolean edge(int x,int y,int size) { return x==0||y==0||x==size-1||y==size-1; }
    private static boolean valid(ServerLevel level,BlockPos controller,BlockPos origin,Direction facing,int n) {
        if(origin.getY()<level.getMinBuildHeight()||origin.getY()+n>level.getMaxBuildHeight())return false;
        for(int x=0;x<n;x++)for(int y=0;y<n;y++) {
            BlockPos p=at(origin,facing,x,y);if(!level.hasChunkAt(p))return false;
            if(p.equals(controller))continue;
            BlockState s=level.getBlockState(p);
            if(edge(x,y,n)) {
                if(!(s.getBlock() instanceof IndustrialDoorBlock frame)||frame.controller
                    ||frame.style!=((IndustrialDoorBlock)level.getBlockState(controller).getBlock()).style||!(level.getBlockEntity(p) instanceof IndustrialDoorEntity member)||member.owner!=null)return false;
            }else if(!s.isAir())return false;
        }
        return true;
    }
    public static boolean assemble(ServerLevel level,BlockPos controller) {
        if(!(level.getBlockEntity(controller) instanceof IndustrialDoorEntity door)||!(door.getBlockState().getBlock() instanceof IndustrialDoorBlock block)||!block.controller||door.size!=0)return false;
        Direction facing=door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        // The controller may occupy any perimeter cell. Validate a candidate before mutation.
        for(int n=5;n<=15;n++)for(int cx=0;cx<n;cx++)for(int cy=0;cy<n;cy++) {
            if(!edge(cx,cy,n))continue;
            BlockPos origin=at(controller,facing,-cx,-cy);
            if(!valid(level,controller,origin,facing,n))continue;
            door.origin=origin.immutable();door.size=n;door.owner=controller.immutable();
            door.doorInstanceId=java.util.UUID.randomUUID();door.allowLinkedRedstone=false;door.accessOpenUntil=0;
            door.allowCameraAutomation=false;door.automationOwner=null;
            AccessDoorService.registerDoor(level,door);
            door.progress=0;door.previousProgress=0;door.targetOpen=false;door.paused=false;
            for(int x=0;x<n;x++)for(int y=0;y<n;y++) {
                BlockPos p=at(origin,facing,x,y);
                if(edge(x,y,n))level.setBlock(p,level.getBlockState(p).setValue(IndustrialDoorBlock.FACING,facing).setValue(IndustrialDoorBlock.ACTIVE,true).setValue(IndustrialDoorBlock.OPEN,false)
                    .setValue(IndustrialDoorBlock.RAIL,(x==0||x==n-1)&&IndustrialDoorVisualState.hasRail(n,y)).setValue(IndustrialDoorBlock.VERTICAL,x==0||x==n-1),3);
                else level.setBlock(p,IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_PART.get().defaultBlockState(),3);
                if(level.getBlockEntity(p) instanceof IndustrialDoorEntity member){member.owner=controller.immutable();member.sync();}
            }
            door.sync();return true;
        }
        return false;
    }
    public static boolean allLoaded(ServerLevel level,IndustrialDoorEntity door) {
        Direction f=door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        for(int x=0;x<door.size;x++)for(int y=0;y<door.size;y++)if(!level.hasChunkAt(at(door.doorOrigin(),f,x,y)))return false;
        return true;
    }
    public static void tick(ServerLevel level,IndustrialDoorEntity door) {
        if(door.size==0||door.tearingDown)return;
        if(!allLoaded(level,door)){if(!door.paused){door.paused=true;door.sync();}return;}
        Direction f=door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        if(level.getGameTime()%10==0) {
            for(int x=0;x<door.size;x++)for(int y=0;y<door.size;y++) {
                BlockPos p=at(door.doorOrigin(),f,x,y);
                if(!(level.getBlockEntity(p) instanceof IndustrialDoorEntity member)||!door.getBlockPos().equals(member.owner)){dismantle(level,door);return;}
            }
            updateAppearance(level,door);
        }
        boolean powered=fr.vengelis.industrialfacilities.access.AccessDoorService.shouldOpen(level,door);
        if(powered!=door.targetOpen){
            door.targetOpen=powered;door.sync();
        }
        float next=IndustrialDoorEntity.advance(door.progress,door.targetOpen);
        boolean blocked=next<door.progress&&obstructed(level,door,next);
        if(door.paused!=blocked){door.paused=blocked;door.sync();}
        if(blocked)return;
        if(next!=door.progress){door.progress=next;door.sync();}
        if(door.getBlockState().getValue(IndustrialDoorBlock.OPEN)!=(door.progress>=1))updateAppearance(level,door);
    }
    public static void updateAppearance(ServerLevel level,IndustrialDoorEntity door) {
        int n=door.size;if(n==0)return;
        Direction f=door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        for(int x=0;x<n;x++)for(int y=0;y<n;y++)if(edge(x,y,n)) {
            BlockPos p=at(door.doorOrigin(),f,x,y);BlockState s=level.getBlockState(p);
            if(s.getBlock() instanceof IndustrialDoorBlock){
                BlockState next=s.setValue(IndustrialDoorBlock.RAIL,(x==0||x==n-1)&&IndustrialDoorVisualState.hasRail(n,y)).setValue(IndustrialDoorBlock.OPEN,door.progress>=1);
                if(next!=s)level.setBlock(p,next,3);
            }
        }
    }
    private static boolean obstructed(ServerLevel level,IndustrialDoorEntity door,float next) {
        int h=door.size-2;double half=h/2d,gap=half*next;
        Direction f=door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        BlockPos a=at(door.doorOrigin(),f,1,1),b=at(door.doorOrigin(),f,h,h);
        double minX=Math.min(a.getX(),b.getX()),maxX=Math.max(a.getX(),b.getX())+1;
        double minZ=Math.min(a.getZ(),b.getZ()),maxZ=Math.max(a.getZ(),b.getZ())+1;
        if(f.getAxis()==Direction.Axis.Z){minZ+=.375;maxZ-=.375;}else{minX+=.375;maxX-=.375;}
        double y=a.getY();
        AABB lower=new AABB(minX,y,minZ,maxX,y+half-gap,maxZ),upper=new AABB(minX,y+half+gap,minZ,maxX,y+h,maxZ);
        return !level.getEntities((net.minecraft.world.entity.Entity)null,lower,e->e.isAlive()&&!e.isSpectator()).isEmpty()||!level.getEntities((net.minecraft.world.entity.Entity)null,upper,e->e.isAlive()&&!e.isSpectator()).isEmpty();
    }
    public static void breakMember(ServerLevel level,IndustrialDoorEntity member) {
        if(member.tearingDown)return;
        IndustrialDoorEntity door=member.size>0?member:member.owner!=null&&level.getBlockEntity(member.owner) instanceof IndustrialDoorEntity d?d:null;
        if(door!=null)dismantle(level,door);
    }
    public static void dismantle(ServerLevel level,IndustrialDoorEntity door) {
        if(door.tearingDown||door.size==0)return;
        fr.vengelis.industrialfacilities.access.AccessDoorService.dismantle(level,door);
        door.tearingDown=true;int n=door.size;
        Direction f=door.getBlockState().getValue(IndustrialDoorBlock.FACING);BlockPos origin=door.doorOrigin();
        for(int x=0;x<n;x++)for(int y=0;y<n;y++) {
            BlockPos p=at(origin,f,x,y);if(!level.hasChunkAt(p))continue;
            if(level.getBlockEntity(p) instanceof IndustrialDoorEntity member&&door.getBlockPos().equals(member.owner)) {
                member.tearingDown=true;member.owner=null;BlockState state=level.getBlockState(p);
                if(state.is(IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_PART.get()))level.removeBlock(p,false);
                else if(state.getBlock() instanceof IndustrialDoorBlock){
                    level.setBlock(p,state.setValue(IndustrialDoorBlock.ACTIVE,false).setValue(IndustrialDoorBlock.OPEN,false).setValue(IndustrialDoorBlock.RAIL,false),3);
                    member.size=0;member.origin=null;member.progress=0;member.targetOpen=false;member.paused=false;member.tearingDown=false;member.sync();
                }
            }
        }
        door.size=0;door.origin=null;door.owner=null;door.progress=0;door.targetOpen=false;door.paused=false;door.tearingDown=false;door.sync();
    }
    private IndustrialDoorManager() {}
}
