package fr.vengelis.industrialfacilities.access.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The client chooses an archived ID; all badge data and transfers stay on the server. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class BadgeBankMessages {
    public static final int PAGE_SIZE=6;
    public record Action(int menuId,int action,String selectedId,int value,String text) implements CustomPacketPayload {
        public Action(int menuId,int action,String selectedId,int value){this(menuId,action,selectedId,value,"");}
        public static final Type<Action> TYPE=new Type<>(AccessMenus.id("badge_bank_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (buf,data)->{buf.writeVarInt(data.menuId);buf.writeVarInt(data.action);buf.writeUtf(data.selectedId,36);buf.writeVarInt(data.value);buf.writeUtf(data.text,64);},
            buf->new Action(buf.readVarInt(),buf.readVarInt(),buf.readUtf(36),buf.readVarInt(),buf.readUtf(64)));
        @Override public Type<Action> type(){return TYPE;}
    }
    public record Entry(UUID id,String name,int masterLevel,int codeCount,int remaining,boolean stored,UUID issuer,boolean foreign,boolean copyAllowed) {
        public Entry(UUID id,String name,int masterLevel,int codeCount,int remaining,boolean stored){this(id,name,masterLevel,codeCount,remaining,stored,new UUID(0,0),false,true);}
    }
    public record State(int menuId,int page,int pages,int total,UUID selectedId,List<Entry> entries,List<String> codes,boolean canDeposit,String status,String searchQuery,int matched) implements CustomPacketPayload {
        public State {entries=List.copyOf(entries);codes=List.copyOf(codes);}
        public State(int menuId,int page,int pages,int total,UUID selectedId,List<Entry> entries,List<String> codes,boolean canDeposit,String status){this(menuId,page,pages,total,selectedId,entries,codes,canDeposit,status,"",total);}
        public State(int menuId,int page,int pages,int total,UUID selectedId,List<Entry> entries,List<String> codes,String status){this(menuId,page,pages,total,selectedId,entries,codes,false,status,"",total);}
        public static final Type<State> TYPE=new Type<>(AccessMenus.id("badge_bank_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((buf,data)->{
            buf.writeVarInt(data.menuId);buf.writeVarInt(data.page);buf.writeVarInt(data.pages);buf.writeVarInt(data.total);
            buf.writeBoolean(data.selectedId!=null);if(data.selectedId!=null)buf.writeUUID(data.selectedId);
            if(data.entries.size()>PAGE_SIZE||data.codes.size()>64)throw new IllegalArgumentException("Oversized bank snapshot");
            buf.writeVarInt(data.entries.size());
            for(Entry entry:data.entries){buf.writeUUID(entry.id);buf.writeUtf(entry.name,64);buf.writeVarInt(entry.masterLevel);buf.writeVarInt(entry.codeCount);buf.writeVarInt(entry.remaining);buf.writeBoolean(entry.stored);buf.writeUUID(entry.issuer);buf.writeBoolean(entry.foreign);buf.writeBoolean(entry.copyAllowed);}
            buf.writeVarInt(data.codes.size());for(String code:data.codes)buf.writeUtf(code,36);
            buf.writeBoolean(data.canDeposit);buf.writeUtf(data.status,512);buf.writeUtf(data.searchQuery,64);buf.writeVarInt(data.matched);
        },buf->{
            int id=buf.readVarInt(),page=buf.readVarInt(),pages=buf.readVarInt(),total=buf.readVarInt();
            UUID selected=buf.readBoolean()?buf.readUUID():null;
            int count=buf.readVarInt();if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("Invalid bank page size");
            List<Entry> entries=new ArrayList<>(count);
            for(int i=0;i<count;i++)entries.add(new Entry(buf.readUUID(),buf.readUtf(64),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readUUID(),buf.readBoolean(),buf.readBoolean()));
            int codeCount=buf.readVarInt();if(codeCount<0||codeCount>64)throw new IllegalArgumentException("Invalid bank code count");
            List<String> codes=new ArrayList<>(codeCount);for(int i=0;i<codeCount;i++)codes.add(buf.readUtf(36));
            return new State(id,page,pages,total,selected,entries,codes,buf.readBoolean(),buf.readUtf(512),buf.readUtf(64),buf.readVarInt());
        });
        @Override public Type<State> type(){return TYPE;}
    }
    public static java.util.function.Consumer<State> receiver=data->{};
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("badge_bank_v3");
        registrar.playToServer(Action.TYPE,Action.CODEC,(data,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof BadgeBankMenu menu&&menu.containerId==data.menuId&&menu.stillValid(context.player()))
                menu.apply(context.player(),data.action,data.selectedId,data.value,data.text);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(data,context)->context.enqueueWork(()->receiver.accept(data)));
    }
    private BadgeBankMessages(){}
}
