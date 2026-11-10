package fr.vengelis.industrialfacilities.security.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.*;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Replays the actual endpoint meshes into caller-owned buffers; no envelopes are drawn. */
public final class NetworkTabletModels {
    /**
     * The incoming pose is in world coordinates (the caller may already subtract the camera).
     * All local translations are balanced. The caller controls targets, depth, shaders and RGB;
     * original texture coordinates and render lanes remain available for alpha-tested masks.
     */
    public static boolean render(NetworkTabletOutlines.Outline endpoint,float partial,PoseStack pose,MultiBufferSource buffers){
        ClientLevel level=Minecraft.getInstance().level;
        if(level==null||endpoint==null||!Float.isFinite(partial)||!level.hasChunkAt(endpoint.root()))return false;
        BlockEntity entity=level.getBlockEntity(endpoint.root());
        if(entity==null||entity.isRemoved()||entity.getLevel()!=level||!entity.getBlockPos().equals(endpoint.root()))return false;
        return switch(endpoint.kind()){
            case "reader","automation"->terminal(level,entity,endpoint,partial,pose,buffers);
            case "door"->entity instanceof IndustrialDoorEntity door&&endpoint.instance().equals(door.doorInstanceId)&&door(level,door,partial,pose,buffers);
            case "camera"->entity instanceof SecurityCameraEntity camera&&endpoint.instance().equals(camera.instanceId)&&camera(level,camera,partial,pose,buffers);
            // These endpoints' instance UUIDs are intentionally not synchronized to clients.
            // The short-lived server-authorized endpoint and loaded type are the same fallback
            // used by NetworkTabletOutlines.collect; this helper does not invent a new link.
            case "alarm"->entity instanceof AlarmBlockEntity&&staticModel(level,entity.getBlockPos(),pose,buffers);
            case "emitter"->entity instanceof SignalEmitterEntity&&staticModel(level,entity.getBlockPos(),pose,buffers);
            default->false;
        };
    }
    private static boolean terminal(ClientLevel level,BlockEntity entity,NetworkTabletOutlines.Outline endpoint,float partial,PoseStack pose,MultiBufferSource buffers){
        if(!(entity instanceof AccessTerminalEntity terminal)||!endpoint.instance().equals(terminal.instanceId)
            ||AccessTerminalEntity.resolve(level,endpoint.root())!=terminal||terminal.getBlockState().getValue(AccessTerminalBlock.HALF)!=DoubleBlockHalf.LOWER)return false;
        boolean automation=terminal instanceof CameraTerminalEntity;
        if(automation?!endpoint.kind().equals("automation"):!endpoint.kind().equals("reader")||terminal.encoder||terminal.bank)return false;
        // The normal terminal BER owns the complete 1.8-block baked model, including the
        // bevels, keyboard and both screen passes. Never draw its upper block separately.
        return blockEntity(terminal,partial,pose,buffers);
    }
    private static boolean camera(ClientLevel level,SecurityCameraEntity camera,float partial,PoseStack pose,MultiBufferSource buffers){
        if(renderer(camera)==null)return false;
        // The block model is only the fixed mount. The BER supplies the optical head and
        // its native mounting/yaw/pitch/sweep transform using this exact partial tick.
        boolean mount=staticModel(level,camera.getBlockPos(),pose,buffers);
        boolean head=blockEntity(camera,partial,pose,buffers);
        return mount||head;
    }
    private static boolean door(ClientLevel level,IndustrialDoorEntity root,float partial,PoseStack pose,MultiBufferSource buffers){
        if(root.size<5||root.size>15||!(root.getBlockState().getBlock() instanceof IndustrialDoorBlock))return false;
        BlockPos origin=root.doorOrigin();Direction facing=root.getBlockState().getValue(IndustrialDoorBlock.FACING);
        // Validate the complete loaded structure before emitting any vertices. No partial
        // outline is fabricated for a large multiblock whose far cells are still unloaded.
        for(int x=0;x<root.size;x++)for(int y=0;y<root.size;y++)if(!level.hasChunkAt(IndustrialDoorManager.at(origin,facing,x,y)))return false;
        List<IndustrialDoorEntity> frames=new ArrayList<>();boolean includesController=false;
        for(int x=0;x<root.size;x++)for(int y=0;y<root.size;y++){
            if(!IndustrialDoorManager.edge(x,y,root.size))continue;
            BlockPos pos=IndustrialDoorManager.at(origin,facing,x,y);BlockState state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof IndustrialDoorBlock)||!(level.getBlockEntity(pos) instanceof IndustrialDoorEntity frame)
                ||frame.isRemoved()||!frame.getBlockPos().equals(pos)||!Objects.equals(frame.owner,root.getBlockPos())||renderer(frame)==null)return false;
            if(frame==root)includesController=true;else if(frame.size!=0)return false;
            frames.add(frame);
        }
        if(!includesController)return false;
        // Each size-zero member BER emits its own connected frame and raised light rail.
        // The controller BER is included ONCE; it already emits its frame AND the two
        // clipped, animated panels, compensating for an eccentric controller internally.
        for(IndustrialDoorEntity frame:frames)blockEntity(frame,partial,pose,buffers);
        return true;
    }
    private static <T extends BlockEntity> BlockEntityRenderer<T> renderer(T entity){
        return entity.hasLevel()&&entity.getType().isValid(entity.getBlockState())?Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity):null;
    }
    private static <T extends BlockEntity> boolean blockEntity(T entity,float partial,PoseStack pose,MultiBufferSource buffers){
        var renderer=renderer(entity);if(renderer==null)return false;BlockPos pos=entity.getBlockPos();
        pose.pushPose();
        try{
            pose.translate(pos.getX(),pos.getY(),pos.getZ());
            // Direct invocation preserves the supplied mask buffers and avoids the normal
            // dispatcher distance cutoff, while reusing every original model transform.
            renderer.render(entity,partial,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        }finally{pose.popPose();}
        return true;
    }
    private static boolean staticModel(ClientLevel level,BlockPos pos,PoseStack pose,MultiBufferSource buffers){
        if(!level.hasChunkAt(pos))return false;BlockState state=level.getBlockState(pos);if(state.getRenderShape()!=RenderShape.MODEL)return false;
        var dispatcher=Minecraft.getInstance().getBlockRenderer();var model=dispatcher.getBlockModel(state);
        ModelData data=model.getModelData(level,pos,state,level.getModelData(pos));long seed=state.getSeed(pos);boolean rendered=false;
        pose.pushPose();
        try{
            pose.translate(pos.getX(),pos.getY(),pos.getZ());
            for(RenderType layer:model.getRenderTypes(state,RandomSource.create(seed),data)){
                // Use native world model data and UVs, but retain all exterior faces even
                // behind adjacent terrain. Entity lanes keep the atlas texture explicit
                // for the caller's mask/outline buffer wrapper.
                var out=buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer,false));
                dispatcher.getModelRenderer().tesselateBlock(level,model,state,pos,pose,out,false,RandomSource.create(seed),seed,OverlayTexture.NO_OVERLAY,data,layer);
                rendered=true;
            }
        }finally{pose.popPose();}
        return rendered;
    }
    private NetworkTabletModels(){}
}
