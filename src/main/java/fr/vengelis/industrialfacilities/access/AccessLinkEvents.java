package fr.vengelis.industrialfacilities.access;

import java.util.UUID;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.item.TerminalLinkCardItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
        AccessBadges.Result result;var terminal=AccessTerminalEntity.resolve(player.level(),event.getPos());
        if(TerminalLinkCardItem.automationMode(stack)){
            result=fr.vengelis.industrialfacilities.security.CameraLinking.click(player,event.getPos(),stack);
            player.displayClientMessage(Component.translatable(result.message()),true);player.getInventory().setChanged();player.containerMenu.broadcastChanges();return;
        }
        if(terminal!=null){
            if(terminal.encoder||terminal.bank)result=AccessBadges.Result.fail("La carte de liaison se configure sur un terminal lecteur.");
            else if(!terminal.isOwner(player))result=AccessBadges.Result.fail("Seul le créateur du terminal peut le lier.");
            else {UUID id=terminal.ensureCode();CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putUUID("AccessReader",id));result=AccessBadges.Result.success("Terminal sélectionné : "+id+". Cliquez gauche sur une porte assemblée.");}
        }else{
            var door=AccessDoorService.resolveDoor(player.level(),event.getPos());CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            result=door==null?AccessBadges.Result.fail("Cliquez gauche sur un lecteur assemblé, puis sur une porte assemblée.")
                :!tag.hasUUID("AccessReader")?AccessBadges.Result.fail("Sélectionnez d'abord un terminal lecteur avec la carte.")
                :AccessDoorService.link(player,tag.getUUID("AccessReader"),door);
        }
        player.displayClientMessage(Component.literal(result.message()),true);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
    }
    private AccessLinkEvents(){}
}
