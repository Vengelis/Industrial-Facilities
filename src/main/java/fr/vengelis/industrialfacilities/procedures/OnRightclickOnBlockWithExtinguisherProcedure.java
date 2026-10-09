package fr.vengelis.industrialfacilities.procedures;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;

@EventBusSubscriber
public class OnRightclickOnBlockWithExtinguisherProcedure {
	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getHand() != InteractionHand.MAIN_HAND)
			return;
		execute(event, event.getLevel(), event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(), event.getFace(), event.getEntity());
	}

	public static void execute(LevelAccessor world, double x, double y, double z, Direction direction, Entity entity) {
		execute(null, world, x, y, z, direction, entity);
	}

	private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Direction direction, Entity entity) {
		if (direction == null || entity == null)
			return;
		double placeX = 0;
		double placeY = 0;
		double placeZ = 0;
		double savedTank = 0;
		if (!world.isClientSide()) {
			if ((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == IndustrialFacilitiesModItems.EXTINGUISHER.get()) {
				if (entity.isShiftKeyDown()) {
					placeX = x + direction.getStepX();
					placeY = y + direction.getStepY();
					placeZ = z + direction.getStepZ();
					if ((world.getBlockState(BlockPos.containing(placeX, placeX, placeX))).is(BlockTags.create(ResourceLocation.parse("minecraft:replaceable")))
							|| (world.getBlockState(BlockPos.containing(placeX, placeX, placeX))).getBlock() == Blocks.AIR || (world.getBlockState(BlockPos.containing(placeX, placeX, placeX))).getBlock() == Blocks.CAVE_AIR) {
						savedTank = (entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("tank");
						world.setBlock(BlockPos.containing(placeX, placeY, placeZ), IndustrialFacilitiesModBlocks.EXTINGUISHER_PLACED.get().defaultBlockState(), 3);
						{
							Direction _dir = (entity.getDirection());
							BlockPos _pos = BlockPos.containing(placeX, placeY, placeZ);
							BlockState _bs = world.getBlockState(_pos);
							Property<?> _property = _bs.getBlock().getStateDefinition().getProperty("facing");
							if (_property instanceof DirectionProperty _dp && _dp.getPossibleValues().contains(_dir)) {
								world.setBlock(_pos, _bs.setValue(_dp, _dir), 3);
							} else {
								_property = _bs.getBlock().getStateDefinition().getProperty("axis");
								if (_property instanceof EnumProperty _ap && _ap.getPossibleValues().contains(_dir.getAxis()))
									world.setBlock(_pos, _bs.setValue(_ap, _dir.getAxis()), 3);
							}
						}
						if (!world.isClientSide()) {
							BlockPos _bp = BlockPos.containing(placeX, placeY, placeZ);
							BlockEntity _blockEntity = world.getBlockEntity(_bp);
							BlockState _bs = world.getBlockState(_bp);
							if (_blockEntity != null) {
								_blockEntity.getPersistentData().putDouble("tank", savedTank);
							}
							if (world instanceof Level _level)
								_level.sendBlockUpdated(_bp, _bs, _bs, 3);
						}
						(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).shrink(1);
					}
				}
			}
		}
	}
}