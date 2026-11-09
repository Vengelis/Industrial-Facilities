package fr.vengelis.industrialfacilities.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import fr.vengelis.industrialfacilities.barrier.IndustrialBarrierBlock;

/** Switches only an upper barrier's infill; its paint and connections stay on the same block. */
public class BarrierPliersItem extends Item {
    public BarrierPliersItem(){super(new Item.Properties().stacksTo(1));}

    public static boolean accepts(BlockState state){
        return state.getBlock() instanceof IndustrialBarrierBlock && state.getValue(IndustrialBarrierBlock.STACKED);
    }

    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var state=level.getBlockState(pos);
        if(!accepts(state))return InteractionResult.PASS;
        var player=context.getPlayer();
        if(player==null||!player.mayBuild()||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        if(!level.isClientSide()){
            level.setBlockAndUpdate(pos,state.cycle(IndustrialBarrierBlock.PANELLED));
            level.playSound(null,pos,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.5F,1.25F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.industrial_facilities.barrier_pliers.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.industrial_facilities.barrier_pliers.targets").withStyle(ChatFormatting.GRAY));
    }
}
