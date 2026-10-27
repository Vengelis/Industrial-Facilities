package fr.vengelis.industrialfacilities.block.entity;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlockEntities;
import fr.vengelis.industrialfacilities.world.inventory.FabricatorGUIMenu;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Zero machine slots: ingredients and products remain in each player's inventory. */
public class FabricatorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    private NonNullList<ItemStack> items = NonNullList.create();
    public FabricatorBlockEntity(BlockPos position, BlockState state) {
        super(IndustrialFacilitiesModBlockEntities.FABRICATOR.get(), position, state);
    }
    @Override protected Component getDefaultName() { return Component.translatable("container.industrial_facilities.fabricator"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new FabricatorGUIMenu(id, inventory, worldPosition); }
    @Override public int getContainerSize() { return 0; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = NonNullList.create(); }
    @Override public boolean canPlaceItem(int index, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction side) { return new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) { return false; }
}
