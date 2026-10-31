package fr.vengelis.industrialfacilities.alarm;

import fr.vengelis.industrialfacilities.block.AlarmBlock;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public class AlarmBlockEntity extends BlockEntity {
    private String soundId = "";
    private int range = AlarmConfig.defaultRange();
    private int ticks;
    private final Set<UUID> listeners = new HashSet<>();
    public AlarmBlockEntity(BlockPos p,BlockState s) { super(AlarmContent.TYPE,p,s); }
    public String soundId() { return AlarmConfig.resolve(soundId).id(); }
    public int range() { return range; }
    public boolean powered() { return level != null && level.hasNeighborSignal(worldPosition); }
    public void configure(String id,int radius) {
        if (AlarmConfig.find(id) == null) return;
        soundId = id; range = Math.clamp(radius,1,AlarmConfig.MAX_RANGE); setChanged();
        broadcast();
    }
    public void updatePower() {
        if (!(level instanceof ServerLevel)) return;
        boolean active = powered();
        if (getBlockState().getValue(AlarmBlock.POWERED) != active)
            level.setBlock(worldPosition,getBlockState().setValue(AlarmBlock.POWERED,active),3);
        broadcast();
    }
    public void tick() { if (++ticks % 5 == 1) updatePower(); }
    public void broadcast() {
        if (!(level instanceof ServerLevel server)) return;
        var entry = AlarmConfig.resolve(soundId);
        boolean active = powered();
        Set<UUID> now = new HashSet<>();
        for (var player : server.players()) {
            if (active && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5) < (double)range*range) {
                PacketDistributor.sendToPlayer(player,new AlarmSignal(server.dimension().location(),worldPosition,entry.sound(),range,true));
                now.add(player.getUUID());
            } else if (listeners.contains(player.getUUID())) sendStop(player);
        }
        listeners.clear(); listeners.addAll(now);
    }
    private void sendStop(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,new AlarmSignal(level.dimension().location(),worldPosition,AlarmConfig.resolve(soundId).sound(),range,false));
    }
    public void stopListeners() {
        if (level instanceof ServerLevel server) for (var player : server.players()) if (listeners.contains(player.getUUID())) sendStop(player);
        listeners.clear();
    }
    @Override public void setRemoved() { stopListeners(); super.setRemoved(); }
    public void writeMenu(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition); buf.writeUtf(soundId(),48); buf.writeVarInt(range);
        var entries=AlarmConfig.entries(); buf.writeVarInt(entries.size());
        for (var e : entries) { buf.writeUtf(e.id(),48); buf.writeUtf(e.name(),80); buf.writeResourceLocation(e.sound()); }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putString("AlarmSound",soundId); tag.putInt("AlarmRange",range);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); soundId=tag.getString("AlarmSound");
        range=tag.contains("AlarmRange") ? Math.clamp(tag.getInt("AlarmRange"),1,AlarmConfig.MAX_RANGE) : AlarmConfig.defaultRange();
    }
}
