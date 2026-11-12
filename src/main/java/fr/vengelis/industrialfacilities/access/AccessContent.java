package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class AccessContent {
    public static BlockEntityType<AccessTerminalEntity> TYPE;
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK_ENTITY_TYPE,helper->{
        TYPE=BlockEntityType.Builder.<AccessTerminalEntity>of((pos,state)->{
            if(state.getBlock() instanceof KeypassTerminalBlock)return new KeypassTerminalEntity(pos,state);
            if(state.getBlock() instanceof fr.vengelis.industrialfacilities.security.CameraTerminalBlock)return new fr.vengelis.industrialfacilities.security.CameraTerminalEntity(pos,state);
            return new AccessTerminalEntity(pos,state);
        },IndustrialFacilitiesModBlocks.TERMINAL_READER.get(),IndustrialFacilitiesModBlocks.TERMINAL_ENCODER.get(),IndustrialFacilitiesModBlocks.TERMINAL_BADGE_BANK.get(),IndustrialFacilitiesModBlocks.CAMERA_TERMINAL.get(),
            IndustrialFacilitiesModBlocks.KEYPASS_READER.get(),IndustrialFacilitiesModBlocks.KEYPASS_MANAGER.get()).build(null);
        helper.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"access_terminal"),TYPE);
    });}
    private AccessContent(){}
}
