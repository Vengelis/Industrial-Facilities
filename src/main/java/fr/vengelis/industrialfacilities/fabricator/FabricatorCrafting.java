package fr.vengelis.industrialfacilities.fabricator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Build a complete inventory transaction before changing any live stack. */
public final class FabricatorCrafting {
    public enum Status { READY, MISSING_MATERIALS, INVENTORY_FULL }
    public record Plan(Status status, List<ItemStack> inventory) {}

    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < 36; i++) if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
        return total;
    }
    public static Plan plan(Inventory inventory, FabricatorRecipe recipe, int batches) {
        if (batches < 1 || batches > 64) throw new IllegalArgumentException("Invalid batch size");
        List<ItemStack> after = new ArrayList<>();
        for (int i = 0; i < 36; i++) after.add(inventory.getItem(i).copy());
        for (Map.Entry<Item, Integer> material : recipe.requirements().entrySet()) {
            int remaining = material.getValue() * batches;
            for (int slot = 0; slot < after.size() && remaining > 0; slot++) {
                ItemStack stack = after.get(slot);
                if (!stack.is(material.getKey())) continue;
                int taken = Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
            }
            if (remaining > 0) return new Plan(Status.MISSING_MATERIALS, List.of());
        }
        ItemStack output = recipe.output();
        int remaining = output.getCount() * batches;
        for (ItemStack stack : after) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, output)) continue;
            int added = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
            stack.grow(added);
            remaining -= added;
        }
        for (int slot = 0; slot < after.size() && remaining > 0; slot++) {
            if (!after.get(slot).isEmpty()) continue;
            int added = Math.min(remaining, output.getMaxStackSize());
            after.set(slot, output.copyWithCount(added));
            remaining -= added;
        }
        return remaining == 0 ? new Plan(Status.READY, after) : new Plan(Status.INVENTORY_FULL, List.of());
    }
    public static boolean craft(Inventory inventory, FabricatorRecipe recipe, int batches) {
        Plan plan = plan(inventory, recipe, batches);
        if (plan.status != Status.READY) return false;
        for (int slot = 0; slot < 36; slot++) inventory.setItem(slot, plan.inventory.get(slot));
        inventory.setChanged();
        return true;
    }
    private FabricatorCrafting() {}
}
