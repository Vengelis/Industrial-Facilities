package fr.vengelis.industrialfacilities.alarm;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.Consumer;
public record AlarmSignal(ResourceLocation dimension,BlockPos pos,ResourceLocation sound,int range,boolean active) implements CustomPacketPayload {
    public static Consumer<AlarmSignal> receiver=signal->{};
    public static final Type<AlarmSignal> TYPE=new Type<>(AlarmContent.id("alarm_signal"));
    public static final StreamCodec<FriendlyByteBuf,AlarmSignal> CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension); b.writeBlockPos(p.pos); b.writeResourceLocation(p.sound); b.writeVarInt(p.range); b.writeBoolean(p.active);
    },b->new AlarmSignal(b.readResourceLocation(),b.readBlockPos(),b.readResourceLocation(),b.readVarInt(),b.readBoolean()));
    @Override public Type<AlarmSignal> type() { return TYPE; }
}
