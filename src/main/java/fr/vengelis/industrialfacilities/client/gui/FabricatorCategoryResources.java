package fr.vengelis.industrialfacilities.client.gui;

import com.google.gson.JsonParser;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.fabricator.FabricatorCategoryConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class FabricatorCategoryResources extends SimplePreparableReloadListener<FabricatorCategoryConfig> {
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "fabricator/categories.json");
    private static FabricatorCategoryConfig current = FabricatorCategoryConfig.defaults();
    public static FabricatorCategoryConfig current() { return current; }
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new FabricatorCategoryResources());
    }
    @Override protected FabricatorCategoryConfig prepare(ResourceManager resources, ProfilerFiller profiler) {
        try {
            var resource = resources.getResource(FILE);
            if (resource.isEmpty()) return FabricatorCategoryConfig.defaults();
            try (var reader = resource.get().openAsReader()) { return FabricatorCategoryConfig.parse(JsonParser.parseReader(reader)); }
        } catch (Exception error) {
            IndustrialFacilitiesMod.LOGGER.error("Invalid Fabricator categories JSON; keeping the previous categories", error);
            return current;
        }
    }
    @Override protected void apply(FabricatorCategoryConfig config, ResourceManager resources, ProfilerFiller profiler) { current = config; }
}
