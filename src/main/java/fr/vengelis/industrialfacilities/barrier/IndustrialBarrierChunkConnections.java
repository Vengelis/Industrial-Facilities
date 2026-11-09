package fr.vengelis.industrialfacilities.barrier;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Refreshes existing builds and connections across chunk borders without loading extra chunks. */
@EventBusSubscriber(modid="industrial_facilities")
public final class IndustrialBarrierChunkConnections {
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().execute(() -> IndustrialFacilitiesMod.queueServerWork(1, () -> refresh(level, chunk)));
    }

    public static void refresh(ServerLevel level, LevelChunk chunk) {
        if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof IndustrialBarrierBlock)) continue;
            int baseY = chunk.getMinBuildHeight() + sectionIndex * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (!(state.getBlock() instanceof IndustrialBarrierBlock barrier)) continue;
                var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                barrier.refreshConnections(level, pos, state);
                IndustrialBarrierBlock.refreshDiagonalNeighbors(level, pos);
            }
        }
    }
}
