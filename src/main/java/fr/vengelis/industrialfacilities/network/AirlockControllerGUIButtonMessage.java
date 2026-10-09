package fr.vengelis.industrialfacilities.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.SectionPos;

import fr.vengelis.industrialfacilities.procedures.AirlockTogglePlayerDetectionProcedure;
import fr.vengelis.industrialfacilities.procedures.AirlockDetectStructureProcedure;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

@EventBusSubscriber
public record AirlockControllerGUIButtonMessage(int buttonID, int x, int y, int z) implements CustomPacketPayload {
	public static final Type<AirlockControllerGUIButtonMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "airlock_controller_gui_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AirlockControllerGUIButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, AirlockControllerGUIButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
	}, (RegistryFriendlyByteBuf buffer) -> new AirlockControllerGUIButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt()));

	@Override
	public Type<AirlockControllerGUIButtonMessage> type() {
		return TYPE;
	}

	public static void handleData(final AirlockControllerGUIButtonMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> handleButtonAction(context.player(), message.buttonID, message.x, message.y, message.z)).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}

    public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z) {
        Level world=entity.level();
        var menu=fr.vengelis.industrialfacilities.airlock.AirlockGuiSupport.menu(world,x,y,z,entity);
        if (menu==null) return;
        switch (buttonID) {
            case 0 -> AirlockTogglePlayerDetectionProcedure.execute(world,x,y,z,entity);
            case 1 -> fr.vengelis.industrialfacilities.procedures.AirlockToggleRedstoneProcedure.execute(world,x,y,z,entity);
            case 2 -> fr.vengelis.industrialfacilities.procedures.AirlockAddPlayerProcedure.execute(world,x,y,z,entity);
            case 3 -> AirlockDetectStructureProcedure.execute(world,x,y,z,entity);
            case 4 -> fr.vengelis.industrialfacilities.procedures.AirlockToggleAccessRestrictionProcedure.execute(world,x,y,z,entity);
            case 5 -> fr.vengelis.industrialfacilities.procedures.AirlockToggleInvertSelectionProcedure.execute(world,x,y,z,entity);
            case 6 -> fr.vengelis.industrialfacilities.procedures.AirlockRemovePlayerProcedure.execute(world,x,y,z,entity);
            default -> { return; }
        }
        menu.refreshState();
    }
	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		IndustrialFacilitiesMod.addNetworkMessage(AirlockControllerGUIButtonMessage.TYPE, AirlockControllerGUIButtonMessage.STREAM_CODEC, AirlockControllerGUIButtonMessage::handleData);
	}
}
