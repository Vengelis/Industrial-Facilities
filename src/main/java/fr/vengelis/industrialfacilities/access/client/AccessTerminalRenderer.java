package fr.vengelis.industrialfacilities.access.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.*;

/** Inventory and multiblock use the exact same baked, bevelled terminal model. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public final class AccessTerminalRenderer implements BlockEntityRenderer<AccessTerminalEntity> {
    private static final ResourceLocation OPEN_SCREEN=ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"block/terminal_reader_open_screen_32");
    private static final Map<BakedQuad,BakedQuad> OPEN_QUADS=Collections.synchronizedMap(new WeakHashMap<>());
    public AccessTerminalRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(AccessContent.TYPE,AccessTerminalRenderer::new);}
    @SubscribeEvent public static void registerScreen(ModelEvent.RegisterAdditional event){
        event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"block/terminal_reader_open_screen")));
    }
    @SubscribeEvent public static void modelReloaded(ModelEvent.BakingCompleted event){OPEN_QUADS.clear();}
    @SubscribeEvent public static void itemColours(RegisterColorHandlersEvent.Item event){
        event.register((stack,index)->0xff000000|colour(index,1,false),IndustrialFacilitiesModItems.TERMINAL_READER.get(),IndustrialFacilitiesModItems.TERMINAL_ENCODER.get(),IndustrialFacilitiesModItems.TERMINAL_BADGE_BANK.get());
    }
    public static int colour(int index,int level,boolean granted){
        if(index>=100&&index<105)return index-100<level?0xf2f5f8:0x343841;
        if(index==200)return granted?0x35ef68:0xf13b43;
        return 0xffffff;
    }
    public static BakedModel model(AccessTerminalEntity be){
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.inventory(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock())));
    }
    /** Remap only the screen's sprite; every bevel, vertex and normal stays intact. */
    public static BakedQuad screenQuad(BakedQuad quad,boolean granted){
        if(!granted||!quad.getSprite().contents().name().getPath().equals("block/terminal_reader_screen_32"))return quad;
        return OPEN_QUADS.computeIfAbsent(quad,source->{
            TextureAtlasSprite closed=source.getSprite(),open=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(OPEN_SCREEN);
            int[] vertices=source.getVertices().clone();int stride=vertices.length/4;
            for(int i=0;i<4;i++){
                int u=i*stride+4,v=i*stride+5;
                float relativeU=(Float.intBitsToFloat(vertices[u])-closed.getU0())/(closed.getU1()-closed.getU0());
                float relativeV=(Float.intBitsToFloat(vertices[v])-closed.getV0())/(closed.getV1()-closed.getV0());
                vertices[u]=Float.floatToRawIntBits(open.getU0()+relativeU*(open.getU1()-open.getU0()));
                vertices[v]=Float.floatToRawIntBits(open.getV0()+relativeV*(open.getV1()-open.getV0()));
            }
            return new BakedQuad(vertices,source.getTintIndex(),source.getDirection(),open,source.isShade(),source.hasAmbientOcclusion());
        });
    }
    @Override public AABB getRenderBoundingBox(AccessTerminalEntity be){return new AABB(be.getBlockPos()).expandTowards(0,1,0).inflate(.05);}
    @Override public int getViewDistance(){return 64;}
    @Override public void render(AccessTerminalEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null||!be.getBlockState().getValue(AccessTerminalBlock.ASSEMBLED)||be.getBlockState().getValue(AccessTerminalBlock.HALF)!=DoubleBlockHalf.LOWER)return;
        Direction facing=be.getBlockState().getValue(AccessTerminalBlock.FACING);
        var lighting=new Lighting(be,facing);var model=model(be);var random=RandomSource.create(42);
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch(facing){case EAST->90;case NORTH->180;case WEST->270;default->0;}));
        pose.translate(-.5,0,-.5);
        // All sprites belong to the block atlas: no stale buffer when switching
        // between casing, screen, keys and the five dynamic level indicators.
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for(int side=0;side<=6;side++){
            random.setSeed(42);
            for(BakedQuad quad:model.getQuads(null,side==6?null:Direction.values()[side],random)){
                int index=quad.getTintIndex();int color=colour(index,Math.clamp(be.accessLevel,1,5),be.accessGranted);
                String sprite=quad.getSprite().contents().name().getPath();
                boolean emissive=sprite.contains("terminal_reader_screen")||sprite.contains("terminal_encoder_screen")||sprite.contains("terminal_badge_bank_screen")
                    ||index==200||(index>=100&&index<100+Math.clamp(be.accessLevel,1,5));
                int packedLight=emissive?LightTexture.FULL_BRIGHT:lighting.sample(quad);
                out.putBulkData(pose.last(),screenQuad(quad,be.accessGranted),(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1f,packedLight,OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
    }
    private static final class Lighting{
        final AccessTerminalEntity be;final Direction facing,right;final Map<BlockPos,Integer> cache=new HashMap<>();
        Lighting(AccessTerminalEntity be,Direction facing){this.be=be;this.facing=facing;right=facing.getCounterClockWise();}
        int sample(BakedQuad quad){
            int[] data=quad.getVertices();int stride=data.length/4;double x=0,y=0,z=0;
            float[][] points=new float[3][3];
            for(int i=0;i<4;i++){
                float px=Float.intBitsToFloat(data[i*stride]),py=Float.intBitsToFloat(data[i*stride+1]),pz=Float.intBitsToFloat(data[i*stride+2]);
                x+=px/4d;y+=py/4d;z+=pz/4d;if(i<3)points[i]=new float[]{px,py,pz};
            }
            var a=new org.joml.Vector3f(points[1]).sub(new org.joml.Vector3f(points[0]));
            var b=new org.joml.Vector3f(points[2]).sub(new org.joml.Vector3f(points[0]));
            var normal=a.cross(b);var direction=quad.getDirection();
            if(normal.lengthSquared()<.000001f)normal.set(direction.getStepX(),direction.getStepY(),direction.getStepZ());else normal.normalize();
            if(normal.x*direction.getStepX()+normal.y*direction.getStepY()+normal.z*direction.getStepZ()<0)normal.negate();
            x+=normal.x*.501;y+=normal.y*.501;z+=normal.z*.501;
            var origin=be.getBlockPos();
            var pos=BlockPos.containing(origin.getX()+.5+right.getStepX()*(x-.5)+facing.getStepX()*(z-.5),origin.getY()+y,origin.getZ()+.5+right.getStepZ()*(x-.5)+facing.getStepZ()*(z-.5));
            return cache.computeIfAbsent(pos,p->LevelRenderer.getLightColor(be.getLevel(),p));
        }
    }
}
