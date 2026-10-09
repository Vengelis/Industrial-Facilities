/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import fr.vengelis.industrialfacilities.block.entity.ExtinguisherPlacedBlockEntity;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

@EventBusSubscriber
public class IndustrialFacilitiesModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, IndustrialFacilitiesMod.MODID);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtinguisherPlacedBlockEntity>> EXTINGUISHER_PLACED = register("extinguisher_placed", IndustrialFacilitiesModBlocks.EXTINGUISHER_PLACED, ExtinguisherPlacedBlockEntity::new);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirlockControllerBlockEntity>> AIRLOCK_CONTROLLER = register("airlock_controller", IndustrialFacilitiesModBlocks.AIRLOCK_CONTROLLER, AirlockControllerBlockEntity::new);

	// Start of user code block custom block entities
	// End of user code block custom block entities
	private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String registryname, DeferredHolder<Block, Block> block, BlockEntityType.BlockEntitySupplier<T> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}

	@SubscribeEvent
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, EXTINGUISHER_PLACED.get(), SidedInvWrapper::new);
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AIRLOCK_CONTROLLER.get(), SidedInvWrapper::new);
	}
}