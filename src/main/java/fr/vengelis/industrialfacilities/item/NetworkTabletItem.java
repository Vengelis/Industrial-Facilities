package fr.vengelis.industrialfacilities.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Holding the tablet enables the separate, server-authorized network overlay. */
public class NetworkTabletItem extends Item {
    public NetworkTabletItem(){super(new Item.Properties().stacksTo(1));}

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.industrial_facilities.network_tablet.hold").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.industrial_facilities.network_tablet.range").withStyle(ChatFormatting.DARK_GRAY));
    }
}
