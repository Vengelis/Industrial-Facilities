package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.energybarrier.EnergyBarrierManager;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

/** Invisible gaps are visual only: the whole centered half-block is solid. */
public class EnergyBarrierFieldBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty COLUMN=IntegerProperty.create("column",0,4);
    public static final IntegerProperty ROW=IntegerProperty.create("row",1,4);
    private static final VoxelShape X=Block.box(0,0,4,16,16,12),Z=Block.box(4,0,0,12,16,16);
    public EnergyBarrierFieldBlock(){
        super(Properties.of().strength(-1,3600000).sound(SoundType.GLASS).noOcclusion().noLootTable().lightLevel(s->10));
        registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.X).setValue(COLUMN,0).setValue(ROW,1));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,COLUMN,ROW);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(AXIS)==Direction.Axis.X?X:Z;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected int getLightBlock(BlockState state,BlockGetter level,BlockPos pos){return 0;}
    @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    @Override protected float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos){return 1;}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction direction){
        return adjacent.is(this)&&state.getValue(AXIS)==adjacent.getValue(AXIS)
            &&(direction.getAxis()==Direction.Axis.Y||direction.getAxis()==state.getValue(AXIS))
            ||super.skipRendering(state,adjacent,direction);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return ItemStack.EMPTY;}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);
        if(!level.isClientSide)level.scheduleTick(pos,this,20);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        EnergyBarrierManager.checkField(level,pos,state);
        if(level.getBlockState(pos).is(this))level.scheduleTick(pos,this,20);
    }
}
