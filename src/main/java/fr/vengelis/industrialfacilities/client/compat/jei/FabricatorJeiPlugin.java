package fr.vengelis.industrialfacilities.client.compat.jei;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.fabricator.FabricatorRecipe;
import fr.vengelis.industrialfacilities.fabricator.FabricatorRecipes;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public final class FabricatorJeiPlugin implements IModPlugin {
    static final RecipeType<RecipeHolder<FabricatorRecipe>> TYPE = RecipeType.createRecipeHolderType(FabricatorRecipes.ID);
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FabricatorRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(TYPE, FabricatorRecipes.all(level));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(TYPE, IndustrialFacilitiesModBlocks.FABRICATOR.get());
    }
}
