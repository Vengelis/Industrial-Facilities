package fr.vengelis.industrialfacilities.access.ui;

import fr.vengelis.industrialfacilities.item.BadgeWalletItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public final class BadgeWalletMenu extends AbstractContainerMenu {
    public static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;
    private static final int CAPACITY = BadgeWalletItem.CAPACITY;

    private final Inventory inventory;
    private final int walletSlot;
    private final ItemStack wallet;
    private ItemContainerContents lastSaved;
    private final SimpleContainer badges = new SimpleContainer(CAPACITY);

    public BadgeWalletMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt(), ItemStack.EMPTY);
    }

    public BadgeWalletMenu(int id, Inventory inventory, int walletSlot, ItemStack wallet) {
        super(AccessMenus.WALLET_TYPE, id);
        this.inventory = inventory;
        this.walletSlot = walletSlot;
        this.wallet = wallet;
        lastSaved = wallet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (!inventory.player.level().isClientSide()) {
            var stored = BadgeWalletItem.contents(wallet);
            for (int i = 0; i < CAPACITY; i++) badges.setItem(i, stored.get(i));
            badges.addListener(container -> save());
        }
        for (int i = 0; i < CAPACITY; i++) {
            addSlot(new Slot(badges, i, 44 + i % 5 * 18, 25 + i / 5 * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return BadgeWalletItem.accepts(stack); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addPlayerSlot(9 + row * 9 + col, 8 + col * 18, 82 + row * 18);
        }
        for (int col = 0; col < 9; col++) addPlayerSlot(col, 8 + col * 18, 140);
    }

    private void addPlayerSlot(int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPickup(Player player) { return index != walletSlot; }
            @Override public boolean mayPlace(ItemStack stack) { return index != walletSlot; }
        });
    }

    private void save() {
        if (!stillValid(inventory.player)) return;
        var stored = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        for (int i = 0; i < CAPACITY; i++) stored.set(i, badges.getItem(i));
        var next = ItemContainerContents.fromItems(stored);
        wallet.set(DataComponents.CONTAINER, next);
        lastSaved = next;
        inventory.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return player == inventory.player && player.isAlive() && inventory.getItem(walletSlot) == wallet
            && wallet.getCount() == 1 && wallet.getItem() instanceof BadgeWalletItem
            && (walletSlot == OFFHAND_SLOT || inventory.selected == walletSlot)
            && lastSaved.equals(wallet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || type == ClickType.SWAP && button == walletSlot) return;
        if (slot >= CAPACITY && slot < slots.size() && slots.get(slot).getContainerSlot() == walletSlot) return;
        super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CAPACITY) {
            if (!moveItemStackTo(stack, CAPACITY, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!BadgeWalletItem.accepts(stack) || !moveItemStackTo(stack, 0, CAPACITY, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
