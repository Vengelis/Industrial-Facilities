package fr.vengelis.industrialfacilities.camera.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Relie l’écran client à la caméra ou au terminal qui a ouvert le menu côté serveur.
 * Le serveur vérifie la position, l’identité du bloc et le propriétaire à chaque demande.
 */
public class CameraMenu extends AbstractContainerMenu {
    public static final int PROGRAM = 0, CAMERA = 1, SAVE = 0, TEST = 1, RUNNING = 2, CONFIGURE = 3;
    public final Inventory inventory;
    public final BlockPos position;
    public final int mode;
    public CompoundTag program = emptyProgram();
    public List<CameraMessages.Link> links = List.of();
    public boolean editable, running, sweepEnabled = true, stagesEnabled, haloEnabled = true;
    public int alertTicks = 60, resetTicks = 40, stage = 1, occupiedTicks, quietTicks;
    public long stageRevision;
    public int revision, responseVersion, actionSerial, lastRequestId, range = 16, sweepPeriodTicks = 160;
    public float yaw, pitch, sweepAngle = 30;
    public String status = "", runtimeStatus = "";
    // Ces deux références sont nulles côté client. Seul le serveur possède le bloc de référence.
    private final CameraTerminalEntity terminal;
    private final SecurityCameraEntity camera;
    private final UUID instanceId;
    private CompoundTag observed;
    private CameraMessages.State lastState;
    public static CompoundTag emptyProgram() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.put("Events", new ListTag());
        return tag;
    }
    public CameraMenu(int id, Inventory inventory, CameraTerminalEntity terminal) {
        super(CameraMenus.TYPE, id);
        this.inventory = inventory;
        this.terminal = terminal;
        camera = null;
        mode = PROGRAM;
        position = terminal.getBlockPos();
        instanceId = terminal.instanceId;
        refresh();
    }
    public CameraMenu(int id, Inventory inventory, SecurityCameraEntity camera) {
        super(CameraMenus.TYPE, id);
        this.inventory = inventory;
        this.camera = camera;
        terminal = null;
        mode = CAMERA;
        position = camera.getBlockPos();
        instanceId = camera.instanceId;
        refresh();
    }
    public CameraMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(CameraMenus.TYPE, id);
        this.inventory = inventory;
        terminal = null;
        camera = null;
        instanceId = null;
        mode = buffer.readVarInt();
        if (mode != PROGRAM && mode != CAMERA)
            throw new IllegalArgumentException("Invalid camera menu mode");
        position = buffer.readBlockPos();
    }
    public static void writeOpening(FriendlyByteBuf buffer, int mode, BlockPos position) {
        buffer.writeVarInt(mode);
        buffer.writeBlockPos(position);
    }
    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide())
            return true;
        if (player != inventory.player || !player.isAlive() || !player.level().hasChunkAt(position)
                || player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64)
            return false;
        if (mode == PROGRAM)
            return terminal != null && !terminal.isRemoved() && terminal.getLevel() == player.level()
                    && terminal.isOwner(player) && Objects.equals(instanceId, terminal.instanceId)
                    && AccessTerminalEntity.resolve(player.level(), position) == terminal;
        return camera != null && !camera.isRemoved() && camera.getLevel() == player.level()
                && Objects.equals(camera.creator, player.getUUID()) && Objects.equals(instanceId, camera.instanceId)
                && player.level().getBlockEntity(position) == camera;
    }
    private void refresh() {
        if (terminal != null) {
            editable = terminal.isOwner(inventory.player);
            running = terminal.isRunning();
            runtimeStatus = terminal.status();
            program = terminal.program();
            if (!program.equals(observed)) {
                observed = program.copy();
                revision++;
            }
            links = terminal.linkViews()
                            .stream()
                            .limit(CameraMessages.MAX_LINKS)
                            .map(link
                                    -> new CameraMessages.Link(link.id(), link.kind(), link.pos(),
                                            link.name().length() > 64 ? link.name().substring(0, 64) : link.name(),
                                            link.available()))
                            .toList();
        } else if (camera != null) {
            editable = Objects.equals(camera.creator, inventory.player.getUUID());
            range = camera.range;
            yaw = camera.yaw;
            pitch = camera.pitch;
            sweepEnabled = camera.sweepEnabled;
            sweepAngle = camera.sweepAngle;
            sweepPeriodTicks = camera.sweepPeriodTicks;
            stagesEnabled = camera.stagesEnabled;
            alertTicks = camera.alertTicks;
            resetTicks = camera.resetTicks;
            haloEnabled = camera.haloEnabled;
            stage = camera.stage;
            stageRevision = camera.stageRevision;
            occupiedTicks = camera.occupiedTicks() / 5 * 5;
            quietTicks = camera.quietTicks() / 5 * 5;
        }
    }
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(inventory.player instanceof ServerPlayer server))
            return;
        refresh();
        var state = new CameraMessages.State(containerId, revision, editable, running, program, links, range, yaw,
                pitch, status, runtimeStatus, actionSerial, lastRequestId, sweepEnabled, sweepAngle, sweepPeriodTicks,
                stagesEnabled, alertTicks, resetTicks, haloEnabled, stage, stageRevision, occupiedTicks, quietTicks);
        if (!state.equals(lastState)) {
            lastState = state;
            PacketDistributor.sendToPlayer(server, state);
        }
    }
    public void receive(CameraMessages.State state) {
        if (state.menuId() != containerId)
            return;
        revision = state.revision();
        editable = state.editable();
        running = state.running();
        program = state.program();
        links = state.links();
        range = Math.clamp(state.range(), 1, 16);
        yaw = state.yaw();
        pitch = state.pitch();
        sweepEnabled = state.sweepEnabled();
        sweepAngle = state.sweepAngle();
        sweepPeriodTicks = state.sweepPeriodTicks();
        stagesEnabled = state.stagesEnabled();
        alertTicks = state.alertTicks();
        resetTicks = state.resetTicks();
        haloEnabled = state.haloEnabled();
        stage = Math.clamp(state.stage(), 1, 3);
        stageRevision = state.stageRevision();
        occupiedTicks = Math.clamp(state.occupiedTicks(), 0, 72000);
        quietTicks = Math.clamp(state.quietTicks(), 0, 72000);
        status = state.status();
        runtimeStatus = state.runtimeStatus();
        actionSerial = state.actionSerial();
        lastRequestId = state.requestId();
        responseVersion++;
    }
    /** Valide le menu et l’action avant d’appliquer la demande, puis renvoie son identifiant. */
    public void apply(Player player, CameraMessages.Action action) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || action.menuId() != containerId
                || action.requestId() < 0 || !stillValid(player))
            return;
        if (mode == PROGRAM) {
            switch (action.action()) {
                case SAVE -> status = terminal.setProgram(server, action.program()).message();
                case TEST -> {
                    var validation = CameraProgram.validate(action.program(), terminal.linkViews());
                    status = validation.ok() ? "gui.industrial_facilities.camera.syntax_valid" : validation.error();
                }
                case RUNNING -> {
                    if (action.value() != 0 && action.value() != 1)
                        return;
                    status = terminal.setRunning(server, action.value() == 1).message();
                }
                default -> {
                    return;
                }
            }
        } else {
            if (action.action() != CONFIGURE || action.range() < 1 || action.range() > 16
                    || !Float.isFinite(action.yaw()) || !Float.isFinite(action.pitch()) || action.yaw() < -180
                    || action.yaw() > 180 || action.pitch() < -80 || action.pitch() > 80
                    || !Float.isFinite(action.sweepAngle()) || action.sweepAngle() < 0 || action.sweepAngle() > 90
                    || action.sweepPeriodTicks() < 40 || action.sweepPeriodTicks() > 2400 || action.alertTicks() < 1
                    || action.alertTicks() > 72000 || action.resetTicks() < 0 || action.resetTicks() > 72000)
                return;
            status = camera.configure(server, action.range(), action.yaw(), action.pitch(), action.sweepEnabled(),
                                   action.sweepAngle(), action.sweepPeriodTicks(), action.stagesEnabled(),
                                   action.alertTicks(), action.resetTicks(), action.haloEnabled())
                             .message();
        }
        actionSerial++;
        lastRequestId = action.requestId();
        lastState = null;
        broadcastChanges();
    }
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
