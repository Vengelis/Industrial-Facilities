package fr.vengelis.industrialfacilities.security;
import fr.vengelis.industrialfacilities.access.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
public final class CameraLinking {
    public static AccessBadges.Result click(ServerPlayer player,BlockPos pos,ItemStack card){
        if(!player.isAlive()||!player.level().hasChunkAt(pos)||player.distanceToSqr(pos.getCenter())>64)return fail("link_denied");
        var resolved=AccessTerminalEntity.resolve(player.level(),pos);
        if(resolved instanceof CameraTerminalEntity terminal){
            if(!terminal.isOwner(player))return fail("not_owner");
            CustomData.update(DataComponents.CUSTOM_DATA,card,tag->{tag.putUUID("AutomationTerminal",terminal.instanceId);tag.putLong("AutomationPos",terminal.getBlockPos().asLong());tag.putString("AutomationDimension",player.level().dimension().location().toString());});
            return AccessBadges.Result.success("gui.industrial_facilities.camera.source_selected");
        }
        CompoundTag tag=card.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID("AutomationTerminal")||!tag.contains("AutomationPos")||!tag.getString("AutomationDimension").equals(player.level().dimension().location().toString()))return fail("select_source");
        BlockPos source=BlockPos.of(tag.getLong("AutomationPos"));
        if(!player.level().hasChunkAt(source)||!(AccessTerminalEntity.resolve(player.level(),source) instanceof CameraTerminalEntity terminal)||!terminal.instanceId.equals(tag.getUUID("AutomationTerminal"))||!terminal.isOwner(player))return fail("source_unavailable");
        return terminal.link(player,pos,player.isShiftKeyDown());
    }
    private static AccessBadges.Result fail(String key){return AccessBadges.Result.fail("gui.industrial_facilities.camera."+key);}
    private CameraLinking(){}
}
