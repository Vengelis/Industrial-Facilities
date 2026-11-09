package fr.vengelis.industrialfacilities.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PanelMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final String text;
    public final boolean toRight, valid, halfDepth;
    public final int width, height;
    private final PanelBlockEntity panel;
    public PanelMenu(int id, Inventory inv, PanelBlockEntity be) {
        super(PanelContent.MENU, id); panel = be; pos = be.getBlockPos();
        var group = PanelStructure.find(be.getLevel(), pos);
        var owner = group.valid() ? group.controller(be.getLevel()) : be;
        if (owner == null) owner = be;
        text = owner.text(); toRight = owner.toRight(); width = group.width(); height = group.height(); valid = group.valid();
        halfDepth=((fr.vengelis.industrialfacilities.block.LuminousPanelBlock)be.getBlockState().getBlock()).halfDepth();
    }
    public PanelMenu(int id, Inventory inv, FriendlyByteBuf b) {
        super(PanelContent.MENU, id); panel = null;
        pos = b.readBlockPos(); text = b.readUtf(PanelBlockEntity.MAX_TEXT); toRight = b.readBoolean();
        width = b.readVarInt(); height = b.readVarInt(); valid = b.readBoolean();
        halfDepth=b.readBoolean();
    }
    @Override public boolean stillValid(Player p) {
        return p.isAlive() && p.mayBuild() && !p.isSpectator() && p.level().hasChunkAt(pos)
            && p.level().getBlockEntity(pos) instanceof PanelBlockEntity current && (panel == null || current == panel)
            && p.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) <= 64;
    }
    public void apply(String value, boolean right, Player player) {
        if (panel == null || !stillValid(player) || value.length() > PanelBlockEntity.MAX_TEXT) return;
        var group = PanelStructure.find(player.level(), pos);
        if (!group.valid()) return;
        for (BlockPos member : group.blocks()) if (player.level().getBlockEntity(member) instanceof PanelBlockEntity be)
            be.configure(value, right);
        player.closeContainer();
    }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
}
