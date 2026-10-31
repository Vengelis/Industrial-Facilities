package fr.vengelis.industrialfacilities.alarm;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class AlarmContent {
    public static BlockEntityType<AlarmBlockEntity> TYPE;
    public static MenuType<AlarmMenu> MENU;
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, path); }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            TYPE = BlockEntityType.Builder.of(AlarmBlockEntity::new, IndustrialFacilitiesModBlocks.ALARM.get()).build(null);
            helper.register(id("alarm"), TYPE);
        });
        event.register(Registries.MENU, helper -> {
            MENU = IMenuTypeExtension.create(AlarmMenu::new);
            helper.register(id("alarm"), MENU);
        });
    }
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(AlarmSettings.TYPE, AlarmSettings.CODEC, (data, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof AlarmMenu menu && menu.containerId == data.menuId()
                && menu.stillValid(context.player())) menu.apply(data.soundId(), data.range(), context.player());
        }));
        // The client receiver is registered from client-only code. No client classes load on servers.
        r.playToClient(AlarmSignal.TYPE, AlarmSignal.CODEC, (data, context) -> context.enqueueWork(() -> AlarmSignal.receiver.accept(data)));
    }
    private AlarmContent() {}
}
