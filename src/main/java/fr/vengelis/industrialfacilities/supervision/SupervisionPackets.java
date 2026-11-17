package fr.vengelis.industrialfacilities.supervision;

import java.util.function.Consumer;
import java.util.UUID;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "industrial_facilities")
public final class SupervisionPackets {
    public record Snapshot(int menu, String sector, List<String> sectors, String renamedFrom, CompoundTag view) implements CustomPacketPayload {
        static final Type<Snapshot> TYPE = new Type<>(SupervisionContent.id("supervision_snapshot"));
        static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.menu); buf.writeUtf(data.sector, 24);
                buf.writeCollection(data.sectors, (out, name) -> out.writeUtf(name, 24));
                buf.writeUtf(data.renamedFrom, 24);
                buf.writeNbt(data.view);
            },
            buf -> new Snapshot(buf.readVarInt(), buf.readUtf(24), buf.readList(in -> in.readUtf(24)), buf.readUtf(24), buf.readNbt()));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    public record Action(int menu, int kind, String name, String sector, UUID node, int page, int x, int y) implements CustomPacketPayload {
        public Action(int menu, int kind, String name) { this(menu, kind, name, "", null, 0, 0, 0); }
        static final Type<Action> TYPE = new Type<>(SupervisionContent.id("supervision_action"));
        static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.menu); buf.writeByte(data.kind); buf.writeUtf(data.name, SupervisionData.MAX_NAME);
                buf.writeUtf(data.sector, 24);
                buf.writeNullable(data.node, (b, id) -> b.writeUUID(id));
                buf.writeVarInt(data.page); buf.writeVarInt(data.x); buf.writeVarInt(data.y);
            },
            buf -> new Action(buf.readVarInt(), buf.readByte(), buf.readUtf(SupervisionData.MAX_NAME), buf.readUtf(24),
                buf.readNullable(b -> b.readUUID()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<Action> type() { return TYPE; }
    }
    public static Consumer<Snapshot> receiver = packet -> {};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("supervision_v4");
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> receiver.accept(packet)));
        registrar.playToServer(Action.TYPE, Action.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                    && player.containerMenu instanceof SupervisionMenu menu && menu.containerId == packet.menu && menu.stillValid(player))
                menu.apply(player, packet);
        }));
    }
    private SupervisionPackets() {}
}
