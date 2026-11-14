package fr.vengelis.industrialfacilities.alarm;

import fr.vengelis.industrialfacilities.block.AlarmBlock;
import fr.vengelis.industrialfacilities.security.AutomationOutputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Détermine quels joueurs entendent l'alarme et leur transmet l'état de la boucle audio. */
public class AlarmBlockEntity extends BlockEntity {
    private static final int BROADCAST_INTERVAL = 5;

    public UUID automationOwner;
    public UUID automationInstanceId = UUID.randomUUID();
    private String soundId = "";
    private int range = AlarmConfig.defaultRange();
    private int ticks;
    private final Set<UUID> listeners = new HashSet<>();

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(AlarmContent.TYPE, pos, state);
    }

    public String soundId() {
        return AlarmConfig.resolve(soundId).id();
    }

    public int range() {
        return range;
    }

    public boolean powered() {
        return level != null && (level.hasNeighborSignal(worldPosition)
            || level instanceof ServerLevel server
                && AutomationOutputs.active(server, worldPosition, "alarm", automationInstanceId));
    }

    public void configure(String id, int radius) {
        if (AlarmConfig.find(id) == null) {
            return;
        }
        soundId = id;
        range = Math.clamp(radius, 1, AlarmConfig.MAX_RANGE);
        setChanged();
        broadcast();
    }

    public void updatePower() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        boolean active = powered();
        if (getBlockState().getValue(AlarmBlock.POWERED) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(AlarmBlock.POWERED, active), 3);
        }
        broadcast();
    }

    public void tick() {
        if (++ticks % BROADCAST_INTERVAL == 1) {
            updatePower();
        }
    }

    public void broadcast() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        var sound = AlarmConfig.resolve(soundId).sound();
        boolean active = powered();
        double x = worldPosition.getX() + .5;
        double y = worldPosition.getY() + .5;
        double z = worldPosition.getZ() + .5;
        double rangeSquared = (double) range * range;

        // Réémettre le signal pour chaque auditeur. Le client expire la boucle si le monde cesse de répondre.
        Set<UUID> currentListeners = new HashSet<>();
        for (ServerPlayer player : server.players()) {
            UUID id = player.getUUID();
            if (active && player.distanceToSqr(x, y, z) < rangeSquared) {
                PacketDistributor.sendToPlayer(player,
                    new AlarmSignal(server.dimension().location(), worldPosition, sound, range, true));
                currentListeners.add(id);
            } else if (listeners.contains(id)) {
                sendStop(player);
            }
        }
        listeners.clear();
        listeners.addAll(currentListeners);
    }

    private void sendStop(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new AlarmSignal(level.dimension().location(), worldPosition, AlarmConfig.resolve(soundId).sound(), range, false));
    }

    public void stopListeners() {
        if (level instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                if (listeners.contains(player.getUUID())) {
                    sendStop(player);
                }
            }
        }
        listeners.clear();
    }

    @Override
    public void setRemoved() {
        stopListeners();
        super.setRemoved();
    }

    public void writeMenu(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
        buffer.writeUtf(soundId(), 48);
        buffer.writeVarInt(range);

        var entries = AlarmConfig.entries();
        buffer.writeVarInt(entries.size());
        for (var entry : entries) {
            buffer.writeUtf(entry.id(), 48);
            buffer.writeUtf(entry.name(), 80);
            buffer.writeResourceLocation(entry.sound());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("AlarmSound", soundId);
        tag.putInt("AlarmRange", range);
        if (automationOwner != null) {
            tag.putUUID("AutomationOwner", automationOwner);
        }
        tag.putUUID("AutomationInstanceId", automationInstanceId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        soundId = tag.getString("AlarmSound");
        automationOwner = tag.hasUUID("AutomationOwner") ? tag.getUUID("AutomationOwner") : null;
        automationInstanceId = tag.hasUUID("AutomationInstanceId") ? tag.getUUID("AutomationInstanceId") : UUID.randomUUID();
        range = tag.contains("AlarmRange")
            ? Math.clamp(tag.getInt("AlarmRange"), 1, AlarmConfig.MAX_RANGE)
            : AlarmConfig.defaultRange();
    }
}
