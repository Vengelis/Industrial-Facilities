package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;

public class ExtinguisherPlacedBlockDestroyedByPlayerProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z) {
		double savedTank = 0;
		ItemStack newExtinguisher = ItemStack.EMPTY;
		if (!world.isClientSide()) {
			savedTank = getBlockNBTNumber(world, BlockPos.containing(x, y, z), "tank");
			newExtinguisher = new ItemStack(IndustrialFacilitiesModItems.EXTINGUISHER.get()).copy();
			{
				final String _tagName = "tank";
				final double _tagValue = savedTank;
				CustomData.update(DataComponents.CUSTOM_DATA, newExtinguisher, tag -> tag.putDouble(_tagName, _tagValue));
			}
			{
				final String _tagName = "useTick";
				final double _tagValue = 0;
				CustomData.update(DataComponents.CUSTOM_DATA, newExtinguisher, tag -> tag.putDouble(_tagName, _tagValue));
			}
			{
				final String _tagName = "soundTick";
				final double _tagValue = 0;
				CustomData.update(DataComponents.CUSTOM_DATA, newExtinguisher, tag -> tag.putDouble(_tagName, _tagValue));
			}
			if (world instanceof ServerLevel _level) {
				ItemEntity entityToSpawn = new ItemEntity(_level, x, y, z, newExtinguisher);
				entityToSpawn.setPickUpDelay(3);
				_level.addFreshEntity(entityToSpawn);
			}
		}
	}

	private static double getBlockNBTNumber(LevelAccessor world, BlockPos pos, String tag) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity != null)
			return blockEntity.getPersistentData().getDouble(tag);
		return -1;
	}
}