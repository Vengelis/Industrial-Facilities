package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.IndustrialLiftDoorBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class IndustrialLiftDoorEntity extends BlockEntity {
    public float progress;
    public float previousProgress;
    private boolean paused;

    public IndustrialLiftDoorEntity(BlockPos pos, BlockState state) {
        super(IndustrialMetalDoorContent.LIFT_TYPE, pos, state);
        progress = previousProgress = state.getValue(DoorBlock.OPEN) ? 1 : 0;
    }

    @SubscribeEvent
    public static void loadExistingDoors(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel server) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.findBlocks(IndustrialMetalDoorBlock::isMetalDoorBase, (pos, state) -> server.scheduleTick(pos.immutable(), state.getBlock(), 1));
    }

    public void tick(Level level, BlockState state) {
        previousProgress = progress;
        boolean opening = state.getValue(DoorBlock.OPEN);
        if (progress == (opening ? 1 : 0) && !paused) return;
        int ticks = IndustrialMetalDoorBlock.motionTicks(level, worldPosition, state, opening);
        float next = IndustrialDoorEntity.advance(progress, opening, ticks);
        if (level instanceof ServerLevel server) {
            var upper = server.getBlockState(worldPosition.above());
            if (!upper.is(state.getBlock()) || IndustrialLiftDoorBlock.isLower(upper)) return;
            boolean blocked = next < progress && obstructed(server, state, next);
            if (blocked != paused || !blocked && next != progress) {
                paused = blocked;
                if (!paused) progress = next;
                setChanged();
                server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        } else if (!paused) {
            progress = next;
        }
    }

    private boolean obstructed(ServerLevel server, BlockState state, float next) {
        var facing = state.getValue(DoorBlock.FACING);
        var upper = IndustrialLiftDoorBlock.panelShape(facing, Math.max(0, 2 * next - 1)).bounds().move(worldPosition.above());
        double bottom = worldPosition.getY() + 2 * next;
        var panel = upper.setMinY(bottom);
        return !server.getEntities((Entity)null, panel, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Paused", paused);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        previousProgress = progress;
        if (tag.contains("Progress")) progress = Math.clamp(tag.getFloat("Progress"), 0, 1);
        paused = tag.getBoolean("Paused");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
