package fr.vengelis.industrialfacilities.panel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PanelSettings(int menuId, String text, boolean toRight) implements CustomPacketPayload {
    public static final Type<PanelSettings> TYPE = new Type<>(PanelContent.id("panel_settings"));
    public static final StreamCodec<FriendlyByteBuf, PanelSettings> CODEC = StreamCodec.of(
        (b, p) -> { b.writeVarInt(p.menuId); b.writeUtf(p.text, PanelBlockEntity.MAX_TEXT); b.writeBoolean(p.toRight); },
        b -> new PanelSettings(b.readVarInt(), b.readUtf(PanelBlockEntity.MAX_TEXT), b.readBoolean()));
    @Override public Type<PanelSettings> type() { return TYPE; }
}
