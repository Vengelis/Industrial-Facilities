package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.energybarrier.EnergyBarrierManager;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;

/** Solid emitter. Scheduled world ticks survive saves and chunk unloads. */
public class EnergyBarrierBaseBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public static final BooleanProperty LEFT=BooleanProperty.create("left"),RIGHT=BooleanProperty.create("right");
    public EnergyBarrierBaseBlock() {
        super(Properties.of().strength(4,30).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .lightLevel(s->s.getValue(ACTIVE)?7:0));
        registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.X).setValue(ACTIVE,false).setValue(LEFT,false).setValue(RIGHT,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,ACTIVE,LEFT,RIGHT);}
    public static BlockState connected(BlockState state,BlockGetter level,BlockPos pos){
        Direction along=state.getValue(AXIS)==Direction.Axis.X?Direction.EAST:Direction.SOUTH;
        return state.setValue(LEFT,level.getBlockState(pos.relative(along.getOpposite())).is(state.getBlock()))
            .setValue(RIGHT,level.getBlockState(pos.relative(along)).is(state.getBlock()));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return connected(defaultBlockState().setValue(AXIS,context.getHorizontalDirection().getClockWise().getAxis()),context.getLevel(),context.getClickedPos());
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        return connected(super.updateShape(state,direction,neighbor,level,pos,other),level,pos);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!level.isClientSide)level.scheduleTick(pos,this,1);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){
        if(!level.isClientSide)level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        BlockState next=connected(state,level,pos);if(next!=state)level.setBlock(pos,next,3);
        EnergyBarrierManager.update(level,pos);
        level.scheduleTick(pos,this,5);
    }
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return true;}
}
