package fr.vengelis.industrialfacilities.energybarrier;

import fr.vengelis.industrialfacilities.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Reconstruct new connection states for barriers already placed in saved worlds. */
@EventBusSubscriber(modid="industrial_facilities")
public final class EnergyBarrierConnections {
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        var sections=chunk.getSections();
        for(int index=0;index<sections.length;index++){
            var section=sections[index];
            if(!section.maybeHas(s->s.getBlock() instanceof EnergyBarrierPillarBlock||s.getBlock() instanceof EnergyBarrierBaseBlock))continue;
            int minY=chunk.getMinBuildHeight()+index*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.getBlockState(x,y,z);
                if(state.getBlock() instanceof EnergyBarrierPillarBlock||state.getBlock() instanceof EnergyBarrierBaseBlock){
                    BlockPos pos=new BlockPos(chunk.getPos().getMinBlockX()+x,minY+y,chunk.getPos().getMinBlockZ()+z);
                    level.scheduleTick(pos,state.getBlock(),1);
                }
            }
        }
    }
    private EnergyBarrierConnections(){}
}
