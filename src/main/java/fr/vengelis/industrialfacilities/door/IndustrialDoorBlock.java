package fr.vengelis.industrialfacilities.door;

import com.mojang.serialization.MapCodec;
import fr.vengelis.industrialfacilities.access.AccessDoorService;
import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.PushReaction;

public class IndustrialDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty RAIL = BooleanProperty.create("rail");
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");
    public final boolean controller;
    public final boolean dark;
    public final IndustrialDoorStyle style;
    public IndustrialDoorBlock(boolean controller) { this(controller,false); }
    public IndustrialDoorBlock(boolean controller,boolean dark) { this(controller,dark?IndustrialDoorStyle.DARK:IndustrialDoorStyle.LIGHT); }
    public IndustrialDoorBlock(boolean controller,IndustrialDoorStyle style) {
        super(Properties.of().strength(4, 30).sound(SoundType.METAL).requiresCorrectToolForDrops()
            .lightLevel(s -> s.getValue(ACTIVE) && s.getValue(RAIL) ? 12 : 0));
        this.controller = controller;
        this.style = style;
        this.dark = style==IndustrialDoorStyle.DARK;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(ACTIVE,false)
            .setValue(OPEN,false).setValue(RAIL,false).setValue(VERTICAL,true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        b.add(FACING,ACTIVE,OPEN,RAIL,VERTICAL);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new IndustrialDoorEntity(p,s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type) {
        return type == IndustrialDoorContent.TYPE ? (world,pos,state,be) -> IndustrialDoorEntity.tick(world,pos,state,(IndustrialDoorEntity)be) : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit) {
        if (controller && !l.isClientSide && l.getBlockEntity(p) instanceof IndustrialDoorEntity door) {
            if(door.size>0&&player.isShiftKeyDown()){
                if(AccessDoorService.canConfigure(player,door))AccessMenus.openDoor(player,door);
                else player.displayClientMessage(Component.literal("Seul le créateur des terminaux liés peut configurer cette porte."),true);
                return InteractionResult.CONSUME;
            }
            int size = door.size;
            boolean ok = size>0 || IndustrialDoorManager.assemble((ServerLevel)l,p);
            player.displayClientMessage(Component.translatable(size>0 ? "door.industrial_facilities.busy" : ok ? "door.industrial_facilities.assembled" : "door.industrial_facilities.invalid",door.size,door.size),true);
        }
        return controller ? InteractionResult.sidedSuccess(l.isClientSide) : InteractionResult.PASS;
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if (!s.is(next.getBlock()) && l instanceof ServerLevel server && l.getBlockEntity(p) instanceof IndustrialDoorEntity be)
            IndustrialDoorManager.breakMember(server,be);
        super.onRemove(s,l,p,next,moving);
    }
    @Override public PushReaction getPistonPushReaction(BlockState s) { return PushReaction.BLOCK; }
    @Override public boolean canConnectRedstone(BlockState s,BlockGetter l,BlockPos p,Direction side) { return controller; }
}
