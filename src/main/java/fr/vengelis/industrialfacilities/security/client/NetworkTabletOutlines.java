package fr.vengelis.industrialfacilities.security.client;

import java.util.*;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.*;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** Loaded endpoint envelopes only; the server snapshot remains the authority for each link. */
public final class NetworkTabletOutlines {
    public record Outline(BlockPos root,UUID instance,String kind,AABB bounds,int color){public Outline{root=root.immutable();}}
    private record Key(BlockPos root,UUID instance,String kind){}

    public static AABB terminalBounds(BlockPos root,Direction facing){
        // Enclose the entire 1.8-block model, including its base and projecting keyboard.
        double low=1d/16,high=15d/16,back=2d/16,front=15.2/16;
        AABB box=switch(facing){
            case SOUTH->new AABB(low,0,back,high,1.8,front);
            case EAST->new AABB(back,0,1-high,front,1.8,1-low);
            case NORTH->new AABB(1-high,0,1-front,1-low,1.8,1-back);
            case WEST->new AABB(1-front,0,low,1-back,1.8,high);
            default->throw new IllegalArgumentException("Terminal facing must be horizontal");
        };
        return box.move(root.getX(),root.getY(),root.getZ());
    }
    public static AABB doorBounds(BlockPos origin,Direction facing,int size){
        if(!facing.getAxis().isHorizontal()||size<5||size>15)throw new IllegalArgumentException("Invalid assembled door");
        BlockPos far=IndustrialDoorManager.at(origin,facing,size-1,size-1);
        return new AABB(origin).minmax(new AABB(far));
    }
    private static Outline source(ClientLevel level,TerminalLinks.Link link){
        if(!level.hasChunkAt(link.sourceRoot()))return null;
        var terminal=AccessTerminalEntity.resolve(level,link.sourceRoot());
        if(terminal==null||terminal.isRemoved()||!terminal.getBlockPos().equals(link.sourceRoot())||!link.sourceId().equals(terminal.instanceId))return null;
        boolean automation=terminal instanceof CameraTerminalEntity;
        boolean matches=automation?link.sourceKind().equals("automation"):link.sourceKind().equals("reader")&&!terminal.encoder&&!terminal.bank;
        if(!matches)return null;
        var facing=terminal.getBlockState().getValue(AccessTerminalBlock.FACING);
        return new Outline(link.sourceRoot(),link.sourceId(),link.sourceKind(),terminalBounds(link.sourceRoot(),facing),automation?0xBE94F0:0x62DDE7);
    }
    private static Outline target(ClientLevel level,TerminalLinks.Link link){
        BlockPos pos=link.targetRoot();if(!level.hasChunkAt(pos))return null;
        var entity=level.getBlockEntity(pos);if(entity==null||entity.isRemoved())return null;
        AABB box;
        if(link.targetKind().equals("door")){
            if(!(entity instanceof IndustrialDoorEntity door)||door.size<5||door.size>15||!link.targetId().equals(door.doorInstanceId))return null;
            box=doorBounds(door.doorOrigin(),door.getBlockState().getValue(IndustrialDoorBlock.FACING),door.size);
        }else{
            boolean matches=switch(link.targetKind()){
                case "camera"->entity instanceof SecurityCameraEntity camera&&link.targetId().equals(camera.instanceId);
                // These two BE types do not synchronize their instance UUID to clients.
                // Their loaded type and the short-lived, server-authorized snapshot identify the endpoint.
                case "alarm"->entity instanceof AlarmBlockEntity;
                case "emitter"->entity instanceof SignalEmitterEntity;
                default->false;
            };
            if(!matches)return null;
            var shape=level.getBlockState(pos).getShape(level,pos);if(shape.isEmpty())return null;
            box=shape.bounds().move(pos.getX(),pos.getY(),pos.getZ());
        }
        return new Outline(pos,link.targetId(),link.targetKind(),box,NetworkTabletClient.colorFor("automation",link.targetKind()));
    }
    public static List<Outline> collect(ClientLevel level,List<TerminalLinks.Link> links){
        if(level==null||links.isEmpty())return List.of();
        Map<Key,Outline> outlines=new LinkedHashMap<>();int count=0;
        for(var link:links){
            if(++count>TerminalLinks.MAX_LINKS)break;
            Outline source=source(level,link);if(source==null)continue;
            outlines.putIfAbsent(new Key(source.root(),source.instance(),source.kind()),source);
            Outline target=target(level,link);
            if(target!=null)outlines.putIfAbsent(new Key(target.root(),target.instance(),target.kind()),target);
        }
        return List.copyOf(outlines.values());
    }
    private NetworkTabletOutlines(){}
}
