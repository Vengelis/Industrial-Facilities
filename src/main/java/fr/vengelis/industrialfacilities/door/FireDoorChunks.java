package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorFireBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class FireDoorChunks {
    // Start monitoring doors in saves created before the periodic tick existed.
    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(FireDoorChunks::isFireDoorBase)) continue;
            int y0 = chunk.getMinBuildHeight() + index * 16;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                BlockState state = section.getBlockState(x, y, z);
                if (isFireDoorBase(state)) level.scheduleTick(new BlockPos(x0 + x, y0 + y, z0 + z), state.getBlock(), 1);
            }
        }
    }

    private static boolean isFireDoorBase(BlockState state) {
        return state.getBlock() instanceof IndustrialMetalDoorFireBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private FireDoorChunks() {}
}
