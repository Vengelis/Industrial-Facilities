package fr.vengelis.industrialfacilities.door;

import java.util.List;
import java.util.function.Supplier;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorBlastBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorDarkBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorFireBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorGlassBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorRadiationBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorRestrictedBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorRustyBlock;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorSafeBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class IndustrialMetalDoorContent {
    private record Door(ResourceLocation id, Supplier<IndustrialMetalDoorBlock> createBlock) {
        Door(String suffix, Supplier<IndustrialMetalDoorBlock> createBlock) {
            this(IndustrialMetalDoorContent.id("industrial_metal_door" + suffix), createBlock);
        }
    }

    private static final List<Door> DOORS = List.of(
        new Door("", IndustrialMetalDoorBlock::new),
        new Door("_dark", IndustrialMetalDoorDarkBlock::new),
        new Door("_restricted", IndustrialMetalDoorRestrictedBlock::new),
        new Door("_glass", IndustrialMetalDoorGlassBlock::new),
        new Door("_rusty", IndustrialMetalDoorRustyBlock::new),
        new Door("_safe", IndustrialMetalDoorSafeBlock::new),
        new Door("_radiation", IndustrialMetalDoorRadiationBlock::new),
        new Door("_fire", IndustrialMetalDoorFireBlock::new),
        new Door("_blast", IndustrialMetalDoorBlastBlock::new)
    );
    public static BlockEntityType<IndustrialLiftDoorEntity> LIFT_TYPE;
    public static BlockEntityType<IndustrialHingedDoorEntity> HINGE_TYPE;
    private static final ResourceLocation TAB = id("industrial_facilities");
    private static final ResourceLocation MULTIBLOCK_ANCHOR = id("industrial_door_radiation_controller");

    // Keep the registrations made by MCreator when present. Its generated lists can
    // omit these locked elements when regenerated from an older open workspace.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            for (Door door : DOORS) {
                if (!BuiltInRegistries.BLOCK.containsKey(door.id())) {
                    helper.register(door.id(), door.createBlock().get());
                }
            }
        });
        event.register(Registries.ITEM, helper -> {
            for (Door door : DOORS) {
                if (!BuiltInRegistries.ITEM.containsKey(door.id())) {
                    var block = BuiltInRegistries.BLOCK.get(door.id());
                    var properties = new Item.Properties();
                    if (block instanceof IndustrialMetalDoorFireBlock) properties.fireResistant();
                    helper.register(door.id(), new DoubleHighBlockItem(block, properties));
                }
            }
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            LIFT_TYPE = BlockEntityType.Builder.of(IndustrialLiftDoorEntity::new,
                BuiltInRegistries.BLOCK.get(id("industrial_metal_door_radiation")),
                BuiltInRegistries.BLOCK.get(id("industrial_metal_door_blast"))).build(null);
            helper.register(id("industrial_lift_door"), LIFT_TYPE);
            var hinges = DOORS.stream().map(door -> BuiltInRegistries.BLOCK.get(door.id()))
                .filter(block -> block instanceof IndustrialMetalDoorBlock metal && !metal.style.lifts())
                .toArray(net.minecraft.world.level.block.Block[]::new);
            HINGE_TYPE = BlockEntityType.Builder.of(IndustrialHingedDoorEntity::new, hinges).build(null);
            helper.register(id("industrial_hinged_door"), HINGE_TYPE);
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(TAB)) return;

        var anchor = new ItemStack(BuiltInRegistries.ITEM.get(MULTIBLOCK_ANCHOR));
        for (Door door : DOORS) {
            Item item = BuiltInRegistries.ITEM.get(door.id());
            var stack = new ItemStack(item);
            boolean visible = event.getParentEntries().contains(stack);
            boolean searchable = event.getSearchEntries().contains(stack);
            if (!visible && !searchable) {
                if (event.getParentEntries().contains(anchor) && event.getSearchEntries().contains(anchor)) {
                    event.insertAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                } else {
                    event.accept(stack);
                }
            } else if (!visible) {
                event.accept(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
            } else if (!searchable) {
                event.accept(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
            }
            anchor = stack;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, path);
    }

    private IndustrialMetalDoorContent() {}
}
