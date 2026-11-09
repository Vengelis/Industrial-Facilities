package fr.vengelis.industrialfacilities.security;
import net.minecraft.core.*;
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
public class SignalEmitterBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public SignalEmitterBlock(){super(Properties.of().strength(3,15).sound(SoundType.METAL).requiresCorrectToolForDrops());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SignalEmitterEntity(pos,state);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide&&type==SecurityContent.EMITTER_TYPE?(l,p,s,be)->((SignalEmitterEntity)be).updatePower():null;}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    // Minecraft's query direction points from the receiver toward this source.
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction query){return state.getValue(POWERED)&&query==state.getValue(FACING).getOpposite()?15:0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction query){return getSignal(state,level,pos,query);}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null&&side==state.getValue(FACING).getOpposite();}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){super.setPlacedBy(level,pos,state,entity,stack);if(!level.isClientSide&&entity instanceof Player player&&level.getBlockEntity(pos) instanceof SignalEmitterEntity emitter){emitter.creator=player.getUUID();emitter.setChanged();}}
}
