package fr.vengelis.industrialfacilities.access;

import java.util.Objects;
import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModSounds;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class AccessTerminalBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ASSEMBLED=BooleanProperty.create("assembled");
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public final boolean encoder,bank;
    public AccessTerminalBlock(boolean encoder){
        this(encoder,false);
    }
    public AccessTerminalBlock(boolean encoder,boolean bank){
        super(Properties.of().strength(3,20).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().dynamicShape());this.encoder=encoder;this.bank=bank;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(ASSEMBLED,false).setValue(HALF,DoubleBlockHalf.LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,ASSEMBLED,HALF);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AccessTerminalEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AccessContent.TYPE?(l,p,s,be)->AccessTerminalEntity.tick(l,p,s,(AccessTerminalEntity)be):null;}
    @Override protected RenderShape getRenderShape(BlockState state){return state.getValue(ASSEMBLED)?RenderShape.INVISIBLE:RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(ASSEMBLED)?Block.box(2,0,2,14,state.getValue(HALF)==DoubleBlockHalf.LOWER?16:12.8,14):Shapes.block();}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide||!(placer instanceof Player player)||!(level.getBlockEntity(pos) instanceof AccessTerminalEntity be))return;
        be.creator=player.getUUID();be.sync();tryAssemble(level,pos);tryAssemble(level,pos.below());
    }
    public static boolean validPair(Level level,BlockPos base){
        BlockState low=level.getBlockState(base),high=level.getBlockState(base.above());
        return low.getBlock() instanceof AccessTerminalBlock&&high.is(low.getBlock())&&low.getValue(ASSEMBLED)&&high.getValue(ASSEMBLED)
            &&low.getValue(HALF)==DoubleBlockHalf.LOWER&&high.getValue(HALF)==DoubleBlockHalf.UPPER&&low.getValue(FACING)==high.getValue(FACING);
    }
    public static boolean tryAssemble(Level level,BlockPos base){
        if(level.isClientSide||!level.hasChunkAt(base)||!level.hasChunkAt(base.above()))return false;
        BlockState low=level.getBlockState(base),high=level.getBlockState(base.above());
        if(!(low.getBlock() instanceof AccessTerminalBlock)||!high.is(low.getBlock())||low.getValue(ASSEMBLED)||high.getValue(ASSEMBLED))return false;
        if(!(level.getBlockEntity(base) instanceof AccessTerminalEntity bottom)||!(level.getBlockEntity(base.above()) instanceof AccessTerminalEntity top)||bottom.creator==null||!Objects.equals(bottom.creator,top.creator))return false;
        level.setBlock(base,low.setValue(ASSEMBLED,true).setValue(HALF,DoubleBlockHalf.LOWER),2);
        level.setBlock(base.above(),high.setValue(FACING,low.getValue(FACING)).setValue(ASSEMBLED,true).setValue(HALF,DoubleBlockHalf.UPPER),3);
        bottom.sync();top.sync();return true;
    }
    public static void checkStructure(Level level,BlockPos pos,BlockState state){
        if(level.isClientSide||!state.getValue(ASSEMBLED))return;
        BlockPos base=state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
        if(!validPair(level,base)){
            if(level.getBlockEntity(pos) instanceof AccessTerminalEntity be)be.invalidate();
            level.setBlock(pos,state.setValue(ASSEMBLED,false).setValue(HALF,DoubleBlockHalf.LOWER),3);
        }
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())&&!level.isClientSide){
            BlockPos base=state.getValue(ASSEMBLED)&&state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
            if(level.getBlockEntity(base) instanceof AccessTerminalEntity be)be.invalidate();
            if(state.getValue(ASSEMBLED)){
                BlockPos other=state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos.above();BlockState sibling=level.getBlockState(other);
                if(sibling.is(state.getBlock())&&sibling.getValue(ASSEMBLED)){
                    if(level.getBlockEntity(other) instanceof AccessTerminalEntity be)be.invalidate();
                    level.setBlock(other,sibling.setValue(ASSEMBLED,false).setValue(HALF,DoubleBlockHalf.LOWER),3);
                }
            }
        }super.onRemove(state,level,pos,next,moving);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;
        AccessTerminalEntity terminal=AccessTerminalEntity.resolve(level,pos);
        if(terminal==null)player.displayClientMessage(Component.literal("Empilez deux blocs de terminal du même type."),true);
        else if(terminal.encoder||terminal.bank||terminal.isOwner(player))AccessMenus.open(player,terminal);
        else player.displayClientMessage(Component.literal("Présentez un badge encodé au lecteur."),true);
        return InteractionResult.CONSUME;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!AccessBadges.isBadge(stack))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level.isClientSide||hand!=InteractionHand.MAIN_HAND)return ItemInteractionResult.SUCCESS;
        var terminal=AccessTerminalEntity.resolve(level,pos);
        if(terminal==null)player.displayClientMessage(Component.literal("Empilez deux blocs de terminal du même type."),true);
        else if(terminal.encoder||terminal.bank)AccessMenus.open(player,terminal);
        else {
            var result=AccessDoorService.activate(player,terminal,stack);player.displayClientMessage(Component.literal(result.message()),true);
            BlockPos base=terminal.getBlockPos();
            level.playSound(null,base.getX()+.5,base.getY()+1.3,base.getZ()+.5,
                result.ok()?IndustrialFacilitiesModSounds.TERMINAL_ACCESS_GRANTED.get():IndustrialFacilitiesModSounds.TERMINAL_ACCESS_DENIED.get(),SoundSource.BLOCKS,.6f,1f);
            if(result.ok())player.setItemInHand(hand,AccessBadges.normalize(stack));
            player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        }
        return ItemInteractionResult.CONSUME;
    }
}
