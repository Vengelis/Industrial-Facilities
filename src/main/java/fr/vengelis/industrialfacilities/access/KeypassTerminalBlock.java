package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.access.ui.KeypassMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class KeypassTerminalBlock extends AccessTerminalBlock {
    public KeypassTerminalBlock(boolean manager){super(manager);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new KeypassTerminalEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server&&AccessTerminalEntity.resolve(level,pos) instanceof KeypassTerminalEntity terminal)
            KeypassMenus.open(server,terminal);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
