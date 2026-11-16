package fr.vengelis.industrialfacilities.door.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.vengelis.industrialfacilities.door.IndustrialJawDoorMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import static fr.vengelis.industrialfacilities.door.client.IndustrialDoorRenderer.box;
import static fr.vengelis.industrialfacilities.door.client.IndustrialDoorRenderer.panelQuad;

final class IndustrialJawDoorRenderer {
    private static final float FRONT = (float) IndustrialJawDoorMotion.FRONT;
    private static final float BACK = (float) IndustrialJawDoorMotion.BACK;
    private static final float STRIPE_HEIGHT = 1.5f / 16;
    private static final float EDGE_UV = .5f / 32;
    private static final float GUIDE_BASE_UV = 29.5f / 32;

    static void render(PoseStack pose, MultiBufferSource buffers, VertexConsumer steel, IndustrialJawDoorMotion.Layout jaws, float progress,
                       IndustrialDoorLighting light, ResourceLocation stripes) {
        guides(pose, steel, light, jaws);
        if (progress >= 1) return;
        panels(pose, steel, light, jaws, progress, false);
        panels(pose, buffers.getBuffer(RenderType.entityCutout(stripes)), light, jaws, progress, true);
    }

    private static void guides(PoseStack pose, VertexConsumer steel, IndustrialDoorLighting light, IndustrialJawDoorMotion.Layout jaws) {
        for (int tooth = 0; tooth < jaws.toothCount(); tooth++) {
            float left = 1 + (float) jaws.start(tooth);
            float right = 1 + (float) jaws.start(tooth + 1);
            float center = (left + right) * .5f;
            float halfWidth = (right - left) * .28f;
            boolean up = jaws.opensPositive(tooth);
            float edge = up ? jaws.travel() + 1 : 1;
            float stub = edge + (up ? -.09f : .09f);
            float tip = edge + (up ? -.23f : .23f);
            for (boolean front : new boolean[]{true, false}) {
                // Paired fingers on the outside of the blade track; the plate slides between them.
                float z0 = front ? FRONT + .018f : .15f, z1 = front ? .85f : BACK - .018f;
                box(pose, steel, center - halfWidth, Math.min(edge, stub), z0, center + halfWidth, Math.max(edge, stub), z1,
                    GUIDE_BASE_UV, EDGE_UV, light);
                box(pose, steel, center - halfWidth * .62f, Math.min(stub, tip), z0 + .018f,
                    center + halfWidth * .62f, Math.max(stub, tip), z1 - .018f, EDGE_UV, EDGE_UV, light);
            }
        }
    }

    private static void panels(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light,
                               IndustrialJawDoorMotion.Layout jaws, float progress, boolean stripe) {
        int height = jaws.travel();
        for (int tooth = 0; tooth < jaws.toothCount(); tooth++) {
            float left = 1 + (float) jaws.start(tooth);
            float right = 1 + (float) jaws.start(tooth + 1);
            boolean up = jaws.opensPositive(tooth);
            float shift = (float) jaws.offset(tooth, progress);
            if (stripe) {
                surfaces(pose, out, light, height, left, right, up ? 0 : height - STRIPE_HEIGHT,
                    up ? STRIPE_HEIGHT : height, shift, true);
            } else {
                surfaces(pose, out, light, height, left, right, up ? STRIPE_HEIGHT : 0,
                    up ? height : height - STRIPE_HEIGHT, shift, false);
                depthFaces(pose, out, light, height, left, right, shift, height + shift, up);
            }
        }
    }

    private static void surfaces(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int height,
                                 float left, float right, float start, float end, float shift, boolean stripe) {
        float depth = IndustrialDoorFrameGroove.DEPTH;
        float bottom = Math.max(-depth, start + shift), top = Math.min(height + depth, end + shift);
        if (bottom >= top) return;
        float v0 = stripe ? 1 - (top - shift - start) / STRIPE_HEIGHT : -(top - shift) * .5f;
        float v1 = stripe ? 1 - (bottom - shift - start) / STRIPE_HEIGHT : -(bottom - shift) * .5f;
        float y0 = 1 + bottom, y1 = 1 + top;
        panelQuad(pose, out, new float[][]{{left,y0,FRONT},{right,y0,FRONT},{right,y1,FRONT},{left,y1,FRONT}},
            0, v0, 1, v1, light, 0, 0, 1);
        panelQuad(pose, out, new float[][]{{right,y0,BACK},{left,y0,BACK},{left,y1,BACK},{right,y1,BACK}},
            0, v0, 1, v1, light, 0, 0, -1);
    }

    private static void depthFaces(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int height,
                                   float left, float right, float start, float end, boolean up) {
        float depth = IndustrialDoorFrameGroove.DEPTH;
        float y0 = 1 + Math.max(-depth, start), y1 = 1 + Math.min(height + depth, end);
        if (y0 >= y1) return;
        edge(pose, out, light, new float[][]{{left,y0,BACK},{left,y0,FRONT},{left,y1,FRONT},{left,y1,BACK}}, -1, 0);
        edge(pose, out, light, new float[][]{{right,y0,FRONT},{right,y0,BACK},{right,y1,BACK},{right,y1,FRONT}}, 1, 0);
        if (up && start > -depth) {
            edge(pose, out, light, new float[][]{{left,y0,BACK},{right,y0,BACK},{right,y0,FRONT},{left,y0,FRONT}}, 0, -1);
        } else if (!up && end < height + depth) {
            edge(pose, out, light, new float[][]{{left,y1,FRONT},{right,y1,FRONT},{right,y1,BACK},{left,y1,BACK}}, 0, 1);
        }
    }

    private static void edge(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, float[][] points, float nx, float ny) {
        panelQuad(pose, out, points, EDGE_UV, EDGE_UV, EDGE_UV, EDGE_UV, light, nx, ny, 0);
    }

    private IndustrialJawDoorRenderer() {}
}
