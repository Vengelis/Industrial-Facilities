package fr.vengelis.industrialfacilities.security.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import fr.vengelis.industrialfacilities.access.ui.TabletLinksMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/** Affiche temporairement les liaisons que le serveur a autorisé ce joueur à voir. */
@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class NetworkTabletClient {
    public static final int TTL_TICKS = 40;
    private static final int MAX_EDGES = TerminalLinks.MAX_LINKS;
    private static final int KEYPASS_COLOR = 0xFF9C38;
    private static final double TERMINAL_HEIGHT_OFFSET = .75;
    private static final double MIN_BEAM_LENGTH_SQUARED = 1.0e-8;
    private static final float MIN_CLIP_W = .001f;
    private static final String HUD_KEY_PREFIX = "gui.industrial_facilities.tablet.";

    private static final RenderType LINK_RENDER_TYPE = RenderType.create(
        "industrial_tablet_lightning_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
        1536, false, false, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setDepthTestState(seeThroughDepth())
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    private record LegendEntry(String translation, String sourceKind, String targetKind) {}

    private static final List<LegendEntry> LEGEND = List.of(
        new LegendEntry("access", "reader", "door"),
        new LegendEntry("camera", "automation", "camera"),
        new LegendEntry("door", "automation", "door"),
        new LegendEntry("alarm", "automation", "alarm"),
        new LegendEntry("emitter", "automation", "emitter"),
        new LegendEntry("panel", "automation", "panel"),
        new LegendEntry("keypass", "automation", TerminalLinks.KEYPASS_READER));

    private record Label(String name, float x, float top, double distance, int color) {}

    private record LabelBox(int left, int top, int right, int bottom) {
        boolean overlaps(LabelBox other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }
    }

    private static ClientLevel world;
    private static ResourceLocation dimension;
    private static List<TerminalLinks.Link> cached = List.of();
    private static long tick;
    private static long receivedAt = -TTL_TICKS;
    private static long sequence = Long.MIN_VALUE;
    private static boolean truncated;
    private static List<Label> labels = List.of();
    private static int rendered;
    private static int renderedOutlines;
    private static int renderedLabels;

    private static RenderStateShard.DepthTestStateShard seeThroughDepth() {
        // En 1.21.1, il faut désactiver le test de profondeur explicitement pour afficher à travers les murs.
        return new RenderStateShard.DepthTestStateShard("industrial_tablet_xray", GL11.GL_ALWAYS) {
            private boolean previousDepth;

            @Override
            public void setupRenderState() {
                previousDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                RenderSystem.disableDepthTest();
            }

            @Override
            public void clearRenderState() {
                if (previousDepth) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
            }
        };
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        TabletLinksMessages.receiver = NetworkTabletClient::receive;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        world = null;
        dimension = null;
        sequence = Long.MIN_VALUE;
        clearCache();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        tick++;
        refreshContext();
    }

    private static boolean held() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isAlive() && TerminalLinks.holdingTablet(player);
    }

    private static void clearCache() {
        cached = List.of();
        labels = List.of();
        truncated = false;
        receivedAt = -TTL_TICKS;
        rendered = 0;
        renderedOutlines = 0;
        renderedLabels = 0;
    }

    private static void refreshContext() {
        var level = Minecraft.getInstance().level;
        if (level != world) {
            // Les numéros de séquence sont propres à la session dans cette dimension.
            world = level;
            dimension = level == null ? null : level.dimension().location();
            sequence = Long.MIN_VALUE;
            clearCache();
        }
        if (level == null || !held() || tick - receivedAt >= TTL_TICKS) {
            clearCache();
        }
    }

    public static void receive(TabletLinksMessages.State state) {
        refreshContext();
        if (world == null || !held() || !Objects.equals(dimension, state.dimension())) {
            clearCache();
            return;
        }
        if (state.sequence() <= sequence) {
            return;
        }

        // Un instantané est utilisé en entier si toutes ses liaisons satisfont le contrat du paquet.
        List<TerminalLinks.Link> links = state.links();
        if (state.sequence() < 0 || links.size() > MAX_EDGES || !links.stream().allMatch(NetworkTabletClient::isValidLink)) {
            clearCache();
            return;
        }
        sequence = state.sequence();
        receivedAt = tick;
        cached = List.copyOf(links);
        truncated = state.truncated();
    }

    private static boolean isValidLink(TerminalLinks.Link link) {
        return link != null && link.sourceRoot() != null && link.targetRoot() != null
            && link.sourceId() != null && link.targetId() != null && link.sourceKind() != null && link.targetKind() != null
            && TerminalLinks.validKinds(link.sourceKind(), link.targetKind()) && link.validNames()
            && TerminalLinks.inRange(link.sourceRoot(), link.targetRoot());
    }

    public static List<TerminalLinks.Link> currentLinks() {
        refreshContext();
        if (world == null || cached.isEmpty()) {
            return List.of();
        }
        return cached.stream().filter(NetworkTabletClient::canRenderLink).toList();
    }

    private static boolean canRenderLink(TerminalLinks.Link link) {
        if (!world.hasChunkAt(link.sourceRoot()) || !world.hasChunkAt(link.targetRoot())) {
            return false;
        }
        // Les lecteurs Keypass peuvent être remplacés pendant la durée de vie de l'instantané.
        return !link.sourceKind().equals(TerminalLinks.KEYPASS_MANAGER)
            || (NetworkTabletOutlines.terminalAt(world, link.sourceRoot(), link.sourceId(), link.sourceKind()) != null
                && NetworkTabletOutlines.terminalAt(world, link.targetRoot(), link.targetId(), link.targetKind()) != null);
    }

    public static boolean fresh() {
        refreshContext();
        return world != null && held() && tick - receivedAt < TTL_TICKS;
    }

    public static long lastSequence() {
        return sequence;
    }

    public static int renderedLinkCount() {
        return rendered;
    }

    public static int renderedOutlineCount() {
        return renderedOutlines;
    }

    public static int renderedLabelCount() {
        return renderedLabels;
    }

    public static List<NetworkTabletOutlines.Outline> currentOutlines() {
        var links = currentLinks();
        return NetworkTabletOutlines.collect(world, links);
    }

    public static int colorFor(String source, String target) {
        if (source.equals(TerminalLinks.KEYPASS_MANAGER) || target.equals(TerminalLinks.KEYPASS_READER)) {
            return KEYPASS_COLOR;
        }
        if (source.equals("reader")) {
            return 0x62DDE7;
        }
        return switch (target) {
            case "door" -> 0x81D6A3;
            case "alarm" -> 0xF0B064;
            case "emitter" -> 0xEE777D;
            case "panel" -> 0xF5DB8D;
            default -> 0xBE94F0;
        };
    }

    static int sourceColor(String source) {
        return colorFor(source, "camera");
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        rendered = 0;
        renderedOutlines = 0;
        labels = List.of();
        List<TerminalLinks.Link> links = currentLinks();
        if (links.isEmpty()) {
            return;
        }

        Vec3 eye = event.getCamera().getPosition();
        renderLinks(event, eye, links);
        var outlines = NetworkTabletOutlines.collect(world, links);
        renderedOutlines = NetworkTabletSilhouettes.render(event, outlines);
        if (renderedOutlines > 0) {
            labels = projectLabels(event, eye, links, outlines);
        }
    }

    private static void renderLinks(RenderLevelStageEvent event, Vec3 eye, List<TerminalLinks.Link> links) {
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-eye.x, -eye.y, -eye.z);

        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(LINK_RENDER_TYPE);
        Set<UUID> drawnSources = new HashSet<>();
        Set<UUID> drawnTargets = new HashSet<>();
        for (var link : links) {
            Vec3 from = link.sourceRoot().getCenter().add(0, TERMINAL_HEIGHT_OFFSET, 0);
            Vec3 to = link.targetRoot().getCenter();
            if (link.targetKind().equals(TerminalLinks.KEYPASS_READER)) {
                to = to.add(0, TERMINAL_HEIGHT_OFFSET, 0);
            }
            if (!event.getFrustum().isVisible(new AABB(from, to).inflate(.1))) {
                continue;
            }

            beam(pose, out, from, to, eye, colorFor(link.sourceKind(), link.targetKind()));
            rendered++;
            if (drawnSources.add(link.sourceId())) {
                cap(pose, out, from, eye, sourceColor(link.sourceKind()));
            }
            if (drawnTargets.add(link.targetId())) {
                cap(pose, out, to, eye, colorFor("automation", link.targetKind()));
            }
        }
        buffers.endBatch(LINK_RENDER_TYPE);
        pose.popPose();
    }

    private static List<Label> projectLabels(RenderLevelStageEvent event, Vec3 eye, List<TerminalLinks.Link> links,
        List<NetworkTabletOutlines.Outline> outlines) {
        Map<UUID, String> names = new HashMap<>();
        for (var link : links) {
            if (!link.sourceName().isEmpty()) {
                names.put(link.sourceId(), link.sourceName());
            }
            if (!link.targetName().isEmpty()) {
                names.put(link.targetId(), link.targetName());
            }
        }
        if (names.isEmpty()) {
            return List.of();
        }

        List<Label> projected = new ArrayList<>();
        for (var outline : outlines) {
            String name = names.get(outline.instance());
            if (name == null
                || NetworkTabletOutlines.terminalAt(world, outline.root(), outline.instance(), TerminalLinks.KEYPASS_READER) == null) {
                continue;
            }
            var box = outline.bounds().inflate(.06);
            if (!event.getFrustum().isVisible(box)) {
                continue;
            }
            var center = box.getCenter();
            var anchor = project(event, eye, center.x, box.maxY, center.z);
            if (anchor.w <= MIN_CLIP_W) {
                continue;
            }
            float x = anchor.x / anchor.w * .5f + .5f;
            if (x < 0 || x > 1) {
                continue;
            }
            float top = projectedTop(event, eye, box);
            if (top >= 0 && top < 1) {
                projected.add(new Label(name, x, top, eye.distanceToSqr(center), outline.color()));
            }
        }
        // En cas de chevauchement, les étiquettes les plus proches sont placées en premier.
        projected.sort(Comparator.comparingDouble(Label::distance));
        return List.copyOf(projected);
    }

    private static float projectedTop(RenderLevelStageEvent event, Vec3 eye, AABB box) {
        // On projette les huit coins, car le plus haut à l'écran dépend de l'angle de vue.
        float top = 1;
        for (int cornerX = 0; cornerX < 2; cornerX++) {
            double x = cornerX == 0 ? box.minX : box.maxX;
            for (int cornerY = 0; cornerY < 2; cornerY++) {
                double y = cornerY == 0 ? box.minY : box.maxY;
                for (int cornerZ = 0; cornerZ < 2; cornerZ++) {
                    double z = cornerZ == 0 ? box.minZ : box.maxZ;
                    var point = project(event, eye, x, y, z);
                    if (point.w <= MIN_CLIP_W) {
                        return -1;
                    }
                    top = Math.min(top, .5f - point.y / point.w * .5f);
                }
            }
        }
        return top;
    }

    private static Vector4f project(RenderLevelStageEvent event, Vec3 eye, double x, double y, double z) {
        var point = new Vector4f((float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z), 1);
        event.getModelViewMatrix().transform(point);
        return event.getProjectionMatrix().transform(point);
    }

    private static void beam(PoseStack pose, VertexConsumer out, Vec3 from, Vec3 to, Vec3 eye, int color) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < MIN_BEAM_LENGTH_SQUARED) {
            return;
        }
        axis = axis.normalize();

        // Oriente un ruban vers l'œil et le second perpendiculairement, même lorsqu'on regarde dans l'axe.
        Vec3 side = axis.cross(eye.subtract(from.add(to).scale(.5)));
        if (side.lengthSqr() < MIN_BEAM_LENGTH_SQUARED) {
            side = axis.cross(new Vec3(0, 1, 0));
        }
        if (side.lengthSqr() < MIN_BEAM_LENGTH_SQUARED) {
            side = axis.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();
        Vec3 across = axis.cross(side).normalize();
        int coreColor = lighten(color);
        for (Vec3 normal : List.of(side, across)) {
            ribbon(pose, out, from, to, normal.scale(.034), color, 20);
            ribbon(pose, out, from, to, normal.scale(.020), color, 42);
            ribbon(pose, out, from, to, normal.scale(.0065), coreColor, 220);
        }
    }

    private static int lighten(int color) {
        int red = ((color >> 16 & 255) + 255) / 2;
        int green = ((color >> 8 & 255) + 255) / 2;
        int blue = ((color & 255) + 255) / 2;
        return (red << 16) | (green << 8) | blue;
    }

    private static void ribbon(PoseStack pose, VertexConsumer out, Vec3 from, Vec3 to, Vec3 side, int color, int alpha) {
        Vec3 fromLeft = from.subtract(side);
        Vec3 toLeft = to.subtract(side);
        Vec3 toRight = to.add(side);
        Vec3 fromRight = from.add(side);

        // Les deux ordres de sommets gardent le même ruban visible depuis les deux côtés.
        vertex(pose, out, fromLeft, color, alpha);
        vertex(pose, out, toLeft, color, alpha);
        vertex(pose, out, toRight, color, alpha);
        vertex(pose, out, fromRight, color, alpha);
        vertex(pose, out, fromRight, color, alpha);
        vertex(pose, out, toRight, color, alpha);
        vertex(pose, out, toLeft, color, alpha);
        vertex(pose, out, fromLeft, color, alpha);
    }

    private static void vertex(PoseStack pose, VertexConsumer out, Vec3 point, int color, int alpha) {
        out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
            .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha);
    }

    private static void cap(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 eye, int color) {
        for (Vec3 axis : List.of(new Vec3(.045, 0, 0), new Vec3(0, .045, 0), new Vec3(0, 0, .045))) {
            beam(pose, out, center.subtract(axis), center.add(axis), eye, color);
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        renderedLabels = 0;
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.level == null || !held()) {
            return;
        }

        var graphics = event.getGuiGraphics();
        List<TerminalLinks.Link> links = currentLinks();
        Component heading = fresh()
            ? Component.translatable(HUD_KEY_PREFIX + "links", links.size())
            : Component.translatable(HUD_KEY_PREFIX + "waiting");
        graphics.drawString(mc.font, heading, 8, 8, 0xDCC7F1, true);

        int lastLegendY = drawLegend(graphics, mc.font);
        if (truncated) {
            graphics.drawString(mc.font, Component.translatable(HUD_KEY_PREFIX + "partial"),
                8, lastLegendY + 12, 0xE9BD85, true);
        }
        drawLabels(graphics, mc.font, lastLegendY + (truncated ? 24 : 12));
    }

    private static int drawLegend(GuiGraphics graphics, Font font) {
        int x = 8;
        int y = 20;
        for (LegendEntry entry : LEGEND) {
            int color = colorFor(entry.sourceKind(), entry.targetKind());
            String label = Component.translatable(HUD_KEY_PREFIX + entry.translation()).getString();
            int width = font.width(label);
            if (x > 8 && x + width + 6 > graphics.guiWidth() - 8) {
                x = 8;
                y += 11;
            }
            graphics.fill(x, y + 3, x + 3, y + 6, 0xFF000000 | color);
            graphics.drawString(font, label, x + 6, y, 0xD6D1DC, true);
            x += width + 14;
        }
        return y;
    }

    private static void drawLabels(GuiGraphics graphics, Font font, int minimumY) {
        List<LabelBox> drawn = new ArrayList<>();
        for (var label : labels) {
            String name = font.plainSubstrByWidth(label.name(), graphics.guiWidth() - 16);
            int width = font.width(name);
            if (width == 0) {
                continue;
            }
            int left = Math.clamp(Math.round(label.x() * graphics.guiWidth()) - width / 2, 8, graphics.guiWidth() - width - 8);
            int top = Math.round(label.top() * graphics.guiHeight()) - font.lineHeight - 6;

            // Essaie trois positions au-dessus du lecteur tout en laissant la légende visible.
            for (int attempt = 0; attempt < 3 && top >= minimumY; attempt++, top -= font.lineHeight + 6) {
                var box = new LabelBox(left - 4, top - 2, left + width + 4, top + font.lineHeight + 2);
                if (drawn.stream().anyMatch(box::overlaps)) {
                    continue;
                }
                graphics.fill(box.left(), box.top(), box.right(), box.bottom(), 0xE61D1712);
                graphics.fill(box.left(), box.bottom() - 1, box.right(), box.bottom(), 0xFF000000 | label.color());
                graphics.drawString(font, name, left, top, 0xFFF1DD, false);
                drawn.add(box);
                renderedLabels++;
                break;
            }
        }
    }

    private NetworkTabletClient() {}
}
