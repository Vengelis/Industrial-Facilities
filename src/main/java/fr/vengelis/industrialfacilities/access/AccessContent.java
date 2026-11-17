package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import fr.vengelis.industrialfacilities.item.BadgeWalletItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class AccessContent {
    private static final ResourceLocation WALLET = ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"badge_wallet");
    public static BlockEntityType<AccessTerminalEntity> TYPE;
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void registerWallet(RegisterEvent event){
        event.register(Registries.ITEM, helper -> {
            if (!BuiltInRegistries.ITEM.containsKey(WALLET)) helper.register(WALLET, new BadgeWalletItem());
        });
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void addWalletToCreative(BuildCreativeModeTabContentsEvent event){
        if (!event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"industrial_facilities"))) return;
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(WALLET));
        if (event.getParentEntries().contains(stack)) return;
        var badge = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"access_badge_limited")));
        if (event.getParentEntries().contains(badge) && event.getSearchEntries().contains(badge)) {
            event.insertAfter(badge, stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else event.accept(stack);
    }
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK_ENTITY_TYPE,helper->{
        TYPE=BlockEntityType.Builder.<AccessTerminalEntity>of((pos,state)->{
            if(state.getBlock() instanceof KeypassTerminalBlock)return new KeypassTerminalEntity(pos,state);
            if(state.getBlock() instanceof ControlTerminalBlock)return new ControlTerminalEntity(pos,state);
            if(state.getBlock() instanceof fr.vengelis.industrialfacilities.security.CameraTerminalBlock)return new fr.vengelis.industrialfacilities.security.CameraTerminalEntity(pos,state);
            return new AccessTerminalEntity(pos,state);
        },IndustrialFacilitiesModBlocks.TERMINAL_READER.get(),IndustrialFacilitiesModBlocks.TERMINAL_ENCODER.get(),IndustrialFacilitiesModBlocks.TERMINAL_BADGE_BANK.get(),IndustrialFacilitiesModBlocks.CAMERA_TERMINAL.get(),
            IndustrialFacilitiesModBlocks.KEYPASS_READER.get(),IndustrialFacilitiesModBlocks.KEYPASS_MANAGER.get(),
            IndustrialFacilitiesModBlocks.TERMINAL_BUTTON.get(),IndustrialFacilitiesModBlocks.TERMINAL_LOCKDOWN.get()).build(null);
        helper.register(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"access_terminal"),TYPE);
    });}
    private AccessContent(){}
}
