package fr.vengelis.industrialfacilities.procedures;

import net.neoforged.neoforge.items.ItemHandlerHelper;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;

public class ExtinguisherPlacedOnBlockRightclickedProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		double savedTank = 0;
		ItemStack newExtinguisher = ItemStack.EMPTY;
		if (!world.isClientSide()) {
			if ((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == Items.WATER_BUCKET) {
				savedTank = getBlockNBTNumber(world, BlockPos.containing(x, y, z), "tank");
				if (savedTank < 6400) {
					if (!world.isClientSide()) {
						BlockPos _bp = BlockPos.containing(x, y, z);
						BlockEntity _blockEntity = world.getBlockEntity(_bp);
						BlockState _bs = world.getBlockState(_bp);
						if (_blockEntity != null) {
							_blockEntity.getPersistentData().putDouble("tank", (Mth.clamp(savedTank + 2134, 0, 6400)));
						}
						if (world instanceof Level _level)
							_level.sendBlockUpdated(_bp, _bs, _bs, 3);
					}
					if (world instanceof Level _level) {
						if (!_level.isClientSide()) {
							_level.playSound(null, BlockPos.containing(x, y, z), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.bucket.empty")), SoundSource.BLOCKS, (float) 0.5, 1);
						} else {
							_level.playLocalSound(x, y, z, BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.bucket.empty")), SoundSource.BLOCKS, (float) 0.5, 1, false);
						}
					}
					if (!(entity instanceof Player _plr ? _plr.getAbilities().instabuild : false)) {
						(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).shrink(1);
						if (entity instanceof Player _player) {
							ItemStack _setstack = new ItemStack(Items.BUCKET).copy();
							_setstack.setCount(1);
							ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
						}
					}
				}
			} else {
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
				if (entity instanceof Player _player) {
					ItemStack _setstack = newExtinguisher.copy();
					_setstack.setCount(1);
					ItemHandlerHelper.giveItemToPlayer(_player, _setstack);
				}
				world.setBlock(BlockPos.containing(x, y, z), Blocks.AIR.defaultBlockState(), 3);
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