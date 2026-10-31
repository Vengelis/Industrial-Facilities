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
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_OPEN = REGISTRY.register("industrial_door_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLOSE = REGISTRY.register("industrial_door_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_OPEN_STOP = REGISTRY.register("industrial_door_open_stop",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_open_stop")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLOSE_STOP = REGISTRY.register("industrial_door_close_stop",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_close_stop")));
}