package fr.vengelis.industrialfacilities.item;

import java.util.List;

import fr.vengelis.industrialfacilities.access.AccessBadges;
import fr.vengelis.industrialfacilities.access.AccessDoorService;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import fr.vengelis.industrialfacilities.access.ui.BadgeWalletMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public final class BadgeWalletItem extends Item {
    public static final int CAPACITY = 10;
    public static final String KEY = "gui.industrial_facilities.badge_wallet.";
    public static final String DENIED = KEY + "denied";

    public BadgeWalletItem() {
        super(new Properties().stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    public static boolean accepts(ItemStack stack) {
        return stack.getCount() == 1 && AccessBadges.isBadge(stack);
    }

    public static NonNullList<ItemStack> contents(ItemStack wallet) {
        var badges = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        wallet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(badges);
        badges.replaceAll(badge -> accepts(badge) ? badge : ItemStack.EMPTY);
        return badges;
    }

    public static AccessBadges.Result activate(Player player, AccessTerminalEntity terminal, ItemStack wallet) {
        var badges = contents(wallet);
        var result = AccessDoorService.activateFromWallet(player, terminal, badges);
        wallet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(badges));
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wallet = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server && wallet.getCount() == 1) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : BadgeWalletMenu.OFFHAND_SLOT;
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new BadgeWalletMenu(id, inventory, slot, wallet),
                Component.translatable(KEY + "title")), buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(wallet, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        long count = contents(stack).stream().filter(badge -> !badge.isEmpty()).count();
        lines.add(Component.translatable(KEY + "capacity", count, CAPACITY));
        lines.add(Component.translatable(KEY + "hint"));
    }
}
