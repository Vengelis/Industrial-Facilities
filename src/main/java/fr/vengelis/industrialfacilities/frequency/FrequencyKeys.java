package fr.vengelis.industrialfacilities.frequency;

import com.mojang.blaze3d.platform.InputConstants;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class FrequencyKeys {
    private static final KeyMapping OPEN = new KeyMapping("key.industrial_facilities.open_frequency_slots",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gameplay");

    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!OPEN.consumeClick()) return;
        Minecraft game = Minecraft.getInstance();
        if (game.player != null && game.screen == null && game.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK)
            PacketDistributor.sendToServer(new FrequencyContent.OpenSlots(hit.getBlockPos()));
    }

    private FrequencyKeys() {}
}

