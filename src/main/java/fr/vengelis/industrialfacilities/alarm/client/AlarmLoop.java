package fr.vengelis.industrialfacilities.alarm.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;

public final class AlarmLoop extends AbstractTickableSoundInstance {
    public int radius;
    public long lastHeartbeat;
    public AlarmLoop(BlockPos pos,ResourceLocation sound,int range) {
        super(SoundEvent.createVariableRangeEvent(sound),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());
        x=pos.getX()+.5; y=pos.getY()+.5; z=pos.getZ()+.5;
        radius=range; looping=true; delay=0; relative=false;
        // Apply the configured radius directly. Vanilla attenuation is normally limited to 16 blocks.
        attenuation=Attenuation.NONE; lastHeartbeat=AlarmClient.ticks(); tick();
    }
    public static float gain(double distance,int radius) { return (float)Math.clamp(1-distance/radius,0,1); }
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        var mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null) { stop(); return; }
        volume=gain(Math.sqrt(mc.player.distanceToSqr(x,y,z)),radius);
    }
    public void finish() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
}
