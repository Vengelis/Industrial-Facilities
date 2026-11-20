package fr.vengelis.industrialfacilities.item;
import java.util.List;
import fr.vengelis.industrialfacilities.frequency.FrequencyChip;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
public class TerminalLinkCardItem extends Item {
    public TerminalLinkCardItem(){super(new Item.Properties().stacksTo(64));}
    public static boolean automationMode(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("ControlMode").equals("automation");}
    @Override public Component getName(ItemStack stack) {
        var chip = FrequencyChip.read(stack);
        String name = chip == null ? "" : FrequencyContent.displayName(stack, chip);
        return name.isEmpty() ? super.getName(stack) : Component.translatable(FrequencyContent.UI + "named_chip", name);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        FrequencyContent.tooltip(stack, lines);
    }
}
