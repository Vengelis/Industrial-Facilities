package fr.vengelis.industrialfacilities.panel;

import fr.vengelis.industrialfacilities.block.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid="industrial_facilities")
public final class PanelContent {
    public static BlockEntityType<PanelBlockEntity> TYPE;
    public static MenuType<PanelMenu> MENU;
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("industrial_facilities", path); }
    public static Block block() { return BuiltInRegistries.BLOCK.get(id("luminous_panel")); }
    public static Block thinBlock() { return BuiltInRegistries.BLOCK.get(id("luminous_panel_thin")); }
    public static Block surfaceBlock() { return BuiltInRegistries.BLOCK.get(id("luminous_panel_surface")); }
    public static java.util.List<Block> panels() { return java.util.List.of(block(),thinBlock(),surfaceBlock()); }
    // MCreator may regenerate its init classes from an older in-memory workspace.
    // Run after its DeferredRegisters: keep its native entry when present, otherwise
    // provide exactly one equivalent entry without referencing generated Java fields.
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            if (!BuiltInRegistries.BLOCK.containsKey(id("luminous_panel"))) helper.register(id("luminous_panel"), new LuminousPanelBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(id("luminous_panel_thin"))) helper.register(id("luminous_panel_thin"), new LuminousPanelThinBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(id("luminous_panel_surface"))) helper.register(id("luminous_panel_surface"), new LuminousPanelSurfaceBlock());
        });
        event.register(Registries.ITEM, helper -> {
            if (!BuiltInRegistries.ITEM.containsKey(id("luminous_panel"))) helper.register(id("luminous_panel"), new BlockItem(block(), new Item.Properties()));
            if (!BuiltInRegistries.ITEM.containsKey(id("luminous_panel_thin"))) helper.register(id("luminous_panel_thin"), new BlockItem(thinBlock(), new Item.Properties()));
            if (!BuiltInRegistries.ITEM.containsKey(id("luminous_panel_surface"))) helper.register(id("luminous_panel_surface"), new BlockItem(surfaceBlock(), new Item.Properties()));
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            TYPE = BlockEntityType.Builder.of(PanelBlockEntity::new, panels().toArray(Block[]::new)).build(null);
            helper.register(id("luminous_panel"), TYPE);
        });
        event.register(Registries.MENU, helper -> {
            MENU = IMenuTypeExtension.create(PanelMenu::new); helper.register(id("luminous_panel"), MENU);
        });
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(id("industrial_facilities")))
            for (Block b:panels()) if (event.getParentEntries().stream().noneMatch(stack -> stack.is(b.asItem()))) event.accept(b);
    }
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(PanelSettings.TYPE, PanelSettings.CODEC,
            (data, context) -> context.enqueueWork(() -> {
                if (context.player().containerMenu instanceof PanelMenu menu && menu.containerId == data.menuId()
                    && menu.stillValid(context.player())) menu.apply(data.text(), data.toRight(), context.player());
            }));
    }
    private PanelContent() {}
}
