package fr.vengelis.industrialfacilities.block;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;

/** Continuous column. Each horizontal emitter face follows its own adjacent field. */
public class EnergyBarrierPillarBlock extends Block {
    public static final BooleanProperty UP=BlockStateProperties.UP,DOWN=BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH=BlockStateProperties.NORTH,EAST=BlockStateProperties.EAST,
        SOUTH=BlockStateProperties.SOUTH,WEST=BlockStateProperties.WEST;
    public EnergyBarrierPillarBlock(){
        super(Properties.of().strength(4,30).sound(SoundType.METAL).requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(UP,false).setValue(DOWN,false)
            .setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false));
    }
    public static BooleanProperty side(Direction direction){
        return switch(direction){case NORTH->NORTH;case EAST->EAST;case SOUTH->SOUTH;case WEST->WEST;default->throw new IllegalArgumentException("Horizontal face required");};
    }
    public static BlockState connected(BlockState state,BlockGetter level,BlockPos pos){
        Block block=state.getBlock();
        state=state.setValue(UP,level.getBlockState(pos.above()).is(block)).setValue(DOWN,level.getBlockState(pos.below()).is(block));
        for(Direction d:Direction.Plane.HORIZONTAL){
            BlockState neighbor=level.getBlockState(pos.relative(d));
            state=state.setValue(side(d),neighbor.getBlock() instanceof EnergyBarrierFieldBlock
                &&neighbor.getValue(EnergyBarrierFieldBlock.AXIS)==d.getAxis());
        }
        return state;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(UP,DOWN,NORTH,EAST,SOUTH,WEST);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return connected(defaultBlockState(),context.getLevel(),context.getClickedPos());}
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        return connected(super.updateShape(state,direction,neighbor,level,pos,other),level,pos);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);if(!level.isClientSide)level.scheduleTick(pos,this,1);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        BlockState next=connected(state,level,pos);if(next!=state)level.setBlock(pos,next,3);
    }
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public boolean canConnectRedstone(BlockState state,BlockGetter level,BlockPos pos,Direction side){return true;}
}
