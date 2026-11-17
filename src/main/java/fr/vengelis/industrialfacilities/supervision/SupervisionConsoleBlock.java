package fr.vengelis.industrialfacilities.supervision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import fr.vengelis.industrialfacilities.access.TerminalLinks;

public class SupervisionConsoleBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 4);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 3);

    public SupervisionConsoleBlock() {
        super(Properties.of().strength(4, 15).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROW, 0).setValue(COLUMN, 1));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ROW, COLUMN); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext placement) {
        return defaultBlockState().setValue(FACING, placement.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SupervisionConsoleEntity(pos, state); }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == SupervisionContent.TYPE ? (l,p,s,be) -> ((SupervisionConsoleEntity) be).tick((ServerLevel) l) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SupervisionConsoleEntity part) {
            part.placedBy = player.getUUID();
            part.sync();
            SupervisionConsoleEntity.assemble(server, pos, player.getUUID(), state.getValue(FACING));
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            var console = SupervisionConsoleEntity.resolve(level, pos);
            if (console == null) {
                console = SupervisionConsoleEntity.assemble(server.serverLevel(), pos, player.getUUID(), state.getValue(FACING));
                if (console == null) {
                    player.displayClientMessage(Component.translatable(NetworkView.UI
                        + (this instanceof fr.vengelis.industrialfacilities.block.SupervisionDisplayBlock ? "display_assemble" : "assemble")), true);
                    return InteractionResult.CONSUME;
                }
            }
            console.update(server.serverLevel());
            if (console.isDisplay()) {
                SupervisionMenu.openDisplay(server, console);
                return InteractionResult.CONSUME;
            }
            var root = console.getBlockPos();
            var right = console.getBlockState().getValue(FACING).getCounterClockWise();
            int column = (pos.getX() - root.getX()) * right.getStepX() + (pos.getZ() - root.getZ()) * right.getStepZ();
            if (root.getY() + console.bottomRow() == pos.getY() && hit.getDirection() == state.getValue(FACING)
                    && (column == console.firstColumn() || column == console.lastColumn())) {
                int direction = column == console.firstColumn() ? -1 : 1;
                if (player.isShiftKeyDown()) console.turnPage(direction);
                else console.turnSector(server.serverLevel(), direction);
            } else SupervisionMenu.openConsole(server, console);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof SupervisionConsoleEntity part) {
            var base = part.root == null || !TerminalLinks.loaded(server, part.root) ? null : level.getBlockEntity(part.root);
            if (base instanceof SupervisionConsoleEntity console && console.consoleId != null) console.dismantle(server, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
