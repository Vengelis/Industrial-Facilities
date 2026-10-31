package fr.vengelis.industrialfacilities.door.client;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.door.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** One independently stoppable industrial mechanism sound for each moving door. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class IndustrialDoorAudio {
    private static final Map<IndustrialDoorEntity,MovementSound> ACTIVE=new IdentityHashMap<>();
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->IndustrialDoorEntity.clientSoundTick=IndustrialDoorAudio::update);
    }
    private static int direction(IndustrialDoorEntity door) {
        if(door.isRemoved()||door.size==0||door.paused||door.tearingDown||door.getLevel()!=Minecraft.getInstance().level)return 0;
        return door.targetOpen ? door.progress<1?1:0 : door.progress>0?-1:0;
    }
    public static void update(IndustrialDoorEntity door) {
        int direction=direction(door);Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.player.distanceToSqr(center(door))>16*16)direction=0;
        MovementSound old=ACTIVE.get(door);
        if(old!=null&&!old.isStopped()&&old.direction==direction)return;
        if(old!=null)old.finish();
        if(direction==0)return;
        MovementSound sound=new MovementSound(door,direction);ACTIVE.put(door,sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }
    private static Vec3 center(IndustrialDoorEntity door) {
        Vec3 a=Vec3.atCenterOf(door.doorOrigin()),b=Vec3.atCenterOf(IndustrialDoorManager.at(door.doorOrigin(),door.getBlockState().getValue(IndustrialDoorBlock.FACING),door.size-1,door.size-1));
        return a.add(b).scale(.5);
    }
    @SubscribeEvent public static void cleanup(ClientTickEvent.Post event) {
        for(MovementSound sound:new ArrayList<>(ACTIVE.values()))
            if(sound.door.isRemoved()||sound.door.getLevel()!=Minecraft.getInstance().level||sound.door.size==0)sound.finish();
    }
    private static final class MovementSound extends AbstractTickableSoundInstance {
        private final IndustrialDoorEntity door;
        private final int direction;
        MovementSound(IndustrialDoorEntity door,int direction) {
            super((direction>0?IndustrialFacilitiesModSounds.INDUSTRIAL_DOOR_OPEN:IndustrialFacilitiesModSounds.INDUSTRIAL_DOOR_CLOSE).get(),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());
            this.door=door;this.direction=direction;
            looping=false;delay=0;volume=.8f;pitch=1f;
            Vec3 center=center(door);x=center.x;y=center.y;z=center.z;
        }
        @Override public void tick() { if(direction(door)!=direction)finish(); }
        private void finish() {
            if(!isStopped()){stop();Minecraft.getInstance().getSoundManager().stop(this);}
            ACTIVE.remove(door,this);
        }
    }
    private IndustrialDoorAudio() {}
}
