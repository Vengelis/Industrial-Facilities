/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

public class IndustrialFacilitiesModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, IndustrialFacilitiesMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> EXTINGUISHER_SPRAY = REGISTRY.register("extinguisher_spray",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "extinguisher_spray")));
	public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_OPEN = REGISTRY.register("airlock_open", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "airlock_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> AIRLOCK_CLOSE = REGISTRY.register("airlock_close", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "airlock_close")));
}