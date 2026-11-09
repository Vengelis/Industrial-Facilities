package fr.vengelis.industrialfacilities.access.ui;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** All mutations are checked against the current server menu; client code is isolated. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class AccessMessages {
    public record Action(int menuId,int action,String code,int value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(AccessMenus.id("access_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (buf,data)->{buf.writeVarInt(data.menuId);buf.writeVarInt(data.action);buf.writeUtf(data.code,64);buf.writeVarInt(data.value);},
            buf->new Action(buf.readVarInt(),buf.readVarInt(),buf.readUtf(64),buf.readVarInt()));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,boolean cameraAutomation,String status) implements CustomPacketPayload {
        public State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,String status){this(menuId,code,level,links,editable,redstone,secured,false,status);}
        public static final Type<State> TYPE=new Type<>(AccessMenus.id("access_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of(
            (buf,data)->{buf.writeVarInt(data.menuId);buf.writeUtf(data.code,64);buf.writeVarInt(data.level);buf.writeVarInt(data.links);buf.writeBoolean(data.editable);buf.writeBoolean(data.redstone);buf.writeBoolean(data.secured);buf.writeBoolean(data.cameraAutomation);buf.writeUtf(data.status,512);},
            buf->new State(buf.readVarInt(),buf.readUtf(64),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readUtf(512)));
        @Override public Type<State> type(){return TYPE;}
    }
    public static java.util.function.Consumer<State> receiver=data->{};
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("access_v2");
        registrar.playToServer(Action.TYPE,Action.CODEC,(data,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof AccessMenu menu&&menu.containerId==data.menuId&&menu.stillValid(context.player()))
                menu.apply(context.player(),data.action,data.code,data.value);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(data,context)->context.enqueueWork(()->receiver.accept(data)));
    }
    private AccessMessages(){}
}
