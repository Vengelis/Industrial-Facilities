package fr.vengelis.industrialfacilities.access.ui;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
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

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class AccessMenus {
    public static MenuType<AccessMenu> TYPE;
    public static MenuType<BadgeBankMenu> BANK_TYPE;
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,path);}
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.MENU,helper->{
            TYPE=IMenuTypeExtension.create(AccessMenu::new);helper.register(id("terminal_control"),TYPE);
            BANK_TYPE=IMenuTypeExtension.create(BadgeBankMenu::new);helper.register(id("badge_bank"),BANK_TYPE);
        });
    }
    public static void open(Player player,AccessTerminalEntity terminal){
        if(!(player instanceof ServerPlayer server)||terminal.getLevel()!=player.level()
            ||AccessTerminalEntity.resolve(player.level(),terminal.getBlockPos())!=terminal)return;
        if(terminal.bank){
            server.openMenu(new SimpleMenuProvider((id,inventory,p)->new BadgeBankMenu(id,inventory,terminal),
                Component.translatable("gui.industrial_facilities.bank.title")),buffer->BadgeBankMenu.writeOpening(buffer,terminal));
            return;
        }
        server.openMenu(new SimpleMenuProvider((id,inventory,p)->new AccessMenu(id,inventory,terminal),
            Component.translatable("gui.industrial_facilities.access."+(terminal.encoder?"encoder":"reader"))),
            buffer->AccessMenu.writeOpening(buffer,terminal));
    }
    public static void openDoor(Player player,IndustrialDoorEntity door){
        if(!(player instanceof ServerPlayer server)||door.getLevel()!=player.level()||door.size<=0)return;
        server.openMenu(new SimpleMenuProvider((id,inventory,p)->new AccessMenu(id,inventory,door),
            Component.translatable("gui.industrial_facilities.access.door")),buffer->AccessMenu.writeOpening(buffer,door));
    }
    private AccessMenus(){}
}
