package fr.vengelis.industrialfacilities.security.client;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import fr.vengelis.industrialfacilities.item.TerminalLinkCardItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The existing linking card keeps its green access appearance and gains a violet automation mode. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class SecurityAssetClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->ItemProperties.register(IndustrialFacilitiesModItems.TERMINAL_LINK_CARD.get(),
            ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"automation_mode"),
            (stack,level,entity,seed)->TerminalLinkCardItem.automationMode(stack)?1f:0f));
    }
    private SecurityAssetClient(){}
}
