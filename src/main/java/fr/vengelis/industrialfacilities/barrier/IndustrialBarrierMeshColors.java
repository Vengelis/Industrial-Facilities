package fr.vengelis.industrialfacilities.barrier;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Only the neutral wire texture is tinted; the existing painted frames stay unchanged. */
@EventBusSubscriber(modid="industrial_facilities",value=Dist.CLIENT)
public final class IndustrialBarrierMeshColors {
    @SubscribeEvent public static void register(RegisterColorHandlersEvent.Block event) {
        event.register((state,view,pos,index)->{
            if(index==0)return 0xff484848;
            if(index!=1)return -1;
            if(state.is(IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_WHITE.get()))return 0xffd4dbcc;
            if(state.is(IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_DARK_GRAY.get()))return 0xff424d4e;
            return 0xffb2a266;
        },
            IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_YELLOW.get(),
            IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_WHITE.get(),
            IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_DARK_GRAY.get(),
            IndustrialFacilitiesModBlocks.INDUSTRIAL_BARRIER_YELLOW_HAZARD.get());
    }
}
