package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.panel.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
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

/** Full cubic display. Connected borders are computed by the server, not by a resource-pack mod. */
public class LuminousPanelBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty EDGES = IntegerProperty.create("edges", 0, 15);
    public LuminousPanelBlock() {
        this(false);
    }
    protected LuminousPanelBlock(boolean thin) {
        super(housingProperties(thin));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EDGES, 15));
    }
    private static Properties housingProperties(boolean thin) {
        var p=Properties.of().strength(3, 6).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 8);
        return thin ? p.noOcclusion() : p;
    }
    public boolean halfDepth() { return false; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, EDGES); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return rotate(s, m.getRotation(s.getValue(FACING))); }
    @Override public PushReaction getPistonPushReaction(BlockState s) { return PushReaction.BLOCK; }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new PanelBlockEntity(p, s); }
    @Override public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(l, p, s, entity, stack);
        if (!l.isClientSide && l.getBlockEntity(p) instanceof PanelBlockEntity be) {
            for (Direction d : PanelStructure.directions(s)) {
                BlockPos next = p.relative(d);
                if (PanelStructure.matches(l, next, s) && l.getBlockEntity(next) instanceof PanelBlockEntity old) {
                    be.configure(old.text(), old.toRight()); break;
                }
            }
        }
    }
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
        super.onPlace(s, l, p, old, moving);
        if (!l.isClientSide) l.scheduleTick(p, this, 1);
    }
    @Override protected void neighborChanged(BlockState s, Level l, BlockPos p, Block neighbor, BlockPos from, boolean moving) {
        if (!l.isClientSide) l.scheduleTick(p, this, 1);
    }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) { PanelStructure.refresh(l, p); }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && player.mayBuild() && !player.isSpectator()
            && l.getBlockEntity(p) instanceof PanelBlockEntity be) {
            server.openMenu(new SimpleMenuProvider((id, inv, who) -> new PanelMenu(id, inv, be),
                Component.translatable(getDescriptionId())), be::writeMenu);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
