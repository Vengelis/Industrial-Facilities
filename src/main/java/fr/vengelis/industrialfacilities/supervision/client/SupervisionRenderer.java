package fr.vengelis.industrialfacilities.supervision.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.supervision.*;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.locale.Language;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;

public final class SupervisionRenderer implements BlockEntityRenderer<SupervisionConsoleEntity> {
    private record CachedGraph(NetworkView network, String sector, int page, Language language, WorldGraphSurface.Frame frame) {}
    private final Map<SupervisionConsoleEntity, CachedGraph> cache = new WeakHashMap<>();

    public SupervisionRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public AABB getRenderBoundingBox(SupervisionConsoleEntity entity) {
        return entity.assembled() ? entity.bounds() : new AABB(entity.getBlockPos());
    }
    @Override public int getViewDistance() { return 32; }

    @Override public void render(SupervisionConsoleEntity entity, float partial, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
        if (!entity.assembled()) return;
        var face = entity.getBlockState().getValue(SupervisionConsoleBlock.FACING);
        var font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (face) { case EAST -> 90; case NORTH -> 180; case WEST -> 270; default -> 0; }));
        var canvas = entity.canvas();
        pose.translate(entity.firstColumn() - .34, entity.bottomRow() + canvas.rows() - .14, .36);
        float height = canvas.rows() - (canvas.fullHeight() ? .28f : 1.22f);
        pose.scale((canvas.columns() - .32f) / canvas.width(), -height / canvas.height(), 1);
        var frame = graph(entity, font);
        var out = buffers.getBuffer(RenderType.textBackground());
        for (var rect : frame.background()) {
            int x = rect.x(), y = rect.y(), right = x + rect.width(), bottom = y + rect.height();
            out.addVertex(pose.last(), x, bottom, 0).setColor(rect.color()).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(pose.last(), right, bottom, 0).setColor(rect.color()).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(pose.last(), right, y, 0).setColor(rect.color()).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(pose.last(), x, y, 0).setColor(rect.color()).setLight(LightTexture.FULL_BRIGHT);
        }
        pose.translate(0, 0, .014);
        for (var glyph : frame.glyphs()) {
            pose.pushPose();
            pose.translate(glyph.x(), glyph.y(), 0);
            pose.scale(glyph.scale(), glyph.scale(), 1);
            font.drawInBatch(glyph.text(), 0, 0, glyph.color(), false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    private WorldGraphSurface.Frame graph(SupervisionConsoleEntity entity, Font font) {
        var cached = cache.get(entity);
        var network = entity.view();
        var language = Language.getInstance();
        if (cached != null && cached.network() == network && cached.sector().equals(entity.sector())
                && cached.page() == entity.page() && cached.language() == language) return cached.frame();

        var page = GraphLayout.page(network, entity.page());
        var surface = new WorldGraphSurface(page.canvas());
        GraphInk.draw(surface, font, entity.sector(), page, entity.isDisplay() ? "display_no_sector" : "no_sector");
        var frame = surface.finish();
        cache.put(entity, new CachedGraph(network, entity.sector(), entity.page(), language, frame));
        return frame;
    }
}
