package fr.vengelis.industrialfacilities.access.ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.KeypassData;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server push only: the client supplies neither an owner nor world positions. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class TabletLinksMessages {
    private static final AtomicLong SEQUENCE=new AtomicLong();
    public record State(ResourceLocation dimension,long sequence,List<TerminalLinks.Link> links,boolean truncated) implements CustomPacketPayload {
        public State {links=List.copyOf(links);}
        public static final Type<State> TYPE=new Type<>(AccessMenus.id("tablet_links"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((buffer,state)->{
            if(state.links.size()>TerminalLinks.MAX_LINKS||state.sequence<0)throw new IllegalArgumentException("Invalid tablet snapshot");
            buffer.writeResourceLocation(state.dimension);buffer.writeVarLong(state.sequence);buffer.writeVarInt(state.links.size());
            for(var link:state.links){
                if(!link.validNames())throw new IllegalArgumentException("Invalid tablet label");
                buffer.writeBlockPos(link.sourceRoot());buffer.writeUUID(link.sourceId());buffer.writeUtf(link.sourceKind(),16);buffer.writeBlockPos(link.targetRoot());buffer.writeUUID(link.targetId());buffer.writeUtf(link.targetKind(),16);
                if(link.sourceKind().equals("reader"))buffer.writeUtf(link.sourceName(),KeypassData.MAX_READER_NAME);
                if(link.targetKind().equals(TerminalLinks.KEYPASS_READER))buffer.writeUtf(link.targetName(),KeypassData.MAX_READER_NAME);
            }
            buffer.writeBoolean(state.truncated);
        },buffer->{
            ResourceLocation dimension=buffer.readResourceLocation();long sequence=buffer.readVarLong();int count=buffer.readVarInt();if(sequence<0||count<0||count>TerminalLinks.MAX_LINKS)throw new IllegalArgumentException("Invalid tablet snapshot size");
            List<TerminalLinks.Link> links=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                var source=buffer.readBlockPos();UUID sourceId=buffer.readUUID();String sourceKind=buffer.readUtf(16);var target=buffer.readBlockPos();UUID targetId=buffer.readUUID();String targetKind=buffer.readUtf(16);
                if(!TerminalLinks.validKinds(sourceKind,targetKind)||!TerminalLinks.inRange(source,target))throw new IllegalArgumentException("Invalid tablet link");
                String sourceName=sourceKind.equals("reader")?buffer.readUtf(KeypassData.MAX_READER_NAME):"";
                String targetName=targetKind.equals(TerminalLinks.KEYPASS_READER)?buffer.readUtf(KeypassData.MAX_READER_NAME):"";
                var link=new TerminalLinks.Link(source,sourceId,sourceKind,target,targetId,targetKind,sourceName,targetName);
                if(!link.validNames())throw new IllegalArgumentException("Invalid tablet label");links.add(link);
            }return new State(dimension,sequence,links,buffer.readBoolean());
        });
        @Override public Type<State> type(){return TYPE;}
    }
    public static java.util.function.Consumer<State> receiver=state->{};
    public static State state(ServerPlayer player,TerminalLinks.Snapshot snapshot){return new State(player.level().dimension().location(),SEQUENCE.incrementAndGet(),snapshot.links(),snapshot.truncated());}
    public static void send(ServerPlayer player,TerminalLinks.Snapshot snapshot){PacketDistributor.sendToPlayer(player,state(player,snapshot));}
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){event.registrar("terminal_tablet_v3").playToClient(State.TYPE,State.CODEC,(state,context)->context.enqueueWork(()->receiver.accept(state)));}
    private TabletLinksMessages(){}
}
