package fr.vengelis.industrialfacilities.panel;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PanelBlockEntity extends BlockEntity {
    public static final int MAX_TEXT = 200;
    private String text = "SECTEUR 04";
    private boolean toRight;
    public java.util.UUID automationInstanceId = java.util.UUID.randomUUID();
    public java.util.UUID automationOwner;
    /** The anchor identity survives saves, but never a broken/replaced block. No chunk loads. */
    public PanelStructure automationGroup(java.util.UUID owner, java.util.UUID identity) {
        if (level == null || isRemoved() || !automationInstanceId.equals(identity)) return null;
        var group = PanelStructure.find(level, worldPosition);
        if (!group.valid()) return null;
        for (BlockPos member : group.blocks()) {
            if (!(level.getBlockEntity(member) instanceof PanelBlockEntity panel)
                || !java.util.Objects.equals(owner, panel.automationOwner)) return null;
            for (Direction d : PanelStructure.directions(getBlockState()))
                if (!level.hasChunkAt(member.relative(d))) return null;
        }
        return group;
    }
    public boolean setAutomationText(java.util.UUID owner, java.util.UUID identity, String value) {
        if (level == null || level.isClientSide) return false;
        var group = automationGroup(owner, identity); if (group == null) return false;
        for (BlockPos member : group.blocks()) if (level.getBlockEntity(member) instanceof PanelBlockEntity panel)
            panel.configure(value, panel.toRight());
        return true;
    }
    public PanelBlockEntity(BlockPos p, BlockState s) { super(PanelContent.TYPE, p, s); }
    public String text() { return text; }
    public boolean toRight() { return toRight; }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
    public static String sanitize(String value) {
        StringBuilder b = new StringBuilder();
        for (int i=0; i<value.length() && b.length()<MAX_TEXT; i++) {
            char c = value.charAt(i);
            if (c >= 32 && c != 127 && c != '\u00a7' && !Character.isSurrogate(c)) b.append(c);
        }
        return b.toString();
    }
    public void configure(String value, boolean right) {
        String clean = sanitize(value); if (text.equals(clean) && toRight == right) return;
        text = clean; toRight = right; setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    public void writeMenu(FriendlyByteBuf b) {
        var group = PanelStructure.find(level, worldPosition);
        var owner = group.valid() ? group.controller(level) : this;
        if (owner == null) owner = this;
        b.writeBlockPos(worldPosition); b.writeUtf(owner.text, MAX_TEXT); b.writeBoolean(owner.toRight);
        b.writeVarInt(group.width()); b.writeVarInt(group.height()); b.writeBoolean(group.valid());
        b.writeBoolean(((fr.vengelis.industrialfacilities.block.LuminousPanelBlock)getBlockState().getBlock()).halfDepth());
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID("AutomationInstance", automationInstanceId);
        if (automationOwner != null) tag.putUUID("AutomationOwner", automationOwner);
        tag.putString("PanelText", text); tag.putBoolean("PanelToRight", toRight);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("AutomationInstance")) automationInstanceId = tag.getUUID("AutomationInstance");
        automationOwner = tag.hasUUID("AutomationOwner") ? tag.getUUID("AutomationOwner") : null;
        text = tag.contains("PanelText") ? sanitize(tag.getString("PanelText")) : "SECTEUR 04";
        toRight = tag.getBoolean("PanelToRight");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
