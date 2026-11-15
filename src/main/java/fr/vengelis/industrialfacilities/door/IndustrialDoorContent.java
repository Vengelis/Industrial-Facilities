package fr.vengelis.industrialfacilities.door;

import java.util.List;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.IndustrialDoorBlastControllerBlock;
import fr.vengelis.industrialfacilities.block.IndustrialDoorBlastFrameBlock;
import fr.vengelis.industrialfacilities.block.IndustrialDoorFireControllerBlock;
import fr.vengelis.industrialfacilities.block.IndustrialDoorFireFrameBlock;
import fr.vengelis.industrialfacilities.block.IndustrialDoorMeshControllerBlock;
import fr.vengelis.industrialfacilities.block.IndustrialDoorMeshFrameBlock;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class IndustrialDoorContent {
    private static final ResourceLocation FIRE_FRAME = id("industrial_door_fire_frame");
    private static final ResourceLocation FIRE_CONTROLLER = id("industrial_door_fire_controller");
    private static final ResourceLocation BLAST_FRAME = id("industrial_door_blast_frame");
    private static final ResourceLocation BLAST_CONTROLLER = id("industrial_door_blast_controller");
    private static final ResourceLocation MESH_FRAME = id("industrial_door_mesh_frame");
    private static final ResourceLocation MESH_CONTROLLER = id("industrial_door_mesh_controller");
    private static final List<ResourceLocation> CUSTOM_PARTS = List.of(FIRE_FRAME, FIRE_CONTROLLER, BLAST_FRAME, BLAST_CONTROLLER,
        MESH_FRAME, MESH_CONTROLLER);
    public static BlockEntityType<IndustrialDoorEntity> TYPE;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerCustomDoors(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            if (!BuiltInRegistries.BLOCK.containsKey(FIRE_FRAME)) helper.register(FIRE_FRAME, new IndustrialDoorFireFrameBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(FIRE_CONTROLLER)) helper.register(FIRE_CONTROLLER, new IndustrialDoorFireControllerBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(BLAST_FRAME)) helper.register(BLAST_FRAME, new IndustrialDoorBlastFrameBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(BLAST_CONTROLLER)) helper.register(BLAST_CONTROLLER, new IndustrialDoorBlastControllerBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(MESH_FRAME)) helper.register(MESH_FRAME, new IndustrialDoorMeshFrameBlock());
            if (!BuiltInRegistries.BLOCK.containsKey(MESH_CONTROLLER)) helper.register(MESH_CONTROLLER, new IndustrialDoorMeshControllerBlock());
        });
        event.register(Registries.ITEM, helper -> {
            for (var id : CUSTOM_PARTS) {
                if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    var properties = new Item.Properties();
                    if (id.equals(FIRE_FRAME) || id.equals(FIRE_CONTROLLER)) properties.fireResistant();
                    helper.register(id, new BlockItem(BuiltInRegistries.BLOCK.get(id), properties));
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void addCustomDoorsToCreative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(id("industrial_facilities"))) return;
        for (var id : CUSTOM_PARTS) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            if (!event.getParentEntries().contains(stack)) event.accept(stack);
        }
    }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            TYPE = BlockEntityType.Builder.of(IndustrialDoorEntity::new,
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_PART.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RUSTY_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RUSTY_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_GLASS_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_GLASS_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RESTRICTED_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RADIATION_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RADIATION_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_SAFE_CONTROLLER.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_SAFE_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_RESTRICTED_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_DARK_FRAME.get(),
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_DARK_CONTROLLER.get(),
                BuiltInRegistries.BLOCK.get(FIRE_FRAME),
                BuiltInRegistries.BLOCK.get(FIRE_CONTROLLER),
                BuiltInRegistries.BLOCK.get(BLAST_FRAME),
                BuiltInRegistries.BLOCK.get(BLAST_CONTROLLER),
                BuiltInRegistries.BLOCK.get(MESH_FRAME),
                BuiltInRegistries.BLOCK.get(MESH_CONTROLLER)).build(null);
            helper.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "industrial_door"), TYPE);
        });
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, path);
    }
    private IndustrialDoorContent() {}
}
