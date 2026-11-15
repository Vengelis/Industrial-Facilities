package fr.vengelis.industrialfacilities.door.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.IndustrialMetalDoorBlastBlock;
import fr.vengelis.industrialfacilities.door.IndustrialLiftDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialMetalDoorContent;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class IndustrialLiftDoorRenderer implements BlockEntityRenderer<IndustrialLiftDoorEntity> {
    private static final float THICKNESS = 3 / 16f;

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(IndustrialMetalDoorContent.LIFT_TYPE, context -> new IndustrialLiftDoorRenderer());
    }

    @Override public AABB getRenderBoundingBox(IndustrialLiftDoorEntity door) {
        return new AABB(door.getBlockPos()).expandTowards(0, 1, 0);
    }

    @Override
    public void render(IndustrialLiftDoorEntity door, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float travel = 2 * Mth.lerp(partial, door.previousProgress, door.progress);
        if (travel >= 2 || door.getLevel() == null) return;
        var state = door.getBlockState();
        var facing = state.getValue(DoorBlock.FACING);
        boolean hingeNorth = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
        String style = state.getBlock() instanceof IndustrialMetalDoorBlastBlock ? "blast" : "radiation";
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YN.rotationDegrees(facing.getClockWise().toYRot()));
        pose.translate(-.5, 0, -.5);

        for (int half = 0; half < 2; half++) {
            float bottom = half + travel, top = Math.min(2, bottom + 1);
            if (bottom >= top) break;
            String side = half == 0 ? "bottom" : "top";
            float v = 1 - (top - bottom);
            int packed = LevelRenderer.getLightColor(door.getLevel(), bottom >= 1 ? door.getBlockPos().above() : door.getBlockPos());
            String mirrored = half == 0 ? side : "top_mirrored";
            quad(pose, buffer(buffers, style, hingeNorth ? side : mirrored), packed, overlay,
                new float[][]{{0,bottom,0},{0,bottom,1},{0,top,1},{0,top,0}}, !hingeNorth && half == 0, v, -1,0,0);
            quad(pose, buffer(buffers, style, hingeNorth ? mirrored : side), packed, overlay,
                new float[][]{{THICKNESS,bottom,1},{THICKNESS,bottom,0},{THICKNESS,top,0},{THICKNESS,top,1}}, hingeNorth && half == 0, v, 1,0,0);
            quad(pose, buffer(buffers, style, (hingeNorth ? "edge_left_" : "edge_right_")+side), packed, overlay,
                new float[][]{{THICKNESS,bottom,0},{0,bottom,0},{0,top,0},{THICKNESS,top,0}}, true, v, 0,0,-1);
            quad(pose, buffer(buffers, style, (hingeNorth ? "edge_right_" : "edge_left_")+side), packed, overlay,
                new float[][]{{0,bottom,1},{THICKNESS,bottom,1},{THICKNESS,top,1},{0,top,1}}, false, v, 0,0,1);
        }
        int lowerLight = LevelRenderer.getLightColor(door.getLevel(), door.getBlockPos().above(travel >= 1 ? 1 : 0));
        quad(pose, buffer(buffers, style, "edge_bottom"), lowerLight, overlay,
            new float[][]{{0,travel,0},{0,travel,1},{THICKNESS,travel,1},{THICKNESS,travel,0}}, !hingeNorth, 0, 0,-1,0);
        if (travel == 0) {
            int upperLight = LevelRenderer.getLightColor(door.getLevel(), door.getBlockPos().above());
            quad(pose, buffer(buffers, style, "edge_top"), upperLight, overlay,
                new float[][]{{THICKNESS,2,0},{THICKNESS,2,1},{0,2,1},{0,2,0}}, !hingeNorth, 0, 0,1,0);
        }
        pose.popPose();
    }

    private static VertexConsumer buffer(MultiBufferSource buffers, String style, String part) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
            IndustrialFacilitiesMod.MODID, "textures/block/industrial_metal_door_" + style + "_" + part + ".png")));
    }

    private static void quad(PoseStack pose, VertexConsumer buffer, int light, int overlay, float[][] corners,
            boolean flip, float topV, float nx, float ny, float nz) {
        for (int i = 0; i < corners.length; i++) {
            var p = corners[i];
            float u = ((i == 0 || i == 3) != flip) ? 0 : 1;
            buffer.addVertex(pose.last(),p[0],p[1],p[2]).setColor(255,255,255,255)
                .setUv(u,i < 2 ? 1 : topV).setLight(light).setOverlay(overlay).setNormal(pose.last(),nx,ny,nz);
        }
    }
}
