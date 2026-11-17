package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.block.SupervisionDisplayBlock;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "industrial_facilities")
public final class SupervisionContent {
    public static BlockEntityType<SupervisionConsoleEntity> TYPE;
    public static MenuType<SupervisionMenu> MENU;
    public static ResourceLocation id(String id) { return ResourceLocation.fromNamespaceAndPath("industrial_facilities", id); }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            TYPE = BlockEntityType.Builder.of(SupervisionConsoleEntity::new, IndustrialFacilitiesModBlocks.SUPERVISION_CONSOLE.get(),
                IndustrialFacilitiesModBlocks.SUPERVISION_DISPLAY.get()).build(null);
            registry.register(id("supervision_console"), TYPE);
        });
        event.register(Registries.MENU, registry -> {
            MENU = IMenuTypeExtension.create(SupervisionMenu::new);
            registry.register(id("supervision_console"), MENU);
        });
    }

    @SubscribeEvent public static void configureConnection(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
            || !(event.getItemStack().getItem() instanceof PortableConsoleItem)) return;
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SupervisionDisplayBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (event.getEntity() instanceof ServerPlayer player) {
                var display = SupervisionConsoleEntity.resolve(event.getLevel(), event.getPos());
                if (display == null) message(player, "display_assemble");
                else if (!player.getUUID().equals(display.placedBy)) message(player, "display_owner");
                else {
                    var source = PortableConsoleItem.resolve(player, event.getItemStack());
                    if (source == null) message(player, "display_unpaired");
                    else {
                        display.bindDisplay(player.serverLevel(), source);
                        message(player, "display_paired");
                    }
                }
            }
            return;
        }
        var terminal = AccessTerminalEntity.resolve(event.getLevel(), event.getPos());
        if (terminal == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getEntity() instanceof ServerPlayer player && terminal.isOwner(player)) {
            SupervisionMenu.openTerminal(player, terminal, event.getItemStack());
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(NetworkView.UI + key), true);
    }

    private SupervisionContent() {}
}
