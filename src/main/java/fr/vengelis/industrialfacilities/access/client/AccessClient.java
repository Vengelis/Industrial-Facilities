package fr.vengelis.industrialfacilities.access.client;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.ui.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class AccessClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(AccessMenus.TYPE,AccessScreen::new);event.register(KeypassMenus.TYPE,KeypassScreen::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        AccessMessages.receiver=state->{var player=Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof AccessMenu menu&&menu.containerId==state.menuId())menu.receive(state);};
        KeypassMessages.receiver=state->{var player=Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof KeypassMenu menu&&menu.containerId==state.menuId())menu.receive(state);};
    }
    private AccessClient(){}
}
