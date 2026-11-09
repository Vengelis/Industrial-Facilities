package fr.vengelis.industrialfacilities.security;
import fr.vengelis.industrialfacilities.camera.ui.CameraMenus;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
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
public class SecurityCameraBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE=BlockStateProperties.ATTACH_FACE;
    public SecurityCameraBlock(){super(Properties.of().strength(3,15).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(FACE,AttachFace.WALL));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,FACE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){Direction face=context.getClickedFace();return defaultBlockState().setValue(FACING,face.getAxis().isHorizontal()?face:context.getHorizontalDirection().getOpposite()).setValue(FACE,face==Direction.UP?AttachFace.FLOOR:face==Direction.DOWN?AttachFace.CEILING:AttachFace.WALL);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SecurityCameraEntity(pos,state);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(2.9,2.9,2.9,13.1,13.1,13.1);}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){super.setPlacedBy(level,pos,state,entity,stack);if(!level.isClientSide&&entity instanceof Player player&&level.getBlockEntity(pos) instanceof SecurityCameraEntity camera){camera.creator=player.getUUID();camera.sweepEpoch=level.getGameTime();camera.pitch=state.getValue(FACE)==AttachFace.CEILING?30:state.getValue(FACE)==AttachFace.FLOOR?-15:0;camera.sync();}}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof SecurityCameraEntity camera&&camera.canConfigure(server))CameraMenus.openCamera(server,camera);return InteractionResult.sidedSuccess(level.isClientSide);}
}
