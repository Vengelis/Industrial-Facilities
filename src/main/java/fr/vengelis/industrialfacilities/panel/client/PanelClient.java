package fr.vengelis.industrialfacilities.panel.client;

import fr.vengelis.industrialfacilities.panel.PanelContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="industrial_facilities", value=Dist.CLIENT)
public final class PanelClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e) { e.register(PanelContent.MENU, PanelScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerBlockEntityRenderer(PanelContent.TYPE, PanelRenderer::new); }
    private PanelClient() {}
}
