package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores the remaining pivot delay in the lower half so saving or unloading cannot reset it. */
public final class IndustrialHingedDoorEntity extends BlockEntity {
    private int remaining = -1;
    private boolean requestedOpen;

    public IndustrialHingedDoorEntity(BlockPos pos, BlockState state) {
        super(IndustrialMetalDoorContent.HINGE_TYPE, pos, state);
    }

    public void begin(boolean opening, int ticks) {
        requestedOpen = opening;
        remaining = ticks;
        setChanged();
    }

    public void tick(ServerLevel level, BlockState state) {
        boolean opening = state.getValue(DoorBlock.POWERED);
        if (opening == state.getValue(DoorBlock.OPEN)) {
            if (remaining != -1) {
                remaining = -1;
                setChanged();
            }
            return;
        }
        if (remaining < 0 || requestedOpen != opening) {
            begin(opening, IndustrialMetalDoorBlock.motionTicks(level, worldPosition, state, opening));
        }
        if (remaining <= 1 && IndustrialMetalDoorBlock.finishPivot(level, worldPosition, opening)) {
            remaining = -1;
        } else if (remaining > 1) {
            remaining--;
        }
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("Remaining", remaining);
        tag.putBoolean("RequestedOpen", requestedOpen);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        remaining = tag.contains("Remaining") ? Math.clamp(tag.getInt("Remaining"), -1, DoorMotionConfig.MAX_TICKS) : -1;
        requestedOpen = tag.getBoolean("RequestedOpen");
    }
}
