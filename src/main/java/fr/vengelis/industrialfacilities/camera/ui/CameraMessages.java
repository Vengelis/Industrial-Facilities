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

/** Paquets du menu caméra. La taille du NBT est contrôlée avant validation du programme côté serveur. */
@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class CameraMessages {
    public static final int MAX_PROGRAM_BYTES = 32768, MAX_LINKS = 24;
    public record Link(UUID id, String kind, BlockPos position, String name, boolean available) {}
    public static boolean programFits(CompoundTag program) {
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(program, new DataOutputStream(bytes));
            return bytes.size() <= MAX_PROGRAM_BYTES;
        } catch (IOException e) {
            return false;
        }
    }
    private static void writeProgram(RegistryFriendlyByteBuf buffer, CompoundTag program) {
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(program, new DataOutputStream(bytes));
            if (bytes.size() > MAX_PROGRAM_BYTES)
                throw new IllegalArgumentException("Camera program too large");
            buffer.writeByteArray(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode camera program", e);
        }
    }
    private static CompoundTag readProgram(RegistryFriendlyByteBuf buffer) {
        byte[] bytes = buffer.readByteArray(MAX_PROGRAM_BYTES);
        try {
            var input = new DataInputStream(new ByteArrayInputStream(bytes));
            var tag = NbtIo.read(input, NbtAccounter.create(131072));
            if (tag == null || input.available() != 0)
                throw new IllegalArgumentException("Invalid camera program");
            return tag;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot decode camera program", e);
        }
    }
    /** Le client envoie une intention pour le menu toujours ouvert, identifié par menuId. */
    public record Action(int menuId, int action, int value, int range, float yaw, float pitch, CompoundTag program,
            int requestId, boolean sweepEnabled, float sweepAngle, int sweepPeriodTicks, boolean stagesEnabled,
            int alertTicks, int resetTicks, boolean haloEnabled) implements CustomPacketPayload {
        public Action {
            program = program.copy();
        }
        public Action(int menuId, int action, int value, int range, float yaw, float pitch, CompoundTag program,
                int requestId, boolean sweepEnabled, float sweepAngle, int sweepPeriodTicks) {
            this(menuId, action, value, range, yaw, pitch, program, requestId, sweepEnabled, sweepAngle,
                    sweepPeriodTicks, false, 60, 40, true);
        }
        public Action(int menuId, int action, int value, int range, float yaw, float pitch, CompoundTag program,
                int requestId) {
            this(menuId, action, value, range, yaw, pitch, program, requestId, false, 30, 160);
        }
        public Action(int menuId, int action, int value, int range, float yaw, float pitch, CompoundTag program) {
            this(menuId, action, value, range, yaw, pitch, program, 0, false, 30, 160);
        }
        @Override
        public CompoundTag program() {
            return program.copy();
        }
        public static final Type<Action> TYPE = new Type<>(CameraMenus.id("camera_action"));
        // L’ordre des champs est commun à l’écriture et à la lecture du paquet.
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buffer, action) -> {
                    buffer.writeVarInt(action.menuId);
                    buffer.writeVarInt(action.action);
                    buffer.writeVarInt(action.value);
                    buffer.writeVarInt(action.range);
                    buffer.writeFloat(action.yaw);
                    buffer.writeFloat(action.pitch);
                    writeProgram(buffer, action.program);
                    buffer.writeVarInt(action.requestId);
                    buffer.writeBoolean(action.sweepEnabled);
                    buffer.writeFloat(action.sweepAngle);
                    buffer.writeVarInt(action.sweepPeriodTicks);
                    buffer.writeBoolean(action.stagesEnabled);
                    buffer.writeVarInt(action.alertTicks);
                    buffer.writeVarInt(action.resetTicks);
                    buffer.writeBoolean(action.haloEnabled);
                },
                buffer -> new Action(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(),
                        buffer.readFloat(), readProgram(buffer), buffer.readVarInt(), buffer.readBoolean(), buffer.readFloat(), buffer.readVarInt(),
                        buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
    /** Le serveur renvoie l’état du menu et l’identifiant de la dernière demande traitée. */
    public record State(int menuId, int revision, boolean editable, boolean running, CompoundTag program,
            List<Link> links, int range, float yaw, float pitch, String status, String runtimeStatus, int actionSerial,
            int requestId, boolean sweepEnabled, float sweepAngle, int sweepPeriodTicks, boolean stagesEnabled,
            int alertTicks, int resetTicks, boolean haloEnabled, int stage, long stageRevision, int occupiedTicks,
            int quietTicks) implements CustomPacketPayload {
        public State {
            program = program.copy();
            links = List.copyOf(links);
        }
        public State(int menuId, int revision, boolean editable, boolean running, CompoundTag program, List<Link> links,
                int range, float yaw, float pitch, String status, String runtimeStatus, int actionSerial, int requestId,
                boolean sweepEnabled, float sweepAngle, int sweepPeriodTicks) {
            this(menuId, revision, editable, running, program, links, range, yaw, pitch, status, runtimeStatus,
                    actionSerial, requestId, sweepEnabled, sweepAngle, sweepPeriodTicks, false, 60, 40, true, 1, 0, 0,
                    0);
        }
        public State(int menuId, int revision, boolean editable, boolean running, CompoundTag program, List<Link> links,
                int range, float yaw, float pitch, String status, String runtimeStatus, int actionSerial,
                int requestId) {
            this(menuId, revision, editable, running, program, links, range, yaw, pitch, status, runtimeStatus,
                    actionSerial, requestId, false, 30, 160);
        }
        public State(int menuId, int revision, boolean editable, boolean running, CompoundTag program, List<Link> links,
                int range, float yaw, float pitch, String status, String runtimeStatus) {
            this(menuId, revision, editable, running, program, links, range, yaw, pitch, status, runtimeStatus, 0, 0,
                    false, 30, 160);
        }
        @Override
        public CompoundTag program() {
            return program.copy();
        }
        public static final Type<State> TYPE = new Type<>(CameraMenus.id("camera_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buffer, state) -> {
                    buffer.writeVarInt(state.menuId);
                    buffer.writeVarInt(state.revision);
                    buffer.writeBoolean(state.editable);
                    buffer.writeBoolean(state.running);
                    writeProgram(buffer, state.program);
                    if (state.links.size() > MAX_LINKS)
                        throw new IllegalArgumentException("Too many camera links");
                    buffer.writeVarInt(state.links.size());
                    for (var link : state.links) {
                        buffer.writeUUID(link.id);
                        buffer.writeUtf(link.kind, 16);
                        buffer.writeBlockPos(link.position);
                        buffer.writeUtf(link.name, 64);
                        buffer.writeBoolean(link.available);
                    }
                    buffer.writeVarInt(state.range);
                    buffer.writeFloat(state.yaw);
                    buffer.writeFloat(state.pitch);
                    buffer.writeUtf(state.status, 512);
                    buffer.writeUtf(state.runtimeStatus, 512);
                    buffer.writeVarInt(state.actionSerial);
                    buffer.writeVarInt(state.requestId);
                    buffer.writeBoolean(state.sweepEnabled);
                    buffer.writeFloat(state.sweepAngle);
                    buffer.writeVarInt(state.sweepPeriodTicks);
                    buffer.writeBoolean(state.stagesEnabled);
                    buffer.writeVarInt(state.alertTicks);
                    buffer.writeVarInt(state.resetTicks);
                    buffer.writeBoolean(state.haloEnabled);
                    buffer.writeVarInt(state.stage);
                    buffer.writeVarLong(state.stageRevision);
                    buffer.writeVarInt(state.occupiedTicks);
                    buffer.writeVarInt(state.quietTicks);
                },
                buffer -> {
                    int id = buffer.readVarInt(), revision = buffer.readVarInt();
                    boolean editable = buffer.readBoolean(), running = buffer.readBoolean();
                    CompoundTag program = readProgram(buffer);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_LINKS)
                        throw new IllegalArgumentException("Invalid camera link count");
                    List<Link> links = new ArrayList<>(count);
                    for (int i = 0; i < count; i++)
                        links.add(new Link(
                                buffer.readUUID(), buffer.readUtf(16), buffer.readBlockPos(), buffer.readUtf(64), buffer.readBoolean()));
                    return new State(id, revision, editable, running, program, links, buffer.readVarInt(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readUtf(512), buffer.readUtf(512), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readBoolean(), buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(),
                            buffer.readVarInt());
                });
        @Override
        public Type<State> type() {
            return TYPE;
        }
    }
    public static java.util.function.Consumer<State> receiver = data -> {};
    @SubscribeEvent
    public static void network(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("camera_native_v5");
        registrar.playToServer(Action.TYPE, Action.CODEC, (data, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CameraMenu menu && menu.containerId == data.menuId
                    && menu.stillValid(context.player()))
                menu.apply(context.player(), data);
        }));
        registrar.playToClient(
                State.TYPE, State.CODEC, (data, context) -> context.enqueueWork(() -> receiver.accept(data)));
    }
    private CameraMessages() {}
}
