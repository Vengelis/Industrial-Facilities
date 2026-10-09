/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

public class IndustrialFacilitiesModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IndustrialFacilitiesMod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INDUSTRIAL_FACILITIES = REGISTRY.register("industrial_facilities",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.industrial_facilities.industrial_facilities")).icon(() -> new ItemStack(IndustrialFacilitiesModItems.EXTINGUISHER.get())).displayItems((parameters, tabData) -> {
				tabData.accept(IndustrialFacilitiesModItems.EXTINGUISHER.get());
				tabData.accept(IndustrialFacilitiesModBlocks.EMERGENCY_EXIT_SIGN_RIGHT.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.EMERGENCY_EXIT_SIGN_LEFT.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.AIRLOCK_FRAME.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.AIRLOCK_CONTROLLER.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.BIOHAZARD_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.RADIOACTIVE_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.WARNING_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.EXTINGUISHER_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.DEAD_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.MEDIC_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.LASER_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.COLD_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.FIRE_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.ASPHYXIA_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.EXPLOSION_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.MONSTER_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.FORBIDDEN_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.STOP_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.INFORMATION_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.STAIRS_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.ELEVATOR_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.LEFT_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.RIGHT_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.UP_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.DOWN_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.STORAGE_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.EMPTY_SIGN.get().asItem());
				tabData.accept(IndustrialFacilitiesModBlocks.FALLING_SIGN.get().asItem());
			}).build());
}