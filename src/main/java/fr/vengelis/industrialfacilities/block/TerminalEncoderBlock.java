package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.access.AccessTerminalBlock;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.frequency.FrequencyEncoding;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TerminalEncoderBlock extends AccessTerminalBlock {
    public TerminalEncoderBlock() {
        super(true);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                         Player player, InteractionHand hand, BlockHitResult hit) {
        if (!FrequencyEncoding.accepts(0, stack)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide()) {
            var terminal = AccessTerminalEntity.resolve(level, pos);
            if (terminal != null) AccessMenus.open(player, terminal, hand);
            else player.displayClientMessage(Component.translatable(FrequencyContent.UI + "assemble"), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
