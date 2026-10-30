package fr.vengelis.industrialfacilities.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import fr.vengelis.industrialfacilities.road.RoadMarkingBlock;
import fr.vengelis.industrialfacilities.road.RoadRotatableMarkingBlock;
import fr.vengelis.industrialfacilities.road.RoadTextMarkingBlock;

/** Rotates only Road line decorations in place; text groups and solid blocks are excluded. */
public class RoadBrushItem extends Item {
    public RoadBrushItem() { super(new Item.Properties().stacksTo(1)); }

    public static boolean accepts(BlockState state) {
        return state.getBlock() instanceof RoadMarkingBlock && !(state.getBlock() instanceof RoadTextMarkingBlock);
    }
    public static BlockState rotated(BlockState state, boolean reverse) {
        if (!accepts(state)) return state;
        if (state.getBlock() instanceof RoadRotatableMarkingBlock)
            return state.setValue(RoadRotatableMarkingBlock.ROTATION,
                Math.floorMod(state.getValue(RoadRotatableMarkingBlock.ROTATION) + (reverse ? -1 : 1), 16));
        return state.rotate(reverse ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!accepts(state)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || !player.mayBuild() || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, rotated(state, player.isShiftKeyDown()));
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.industrial_facilities.road_brush.rotate").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.industrial_facilities.road_brush.targets").withStyle(ChatFormatting.GRAY));
    }
}
