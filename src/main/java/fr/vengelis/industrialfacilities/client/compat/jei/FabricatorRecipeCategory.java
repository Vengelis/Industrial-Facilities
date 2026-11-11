package fr.vengelis.industrialfacilities.client.compat.jei;

import fr.vengelis.industrialfacilities.fabricator.FabricatorRecipe;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

final class FabricatorRecipeCategory implements IRecipeCategory<RecipeHolder<FabricatorRecipe>> {
    private static final int COLUMNS = 4;
    private static final int OUTPUT_X = 148;
    private static final int OUTPUT_Y = 16;
    private final IDrawable icon;
    private final IDrawable arrow;

    FabricatorRecipeCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(IndustrialFacilitiesModBlocks.FABRICATOR.get());
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<FabricatorRecipe>> getRecipeType() {
        return FabricatorJeiPlugin.TYPE;
    }

    @Override
    public Component getTitle() {
        return IndustrialFacilitiesModBlocks.FABRICATOR.get().getName();
    }

    @Override public int getWidth() { return 172; }
    @Override public int getHeight() { return 60; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<FabricatorRecipe> recipe) {
        return recipe.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FabricatorRecipe> recipe, IFocusGroup focuses) {
        // The item slots make the recipe searchable. Counts are drawn separately so totals above a stack fit.
        int i = 0;
        for (var item : recipe.value().requirements().keySet()) {
            builder.addInputSlot(inputX(i), inputY(i)).setStandardSlotBackground().addItemStack(new ItemStack(item));
            i++;
        }
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).setOutputSlotBackground()
            .addItemStack(recipe.value().output().copyWithCount(1));
    }

    @Override
    public void draw(RecipeHolder<FabricatorRecipe> recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 119, OUTPUT_Y + (16 - arrow.getHeight()) / 2);
        int i = 0;
        for (int count : recipe.value().requirements().values()) {
            drawCount(graphics, count, inputX(i), inputY(i));
            i++;
        }
        drawCount(graphics, recipe.value().output().getCount(), OUTPUT_X, OUTPUT_Y);
    }

    private static int inputX(int index) { return 8 + index % COLUMNS * 30; }
    private static int inputY(int index) { return 1 + index / COLUMNS * 30; }

    private static void drawCount(GuiGraphics graphics, int count, int x, int y) {
        var font = Minecraft.getInstance().font;
        String text = Integer.toString(count);
        graphics.drawString(font, text, x + 8 - font.width(text) / 2, y + 20, 0xff404040, false);
    }
}
