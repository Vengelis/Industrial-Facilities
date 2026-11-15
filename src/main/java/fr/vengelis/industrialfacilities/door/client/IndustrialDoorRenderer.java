package fr.vengelis.industrialfacilities.door.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.door.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.Objects;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID,value=Dist.CLIENT)
public class IndustrialDoorRenderer implements BlockEntityRenderer<IndustrialDoorEntity> {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,"textures/block/industrial_door_frame_32.png");
    private static final double MESH_RAIL_HEIGHT = 1 / 16.0;
    private static ResourceLocation texture(IndustrialDoorStyle style,String part) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID,style.texturePath(part));
    }
    public IndustrialDoorRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(IndustrialDoorContent.TYPE,IndustrialDoorRenderer::new);
    }
    @Override public boolean shouldRenderOffScreen(IndustrialDoorEntity be) { return be.size>0; }
    @Override public int getViewDistance() { return 128; }
    @Override public AABB getRenderBoundingBox(IndustrialDoorEntity be) {
        return be.size>0 ? new AABB(be.getBlockPos()).inflate(16) : new AABB(be.getBlockPos()).inflate(.125);
    }
    @Override public void render(IndustrialDoorEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(!(be.getBlockState().getBlock() instanceof IndustrialDoorBlock block)||be.getLevel()==null)return;
        Direction f=be.getBlockState().getValue(IndustrialDoorBlock.FACING);
        IndustrialDoorLighting lighting=new IndustrialDoorLighting(be.getLevel(),be.getBlockPos(),f);
        pose.pushPose();pose.translate(.5,0,.5);
        float angle=rotationDegrees(f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));pose.translate(-.5,0,-.5);
        VertexConsumer out;
        frame(be,f,pose,buffers,lighting);
        if(((IndustrialDoorBlock)be.getBlockState().getBlock()).controller && !be.getBlockState().getValue(IndustrialDoorBlock.ACTIVE)) {
            out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(block.style,"frame")));
            box(pose,out,.125f,.125f,1f,.875f,.875f,1.0625f,.328125f,.515625f,lighting);
            out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(block.style,"controller")));
            quad(pose,out,new float[][]{{.15625f,.15625f,1.0703125f},{.84375f,.15625f,1.0703125f},{.84375f,.84375f,1.0703125f},{.15625f,.84375f,1.0703125f}},0,0,1,1,lighting,0,0,1);
        }
        if(be.size>0) {
            var panel = texture(block.style, "panel");
            out = buffers.getBuffer(switch (block.style) {
                case GLASS -> RenderType.entityTranslucent(panel);
                case MESH -> RenderType.entityCutout(panel);
                default -> RenderType.entityCutoutNoCull(panel);
            });
            BlockPos origin=be.doorOrigin(),controller=be.getBlockPos();Direction right=f.getCounterClockWise();
            pose.translate((origin.getX()-controller.getX())*right.getStepX()+(origin.getZ()-controller.getZ())*right.getStepZ(),origin.getY()-controller.getY(),0);
            float p=be.previousProgress+(be.progress-be.previousProgress)*partial;
            double h=be.size-2;
            lighting=new IndustrialDoorLighting(be.getLevel(),origin,f,true);
            if (block.style == IndustrialDoorStyle.MESH) {
                var lower = IndustrialMeshDoorMotion.lower(h, p);
                var upper = IndustrialMeshDoorMotion.upper(h, p);
                meshPanel(pose, out, (int) h, lower, IndustrialMeshDoorMotion.HAZARD_HEIGHT, MESH_RAIL_HEIGHT, lighting);
                meshPanel(pose, out, (int) h, upper, MESH_RAIL_HEIGHT, 0, lighting);
                var rail = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(block.style, "frame_middle")));
                meshRail(pose, rail, (int) h, upper.bottom(), upper.depth(), lighting);
                meshRail(pose, rail, (int) h, lower.end() + lower.rise() - MESH_RAIL_HEIGHT, lower.depth(), lighting);
                var hazard = buffers.getBuffer(RenderType.entityCutout(texture(block.style, "hazard")));
                meshHazardBand(pose, hazard, (int) h, IndustrialMeshDoorMotion.hazardBeam(lower), lighting);
            } else if (block.style.lifts()) {
                segment(pose,out,0,h,h*p,h,lighting);
            } else {
                double half=h/2,travel=half*p;
                segment(pose,out,0,half,-travel,h,lighting);
                segment(pose,out,half,h,travel,h,lighting);
            }
        }
        pose.popPose();
    }
    public static float rotationDegrees(Direction facing) {
        return switch(facing){case EAST->90;case NORTH->180;case WEST->270;default->0;};
    }
    public static IndustrialDoorFrameLayout.Tile frameTile(IndustrialDoorEntity be,Direction f) {
        return IndustrialDoorFrameLayout.tile(frameMask(be,f));
    }
    private static int frameMask(IndustrialDoorEntity be,Direction f) {
        int mask=0;
        if(compatible(be,be.getBlockPos().above(),f))mask|=1;
        if(compatible(be,be.getBlockPos().relative(f.getCounterClockWise()),f))mask|=2;
        if(compatible(be,be.getBlockPos().below(),f))mask|=4;
        if(compatible(be,be.getBlockPos().relative(f.getClockWise()),f))mask|=8;
        return mask;
    }
    private static boolean compatible(IndustrialDoorEntity be,BlockPos pos,Direction f) {
        if(!be.getLevel().hasChunkAt(pos))return false;
        BlockState state=be.getLevel().getBlockState(pos);
        return state.getBlock() instanceof IndustrialDoorBlock nextBlock
            && nextBlock.style==((IndustrialDoorBlock)be.getBlockState().getBlock()).style && state.getValue(IndustrialDoorBlock.FACING)==f
            && be.getLevel().getBlockEntity(pos) instanceof IndustrialDoorEntity next && Objects.equals(be.owner,next.owner);
    }
    private static IndustrialDoorEntity root(IndustrialDoorEntity be) {
        if(be.size>0)return be;
        return be.owner!=null&&be.getLevel().getBlockEntity(be.owner) instanceof IndustrialDoorEntity d&&d.size>0?d:null;
    }
    private static void frame(IndustrialDoorEntity be,Direction f,PoseStack pose,MultiBufferSource buffers,IndustrialDoorLighting light) {
        renderFrameBody(be,f,pose,buffers,light);
        if(!be.getBlockState().getValue(IndustrialDoorBlock.ACTIVE)||!be.getBlockState().getValue(IndustrialDoorBlock.RAIL))return;
        IndustrialDoorEntity d=root(be);
        if(d==null)return;
        int row=be.getBlockPos().getY()-d.doorOrigin().getY();
        float y0=(float)IndustrialDoorVisualState.railStart(d.size,row),y1=(float)IndustrialDoorVisualState.railEnd(d.size,row);
        if(y1<=y0)return;
        int color=IndustrialDoorVisualState.lampColor(d.progress,d.targetProgress(),be.getLevel().getGameTime());
        // Decorative housings stand proud of the full cube by one eighth of a block.
        // Its face, dark recess and opaque lens have distinct depths; no additive overlay.
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(FRAME));
        for(boolean backSide:new boolean[]{false,true}) {
            float z0=backSide?-.109375f:1f,z1=backSide?0:1.109375f;
            box(pose,out,.34375f,y0,z0,.65625f,y1,z1,.015625f,.515625f,light);
            box(pose,out,.375f,y0,backSide?-.1171875f:1.109375f,.625f,y1,backSide?-.109375f:1.1171875f,.328125f,.515625f,light);
            float lens0=(float)Math.max(y0,IndustrialDoorVisualState.railBottom(d.size)+.03125-row);
            float lens1=(float)Math.min(y1,IndustrialDoorVisualState.railTop(d.size)-.03125-row);
            if(color!=IndustrialDoorVisualState.OFF&&lens1>lens0)lens(pose,out,.4375f,.5625f,lens0,lens1,backSide?-.125f:1.125f,color);
        }
    }
    private static void renderFrameBody(IndustrialDoorEntity be,Direction f,PoseStack pose,MultiBufferSource buffers,IndustrialDoorLighting light) {
        int mask=frameMask(be,f);
        var style=((IndustrialDoorBlock)be.getBlockState().getBlock()).style;
        // Front and back follow the same physical XY path. Each corner has two mitered rails.
        frameFace(pose,buffers,style,new float[][]{{0,0,1},{1,0,1},{1,1,1},{0,1,1}},mask,light,0,0,1);
        frameFace(pose,buffers,style,new float[][]{{0,0,0},{1,0,0},{1,1,0},{0,1,0}},mask,light,0,0,-1);
        // Other faces only see the connections in their own plane. A corner thus gets
        // a U-shaped end cap across the thickness, with one return at BOTH front and back.
        // Shared internal faces are hidden by the adjacent full frame cube.
        if((mask&1)==0)frameFace(pose,buffers,style,new float[][]{{0,1,1},{1,1,1},{1,1,0},{0,1,0}},mask&10,light,0,1,0);
        if((mask&4)==0)frameFace(pose,buffers,style,new float[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},mask&10,light,0,-1,0);
        if((mask&8)==0)frameFace(pose,buffers,style,new float[][]{{0,0,0},{0,0,1},{0,1,1},{0,1,0}},mask&5,light,-1,0,0);
        if((mask&2)==0)frameFace(pose,buffers,style,new float[][]{{1,0,1},{1,0,0},{1,1,0},{1,1,1}},mask&5,light,1,0,0);
    }
    private static void frameFace(PoseStack pose,MultiBufferSource buffers,IndustrialDoorStyle style,float[][] points,int mask,IndustrialDoorLighting light,float nx,float ny,float nz) {
        var tile=IndustrialDoorFrameLayout.tile(mask);
        boolean corner=tile.part().equals("frame_corner");
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(style,corner?"frame_middle":tile.part())));
        if(!corner){material(pose,out,points,tile,light,nx,ny,nz);return;}
        // Bend the existing full-resolution middle material instead of rotating a
        // one-sided L image onto all six faces. Both strips sample identical UVs
        // along the diagonal, closing the inner AND outer rail without a gap.
        float[][][] triangles={{{0,0},{1,1},{0,1},{0,1}},{{0,0},{1,0},{1,1},{1,1}}};
        for(int half=0;half<2;half++) {
            float[][] vertices=new float[4][3],coords=new float[4][2];
            for(int i=0;i<4;i++) {
                float x=triangles[half][i][0],y=triangles[half][i][1];
                coords[i]=half==0?new float[]{x,1-y}:new float[]{y,1-x};
                var rotated=IndustrialDoorFrameLayout.uv(x,y,tile.turns());
                for(int axis=0;axis<3;axis++)vertices[i][axis]=points[0][axis]+rotated[0]*(points[1][axis]-points[0][axis])+rotated[1]*(points[3][axis]-points[0][axis]);
            }
            textured(pose,out,vertices,coords,light,nx,ny,nz);
        }
    }
    private static void material(PoseStack pose,VertexConsumer out,float[][] p,IndustrialDoorFrameLayout.Tile tile,IndustrialDoorLighting light,float nx,float ny,float nz) {
        float[][] coords={{0,1},{1,1},{1,0},{0,0}};
        for(int i=0;i<4;i++)coords[i]=IndustrialDoorFrameLayout.uv(coords[i][0],coords[i][1],tile.turns());
        textured(pose,out,p,coords,light,nx,ny,nz);
    }
    private static void box(PoseStack pose,VertexConsumer out,float x0,float y0,float z0,float x1,float y1,float z1,float u,float v,IndustrialDoorLighting light) {
        quad(pose,out,new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},u,v,u,v,light,0,0,1);
        quad(pose,out,new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},u,v,u,v,light,0,0,-1);
        quad(pose,out,new float[][]{{x0,y1,z1},{x1,y1,z1},{x1,y1,z0},{x0,y1,z0}},u,v,u,v,light,0,1,0);
        quad(pose,out,new float[][]{{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1}},u,v,u,v,light,0,-1,0);
        quad(pose,out,new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},u,v,u,v,light,-1,0,0);
        quad(pose,out,new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}},u,v,u,v,light,1,0,0);
    }
    private static void lens(PoseStack pose,VertexConsumer out,float x0,float x1,float y0,float y1,float z,int color) {
        float[][] points={{x0,y0,z},{x1,y0,z},{x1,y1,z},{x0,y1,z}};
        for(float[] p:points)out.addVertex(pose.last(),p[0],p[1],p[2]).setColor(color>>16&255,color>>8&255,color&255,255)
            .setUv(.171875f,.515625f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,z<0?-1:1);
    }
    private static void meshPanel(PoseStack pose, VertexConsumer out, int height,
                                  IndustrialMeshDoorMotion.Panel panel, double footHeight, double headHeight, IndustrialDoorLighting light) {
        // Tile the lattice per block and clip the moving texture at the lintel.
        double start = panel.start() + footHeight;
        double end = Math.min(panel.end() - headHeight, height - panel.rise());
        if (start >= end) return;
        float z = (float) panel.depth();
        for (int row = (int) start; row < end; row++) {
            double from = Math.max(row, start);
            double to = Math.min(row + 1, end);
            float bottom = (float) (1 + from + panel.rise());
            float top = (float) (1 + to + panel.rise());
            float topV = (float) (row + 1 - to);
            float bottomV = (float) (row + 1 - from);
            for (int column = 0; column < height; column++) {
                float left = column + 1, right = left + 1;
                // Opposite winding on the same plane keeps holes aligned from either side.
                panelQuad(pose, out, new float[][]{{left,bottom,z},{right,bottom,z},{right,top,z},{left,top,z}},
                    0, topV, 1, bottomV, light, 0, 0, 1);
                panelQuad(pose, out, new float[][]{{right,bottom,z},{left,bottom,z},{left,top,z},{right,top,z}},
                    1, topV, 0, bottomV, light, 0, 0, -1);
            }
        }
    }

    private static void meshRail(PoseStack pose, VertexConsumer out, int height, double bottom, double depth,
                                 IndustrialDoorLighting light) {
        if (bottom >= height) return;
        float z = (float) depth;
        box(pose, out, 1, (float) (1 + bottom), z - .04f, height + 1,
            (float) (1 + Math.min(height, bottom + MESH_RAIL_HEIGHT)), z + .04f,
            9.5f / 32, 10.5f / 32, light);
    }

    private static void meshHazardBand(PoseStack pose, VertexConsumer out, int height,
                                       IndustrialMeshDoorMotion.Panel beam, IndustrialDoorLighting light) {
        double bottom = beam.bottom();
        if (bottom >= height) return;
        double top = Math.min(height, beam.end() + beam.rise());
        float y0 = (float) (1 + bottom), y1 = (float) (1 + top);
        float topV = (float) (1 - (top - bottom) / IndustrialMeshDoorMotion.HAZARD_HEIGHT);
        float front = (float) beam.farDepth(), back = (float) beam.nearDepth();
        float left = 1, right = height + 1;
        for (int col = 0; col < height; col++) {
            float x0 = col + 1, x1 = x0 + 1;
            panelQuad(pose, out, new float[][]{{x0,y0,front},{x1,y0,front},{x1,y1,front},{x0,y1,front}},
                0, topV, 1, 1, light, 0, 0, 1);
            panelQuad(pose, out, new float[][]{{x1,y0,back},{x0,y0,back},{x0,y1,back},{x1,y1,back}},
                0, topV, 1, 1, light, 0, 0, -1);
        }
        float u = .5f / 32, v = 1.5f / 8;
        panelQuad(pose, out, new float[][]{{left,y1,front},{right,y1,front},{right,y1,back},{left,y1,back}},
            u, v, u, v, light, 0, 1, 0);
        panelQuad(pose, out, new float[][]{{left,y0,back},{right,y0,back},{right,y0,front},{left,y0,front}},
            u, v, u, v, light, 0, -1, 0);
        panelQuad(pose, out, new float[][]{{left,y0,back},{left,y0,front},{left,y1,front},{left,y1,back}},
            u, v, u, v, light, -1, 0, 0);
        panelQuad(pose, out, new float[][]{{right,y0,front},{right,y0,back},{right,y1,back},{right,y1,front}},
            u, v, u, v, light, 1, 0, 0);
    }
    private static void segment(PoseStack pose,VertexConsumer out,double start,double end,double offset,double height,IndustrialDoorLighting light) {
        double a=Math.max(0,start+offset),b=Math.min(height,end+offset);
        if(b<=a)return;
        float y0=(float)(1+a),y1=(float)(1+b),x0=1,x1=(float)height+1;
        // Both halves reference the same original door image, including while clipped by the frame.
        float v0=IndustrialDoorVisualState.panelV(height,b-offset),v1=IndustrialDoorVisualState.panelV(height,a-offset);
        panelQuad(pose,out,new float[][]{{x0,y0,.625f},{x1,y0,.625f},{x1,y1,.625f},{x0,y1,.625f}},0,v0,1,v1,light,0,0,1);
        panelQuad(pose,out,new float[][]{{x1,y0,.375f},{x0,y0,.375f},{x0,y1,.375f},{x1,y1,.375f}},0,v0,1,v1,light,0,0,-1);
        panelQuad(pose,out,new float[][]{{x0,y1,.625f},{x1,y1,.625f},{x1,y1,.375f},{x0,y1,.375f}},0,0,.5f,.5f,light,0,1,0);
        panelQuad(pose,out,new float[][]{{x0,y0,.375f},{x1,y0,.375f},{x1,y0,.625f},{x0,y0,.625f}},0,0,.5f,.5f,light,0,-1,0);
        panelQuad(pose,out,new float[][]{{x0,y0,.375f},{x0,y0,.625f},{x0,y1,.625f},{x0,y1,.375f}},0,0,.5f,.5f,light,-1,0,0);
        panelQuad(pose,out,new float[][]{{x1,y0,.625f},{x1,y0,.375f},{x1,y1,.375f},{x1,y1,.625f}},0,0,.5f,.5f,light,1,0,0);
    }
    private static void panelQuad(PoseStack pose,VertexConsumer out,float[][] points,float u0,float v0,float u1,float v1,IndustrialDoorLighting light,float nx,float ny,float nz) {
        // Break only the geometry into block-sized light samples. UVs still refer
        // to the single full panel image, including the two clipped moving halves.
        float[] columns=lightCuts(points[0],points[1]),rows=lightCuts(points[0],points[3]);
        for(int row=0;row<rows.length-1;row++)for(int column=0;column<columns.length-1;column++){
            float a=columns[column],b=columns[column+1],c=rows[row],d=rows[row+1];
            float[][] p=new float[4][3];float[][] st={{a,c},{b,c},{b,d},{a,d}};
            for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++)p[i][axis]=points[0][axis]+st[i][0]*(points[1][axis]-points[0][axis])+st[i][1]*(points[3][axis]-points[0][axis]);
            quad(pose,out,p,u0+(u1-u0)*a,v1+(v0-v1)*d,u0+(u1-u0)*b,v1+(v0-v1)*c,light,nx,ny,nz);
        }
    }
    private static float[] lightCuts(float[] start,float[] end) {
        int axis=0;
        for(int i=1;i<3;i++)if(Math.abs(end[i]-start[i])>Math.abs(end[axis]-start[axis]))axis=i;
        float delta=end[axis]-start[axis];
        java.util.List<Float> cuts=new java.util.ArrayList<>();cuts.add(0f);cuts.add(1f);
        for(int cell=(int)Math.floor(Math.min(start[axis],end[axis]))+1;cell<Math.max(start[axis],end[axis]);cell++)cuts.add((cell-start[axis])/delta);
        cuts.sort(Float::compare);float[] result=new float[cuts.size()];
        for(int i=0;i<result.length;i++)result[i]=cuts.get(i);
        return result;
    }
    private static void quad(PoseStack pose,VertexConsumer out,float[][] p,float u0,float v0,float u1,float v1,IndustrialDoorLighting light,float nx,float ny,float nz) {
        textured(pose,out,p,new float[][]{{u0,v1},{u1,v1},{u1,v0},{u0,v0}},light,nx,ny,nz);
    }
    private static void textured(PoseStack pose,VertexConsumer out,float[][] p,float[][] uv,IndustrialDoorLighting light,float nx,float ny,float nz) {
        int packedLight=light.face(p,nx,ny,nz);
        for(int i=0;i<4;i++)out.addVertex(pose.last(),p[i][0],p[i][1],p[i][2]).setColor(255,255,255,255)
            .setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light.vertex(p[i],nz,packedLight)).setNormal(pose.last(),nx,ny,nz);
    }
}
