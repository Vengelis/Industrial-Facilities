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
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_LIGHT_OPEN = REGISTRY.register("industrial_door_multiblock_light_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_light_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_LIGHT_CLOSE = REGISTRY.register("industrial_door_multiblock_light_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_light_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_DARK_OPEN = REGISTRY.register("industrial_door_multiblock_dark_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_dark_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_DARK_CLOSE = REGISTRY.register("industrial_door_multiblock_dark_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_dark_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RESTRICTED_OPEN = REGISTRY.register("industrial_door_multiblock_restricted_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_restricted_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RESTRICTED_CLOSE = REGISTRY.register("industrial_door_multiblock_restricted_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_restricted_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_GLASS_OPEN = REGISTRY.register("industrial_door_multiblock_glass_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_glass_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_GLASS_CLOSE = REGISTRY.register("industrial_door_multiblock_glass_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_glass_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RUSTY_OPEN = REGISTRY.register("industrial_door_multiblock_rusty_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_rusty_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RUSTY_CLOSE = REGISTRY.register("industrial_door_multiblock_rusty_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_rusty_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_SAFE_OPEN = REGISTRY.register("industrial_door_multiblock_safe_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_safe_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_SAFE_CLOSE = REGISTRY.register("industrial_door_multiblock_safe_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_safe_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RADIATION_OPEN = REGISTRY.register("industrial_door_multiblock_radiation_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_radiation_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_RADIATION_CLOSE = REGISTRY.register("industrial_door_multiblock_radiation_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_radiation_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_FIRE_OPEN = REGISTRY.register("industrial_door_multiblock_fire_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_fire_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_FIRE_CLOSE = REGISTRY.register("industrial_door_multiblock_fire_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_fire_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_BLAST_OPEN = REGISTRY.register("industrial_door_multiblock_blast_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_blast_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_MESH_OPEN = REGISTRY.register("industrial_door_multiblock_mesh_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_mesh_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_IRIS_OPEN = REGISTRY.register("industrial_door_multiblock_iris_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_iris_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_JAW_OPEN = REGISTRY.register("industrial_door_multiblock_jaw_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_jaw_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_BLAST_CLOSE = REGISTRY.register("industrial_door_multiblock_blast_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_blast_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_MESH_CLOSE = REGISTRY.register("industrial_door_multiblock_mesh_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_mesh_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_IRIS_CLOSE = REGISTRY.register("industrial_door_multiblock_iris_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_iris_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_MULTIBLOCK_JAW_CLOSE = REGISTRY.register("industrial_door_multiblock_jaw_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_multiblock_jaw_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_LIGHT_OPEN = REGISTRY.register("industrial_door_classic_light_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_light_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_LIGHT_CLOSE = REGISTRY.register("industrial_door_classic_light_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_light_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_DARK_OPEN = REGISTRY.register("industrial_door_classic_dark_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_dark_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_DARK_CLOSE = REGISTRY.register("industrial_door_classic_dark_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_dark_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RESTRICTED_OPEN = REGISTRY.register("industrial_door_classic_restricted_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_restricted_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RESTRICTED_CLOSE = REGISTRY.register("industrial_door_classic_restricted_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_restricted_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_GLASS_OPEN = REGISTRY.register("industrial_door_classic_glass_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_glass_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_GLASS_CLOSE = REGISTRY.register("industrial_door_classic_glass_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_glass_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RUSTY_OPEN = REGISTRY.register("industrial_door_classic_rusty_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_rusty_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RUSTY_CLOSE = REGISTRY.register("industrial_door_classic_rusty_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_rusty_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_SAFE_OPEN = REGISTRY.register("industrial_door_classic_safe_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_safe_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_SAFE_CLOSE = REGISTRY.register("industrial_door_classic_safe_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_safe_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RADIATION_OPEN = REGISTRY.register("industrial_door_classic_radiation_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_radiation_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_RADIATION_CLOSE = REGISTRY.register("industrial_door_classic_radiation_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_radiation_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_FIRE_OPEN = REGISTRY.register("industrial_door_classic_fire_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_fire_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_FIRE_CLOSE = REGISTRY.register("industrial_door_classic_fire_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_fire_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_BLAST_OPEN = REGISTRY.register("industrial_door_classic_blast_open",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_blast_open")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLASSIC_BLAST_CLOSE = REGISTRY.register("industrial_door_classic_blast_close",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_classic_blast_close")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_OPEN_STOP = REGISTRY.register("industrial_door_open_stop",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_open_stop")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INDUSTRIAL_DOOR_CLOSE_STOP = REGISTRY.register("industrial_door_close_stop",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "industrial_door_close_stop")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_ACCESS_GRANTED = REGISTRY.register("terminal_access_granted",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "terminal_access_granted")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_ACCESS_DENIED = REGISTRY.register("terminal_access_denied",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "terminal_access_denied")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CAMERA_ROTATE = REGISTRY.register("camera_rotate", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "camera_rotate")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CAMERA_TRACKING_ACQUIRE = REGISTRY.register("camera_tracking_acquire",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "camera_tracking_acquire")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CAMERA_TRACKING_RELEASE = REGISTRY.register("camera_tracking_release",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("industrial_facilities", "camera_tracking_release")));
}