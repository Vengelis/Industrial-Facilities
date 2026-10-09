/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;

import fr.vengelis.industrialfacilities.procedures.ExtinguisherPropertyValueProviderProcedure;
import fr.vengelis.industrialfacilities.item.ExtinguisherItem;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

public class IndustrialFacilitiesModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(IndustrialFacilitiesMod.MODID);
	public static final DeferredItem<Item> EXTINGUISHER;
	public static final DeferredItem<Item> EXTINGUISHER_PLACED;
	public static final DeferredItem<Item> EMERGENCY_EXIT_SIGN_RIGHT;
	public static final DeferredItem<Item> EMERGENCY_EXIT_SIGN_LEFT;
	public static final DeferredItem<Item> AIRLOCK_FRAME;
	public static final DeferredItem<Item> AIRLOCK_CONTROLLER;
	public static final DeferredItem<Item> AIRLOCK_SEAL;
	public static final DeferredItem<Item> BIOHAZARD_SIGN;
	public static final DeferredItem<Item> RADIOACTIVE_SIGN;
	public static final DeferredItem<Item> WARNING_SIGN;
	public static final DeferredItem<Item> EXTINGUISHER_SIGN;
	public static final DeferredItem<Item> DEAD_SIGN;
	public static final DeferredItem<Item> MEDIC_SIGN;
	public static final DeferredItem<Item> LASER_SIGN;
	public static final DeferredItem<Item> COLD_SIGN;
	public static final DeferredItem<Item> FIRE_SIGN;
	public static final DeferredItem<Item> ASPHYXIA_SIGN;
	public static final DeferredItem<Item> EXPLOSION_SIGN;
	public static final DeferredItem<Item> MONSTER_SIGN;
	public static final DeferredItem<Item> FORBIDDEN_SIGN;
	public static final DeferredItem<Item> STOP_SIGN;
	public static final DeferredItem<Item> INFORMATION_SIGN;
	public static final DeferredItem<Item> STAIRS_SIGN;
	public static final DeferredItem<Item> ELEVATOR_SIGN;
	public static final DeferredItem<Item> LEFT_SIGN;
	public static final DeferredItem<Item> RIGHT_SIGN;
	public static final DeferredItem<Item> UP_SIGN;
	public static final DeferredItem<Item> DOWN_SIGN;
	public static final DeferredItem<Item> STORAGE_SIGN;
	public static final DeferredItem<Item> EMPTY_SIGN;
	public static final DeferredItem<Item> FALLING_SIGN;
	static {
		EXTINGUISHER = REGISTRY.register("extinguisher", ExtinguisherItem::new);
		EXTINGUISHER_PLACED = block(IndustrialFacilitiesModBlocks.EXTINGUISHER_PLACED);
		EMERGENCY_EXIT_SIGN_RIGHT = block(IndustrialFacilitiesModBlocks.EMERGENCY_EXIT_SIGN_RIGHT);
		EMERGENCY_EXIT_SIGN_LEFT = block(IndustrialFacilitiesModBlocks.EMERGENCY_EXIT_SIGN_LEFT);
		AIRLOCK_FRAME = block(IndustrialFacilitiesModBlocks.AIRLOCK_FRAME);
		AIRLOCK_CONTROLLER = block(IndustrialFacilitiesModBlocks.AIRLOCK_CONTROLLER);
		AIRLOCK_SEAL = block(IndustrialFacilitiesModBlocks.AIRLOCK_SEAL);
		BIOHAZARD_SIGN = block(IndustrialFacilitiesModBlocks.BIOHAZARD_SIGN);
		RADIOACTIVE_SIGN = block(IndustrialFacilitiesModBlocks.RADIOACTIVE_SIGN);
		WARNING_SIGN = block(IndustrialFacilitiesModBlocks.WARNING_SIGN);
		EXTINGUISHER_SIGN = block(IndustrialFacilitiesModBlocks.EXTINGUISHER_SIGN);
		DEAD_SIGN = block(IndustrialFacilitiesModBlocks.DEAD_SIGN);
		MEDIC_SIGN = block(IndustrialFacilitiesModBlocks.MEDIC_SIGN);
		LASER_SIGN = block(IndustrialFacilitiesModBlocks.LASER_SIGN);
		COLD_SIGN = block(IndustrialFacilitiesModBlocks.COLD_SIGN);
		FIRE_SIGN = block(IndustrialFacilitiesModBlocks.FIRE_SIGN);
		ASPHYXIA_SIGN = block(IndustrialFacilitiesModBlocks.ASPHYXIA_SIGN);
		EXPLOSION_SIGN = block(IndustrialFacilitiesModBlocks.EXPLOSION_SIGN);
		MONSTER_SIGN = block(IndustrialFacilitiesModBlocks.MONSTER_SIGN);
		FORBIDDEN_SIGN = block(IndustrialFacilitiesModBlocks.FORBIDDEN_SIGN);
		STOP_SIGN = block(IndustrialFacilitiesModBlocks.STOP_SIGN);
		INFORMATION_SIGN = block(IndustrialFacilitiesModBlocks.INFORMATION_SIGN);
		STAIRS_SIGN = block(IndustrialFacilitiesModBlocks.STAIRS_SIGN);
		ELEVATOR_SIGN = block(IndustrialFacilitiesModBlocks.ELEVATOR_SIGN);
		LEFT_SIGN = block(IndustrialFacilitiesModBlocks.LEFT_SIGN);
		RIGHT_SIGN = block(IndustrialFacilitiesModBlocks.RIGHT_SIGN);
		UP_SIGN = block(IndustrialFacilitiesModBlocks.UP_SIGN);
		DOWN_SIGN = block(IndustrialFacilitiesModBlocks.DOWN_SIGN);
		STORAGE_SIGN = block(IndustrialFacilitiesModBlocks.STORAGE_SIGN);
		EMPTY_SIGN = block(IndustrialFacilitiesModBlocks.EMPTY_SIGN);
		FALLING_SIGN = block(IndustrialFacilitiesModBlocks.FALLING_SIGN);
	}

	// Start of user code block custom items
	// End of user code block custom items
	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}

	@EventBusSubscriber(Dist.CLIENT)
	public static class ItemsClientSideHandler {
		@SubscribeEvent
		@OnlyIn(Dist.CLIENT)
		public static void clientLoad(FMLClientSetupEvent event) {
			event.enqueueWork(() -> {
				ItemProperties.register(EXTINGUISHER.get(), ResourceLocation.parse("industrial_facilities:extinguisher_spray_anim"),
						(itemStackToRender, clientWorld, entity, itemEntityId) -> (float) ExtinguisherPropertyValueProviderProcedure.execute(itemStackToRender));
			});
		}
	}
}