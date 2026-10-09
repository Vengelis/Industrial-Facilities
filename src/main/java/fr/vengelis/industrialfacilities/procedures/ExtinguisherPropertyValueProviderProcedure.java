package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class ExtinguisherPropertyValueProviderProcedure {
	public static double execute(ItemStack itemstack) {
		if (!(itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("animTick") == 0)) {
			if (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("animTick") < 4) {
				return 0;
			} else if (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("animTick") < 8) {
				return 1;
			} else {
				return 2;
			}
		}
		return 1;
	}
}