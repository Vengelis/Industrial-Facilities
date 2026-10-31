package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class IndustrialDoorContent {
    public static BlockEntityType<IndustrialDoorEntity> TYPE;
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
                IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_DARK_CONTROLLER.get()).build(null);
            helper.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "industrial_door"), TYPE);
        });
    }
    private IndustrialDoorContent() {}
}
