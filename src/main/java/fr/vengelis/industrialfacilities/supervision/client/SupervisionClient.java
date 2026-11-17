package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "industrial_facilities", value = Dist.CLIENT)
public final class SupervisionClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(SupervisionContent.MENU, SupervisionScreen::new); }
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SupervisionContent.TYPE, SupervisionRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SupervisionPackets.receiver = packet -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof SupervisionMenu menu) menu.receive(packet);
        };
    }
}
