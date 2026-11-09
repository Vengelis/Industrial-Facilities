package fr.vengelis.industrialfacilities.security;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.camera.ui.CameraMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
public class CameraTerminalBlock extends AccessTerminalBlock {
    public CameraTerminalBlock(){super(true,false);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CameraTerminalEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AccessContent.TYPE?(l,p,s,be)->{AccessTerminalEntity.tick(l,p,s,(AccessTerminalEntity)be);if(!l.isClientSide&&s.getValue(HALF)==DoubleBlockHalf.LOWER&&s.getValue(ASSEMBLED))((CameraTerminalEntity)be).tickServer();}:null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){open(level,pos,player);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.MAIN_HAND)open(level,pos,player);return ItemInteractionResult.sidedSuccess(level.isClientSide);}
    private void open(Level level,BlockPos pos,Player player){if(player instanceof ServerPlayer server&&AccessTerminalEntity.resolve(level,pos) instanceof CameraTerminalEntity terminal&&terminal.isOwner(player))CameraMenus.open(server,terminal);}
}
