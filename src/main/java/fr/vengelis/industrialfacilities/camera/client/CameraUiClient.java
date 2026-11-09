package fr.vengelis.industrialfacilities.camera.client;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.camera.ui.*;
import fr.vengelis.industrialfacilities.security.SecurityCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class CameraUiClient {
    public record FieldPreview(BlockPos position,int range,float yaw,float pitch,boolean sweepEnabled,float sweepAngle,int sweepPeriodTicks) {
        public FieldPreview(BlockPos position,int range,float yaw,float pitch){this(position,range,yaw,pitch,false,30,160);}
    }
    public static FieldPreview fieldPreview;
    public static float previewYaw(FieldPreview preview,float partial){
        var level=Minecraft.getInstance().level;double time=level==null?0:level.getGameTime()+(double)partial;long epoch=0;
        if(level!=null&&level.hasChunkAt(preview.position())&&level.getBlockEntity(preview.position()) instanceof SecurityCameraEntity camera)epoch=camera.sweepEpoch;
        return preview.yaw()+SecurityCameraEntity.sweepOffsetAt(time-epoch,preview.sweepEnabled(),preview.sweepAngle(),preview.sweepPeriodTicks());
    }
    public static float getPreviewYaw(float partial){return fieldPreview==null?0:previewYaw(fieldPreview,partial);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(CameraMenus.TYPE,CameraScreen::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){CameraMessages.receiver=state->{var player=Minecraft.getInstance().player;if(player!=null&&player.containerMenu instanceof CameraMenu menu&&menu.containerId==state.menuId())menu.receive(state);};}
    private CameraUiClient(){}
}
