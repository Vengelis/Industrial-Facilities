package fr.vengelis.industrialfacilities.access;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public class AccessBadgeItem extends Item {
    public AccessBadgeItem(){super(new Item.Properties().stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,context,lines,flag);lines.addAll(AccessBadges.describe(stack));}
}
