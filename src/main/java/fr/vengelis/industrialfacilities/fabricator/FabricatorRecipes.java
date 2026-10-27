package fr.vengelis.industrialfacilities.fabricator;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Independent event registration survives regeneration of MCreator's init classes. */
@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class FabricatorRecipes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "fabricator");
    public static final RecipeType<FabricatorRecipe> TYPE = new RecipeType<>() {
        @Override public String toString() { return ID.toString(); }
    };
    public static final FabricatorRecipe.Serializer SERIALIZER = new FabricatorRecipe.Serializer();
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> helper.register(ID, TYPE));
        event.register(Registries.RECIPE_SERIALIZER, helper -> helper.register(ID, SERIALIZER));
    }
    public static List<RecipeHolder<FabricatorRecipe>> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(TYPE).stream()
            .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
    }
    private FabricatorRecipes() {}
}
