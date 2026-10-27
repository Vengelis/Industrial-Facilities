package fr.vengelis.industrialfacilities.fabricator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A list of counted items, deliberately independent of crafting-grid positions. */
public final class FabricatorRecipe implements Recipe<RecipeInput> {
    public record Material(Item item, int count) {
        public static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Material::item),
            Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Material::count)
        ).apply(instance, Material::new));
    }
    private static final Codec<List<Material>> MATERIALS = Material.CODEC.listOf().validate(list ->
        list.isEmpty() || list.size() > 8 || list.stream().anyMatch(m -> m.item() == net.minecraft.world.item.Items.AIR)
            ? DataResult.error(() -> "Fabricator requires 1 to 8 non-air materials") : DataResult.success(list));
    private static final Codec<String> CATEGORY = Codec.STRING.validate(value -> FabricatorCategoryConfig.validId(value)
        ? DataResult.success(value) : DataResult.error(() -> "Invalid Fabricator category id: " + value));
    public static final MapCodec<FabricatorRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        MATERIALS.fieldOf("materials").forGetter(FabricatorRecipe::materials),
        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
        CATEGORY.optionalFieldOf("category", "uncategorized").forGetter(FabricatorRecipe::category)
    ).apply(instance, FabricatorRecipe::new));

    private final List<Material> materials;
    private final ItemStack result;
    private final String category;
    public FabricatorRecipe(List<Material> materials, ItemStack result) {
        this(materials, result, "uncategorized");
    }
    public FabricatorRecipe(List<Material> materials, ItemStack result, String category) {
        this.materials = List.copyOf(materials);
        this.result = result.copy();
        if (!FabricatorCategoryConfig.validId(category)) throw new IllegalArgumentException("Invalid category id");
        this.category = category;
    }
    public String category() { return category; }
    public List<Material> materials() { return materials; }
    public ItemStack output() { return result.copy(); }
    public Map<Item, Integer> requirements() {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (Material material : materials) totals.merge(material.item(), material.count(), Integer::sum);
        return totals;
    }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return output(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return output(); }
    @Override public RecipeSerializer<?> getSerializer() { return FabricatorRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return FabricatorRecipes.TYPE; }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<FabricatorRecipe> {
        private static final StreamCodec<RegistryFriendlyByteBuf, FabricatorRecipe> STREAM = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeVarInt(recipe.materials.size());
                for (Material material : recipe.materials) {
                    buffer.writeResourceLocation(BuiltInRegistries.ITEM.getKey(material.item()));
                    buffer.writeVarInt(material.count());
                }
                ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                buffer.writeUtf(recipe.category, 64);
            }, buffer -> {
                int size = buffer.readVarInt();
                if (size < 1 || size > 8) throw new IllegalArgumentException("Invalid Fabricator material count");
                var materials = new java.util.ArrayList<Material>();
                for (int i = 0; i < size; i++) {
                    Item item = BuiltInRegistries.ITEM.get(buffer.readResourceLocation());
                    int count = buffer.readVarInt();
                    if (item == net.minecraft.world.item.Items.AIR || count < 1 || count > 4096)
                        throw new IllegalArgumentException("Invalid Fabricator material");
                    materials.add(new Material(item, count));
                }
                ItemStack output = ItemStack.STREAM_CODEC.decode(buffer);
                return new FabricatorRecipe(materials, output, buffer.readUtf(64));
            });
        @Override public MapCodec<FabricatorRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, FabricatorRecipe> streamCodec() { return STREAM; }
    }
}
