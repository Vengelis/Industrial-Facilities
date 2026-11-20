package fr.vengelis.industrialfacilities.supervision;

import java.util.function.Consumer;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
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
    public record EditRoute(int menu, String sector, int page, GraphRoutes.Key key,
                            List<GraphRoutes.Bend> points) implements CustomPacketPayload {
        static final Type<EditRoute> TYPE = new Type<>(SupervisionContent.id("supervision_route"));
        static final StreamCodec<RegistryFriendlyByteBuf, EditRoute> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeVarInt(data.menu);
            buf.writeUtf(data.sector, 24);
            buf.writeVarInt(data.page);
            buf.writeUUID(data.key.from());
            buf.writeByte(data.key.fromSlot());
            buf.writeUUID(data.key.to());
            buf.writeByte(data.key.toSlot());
            buf.writeInt(data.key.channel());
            buf.writeVarInt(data.points.size());
            for (var point : data.points) {
                buf.writeVarInt(point.x());
                buf.writeVarInt(point.y());
            }
        }, buf -> {
            int menu = buf.readVarInt();
            String sector = buf.readUtf(24);
            int page = buf.readVarInt();
            var key = new GraphRoutes.Key(buf.readUUID(), buf.readByte(), buf.readUUID(), buf.readByte(), buf.readInt());
            int count = buf.readVarInt();
            if (!key.valid() || count < 0 || count > GraphRoutes.MAX_POINTS)
                throw new io.netty.handler.codec.DecoderException("Invalid synoptic route");
            var points = new ArrayList<GraphRoutes.Bend>(count);
            for (int i = 0; i < count; i++) points.add(new GraphRoutes.Bend(buf.readVarInt(), buf.readVarInt()));
            return new EditRoute(menu, sector, page, key, List.copyOf(points));
        });
        @Override public Type<EditRoute> type() { return TYPE; }
    }
    public record EditSocket(int menu, String sector, int page, GraphRoutes.SocketKey key,
                             GraphRoutes.Socket socket) implements CustomPacketPayload {
        static final Type<EditSocket> TYPE = new Type<>(SupervisionContent.id("supervision_socket"));
        static final StreamCodec<RegistryFriendlyByteBuf, EditSocket> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeVarInt(data.menu);
            buf.writeUtf(data.sector, 24);
            buf.writeVarInt(data.page);
            buf.writeUUID(data.key.node());
            buf.writeByte(data.key.slot());
            buf.writeInt(data.key.channel());
            buf.writeByte(data.socket == null ? -1 : data.socket.side().ordinal());
            if (data.socket != null) buf.writeByte(data.socket.offset());
        }, buf -> {
            int menu = buf.readVarInt();
            String sector = buf.readUtf(24);
            int page = buf.readVarInt();
            var key = new GraphRoutes.SocketKey(buf.readUUID(), buf.readByte(), buf.readInt());
            int side = buf.readByte();
            if (!key.valid() || side < -1 || side >= GraphRoutes.Side.values().length)
                throw new io.netty.handler.codec.DecoderException("Invalid synoptic socket");
            var socket = side == -1 ? null : new GraphRoutes.Socket(GraphRoutes.Side.values()[side], buf.readByte());
            return new EditSocket(menu, sector, page, key, socket);
        });
        @Override public Type<EditSocket> type() { return TYPE; }
    }
    private static void writeRouteKey(net.minecraft.network.FriendlyByteBuf buf, GraphRoutes.Key key) {
        buf.writeUUID(key.from()); buf.writeByte(key.fromSlot());
        buf.writeUUID(key.to()); buf.writeByte(key.toSlot()); buf.writeInt(key.channel());
    }
    private static GraphRoutes.Key readRouteKey(net.minecraft.network.FriendlyByteBuf buf) {
        var key = new GraphRoutes.Key(buf.readUUID(), buf.readByte(), buf.readUUID(), buf.readByte(), buf.readInt());
        if (!key.valid()) throw new io.netty.handler.codec.DecoderException("Invalid joined route");
        return key;
    }
    public record JoinRoutes(int menu, String sector, int page, GraphRoutes.Key child,
                              GraphRoutes.Key parent) implements CustomPacketPayload {
        static final Type<JoinRoutes> TYPE = new Type<>(SupervisionContent.id("supervision_join_routes"));
        static final StreamCodec<RegistryFriendlyByteBuf, JoinRoutes> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeVarInt(data.menu); buf.writeUtf(data.sector, 24); buf.writeVarInt(data.page);
            writeRouteKey(buf, data.child);
            buf.writeNullable(data.parent, SupervisionPackets::writeRouteKey);
        }, buf -> new JoinRoutes(buf.readVarInt(), buf.readUtf(24), buf.readVarInt(),
            readRouteKey(buf), buf.readNullable(SupervisionPackets::readRouteKey)));
        @Override public Type<JoinRoutes> type() { return TYPE; }
    }
    public static Consumer<Snapshot> receiver = packet -> {};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("supervision_v7");
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> receiver.accept(packet)));
        registrar.playToServer(Action.TYPE, Action.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                    && player.containerMenu instanceof SupervisionMenu menu && menu.containerId == packet.menu && menu.stillValid(player))
                menu.apply(player, packet);
        }));
        registrar.playToServer(EditRoute.TYPE, EditRoute.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                    && player.containerMenu instanceof SupervisionMenu menu && menu.containerId == packet.menu())
                menu.editRoute(player, packet);
        }));
        registrar.playToServer(EditSocket.TYPE, EditSocket.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                    && player.containerMenu instanceof SupervisionMenu menu && menu.containerId == packet.menu())
                menu.editSocket(player, packet);
        }));
        registrar.playToServer(JoinRoutes.TYPE, JoinRoutes.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                    && player.containerMenu instanceof SupervisionMenu menu && menu.containerId == packet.menu())
                menu.joinRoutes(player, packet);
        }));
    }
    private SupervisionPackets() {}
}
