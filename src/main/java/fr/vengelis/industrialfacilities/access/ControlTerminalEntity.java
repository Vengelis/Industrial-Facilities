package fr.vengelis.industrialfacilities.access;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class ControlTerminalEntity extends AccessTerminalEntity {
    public static final int DEFAULT_SECONDS = 5;
    public static final int MAX_SECONDS = 300;
    private final boolean lockdown;
    private int seconds = DEFAULT_SECONDS;

    public ControlTerminalEntity(BlockPos position, BlockState state) {
        super(position, state);
        lockdown = ((ControlTerminalBlock) state.getBlock()).lockdown();
    }

    public boolean lockdown() { return lockdown; }
    public int seconds() { return seconds; }

    public boolean setSeconds(Player player, int value) {
        if (lockdown || !isOwner(player) || value < 1 || value > MAX_SECONDS || isLockedDown()) return false;
        seconds = value;
        sync();
        return true;
    }

    @Override public int linkedCount() {
        return level instanceof ServerLevel server ? TerminalControlData.get(server).links(this).size() : 0;
    }

    @Override public boolean clearLinks(Player player) {
        if (!(level instanceof ServerLevel server) || !isOwner(player) || isLockedDown()) return false;
        TerminalControlData.get(server).clear(server, this);
        return true;
    }

    @Override public void updateAccessState() {
        if (!(level instanceof ServerLevel server)) return;
        var controls = TerminalControlData.get(server);
        long now = server.getGameTime();
        boolean assembled = AccessTerminalEntity.resolve(level, worldPosition) == this;
        long until = assembled ? controls.activationUntil(this, now) : 0;
        boolean active = lockdown ? assembled && controls.on(this, now) : until > now;
        updateScreenState(active, until);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (!lockdown) tag.putInt("ActivationSeconds", seconds);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        seconds = tag.contains("ActivationSeconds") ? Math.clamp(tag.getInt("ActivationSeconds"), 1, MAX_SECONDS) : DEFAULT_SECONDS;
    }
}
