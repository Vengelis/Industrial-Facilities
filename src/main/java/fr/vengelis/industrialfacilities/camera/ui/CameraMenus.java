package fr.vengelis.industrialfacilities.camera.ui;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class CameraMenus {
    public static MenuType<CameraMenu> TYPE;
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, path);
    }
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.MENU, helper -> {
            TYPE = IMenuTypeExtension.create(CameraMenu::new);
            helper.register(id("camera_control"), TYPE);
        });
    }
    public static void open(Player player, CameraTerminalEntity terminal) {
        if (!(player instanceof ServerPlayer server) || terminal.getLevel() != player.level()
                || !terminal.isOwner(player)
                || AccessTerminalEntity.resolve(player.level(), terminal.getBlockPos()) != terminal)
            return;
        server.openMenu(new SimpleMenuProvider((id, inventory, p)
                                                       -> new CameraMenu(id, inventory, terminal),
                                Component.translatable("gui.industrial_facilities.camera.terminal")),
                buffer -> CameraMenu.writeOpening(buffer, CameraMenu.PROGRAM, terminal.getBlockPos()));
    }
    public static void openCamera(Player player, SecurityCameraEntity camera) {
        if (!(player instanceof ServerPlayer server) || camera.getLevel() != player.level() || camera.creator == null
                || !camera.creator.equals(player.getUUID())
                || player.level().getBlockEntity(camera.getBlockPos()) != camera)
            return;
        server.openMenu(new SimpleMenuProvider((id, inventory, p)
                                                       -> new CameraMenu(id, inventory, camera),
                                Component.translatable("gui.industrial_facilities.camera.settings")),
                buffer -> CameraMenu.writeOpening(buffer, CameraMenu.CAMERA, camera.getBlockPos()));
    }
    private CameraMenus() {}
}
