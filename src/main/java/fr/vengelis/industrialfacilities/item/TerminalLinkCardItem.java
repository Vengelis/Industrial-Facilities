package fr.vengelis.industrialfacilities.item;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class TerminalLinkCardItem extends Item {
    public TerminalLinkCardItem(){super(new Item.Properties().stacksTo(1));}
    public static boolean automationMode(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("ControlMode").equals("automation");}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(!player.isShiftKeyDown())return InteractionResultHolder.pass(stack);
        if(!level.isClientSide&&hand==InteractionHand.MAIN_HAND){boolean mode=!automationMode(stack);CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("ControlMode",mode?"automation":"access"));player.displayClientMessage(Component.translatable("gui.industrial_facilities.camera."+(mode?"mode_automation":"mode_access")),true);player.getInventory().setChanged();player.containerMenu.broadcastChanges();}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("gui.industrial_facilities.camera."+(automationMode(stack)?"card_automation_hint":"card_access_hint")));
        if(!automationMode(stack))lines.add(Component.translatable("gui.industrial_facilities.access.card_peer_hint"));
        lines.add(Component.translatable("gui.industrial_facilities.camera.card_mode_hint"));
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(tag.hasUUID("AccessReader"))lines.add(Component.literal("Terminal : "+tag.getUUID("AccessReader")));
        if(automationMode(stack)&&tag.hasUUID("AutomationTerminal"))lines.add(Component.translatable("gui.industrial_facilities.camera.source_card",tag.getUUID("AutomationTerminal").toString()));
    }
}
