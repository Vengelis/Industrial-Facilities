package fr.vengelis.industrialfacilities.alarm;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record AlarmSettings(int menuId,String soundId,int range,boolean fireAlarm) implements CustomPacketPayload {
    public static final Type<AlarmSettings> TYPE=new Type<>(AlarmContent.id("alarm_settings"));
    public static final StreamCodec<FriendlyByteBuf,AlarmSettings> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.menuId); b.writeUtf(p.soundId,48); b.writeVarInt(p.range); b.writeBoolean(p.fireAlarm);
    },b->new AlarmSettings(b.readVarInt(),b.readUtf(48),b.readVarInt(),b.readBoolean()));
    @Override public Type<AlarmSettings> type() { return TYPE; }
}
