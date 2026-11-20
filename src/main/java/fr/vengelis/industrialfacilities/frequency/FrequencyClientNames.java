package fr.vengelis.industrialfacilities.frequency;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
final class FrequencyClientNames {
    private static final Map<FrequencyChip, String> NAMES = new HashMap<>();
    private static boolean synced;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            FrequencyContent.namesReceiver = FrequencyClientNames::receive;
            FrequencyContent.displayNames = FrequencyClientNames::lookup;
        });
    }

    private static Optional<String> lookup(FrequencyChip frequency) {
        var player = Minecraft.getInstance().player;
        if (!synced || player == null || !frequency.permits(player.getUUID())) return Optional.empty();
        return Optional.of(NAMES.getOrDefault(frequency, ""));
    }

    private static void receive(FrequencyContent.Names packet) {
        if (packet.reset()) {
            NAMES.clear();
            synced = true;
        }
        for (var entry : packet.entries()) {
            if (entry.name().isEmpty()) NAMES.remove(entry.frequency());
            else NAMES.put(entry.frequency(), entry.name());
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        synced = false;
        NAMES.clear();
    }

    private FrequencyClientNames() {}
}

