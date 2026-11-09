package fr.vengelis.industrialfacilities.panel.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.panel.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Render only this block's slice. No oversized bounding boxes or cross-block z-fighting. */
public class PanelRenderer implements BlockEntityRenderer<PanelBlockEntity> {
    private static final ResourceLocation PIXEL = PanelContent.id("textures/block/luminous_panel_led.png");
    private final Map<String, LedText> textCache = new LinkedHashMap<>();
    public PanelRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PanelBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null) return;
        var group = PanelStructure.find(level, be.getBlockPos());
        if (!group.valid()) return;
        var owner = group.controller(level);
        if (owner == null || owner.text().isEmpty()) return;
        if (textCache.size()>128) textCache.clear();
        LedText text = textCache.computeIfAbsent(owner.text(), LedText::new);
        int col = group.column(be.getBlockPos()), row = group.height()-1-group.row(be.getBlockPos());
        pose.pushPose(); pose.translate(.5, .5, .5);
        var basis=PanelBasis.of(be.getBlockState());
        var matrix=new org.joml.Matrix3f()
            .setColumn(0,new org.joml.Vector3f(basis.right().getStepX(),basis.right().getStepY(),basis.right().getStepZ()))
            .setColumn(1,new org.joml.Vector3f(basis.up().getStepX(),basis.up().getStepY(),basis.up().getStepZ()))
            .setColumn(2,new org.joml.Vector3f(basis.normal().getStepX(),basis.normal().getStepY(),basis.normal().getStepZ()));
        pose.mulPose(new org.joml.Quaternionf().setFromNormalized(matrix));
        pose.translate(-.5, -.5, basis.offset()+.0008);
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(PIXEL));
        for (int y=0; y<16; y++) for (int x=0; x<16; x++) {
            // Exterior rim is three texture pixels; internal boundaries have no margin.
            if ((col==0 && x<2) || (col==group.width()-1 && x>13)
                || (row==0 && y<2) || (row==group.height()-1 && y>13)) continue;
            if (!text.lit(col*16+x, row*16+y, group.height(), level.getGameTime(), owner.toRight())) continue;
            float cx=(x+.5F)/16, cy=1-(y+.5F)/16;
            quad(vertices, pose.last(), cx, cy, .029F, 0, 207, 235, 46, 55);
            quad(vertices, pose.last(), cx, cy, .0205F, .00015F, 238, 255, 61, 255);
        }
        pose.popPose();
    }
    private static void quad(VertexConsumer v, PoseStack.Pose p, float x, float y, float radius, float z, int r, int g, int b, int a) {
        vertex(v,p,x-radius,y-radius,z,0,1,r,g,b,a); vertex(v,p,x+radius,y-radius,z,1,1,r,g,b,a);
        vertex(v,p,x+radius,y+radius,z,1,0,r,g,b,a); vertex(v,p,x-radius,y+radius,z,0,0,r,g,b,a);
    }
    private static void vertex(VertexConsumer v, PoseStack.Pose p, float x, float y, float z, float u, float vv, int r, int g, int b, int a) {
        v.addVertex(p.pose(),x,y,z).setColor(r,g,b,a).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
    }
    @Override public int getViewDistance() { return 96; }
}
