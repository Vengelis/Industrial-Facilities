package fr.vengelis.industrialfacilities.world.inventory;

import fr.vengelis.industrialfacilities.block.entity.FabricatorBlockEntity;
import fr.vengelis.industrialfacilities.fabricator.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class FabricatorGUIMenu extends AbstractContainerMenu {
    public final Inventory inventory;
    public final BlockPos position;
    private final FabricatorBlockEntity machine;
    public static final int CRAFT_ONE = 0, CRAFT_SIXTEEN = 1, CRAFT_STACK = 2;

    public FabricatorGUIMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos());
    }
    public FabricatorGUIMenu(int id, Inventory inventory, BlockPos position) {
        super(IndustrialFacilitiesModMenus.FABRICATOR_GUI.get(), id);
        this.inventory = inventory;
        this.position = position;
        machine = inventory.player.level().getBlockEntity(position) instanceof FabricatorBlockEntity be ? be : null;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 139 + col * 18, 224 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 139 + col * 18, 282));
    }
    /** Slot indices and containers stay identical; only client drawing coordinates change. */
    public void layoutClientSlots(FabricatorLayout layout) {
        if (!inventory.player.level().isClientSide()) return;
        for (int index = 0; index < slots.size(); index++) {
            Slot old = slots.get(index);
            int col = index < 27 ? index % 9 : index - 27;
            int y = index < 27 ? layout.inventoryY() + index / 9 * 18 : layout.hotbarY();
            int x = layout.inventoryX() + col * 18;
            if (old.x == x && old.y == y) continue;
            Slot updated = new Slot(inventory, old.getContainerSlot(), x, y);
            updated.index = old.index;
            slots.set(index, updated);
        }
    }
    public List<RecipeHolder<FabricatorRecipe>> recipes() { return FabricatorRecipes.all(inventory.player.level()); }
    public FabricatorCrafting.Status status(FabricatorRecipe recipe, int batches) {
        return FabricatorCrafting.plan(inventory, recipe, batches).status();
    }
    /** Only called from our bounded, server-authoritative payload handler. */
    public void fabricate(Player player, net.minecraft.resources.ResourceLocation recipeId, int batches) {
        if (player.level().isClientSide() || player.containerMenu != this || !stillValid(player)
            || (batches != 1 && batches != 16 && batches != 64)) return;
        var holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof FabricatorRecipe recipe)) return;
        if (FabricatorCrafting.craft(inventory, recipe, batches)) {
            player.level().playSound(null, position, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, .7f, .8f);
            broadcastChanges();
        }
    }
    @Override public boolean stillValid(Player player) {
        var level = player.level();
        if (level.isClientSide()) return true;
        return player == inventory.player && player.isAlive() && level.hasChunkAt(position)
            && machine != null && !machine.isRemoved() && level.getBlockEntity(position) == machine
            && level.getBlockState(position).is(IndustrialFacilitiesModBlocks.FABRICATOR.get())
            && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 27 ? !moveItemStackTo(stack, 27, 36, false) : !moveItemStackTo(stack, 0, 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
