package fr.vengelis.industrialfacilities.fabricator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.util.GsonHelper;

/** Cosmetic categories. Recipe membership is synced by the recipe serializer. */
public record FabricatorCategoryConfig(String allLabel, String uncategorizedLabel, List<Category> categories) {
    public record Category(String id, String label, int order) {}
    public FabricatorCategoryConfig { categories = List.copyOf(categories); }
    public static boolean validId(String id) { return id != null && id.matches("[a-z0-9_./-]{1,64}"); }
    public static FabricatorCategoryConfig defaults() {
        return new FabricatorCategoryConfig("Toutes", "Autres", List.of(
            new Category("panels", "Panneaux", 10), new Category("blocks", "Blocs", 20), new Category("machines", "Machines", 30)));
    }
    public static FabricatorCategoryConfig parse(JsonElement input) {
        JsonObject root = GsonHelper.convertToJsonObject(input, "categories config");
        String allLabel = GsonHelper.getAsString(root, "all_label", "Toutes");
        String otherLabel = GsonHelper.getAsString(root, "uncategorized_label", "Autres");
        if (allLabel.isBlank() || otherLabel.isBlank()) throw new IllegalArgumentException("Category labels must not be blank");
        List<Category> categories = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : GsonHelper.getAsJsonArray(root, "categories")) {
            JsonObject object = GsonHelper.convertToJsonObject(entry, "category");
            String id = GsonHelper.getAsString(object, "id");
            String label = GsonHelper.getAsString(object, "label");
            if (!validId(id) || !ids.add(id) || label.isBlank())
                throw new IllegalArgumentException("Invalid or duplicate category: " + id);
            categories.add(new Category(id, label, GsonHelper.getAsInt(object, "order", categories.size())));
        }
        categories.sort(Comparator.comparingInt(Category::order));
        return new FabricatorCategoryConfig(allLabel, otherLabel, categories);
    }
    public List<Category> tabs(Collection<String> recipeCategories) {
        List<Category> result = new ArrayList<>();
        result.add(new Category("*", allLabel, Integer.MIN_VALUE));
        result.addAll(categories);
        Set<String> configured = new HashSet<>();
        for (Category category : result) configured.add(category.id());
        recipeCategories.stream().distinct().sorted().filter(id -> !configured.contains(id)).forEach(id ->
            result.add(new Category(id, id.equals("uncategorized") ? uncategorizedLabel : id, Integer.MAX_VALUE)));
        return List.copyOf(result);
    }
}
