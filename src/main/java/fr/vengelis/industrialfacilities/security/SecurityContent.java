package fr.vengelis.industrialfacilities.security;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class SecurityContent {
    public static BlockEntityType<SecurityCameraEntity> CAMERA_TYPE;
    public static BlockEntityType<SignalEmitterEntity> EMITTER_TYPE;
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK_ENTITY_TYPE,h->{
        CAMERA_TYPE=BlockEntityType.Builder.of(SecurityCameraEntity::new,IndustrialFacilitiesModBlocks.SECURITY_CAMERA.get()).build(null);h.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"security_camera"),CAMERA_TYPE);
        EMITTER_TYPE=BlockEntityType.Builder.of(SignalEmitterEntity::new,IndustrialFacilitiesModBlocks.SIGNAL_EMITTER.get()).build(null);h.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"signal_emitter"),EMITTER_TYPE);
    });}
    private SecurityContent(){}
}
