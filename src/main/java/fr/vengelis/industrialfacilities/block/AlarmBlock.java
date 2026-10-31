package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.alarm.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A centred 10 x 10 x 3 pixel panel; FACING points out of its mounting surface. */
public class AlarmBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape[] SHAPES = {
        box(3,13,3,13,16,13), box(3,0,3,13,3,13), box(3,3,13,13,13,16),
        box(3,3,0,13,13,3), box(13,3,3,16,13,13), box(0,3,3,3,13,13)
    };
    public AlarmBlock() {
        super(Properties.of().strength(2,6).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()
            .lightLevel(s -> s.getValue(POWERED) ? 7 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(POWERED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING,POWERED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING,c.getClickedFace()).setValue(POWERED,c.getLevel().hasNeighborSignal(c.getClickedPos()));
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPES[s.getValue(FACING).get3DDataValue()]; }
    @Override protected BlockState rotate(BlockState s,Rotation r) { return s.setValue(FACING,r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(FACING))); }
    @Override public boolean canConnectRedstone(BlockState s,BlockGetter l,BlockPos p,Direction side) { return true; }
    @Override public PushReaction getPistonPushReaction(BlockState s) { return PushReaction.BLOCK; }
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s) { return new AlarmBlockEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t) {
        return !l.isClientSide && t == AlarmContent.TYPE ? (w,p,state,be) -> ((AlarmBlockEntity)be).tick() : null;
    }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block neighbor,BlockPos from,boolean moving) {
        if (!l.isClientSide && l.getBlockEntity(p) instanceof AlarmBlockEntity be) be.updatePower();
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if (player instanceof ServerPlayer server && l.getBlockEntity(p) instanceof AlarmBlockEntity be) {
            server.openMenu(new SimpleMenuProvider((id,inv,who) -> new AlarmMenu(id,inv,be),Component.translatable("block.industrial_facilities.alarm")),be::writeMenu);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if (!s.is(next.getBlock()) && l.getBlockEntity(p) instanceof AlarmBlockEntity be) be.stopListeners();
        super.onRemove(s,l,p,next,moving);
    }
}
