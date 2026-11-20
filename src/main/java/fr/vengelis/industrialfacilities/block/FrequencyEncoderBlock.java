package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class FrequencyEncoderBlock extends TerminalEncoderBlock {
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player) || AccessTerminalEntity.resolve(level, pos) != null)
            return;
        // The original single-block encoder did not store a placer. Adopt it when completing the pair.
        for (BlockPos neighbour : new BlockPos[]{pos.below(), pos.above()}) {
            if (!level.hasChunkAt(neighbour)) continue;
            BlockState other = level.getBlockState(neighbour);
            if (!other.is(this) || other.getValue(ASSEMBLED)) continue;
            var entity = level.getBlockEntity(neighbour);
            if (entity == null) {
                entity = newBlockEntity(neighbour, other);
                level.setBlockEntity(entity);
            }
            if (!(entity instanceof AccessTerminalEntity module) || module.creator != null) continue;
            module.creator = player.getUUID();
            module.sync();
            tryAssemble(level, neighbour.getY() < pos.getY() ? neighbour : pos);
            return;
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                          BlockHitResult hit) {
        if (player instanceof ServerPlayer server && server.isAlive() && !server.isSpectator()) {
            var terminal = AccessTerminalEntity.resolve(level, pos);
            if (terminal != null) AccessMenus.open(server, terminal);
            else server.displayClientMessage(Component.translatable(FrequencyContent.UI + "assemble"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

}
