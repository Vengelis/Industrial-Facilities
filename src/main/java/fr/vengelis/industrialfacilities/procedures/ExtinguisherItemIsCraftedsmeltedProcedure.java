package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class ExtinguisherItemIsCraftedsmeltedProcedure {
	public static void execute(ItemStack itemstack) {
		{
			final String _tagName = "tank";
			final double _tagValue = 0;
			CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
		}
		{
			final String _tagName = "useTick";
			final double _tagValue = 0;
			CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
		}
		{
			final String _tagName = "animTick";
			final double _tagValue = 0;
			CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
		}
		{
			final String _tagName = "soundTick";
			final double _tagValue = 0;
			CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
		}
	}
}