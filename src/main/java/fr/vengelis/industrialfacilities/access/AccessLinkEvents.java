package fr.vengelis.industrialfacilities.access;

import java.util.UUID;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.item.TerminalLinkCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class AccessLinkEvents {
    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event){
        var stack=event.getEntity().getMainHandItem();if(!(stack.getItem() instanceof TerminalLinkCardItem))return;
        // Cancel START/STOP/ABORT on both sides: the setup tool must never mine a terminal or door.
        event.setCanceled(true);
        if(event.getLevel().isClientSide||event.getAction()!=PlayerInteractEvent.LeftClickBlock.Action.START||!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive()
            ||!player.level().hasChunkAt(event.getPos())||player.distanceToSqr(event.getPos().getCenter())>64)return;
        if(TerminalLinkCardItem.automationMode(stack)){
            var result=fr.vengelis.industrialfacilities.security.CameraLinking.click(player,event.getPos(),stack);
            player.displayClientMessage(Component.translatable(result.message()),true);player.getInventory().setChanged();player.containerMenu.broadcastChanges();return;
        }
        var result=accessClick(player,event.getPos(),stack);
        player.displayClientMessage(result.message().startsWith("gui.industrial_facilities.keypass.")?Component.translatable(result.message()):Component.literal(result.message()),true);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
    }
    static AccessBadges.Result accessClick(ServerPlayer player,BlockPos pos,ItemStack stack){
        var terminal=AccessTerminalEntity.resolve(player.level(),pos);
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(terminal!=null){
            if(terminal instanceof KeypassTerminalEntity kp&&kp.manager()){
                if(!kp.isOwner(player)||!KeypassData.validDevice(player,kp))return keypass("link_denied");
                CustomData.update(DataComponents.CUSTOM_DATA,stack,data->{data.remove("AccessReader");data.putUUID("KeypassManager",kp.instanceId);data.putLong("KeypassManagerPos",kp.getBlockPos().asLong());data.putString("KeypassDimension",player.level().dimension().location().toString());});
                return keypass("manager_selected");
            }
            if(terminal.encoder||terminal.bank)return AccessBadges.Result.fail("La carte de liaison se configure sur un terminal lecteur.");
            if(!terminal.isOwner(player))return AccessBadges.Result.fail("Seul le créateur du terminal peut le lier.");
            if(player.isShiftKeyDown()&&tag.hasUUID("KeypassManager")){
                if(!(terminal instanceof KeypassTerminalEntity reader)||!tag.getString("KeypassDimension").equals(player.level().dimension().location().toString()))return keypass("link_denied");
                BlockPos managerPos=BlockPos.of(tag.getLong("KeypassManagerPos"));
                if(!TerminalLinks.loaded(player.serverLevel(),managerPos)||!(AccessTerminalEntity.resolve(player.level(),managerPos) instanceof KeypassTerminalEntity manager)
                    ||!manager.instanceId.equals(tag.getUUID("KeypassManager")))return keypass("link_denied");
                return keypass(KeypassData.get(player.serverLevel()).link(player,manager,reader));
            }
            if(player.isShiftKeyDown())return tag.hasUUID("AccessReader")?AccessDoorService.linkReaders(player,tag.getUUID("AccessReader"),terminal)
                :AccessBadges.Result.fail("Sélectionne d'abord un lecteur avec un clic gauche sans Maj.");
            UUID id=terminal.ensureCode();CustomData.update(DataComponents.CUSTOM_DATA,stack,data->{data.remove("KeypassManager");data.remove("KeypassManagerPos");data.remove("KeypassDimension");data.putUUID("AccessReader",id);});
            return AccessBadges.Result.success("Lecteur sélectionné. Clic gauche sur une porte, ou Maj + clic gauche sur un autre lecteur.");
        }
        var door=AccessDoorService.resolveDoor(player.level(),pos);
        return door==null?AccessBadges.Result.fail("Sélectionne un lecteur assemblé, puis une porte ou un autre lecteur avec Maj.")
            :!tag.hasUUID("AccessReader")?AccessBadges.Result.fail("Sélectionnez d'abord un terminal lecteur avec la carte.")
            :AccessDoorService.link(player,tag.getUUID("AccessReader"),door);
    }
    private static AccessBadges.Result keypass(String suffix){String key="gui.industrial_facilities.keypass."+suffix;return suffix.equals("linked")||suffix.equals("manager_selected")?AccessBadges.Result.success(key):AccessBadges.Result.fail(key);}
    private AccessLinkEvents(){}
}
