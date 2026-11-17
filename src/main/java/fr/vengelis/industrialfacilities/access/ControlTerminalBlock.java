package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.access.ui.AccessMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ControlTerminalBlock extends AccessTerminalBlock {
    public static final String UI = "gui.industrial_facilities.terminal_control.";
    private final boolean lockdown;

    protected ControlTerminalBlock(boolean lockdown) {
        super(false);
        this.lockdown = lockdown;
    }

    public boolean lockdown() { return lockdown; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlTerminalEntity(pos, state);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                         BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            if (!(AccessTerminalEntity.resolve(level, pos) instanceof ControlTerminalEntity terminal)) {
                player.displayClientMessage(Component.translatable(UI + "assemble"), true);
            } else if (terminal.canInteract(player)) {
                if (player.isShiftKeyDown()) {
                    if (terminal.isOwner(player)) AccessMenus.open(player, terminal);
                    else player.displayClientMessage(Component.translatable(UI + "owner_only"), true);
                } else {
                    activate(server, player, terminal);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void activate(ServerLevel level, Player player, ControlTerminalEntity terminal) {
        if (lockdown && !terminal.isOwner(player)) {
            player.displayClientMessage(Component.translatable(UI + "owner_only"), true);
            return;
        }
        var data = TerminalControlData.get(level);
        String status;
        float pitch = .6f;
        if (lockdown) {
            boolean active = data.toggle(level, terminal);
            status = active ? "lockdown_on" : "lockdown_off";
            if (!active) pitch = .5f;
        } else {
            if (!data.press(level, terminal)) {
                player.displayClientMessage(Component.translatable(UI + "no_door"), true);
                return;
            }
            status = "button_pressed";
        }
        terminal.updateAccessState();
        level.playSound(null, terminal.getBlockPos(), lockdown ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_ON,
            SoundSource.BLOCKS, .4f, pitch);
        player.displayClientMessage(Component.translatable(UI + status), true);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
