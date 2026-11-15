package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class IndustrialDoorSounds {
    public static ResourceLocation id(IndustrialDoorStyle style, boolean classic, boolean opening) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,
            "industrial_door_" + (classic ? "classic_" : "multiblock_") + style.configName() + (opening ? "_open" : "_close"));
    }

    public static SoundEvent event(IndustrialDoorStyle style, boolean classic, boolean opening) {
        return BuiltInRegistries.SOUND_EVENT.get(id(style, classic, opening));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {
            for (var style : IndustrialDoorStyle.values()) {
                for (boolean classic : new boolean[]{false, true}) {
                    if (classic && !style.hasClassic()) continue;
                    for (boolean opening : new boolean[]{false, true}) {
                        var id = id(style, classic, opening);
                        if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) helper.register(id, SoundEvent.createVariableRangeEvent(id));
                    }
                }
            }
        });
    }

    private IndustrialDoorSounds() {}
}
