package fr.vengelis.industrialfacilities.access.ui;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.KeypassData;
import fr.vengelis.industrialfacilities.access.KeypassTerminalEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class KeypassMenus {
    public static final String KEY="gui.industrial_facilities.keypass.";
    public static MenuType<KeypassMenu> TYPE;
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.MENU,helper->{TYPE=IMenuTypeExtension.create(KeypassMenu::new);helper.register(AccessMenus.id("keypass"),TYPE);});}
    public static void open(ServerPlayer player,KeypassTerminalEntity terminal){
        if(!KeypassData.validDevice(player,terminal)||player.distanceToSqr(terminal.getBlockPos().getCenter())>64)return;
        if(terminal.manager()&&!terminal.isOwner(player)){player.displayClientMessage(Component.translatable(KEY+"link_denied"),true);return;}
        player.openMenu(new SimpleMenuProvider((id,inv,p)->new KeypassMenu(id,inv,terminal),Component.translatable(KEY+(terminal.manager()?"manager":"reader"))),
            b->{b.writeBoolean(terminal.manager());b.writeBoolean(terminal.isOwner(player));});
    }
    private KeypassMenus(){}
}
