package fr.vengelis.industrialfacilities.access.ui;

import java.util.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.KeypassData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class KeypassMessages {
    public static final UUID NONE=new UUID(0,0);
    public record Action(int menuId,int kind,UUID reader,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(AccessMenus.id("keypass_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (b,m)->{b.writeVarInt(m.menuId);b.writeVarInt(m.kind);b.writeUUID(m.reader);b.writeUtf(m.value,limit(m.kind));},
            b->{int menuId=b.readVarInt(),kind=b.readVarInt();return new Action(menuId,kind,b.readUUID(),b.readUtf(limit(kind)));});
        private static int limit(int kind){return kind==KeypassMenu.RENAME?KeypassData.MAX_READER_NAME:KeypassData.PIN_LENGTH;}
        @Override public Type<Action> type(){return TYPE;}
    }
    public record State(int menuId,int revision,int doors,String status,List<KeypassData.Reader> readers) implements CustomPacketPayload {
        public State{readers=List.copyOf(readers);}
        public static final Type<State> TYPE=new Type<>(AccessMenus.id("keypass_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,m)->{
            b.writeVarInt(m.menuId);b.writeVarInt(m.revision);b.writeVarInt(m.doors);b.writeUtf(m.status,64);b.writeVarInt(m.readers.size());
            for(var r:m.readers){b.writeUUID(r.id());b.writeBlockPos(r.position());b.writeBoolean(r.configured());b.writeUtf(r.name(),KeypassData.MAX_READER_NAME);}
        },b->{
            int id=b.readVarInt(),revision=b.readVarInt(),doors=b.readVarInt();String status=b.readUtf(64);int n=b.readVarInt();
            if(n<0||n>KeypassData.MAX_READERS)throw new IllegalArgumentException("Invalid Keypass list size");
            List<KeypassData.Reader> readers=new ArrayList<>(n);for(int i=0;i<n;i++)readers.add(new KeypassData.Reader(b.readUUID(),b.readBlockPos(),b.readBoolean(),b.readUtf(KeypassData.MAX_READER_NAME)));
            return new State(id,revision,doors,status,readers);
        });
        @Override public Type<State> type(){return TYPE;}
    }
    public static java.util.function.Consumer<State> receiver=m->{};
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("keypass_v2");
        registrar.playToServer(Action.TYPE,Action.CODEC,(m,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer player&&player.containerMenu instanceof KeypassMenu menu&&menu.containerId==m.menuId())menu.apply(player,m);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(m,c)->c.enqueueWork(()->receiver.accept(m)));
    }
    private KeypassMessages(){}
}
