package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof fr.vengelis.industrialfacilities.block.SupervisionDisplayBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
                var level = player.serverLevel();
                var display = SupervisionConsoleEntity.resolve(level, event.getPos());
                if (display == null) display = SupervisionConsoleEntity.assemble(level, event.getPos(), player.getUUID(),
                    level.getBlockState(event.getPos()).getValue(SupervisionConsoleBlock.FACING));
                String message;
                if (display == null) message = "display_assemble";
                else if (!player.getUUID().equals(display.placedBy) || display.bounds().distanceToSqr(player.position()) > 64) message = "display_owner";
                else {
                    var connection = PortableConsoleItem.resolve(player, event.getItemStack());
                    if (connection == null) message = "display_unpaired";
                    else { display.bindDisplay(level, connection, true); message = "display_paired"; }
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(NetworkView.UI + message), true);
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

    private SupervisionContent() {}
}
