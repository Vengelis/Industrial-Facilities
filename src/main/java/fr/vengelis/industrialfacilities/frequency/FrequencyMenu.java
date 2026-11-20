package fr.vengelis.industrialfacilities.frequency;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class FrequencyMenu extends AbstractContainerMenu {
    public static final int WIDTH = 244, HEIGHT = 270;
    public static final int INVENTORY_Y = 186;
    public final int deviceSlots;
    private final SimpleContainer chips;
    private final FrequencyEndpoint endpoint;
    private final ServerLevel level;
    private final BlockPos anchor;
    private final Inventory inventory;

    public FrequencyMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, data.readUnsignedByte(), null, null, BlockPos.ZERO);
    }

    private FrequencyMenu(int id, Inventory inventory, int count,
                          ServerLevel level, FrequencyEndpoint endpoint, BlockPos anchor) {
        super(FrequencyContent.MENU, id);
        this.deviceSlots = count;
        this.endpoint = endpoint;
        this.level = level;
        this.anchor = anchor;
        this.inventory = inventory;
        this.chips = endpoint == null ? new SimpleContainer(count) : FrequencyNetwork.get(level).open(level, endpoint);
        for (int i = 0; i < count; i++) {
            addSlot(new Slot(chips, i, slotX(count, i), SLOT_Y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if (!stillValid(inventory.player) || !stack.is(FrequencyContent.CHIP.get())) return false;
                    FrequencyChip chip = FrequencyChip.read(stack);
                    return chip != null && (endpoint == null || chip.permits(endpoint.owner()));
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 42 + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 42 + col * 18, INVENTORY_Y + 58));
    }

    private static final int SLOT_Y = 57;

    private static int slotX(int count, int slot) {
        return (WIDTH - count * 20) / 2 + slot * 20 + 2;
    }

    public void layoutInventory(int y) {
        if (level != null) return;
        for (int i = deviceSlots; i < slots.size(); i++) {
            int index = i - deviceSlots;
            var old = slots.get(i);
            var updated = new Slot(inventory, old.getContainerSlot(), old.x, y + (index < 27 ? index / 9 * 18 : 58));
            updated.index = old.index;
            slots.set(i, updated);
        }
    }

    public static void openDevice(ServerPlayer player, FrequencyEndpoint endpoint, BlockPos clicked) {
        if (!player.getUUID().equals(endpoint.owner()) || player.isSpectator() || !player.isAlive()) {
            player.displayClientMessage(Component.translatable(FrequencyContent.UI + "owner_only"), true);
            return;
        }
        int size = endpoint.slots();
        var level = player.serverLevel();
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
            new FrequencyMenu(id, inventory, size, level, endpoint, clicked),
            Component.translatable(FrequencyContent.UI + (size > 1 ? "short_title" : "slots"))),
            buf -> buf.writeByte(size));
    }

    @Override public boolean stillValid(Player player) {
        if (level == null) return true;
        if (player.level() != level || !player.isAlive() || player.isSpectator() || player.distanceToSqr(anchor.getCenter()) > 64) return false;
        return player.getUUID().equals(endpoint.owner()) && endpoint.online(level);
    }

    @Override public boolean clickMenuButton(Player player, int slot) {
        if (level == null || !stillValid(player) || slot < 0 || slot >= deviceSlots) return false;
        ItemStack stack = chips.getItem(slot);
        FrequencyChip chip = FrequencyChip.read(stack);
        if (chip == null || !chip.permits(endpoint.owner())) return false;
        chips.setItem(slot, FrequencyChip.withMode(stack, FrequencyChip.mode(stack).next()));
        broadcastChanges();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < deviceSlots) {
            if (!moveItemStackTo(stack, deviceSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, deviceSlots, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (level != null) FrequencyNetwork.get(level).close(endpoint, chips);
    }
}

