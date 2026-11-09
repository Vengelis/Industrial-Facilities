package fr.vengelis.industrialfacilities.item;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
public class TerminalLinkCardItem extends Item {
    public TerminalLinkCardItem(){super(new Item.Properties().stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.literal("Clic gauche : lecteur, puis porte assemblée."));
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(tag.hasUUID("AccessReader"))lines.add(Component.literal("Terminal : "+tag.getUUID("AccessReader")));
    }
}
