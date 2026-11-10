package fr.vengelis.industrialfacilities.camera.ui;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded binary NBT is decoded before a server-only, owner-checked intent is applied. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class CameraMessages {
    public static final int MAX_PROGRAM_BYTES=32768,MAX_LINKS=24;
    public record Link(UUID id,String kind,BlockPos position,String name,boolean available) {}
    private static void writeProgram(RegistryFriendlyByteBuf buffer,CompoundTag program){
        try{var bytes=new ByteArrayOutputStream();NbtIo.write(program,new DataOutputStream(bytes));
            if(bytes.size()>MAX_PROGRAM_BYTES)throw new IllegalArgumentException("Camera program too large");buffer.writeByteArray(bytes.toByteArray());
        }catch(IOException e){throw new IllegalArgumentException("Cannot encode camera program",e);}
    }
    private static CompoundTag readProgram(RegistryFriendlyByteBuf buffer){
        byte[] bytes=buffer.readByteArray(MAX_PROGRAM_BYTES);
        try{var input=new DataInputStream(new ByteArrayInputStream(bytes));var tag=NbtIo.read(input,NbtAccounter.create(131072));
            if(tag==null||input.available()!=0)throw new IllegalArgumentException("Invalid camera program");return tag;
        }catch(IOException e){throw new IllegalArgumentException("Cannot decode camera program",e);}
    }
    public record Action(int menuId,int action,int value,int range,float yaw,float pitch,CompoundTag program,int requestId,boolean sweepEnabled,float sweepAngle,int sweepPeriodTicks,boolean stagesEnabled,int alertTicks,int resetTicks,boolean haloEnabled) implements CustomPacketPayload {
        public Action {program=program.copy();}
        public Action(int menuId,int action,int value,int range,float yaw,float pitch,CompoundTag program,int requestId,boolean sweepEnabled,float sweepAngle,int sweepPeriodTicks){this(menuId,action,value,range,yaw,pitch,program,requestId,sweepEnabled,sweepAngle,sweepPeriodTicks,false,60,40,true);}
        public Action(int menuId,int action,int value,int range,float yaw,float pitch,CompoundTag program,int requestId){this(menuId,action,value,range,yaw,pitch,program,requestId,false,30,160);}
        public Action(int menuId,int action,int value,int range,float yaw,float pitch,CompoundTag program){this(menuId,action,value,range,yaw,pitch,program,0,false,30,160);}
        @Override public CompoundTag program(){return program.copy();}
        public static final Type<Action> TYPE=new Type<>(CameraMenus.id("camera_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,d)->{
            b.writeVarInt(d.menuId);b.writeVarInt(d.action);b.writeVarInt(d.value);b.writeVarInt(d.range);b.writeFloat(d.yaw);b.writeFloat(d.pitch);writeProgram(b,d.program);b.writeVarInt(d.requestId);b.writeBoolean(d.sweepEnabled);b.writeFloat(d.sweepAngle);b.writeVarInt(d.sweepPeriodTicks);b.writeBoolean(d.stagesEnabled);b.writeVarInt(d.alertTicks);b.writeVarInt(d.resetTicks);b.writeBoolean(d.haloEnabled);
        },b->new Action(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),readProgram(b),b.readVarInt(),b.readBoolean(),b.readFloat(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record State(int menuId,int revision,boolean editable,boolean running,CompoundTag program,List<Link> links,int range,float yaw,float pitch,String status,String runtimeStatus,int actionSerial,int requestId,boolean sweepEnabled,float sweepAngle,int sweepPeriodTicks,boolean stagesEnabled,int alertTicks,int resetTicks,boolean haloEnabled,int stage,long stageRevision,int occupiedTicks,int quietTicks) implements CustomPacketPayload {
        public State {program=program.copy();links=List.copyOf(links);}
        public State(int menuId,int revision,boolean editable,boolean running,CompoundTag program,List<Link> links,int range,float yaw,float pitch,String status,String runtimeStatus,int actionSerial,int requestId,boolean sweepEnabled,float sweepAngle,int sweepPeriodTicks){this(menuId,revision,editable,running,program,links,range,yaw,pitch,status,runtimeStatus,actionSerial,requestId,sweepEnabled,sweepAngle,sweepPeriodTicks,false,60,40,true,1,0,0,0);}
        public State(int menuId,int revision,boolean editable,boolean running,CompoundTag program,List<Link> links,int range,float yaw,float pitch,String status,String runtimeStatus,int actionSerial,int requestId){this(menuId,revision,editable,running,program,links,range,yaw,pitch,status,runtimeStatus,actionSerial,requestId,false,30,160);}
        public State(int menuId,int revision,boolean editable,boolean running,CompoundTag program,List<Link> links,int range,float yaw,float pitch,String status,String runtimeStatus){this(menuId,revision,editable,running,program,links,range,yaw,pitch,status,runtimeStatus,0,0,false,30,160);}
        @Override public CompoundTag program(){return program.copy();}
        public static final Type<State> TYPE=new Type<>(CameraMenus.id("camera_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,d)->{
            b.writeVarInt(d.menuId);b.writeVarInt(d.revision);b.writeBoolean(d.editable);b.writeBoolean(d.running);writeProgram(b,d.program);
            if(d.links.size()>MAX_LINKS)throw new IllegalArgumentException("Too many camera links");b.writeVarInt(d.links.size());
            for(var link:d.links){b.writeUUID(link.id);b.writeUtf(link.kind,16);b.writeBlockPos(link.position);b.writeUtf(link.name,64);b.writeBoolean(link.available);}
            b.writeVarInt(d.range);b.writeFloat(d.yaw);b.writeFloat(d.pitch);b.writeUtf(d.status,512);b.writeUtf(d.runtimeStatus,512);b.writeVarInt(d.actionSerial);b.writeVarInt(d.requestId);b.writeBoolean(d.sweepEnabled);b.writeFloat(d.sweepAngle);b.writeVarInt(d.sweepPeriodTicks);b.writeBoolean(d.stagesEnabled);b.writeVarInt(d.alertTicks);b.writeVarInt(d.resetTicks);b.writeBoolean(d.haloEnabled);b.writeVarInt(d.stage);b.writeVarLong(d.stageRevision);b.writeVarInt(d.occupiedTicks);b.writeVarInt(d.quietTicks);
        },b->{
            int id=b.readVarInt(),revision=b.readVarInt();boolean editable=b.readBoolean(),running=b.readBoolean();CompoundTag program=readProgram(b);
            int count=b.readVarInt();if(count<0||count>MAX_LINKS)throw new IllegalArgumentException("Invalid camera link count");List<Link> links=new ArrayList<>(count);
            for(int i=0;i<count;i++)links.add(new Link(b.readUUID(),b.readUtf(16),b.readBlockPos(),b.readUtf(64),b.readBoolean()));
            return new State(id,revision,editable,running,program,links,b.readVarInt(),b.readFloat(),b.readFloat(),b.readUtf(512),b.readUtf(512),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readFloat(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarLong(),b.readVarInt(),b.readVarInt());
        });
        @Override public Type<State> type(){return TYPE;}
    }
    public static java.util.function.Consumer<State> receiver=data->{};
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("camera_native_v5");
        registrar.playToServer(Action.TYPE,Action.CODEC,(data,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof CameraMenu menu&&menu.containerId==data.menuId&&menu.stillValid(context.player()))menu.apply(context.player(),data);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(data,context)->context.enqueueWork(()->receiver.accept(data)));
    }
    private CameraMessages(){}
}
