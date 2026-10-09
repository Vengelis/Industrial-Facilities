/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import fr.vengelis.industrialfacilities.client.gui.AirlockControllerGUIScreen;

@EventBusSubscriber(Dist.CLIENT)
public class IndustrialFacilitiesModScreens {
	@SubscribeEvent
	public static void clientLoad(RegisterMenuScreensEvent event) {
		event.register(IndustrialFacilitiesModMenus.AIRLOCK_CONTROLLER_GUI.get(), AirlockControllerGUIScreen::new);
	}

	public interface ScreenAccessor {
		void updateMenuState(int elementType, String name, Object elementState);
	}
}