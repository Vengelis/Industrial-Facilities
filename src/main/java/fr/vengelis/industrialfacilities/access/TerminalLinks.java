package fr.vengelis.industrialfacilities.access;

import java.util.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.item.NetworkTabletItem;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Shared limits and a server-owned, loaded-only view of the current player's network. */
public final class TerminalLinks {
    public static final int MAX_DISTANCE=64,SOURCE_RADIUS=96,TARGET_RADIUS=128,MAX_LINKS=512,MAX_SOURCES=128,MAX_SCANNED_BLOCK_ENTITIES=32768;
    public record Link(BlockPos sourceRoot,UUID sourceId,String sourceKind,BlockPos targetRoot,UUID targetId,String targetKind) {
        public Link {sourceRoot=sourceRoot.immutable();targetRoot=targetRoot.immutable();}
    }
    public record Snapshot(List<Link> links,boolean truncated) {public Snapshot {links=List.copyOf(links);}}
    public static boolean inRange(BlockPos source,BlockPos target){return source.distSqr(target)<=(double)MAX_DISTANCE*MAX_DISTANCE;}
    /** Nonblocking: a ticket alone is not proof that full block data is already available. */
    public static boolean loaded(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    public static boolean holdingTablet(Player player){return player.isAlive()&&(player.getMainHandItem().getItem() instanceof NetworkTabletItem||player.getOffhandItem().getItem() instanceof NetworkTabletItem);}
    public static boolean admissible(ServerLevel level,BlockPos source,AccessRegistry.DoorLink link){return link.dimension().equals(level.dimension().location().toString())&&inRange(source,link.position());}
    /** A registry entry is a locator, never sufficient authority to operate a missing reader. */
    public static AccessTerminalEntity loadedReader(ServerLevel level,AccessRegistry.Entry entry){
        if(entry==null||!entry.dimension.equals(level.dimension().location().toString())||!loaded(level,entry.position))return null;
        AccessTerminalEntity terminal=AccessTerminalEntity.resolve(level,entry.position);
        return terminal!=null&&!terminal.isRemoved()&&!terminal.encoder&&!terminal.bank&&terminal.getBlockPos().equals(entry.position)
            &&entry.id.equals(terminal.terminalId)&&entry.owner.equals(terminal.creator)?terminal:null;
    }
    public static IndustrialDoorEntity loadedDoor(ServerLevel level,AccessRegistry.DoorLink link){
        if(!link.dimension().equals(level.dimension().location().toString())||!loaded(level,link.position()))return null;
        var door=AccessDoorService.resolveDoor(level,link.position());
        return door!=null&&!door.isRemoved()&&door.getBlockPos().equals(link.position())&&link.instance().equals(door.doorInstanceId)?door:null;
    }
    private static boolean near(Player player,BlockPos pos,int radius){return player.distanceToSqr(pos.getCenter())<=(double)radius*radius;}
    private static boolean automationTarget(ServerLevel level,UUID owner,CameraTerminalEntity.LinkView link){
        if(!loaded(level,link.pos()))return false;var target=level.getBlockEntity(link.pos());if(target==null||target.isRemoved())return false;
        return switch(link.kind()){
            case "camera"->target instanceof SecurityCameraEntity camera&&link.id().equals(camera.instanceId)&&owner.equals(camera.creator);
            case "door"->target instanceof IndustrialDoorEntity door&&door.size>0&&link.id().equals(door.doorInstanceId)&&owner.equals(door.automationOwner)&&AccessDoorService.canConfigureOwner(owner,door);
            case "alarm"->target instanceof AlarmBlockEntity alarm&&link.id().equals(alarm.automationInstanceId)&&owner.equals(alarm.automationOwner);
            case "emitter"->target instanceof SignalEmitterEntity emitter&&link.id().equals(emitter.instanceId)&&owner.equals(emitter.creator);
            default->false;
        };
    }
    public static Snapshot snapshot(ServerPlayer player){
        if(!holdingTablet(player))return new Snapshot(List.of(),false);
        ServerLevel level=player.serverLevel();List<AccessTerminalEntity> candidates=new ArrayList<>();boolean truncated=false;int inspected=0;
        int minX=((int)Math.floor(player.getX()-SOURCE_RADIUS))>>4,maxX=((int)Math.floor(player.getX()+SOURCE_RADIUS))>>4;
        int minZ=((int)Math.floor(player.getZ()-SOURCE_RADIUS))>>4,maxZ=((int)Math.floor(player.getZ()+SOURCE_RADIUS))>>4;
        // Iterate only existing full chunks. No global source cache or chunk-loading request is used.
        scan:for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var entity:chunk.getBlockEntities().values()){
                if(++inspected>MAX_SCANNED_BLOCK_ENTITIES){truncated=true;break scan;}
                if(entity instanceof AccessTerminalEntity terminal&&!terminal.isRemoved()&&terminal.isOwner(player)&&near(player,terminal.getBlockPos(),SOURCE_RADIUS)
                    &&(terminal instanceof CameraTerminalEntity||!terminal.encoder&&!terminal.bank))candidates.add(terminal);
            }
        }
        candidates.sort(Comparator.comparingDouble((AccessTerminalEntity terminal)->player.distanceToSqr(terminal.getBlockPos().getCenter())).thenComparingLong(terminal->terminal.getBlockPos().asLong()));
        List<Link> result=new ArrayList<>();int sources=0;AccessRegistry registry=AccessRegistry.get(level.getServer());
        for(var terminal:candidates){
            if(AccessTerminalEntity.resolve(level,terminal.getBlockPos())!=terminal)continue;
            if(++sources>MAX_SOURCES){truncated=true;break;}
            if(terminal instanceof CameraTerminalEntity automation){
                for(var link:automation.linkViews()){
                    if(!inRange(terminal.getBlockPos(),link.pos())||!near(player,link.pos(),TARGET_RADIUS)||!automationTarget(level,player.getUUID(),link))continue;
                    if(result.size()>=MAX_LINKS){truncated=true;break;}
                    result.add(new Link(terminal.getBlockPos(),terminal.instanceId,"automation",link.pos(),link.id(),link.kind()));
                }
            }else{
                var entry=terminal.terminalId==null?null:registry.find(terminal.terminalId);if(loadedReader(level,entry)!=terminal)continue;int count=0;
                for(var link:entry.doors){
                    if(++count>64){truncated=true;break;}
                    if(!admissible(level,terminal.getBlockPos(),link)||!near(player,link.position(),TARGET_RADIUS))continue;
                    var door=loadedDoor(level,link);if(door==null||!AccessDoorService.canConfigureOwner(player.getUUID(),door))continue;
                    if(result.size()>=MAX_LINKS){truncated=true;break;}
                    result.add(new Link(terminal.getBlockPos(),terminal.instanceId,"reader",door.getBlockPos(),door.doorInstanceId,"door"));
                }
            }
            if(result.size()>=MAX_LINKS){truncated=true;break;}
        }
        return new Snapshot(result,truncated);
    }
    private TerminalLinks(){}
}
