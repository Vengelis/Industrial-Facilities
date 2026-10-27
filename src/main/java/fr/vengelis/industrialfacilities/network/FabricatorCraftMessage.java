package fr.vengelis.industrialfacilities.network;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.world.inventory.FabricatorGUIMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record FabricatorCraftMessage(int containerId, ResourceLocation recipe, int batches) implements CustomPacketPayload {
    public static final Type<FabricatorCraftMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "fabricator_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricatorCraftMessage> STREAM = StreamCodec.of(
        (buffer, message) -> { buffer.writeVarInt(message.containerId); buffer.writeResourceLocation(message.recipe); buffer.writeVarInt(message.batches); },
        buffer -> new FabricatorCraftMessage(buffer.readVarInt(), buffer.readResourceLocation(), buffer.readVarInt()));
    @Override public Type<FabricatorCraftMessage> type() { return TYPE; }

    @EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("fabricator_v1").playToServer(TYPE, STREAM, (message, context) -> context.enqueueWork(() -> {
                if (context.player().containerMenu instanceof FabricatorGUIMenu menu && menu.containerId == message.containerId)
                    menu.fabricate(context.player(), message.recipe, message.batches);
            }));
        }
    }
}
