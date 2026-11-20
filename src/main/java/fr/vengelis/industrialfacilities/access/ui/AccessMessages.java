package fr.vengelis.industrialfacilities.access.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.TerminalDevices;
import fr.vengelis.industrialfacilities.supervision.SupervisionData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
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
    public record State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,
                        boolean cameraAutomation,boolean meshDoor,boolean halfOpening,int irisOpeningPercent,String status) implements CustomPacketPayload {
        public State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,String status){
            this(menuId,code,level,links,editable,redstone,secured,false,false,false,-1,status);
        }
        public State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,boolean cameraAutomation,String status){
            this(menuId,code,level,links,editable,redstone,secured,cameraAutomation,false,false,-1,status);
        }
        public State(int menuId,String code,int level,int links,boolean editable,boolean redstone,boolean secured,boolean cameraAutomation,
                     boolean meshDoor,boolean halfOpening,String status){
            this(menuId,code,level,links,editable,redstone,secured,cameraAutomation,meshDoor,halfOpening,-1,status);
        }
        public static final Type<State> TYPE=new Type<>(AccessMenus.id("access_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of(
            (buf,data)->{
                buf.writeVarInt(data.menuId);buf.writeUtf(data.code,64);buf.writeVarInt(data.level);buf.writeVarInt(data.links);
                buf.writeBoolean(data.editable);buf.writeBoolean(data.redstone);buf.writeBoolean(data.secured);buf.writeBoolean(data.cameraAutomation);
                buf.writeBoolean(data.meshDoor);buf.writeBoolean(data.halfOpening);buf.writeByte(data.irisOpeningPercent);buf.writeUtf(data.status,512);
            },
            buf->new State(buf.readVarInt(),buf.readUtf(64),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),
                buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readByte(),buf.readUtf(512)));
        @Override public Type<State> type(){return TYPE;}
    }
    public record UnlinkDevice(int menuId, UUID device, BlockPos position) implements CustomPacketPayload {
        public static final Type<UnlinkDevice> TYPE = new Type<>(AccessMenus.id("unlink_offline_device"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UnlinkDevice> CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.menuId);
                buf.writeUUID(data.device);
                buf.writeBlockPos(data.position);
            },
            buf -> new UnlinkDevice(buf.readVarInt(), buf.readUUID(), buf.readBlockPos()));
        @Override public Type<UnlinkDevice> type() { return TYPE; }
    }

    public record DeviceList(int menuId, List<TerminalDevices.Link> links) implements CustomPacketPayload {
        public DeviceList { links = List.copyOf(links); }
        public static final Type<DeviceList> TYPE = new Type<>(AccessMenus.id("access_device_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeviceList> CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.menuId);
                buf.writeVarInt(data.links.size());
                for (var link : data.links) {
                    buf.writeUUID(link.id());
                    buf.writeBlockPos(link.position());
                    buf.writeEnum(link.kind());
                    buf.writeBoolean(link.available());
                    buf.writeUtf(link.name(), SupervisionData.MAX_NAME);
                }
            },
            buf -> {
                int menu = buf.readVarInt(), count = buf.readVarInt();
                if (count < 0 || count > TerminalDevices.MAX_LINKS)
                    throw new IllegalArgumentException("Invalid access link list size");
                var links = new ArrayList<TerminalDevices.Link>(count);
                for (int i = 0; i < count; i++) {
                    links.add(new TerminalDevices.Link(buf.readUUID(), buf.readBlockPos(),
                        buf.readEnum(TerminalDevices.Kind.class), buf.readBoolean(), buf.readUtf(SupervisionData.MAX_NAME)));
                }
                return new DeviceList(menu, links);
            });
        @Override public Type<DeviceList> type() { return TYPE; }
    }

    public static java.util.function.Consumer<State> receiver=data->{};
    public static java.util.function.Consumer<DeviceList> deviceReceiver = data -> {};
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("access_v6");
        registrar.playToServer(Action.TYPE,Action.CODEC,(data,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof AccessMenu menu&&menu.containerId==data.menuId&&menu.stillValid(context.player()))
                menu.apply(context.player(),data.action,data.code,data.value);
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(data,context)->context.enqueueWork(()->receiver.accept(data)));
        registrar.playToServer(UnlinkDevice.TYPE, UnlinkDevice.CODEC, (data, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.containerMenu.containerId != data.menuId())
                return;
            if (player.containerMenu instanceof AccessMenu menu) menu.unlinkDevice(player, data);
            if (player.containerMenu instanceof KeypassMenu menu) menu.unlinkDevice(player, data);
        }));
        registrar.playToClient(DeviceList.TYPE, DeviceList.CODEC,
            (data, context) -> context.enqueueWork(() -> deviceReceiver.accept(data)));
    }
    private AccessMessages(){}
}
