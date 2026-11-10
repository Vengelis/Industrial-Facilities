package fr.vengelis.industrialfacilities.security.client;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;

/** Actual textured model masks, converted to exterior screen-space silhouettes. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class NetworkTabletSilhouettes {
    public record MaskFrame(NetworkTabletOutlines.Outline endpoint,RenderTarget target,int left,int bottom,int width,int height,long frame,float partial){}
    /** Optional read-only diagnostic observer; it must leave framebuffer state unchanged. */
    public static Consumer<MaskFrame> maskObserver;
    private record Rect(int left,int bottom,int width,int height){
        Rect expand(int amount,int w,int h){int x=Math.max(0,left-amount),y=Math.max(0,bottom-amount),r=Math.min(w,left+width+amount),t=Math.min(h,bottom+height+amount);return new Rect(x,y,r-x,t-y);}
    }
    private static ShaderInstance maskShader,edgeShader;
    private static TextureTarget mask,contours;
    private static final Map<RenderType,ResourceLocation> textures=new IdentityHashMap<>();
    private static final Map<ResourceLocation,RenderType> maskTypes=new HashMap<>();
    private static final LinkedHashMap<RenderType,ByteBufferBuilder> builders=new LinkedHashMap<>();
    private static ByteBufferBuilder fallback;private static MultiBufferSource.BufferSource buffers;private static boolean dirty=true;private static long frame;
    private static final Set<RenderType> unknown=new HashSet<>();
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,path);}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws IOException{
        event.registerShader(new ShaderInstance(event.getResourceProvider(),id("network_tablet_mask"),DefaultVertexFormat.POSITION_TEX_COLOR),shader->maskShader=shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),id("network_tablet_silhouette"),DefaultVertexFormat.POSITION_TEX_COLOR),shader->edgeShader=shader);
        dirty=true;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){releaseTargets();}
    public static RenderTarget maskTarget(){return mask;}
    public static RenderTarget contoursTarget(){return contours;}
    private static void releaseTargets(){if(mask!=null)mask.destroyBuffers();if(contours!=null)contours.destroyBuffers();mask=null;contours=null;}
    private static void ensure(int width,int height){
        if(mask==null||mask.width!=width||mask.height!=height){releaseTargets();mask=new TextureTarget(width,height,true,Minecraft.ON_OSX);contours=new TextureTarget(width,height,false,Minecraft.ON_OSX);mask.setClearColor(0,0,0,0);contours.setClearColor(0,0,0,0);}
        if(!dirty)return;
        for(var builder:builders.values())builder.close();builders.clear();if(fallback!=null)fallback.close();
        textures.clear();maskTypes.clear();unknown.clear();
        registerTexture(TextureAtlas.LOCATION_BLOCKS);
        for(var texture:Minecraft.getInstance().getResourceManager().listResources("textures",location->location.getNamespace().equals(IndustrialFacilitiesMod.MODID)&&location.getPath().endsWith(".png")).keySet())registerTexture(texture);
        textures.put(RenderType.solid(),TextureAtlas.LOCATION_BLOCKS);textures.put(RenderType.cutout(),TextureAtlas.LOCATION_BLOCKS);textures.put(RenderType.cutoutMipped(),TextureAtlas.LOCATION_BLOCKS);textures.put(RenderType.translucent(),TextureAtlas.LOCATION_BLOCKS);
        fallback=new ByteBufferBuilder(1536);buffers=MultiBufferSource.immediateWithBuffers(builders,fallback);dirty=false;
    }
    private static void registerTexture(ResourceLocation texture){
        textures.put(RenderType.entitySolid(texture),texture);textures.put(RenderType.entityCutout(texture),texture);textures.put(RenderType.entityCutoutNoCull(texture),texture);textures.put(RenderType.entityTranslucent(texture),texture);textures.put(RenderType.eyes(texture),texture);
        RenderType type=RenderType.create("industrial_tablet_mask_"+maskTypes.size(),DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,1536,false,false,
            RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->maskShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false)).setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
        maskTypes.put(texture,type);builders.put(type,new ByteBufferBuilder(1536));
    }
    private static final VertexConsumer DISCARD=new VertexConsumer(){
        public VertexConsumer addVertex(float x,float y,float z){return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
    };
    private static MultiBufferSource colored(int color){return original->{
        ResourceLocation texture=textures.get(original);
        if(texture==null){if(unknown.add(original))IndustrialFacilitiesMod.LOGGER.warn("Unsupported tablet model material: {}",original);return DISCARD;}
        VertexConsumer out=buffers.getBuffer(maskTypes.get(texture));
        return new VertexConsumer(){
            public VertexConsumer addVertex(float x,float y,float z){out.addVertex(x,y,z);return this;}
            public VertexConsumer setColor(int r,int g,int b,int a){out.setColor(color>>16&255,color>>8&255,color&255,a);return this;}
            public VertexConsumer setUv(float u,float v){out.setUv(u,v);return this;}
            public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
        };
    };}
    private static AABB envelope(NetworkTabletOutlines.Outline endpoint){return endpoint.bounds().inflate(endpoint.kind().equals("camera")?.8:endpoint.kind().equals("door")?.2:.06);}
    private static Rect project(AABB box,RenderLevelStageEvent event,int width,int height){
        Vec3 eye=event.getCamera().getPosition();double minX=width,minY=height,maxX=0,maxY=0;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
            var p=new Vector4f((float)((x==0?box.minX:box.maxX)-eye.x),(float)((y==0?box.minY:box.maxY)-eye.y),(float)((z==0?box.minZ:box.maxZ)-eye.z),1);
            event.getModelViewMatrix().transform(p);event.getProjectionMatrix().transform(p);
            if(p.w<=.001f)return new Rect(0,0,width,height);
            double px=(p.x/p.w*.5+.5)*width,py=(p.y/p.w*.5+.5)*height;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
        }
        int l=(int)Math.max(0,Math.floor(minX)),b=(int)Math.max(0,Math.floor(minY)),r=(int)Math.min(width,Math.ceil(maxX)),t=(int)Math.min(height,Math.ceil(maxY));return r<=l||t<=b?null:new Rect(l,b,r-l,t-b);
    }
    private static void quad(){
        RenderSystem.setShader(()->edgeShader);var builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(-1,-1,0).setUv(0,0).setColor(-1);builder.addVertex(1,-1,0).setUv(1,0).setColor(-1);builder.addVertex(1,1,0).setUv(1,1).setColor(-1);builder.addVertex(-1,1,0).setUv(0,1).setColor(-1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }
    public static int render(RenderLevelStageEvent event,List<NetworkTabletOutlines.Outline> endpoints){
        if(endpoints.isEmpty()||maskShader==null||edgeShader==null)return 0;
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();int width=main.width,height=main.height;if(width<1||height<1)return 0;
        int rendered=0;float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);frame++;
        try(var state=new TabletRenderState()){
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            ensure(width,height);RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);RenderSystem.setShaderColor(1,1,1,1);
            contours.clear(Minecraft.ON_OSX);maskShader.safeGetUniform("MaskProjection").set(event.getProjectionMatrix());edgeShader.safeGetUniform("TexelSize").set(1f/width,1f/height);
            PoseStack pose=new PoseStack();pose.mulPose(event.getModelViewMatrix());Vec3 eye=event.getCamera().getPosition();pose.translate(-eye.x,-eye.y,-eye.z);
            for(var endpoint:endpoints){
                AABB bounds=envelope(endpoint);if(!event.getFrustum().isVisible(bounds))continue;Rect region=project(bounds,event,width,height);if(region==null)continue;
                Rect clear=region.expand(10,width,height),output=region.expand(4,width,height);
                RenderSystem.enableScissor(clear.left,clear.bottom,clear.width,clear.height);RenderSystem.depthMask(true);mask.clear(Minecraft.ON_OSX);mask.bindWrite(true);
                if(!NetworkTabletModels.render(endpoint,partial,pose,colored(endpoint.color())))continue;
                buffers.endBatch();
                if(maskObserver!=null)maskObserver.accept(new MaskFrame(endpoint,mask,clear.left,clear.bottom,clear.width,clear.height,frame,partial));
                contours.bindWrite(true);RenderSystem.enableScissor(output.left,output.bottom,output.width,output.height);
                RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendEquation(GL14.GL_FUNC_ADD);RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                edgeShader.setSampler("MaskSampler",mask.getColorTextureId());edgeShader.safeGetUniform("CompositeOnly").set(0);quad();rendered++;
            }
            RenderSystem.disableScissor();state.bindCaller();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendEquation(GL14.GL_FUNC_ADD);RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            edgeShader.setSampler("MaskSampler",contours.getColorTextureId());edgeShader.safeGetUniform("CompositeOnly").set(1);quad();
        }
        return rendered;
    }
    private NetworkTabletSilhouettes(){}
}
