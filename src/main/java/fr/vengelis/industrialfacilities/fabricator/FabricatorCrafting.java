package fr.vengelis.industrialfacilities.fabricator;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Simule la fabrication dans une copie de l'inventaire avant de consommer les matériaux. */
public final class FabricatorCrafting {
    // Les 36 cases principales excluent l'armure et la seconde main.
    private static final int STORAGE_SLOTS = 36;
    private static final int MAX_BATCHES = 64;

    public enum Status { READY, MISSING_MATERIALS, INVENTORY_FULL }
    public record Plan(Status status, List<ItemStack> inventory) {}

    private FabricatorCrafting() {}

    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static Plan plan(Inventory inventory, FabricatorRecipe recipe, int batches) {
        if (batches < 1 || batches > MAX_BATCHES) {
            throw new IllegalArgumentException("Invalid batch size");
        }

        List<ItemStack> after = new ArrayList<>(STORAGE_SLOTS);
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            after.add(inventory.getItem(slot).copy());
        }
        for (var material : recipe.requirements().entrySet()) {
            if (!consumeMaterial(after, material.getKey(), material.getValue() * batches)) {
                return new Plan(Status.MISSING_MATERIALS, List.of());
            }
        }

        // Les piles épuisées peuvent libérer les cases nécessaires au résultat.
        ItemStack output = recipe.output();
        if (!storeOutput(after, output, output.getCount() * batches)) {
            return new Plan(Status.INVENTORY_FULL, List.of());
        }
        return new Plan(Status.READY, after);
    }

    private static boolean consumeMaterial(List<ItemStack> stacks, Item material, int remaining) {
        for (int slot = 0; slot < stacks.size() && remaining > 0; slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.is(material)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining <= 0;
    }

    private static boolean storeOutput(List<ItemStack> stacks, ItemStack output, int remaining) {
        // Fusionner seulement les piles dont les composants d'objet correspondent aussi.
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, output)) {
                continue;
            }
            int freeSpace = Math.max(0, stack.getMaxStackSize() - stack.getCount());
            int added = Math.min(remaining, freeSpace);
            stack.grow(added);
            remaining -= added;
        }
        for (int slot = 0; slot < stacks.size() && remaining > 0; slot++) {
            if (!stacks.get(slot).isEmpty()) {
                continue;
            }
            int added = Math.min(remaining, output.getMaxStackSize());
            stacks.set(slot, output.copyWithCount(added));
            remaining -= added;
        }
        return remaining == 0;
    }

    public static boolean craft(Inventory inventory, FabricatorRecipe recipe, int batches) {
        Plan plan = plan(inventory, recipe, batches);
        if (plan.status != Status.READY) {
            return false;
        }
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            inventory.setItem(slot, plan.inventory.get(slot));
        }
        inventory.setChanged();
        return true;
    }
}
