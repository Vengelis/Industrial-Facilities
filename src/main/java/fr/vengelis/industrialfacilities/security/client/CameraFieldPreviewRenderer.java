package fr.vengelis.industrialfacilities.security.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.camera.client.CameraUiClient;
import fr.vengelis.industrialfacilities.item.TerminalLinkCardItem;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded native world preview. It draws an outline and never performs a scan or loads chunks. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class CameraFieldPreviewRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        CameraUiClient.FieldPreview draft=CameraUiClient.fieldPreview;BlockPos pos;
        if(draft!=null)pos=draft.position();
        else{
            if(!(mc.player.getMainHandItem().getItem() instanceof TerminalLinkCardItem)||!TerminalLinkCardItem.automationMode(mc.player.getMainHandItem())||!(mc.hitResult instanceof BlockHitResult hit))return;
            pos=hit.getBlockPos();
        }
        if(!mc.level.hasChunkAt(pos)||!(mc.level.getBlockEntity(pos) instanceof SecurityCameraEntity camera)||camera.creator==null||!camera.creator.equals(mc.player.getUUID())||mc.player.distanceToSqr(pos.getCenter())>64*64)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int range=Math.clamp(draft==null?camera.range:draft.range(),1,16);
        float yaw=draft==null?camera.effectiveYaw(partial):CameraUiClient.previewYaw(draft,partial),pitch=draft==null?camera.pitch:draft.pitch();
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        var facing=camera.getBlockState().getValue(SecurityCameraBlock.FACING);double a=Math.toRadians(yaw),p=Math.toRadians(pitch),x=facing.getStepX(),z=facing.getStepZ();
        Vec3 forward=new Vec3((x*Math.cos(a)-z*Math.sin(a))*Math.cos(p),-Math.sin(p),(x*Math.sin(a)+z*Math.cos(a))*Math.cos(p)).normalize();
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize(),origin=pos.getCenter().add(forward.scale(SecurityCameraEntity.LENS_DISTANCE)),far=origin.add(forward.scale(range));
        Vec3[] corners=new Vec3[4];double h=range*Math.tan(Math.toRadians(30));int i=0;
        for(int horizontal:new int[]{-1,1})for(int vertical:new int[]{-1,1})corners[i++]=far.add(right.scale(range*horizontal)).add(up.scale(h*vertical));
        PoseStack pose=event.getPoseStack();pose.pushPose();var eye=event.getCamera().getPosition();pose.translate(-eye.x,-eye.y,-eye.z);
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.lines());
        for(var corner:corners)line(pose,out,origin,corner);
        line(pose,out,corners[0],corners[1]);line(pose,out,corners[0],corners[2]);line(pose,out,corners[1],corners[3]);line(pose,out,corners[2],corners[3]);line(pose,out,origin,far);
        pose.popPose();buffers.endBatch(RenderType.lines());
    }
    private static void line(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to){
        Vec3 normal=to.subtract(from).normalize();
        out.addVertex(pose.last(),(float)from.x,(float)from.y,(float)from.z).setColor(185,92,238,190).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
        out.addVertex(pose.last(),(float)to.x,(float)to.y,(float)to.z).setColor(185,92,238,190).setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
    }
    private CameraFieldPreviewRenderer(){}
}
