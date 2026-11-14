package fr.vengelis.industrialfacilities.security.client;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.security.SecurityCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.*;

/**
 * Dessine le faisceau des caméras dans la passe translucide du shader.
 * Les rayons limitent le volume et projettent l’empreinte du faisceau sur les surfaces touchées.
 */
@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class CameraHaloRenderer {
    public static final int WHITE = 0xE8E2CD, ORANGE = 0xFFA02F, RED = 0xFF3828;
    private static final int HORIZONTAL_CELLS = 12;
    private static final int VERTICAL_CELLS = 8;
    private static final int SLICES = 10;
    private static final int MAX_CAMERAS = 16;
    private static final int MAX_SCANNED = 32768;
    private static final int MAX_CANDIDATES = 256;
    private static final double VERTICAL_HALF_ANGLE_TANGENT = Math.tan(Math.toRadians(30));
    private static final double MAX_RENDER_DISTANCE = 64;
    private static ShaderInstance haloShader;
    private static final RenderType TYPE = RenderType.create("industrial_camera_halo",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> haloShader))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final Map<UUID, Field> fields = new HashMap<>();
    private static List<SecurityCameraEntity> cameras = List.of();
    private static ClientLevel world;
    private static long discoveryTick = Long.MIN_VALUE;
    private static Method packInUse, fallback, pipelineManager, pipelineGetter, bindDefault;
    private static java.lang.reflect.Field shadowActive;
    private static boolean irisChecked, irisShaderReady;
    private static int renderedCameras, volumeQuads, surfaceQuads;
    /** Optional read-only render diagnostic; no framebuffer or world mutation is permitted. */
    public static Consumer<HaloFrame> observer;
    public record HaloFrame(BlockPos position, UUID instance, int stage, int color, Vec3 lens, Vec3 direction,
            int range, int volumeQuads, int surfaceQuads, long sampleTick) {
    }
    private record Ray(Vec3 point, Direction normal, double depth, boolean surface) {
    }
    private record Patch(Ray[] rays, double[] us, double[] vs) {
    }
    private record VolumeVertex(Vec3 point, double depth, int alpha) {
    }
    private record Field(SecurityCameraEntity camera, Vec3 lens, Vec3 forward, Vec3 right, Vec3 up, int range,
            long tick, int config, Ray[][] rays, Ray[][] centers, List<Patch> patches) {
    }
    /** Conserve les cibles OpenGL à restaurer après le dessin dans le tampon actif d’Iris. */
    private record Target(int callerDraw, int callerRead, int irisDraw, int[] drawBuffers) {
        static Target prepare() {
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            try {
                Object manager = pipelineManager.invoke(null);
                Object pipeline = ((Optional<?>) pipelineGetter.invoke(manager)).orElse(null);
                if (pipeline == null || !bindDefault.getDeclaringClass().isInstance(pipeline))
                    return null;
                // ExtendedShader.clear() a restauré la cible Minecraft. Iris sélectionne sa couleur et sa profondeur actives.
                bindDefault.invoke(pipeline);
                int target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                int count = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
                int[] attachments = new int[count];
                for (int i = 0; i < count; i++)
                    attachments[i] = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
                return new Target(draw, read, target, attachments);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            }
        }
        void begin() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, irisDraw);
            GL20.glDrawBuffers(new int[] {drawBuffers[0]});
        }
        void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, irisDraw);
            GL20.glDrawBuffers(drawBuffers);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, callerDraw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, callerRead);
        }
    }
    /** Une forme peut consulter des blocs voisins, y compris aux frontières de chunks. */
    private static final class LoadedGetter implements BlockGetter {
        private final ClientLevel level;
        private boolean unknown;
        LoadedGetter(ClientLevel level) {
            this.level = level;
        }
        private boolean loaded(BlockPos pos) {
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) {
                unknown = true;
                return false;
            }
            return true;
        }
        public BlockEntity getBlockEntity(BlockPos pos) {
            return loaded(pos) ? level.getBlockEntity(pos) : null;
        }
        public BlockState getBlockState(BlockPos pos) {
            return loaded(pos) ? level.getBlockState(pos) : Blocks.BEDROCK.defaultBlockState();
        }
        public FluidState getFluidState(BlockPos pos) {
            return loaded(pos) ? level.getFluidState(pos) : Fluids.EMPTY.defaultFluidState();
        }
        public int getHeight() {
            return level.getHeight();
        }
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }

    /** Active le halo uniquement lorsque Iris utilise un shader compatible. */
    private static void initializeIris() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> iris =
                        Class.forName("net.irisshaders.iris.Iris", false, CameraHaloRenderer.class.getClassLoader());
                packInUse = iris.getMethod("isPackInUseQuick");
                fallback = iris.getMethod("isFallback");
                pipelineManager = iris.getMethod("getPipelineManager");
                pipelineGetter = pipelineManager.getReturnType().getMethod("getPipeline");
                bindDefault = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", false,
                                           CameraHaloRenderer.class.getClassLoader())
                                      .getMethod("bindDefault");
                shadowActive = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer", false,
                                            CameraHaloRenderer.class.getClassLoader())
                                       .getField("ACTIVE");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                packInUse = null;
                fallback = null;
            }
        }
    }
    private static boolean shadowPass() {
        initializeIris();
        if (packInUse == null || shadowActive == null)
            return false;
        try {
            return shadowActive.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }
    public static boolean shaderActive() {
        initializeIris();
        if (packInUse == null || fallback == null)
            return false;
        try {
            return irisShaderReady && Boolean.TRUE.equals(packInUse.invoke(null))
                    && !Boolean.TRUE.equals(fallback.invoke(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
    public static int renderedCameras() {
        return renderedCameras;
    }
    public static int volumeQuads() {
        return volumeQuads;
    }
    public static int surfaceQuads() {
        return surfaceQuads;
    }
    public static int colorFor(int stage, boolean stagesEnabled) {
        return !stagesEnabled || stage <= 1 ? WHITE : stage == 2 ? ORANGE : RED;
    }
    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        // A private core program preserves low alpha and palier colors: some packs'
        // lightning programs discard alpha<0.1 or replace every bolt color with white.
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "camera_halo"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> {
                    haloShader = shader;
                    irisShaderReady = allowHaloShader(shader);
                });
    }
    private static boolean allowHaloShader(ShaderInstance shader) {
        try {
            ClassLoader loader = CameraHaloRenderer.class.getClassLoader();
            Class<?> contract =
                    Class.forName("net.irisshaders.iris.mixinterface.ShaderInstanceInterface", false, loader);
            if (!contract.isInstance(shader))
                return false;
            Object neverSkip = Class.forName("net.irisshaders.iris.compat.SkipList", false, loader)
                                       .getField("NONE_FORCE")
                                       .get(null);
            // Iris normally masks writes by unknown core shaders. Opt in ONLY this
            // instance using its compatibility API, including each shader reload.
            contract.getMethod("setShouldSkip", MethodHandle.class).invoke(shader, neverSkip);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
    private static void clear() {
        world = null;
        cameras = List.of();
        fields.clear();
        discoveryTick = Long.MIN_VALUE;
        renderedCameras = volumeQuads = surfaceQuads = 0;
    }

    private static void discover(ClientLevel level, Vec3 eye, long now) {
        if (world != level) {
            clear();
            world = level;
        }
        if (discoveryTick != Long.MIN_VALUE && now >= discoveryTick && now - discoveryTick < 5)
            return;
        discoveryTick = now;
        Comparator<SecurityCameraEntity> distance =
                Comparator.comparingDouble(camera -> camera.getBlockPos().getCenter().distanceToSqr(eye));
        PriorityQueue<SecurityCameraEntity> nearest = new PriorityQueue<>(MAX_CANDIDATES, distance.reversed());
        int scanned = 0;
        int minX = ((int) Math.floor(eye.x - MAX_RENDER_DISTANCE)) >> 4, maxX = ((int) Math.floor(eye.x + MAX_RENDER_DISTANCE)) >> 4;
        int minZ = ((int) Math.floor(eye.z - MAX_RENDER_DISTANCE)) >> 4, maxZ = ((int) Math.floor(eye.z + MAX_RENDER_DISTANCE)) >> 4;
    search:
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                // false is essential: only already-present client chunks are inspected.
                var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null)
                    continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (++scanned > MAX_SCANNED)
                        break search;
                    if (entity instanceof SecurityCameraEntity camera && !camera.isRemoved()
                            && camera.getBlockPos().getCenter().distanceToSqr(eye) <= MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                        if (nearest.size() < MAX_CANDIDATES)
                            nearest.add(camera);
                        else if (distance.compare(camera, nearest.peek()) < 0) {
                            nearest.poll();
                            nearest.add(camera);
                        }
                    }
                }
            }
        List<SecurityCameraEntity> found = new ArrayList<>(nearest);
        found.sort(distance);
        cameras = List.copyOf(found);
        Set<UUID> live = new HashSet<>();
        for (var camera : cameras)
            live.add(camera.instanceId);
        fields.keySet().retainAll(live);
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        // Iris rejoue la passe translucide pour les ombres. Les compteurs décrivent la vue principale.
        if (shadowPass())
            return;
        renderedCameras = volumeQuads = surfaceQuads = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || haloShader == null || !shaderActive()) {
            if (world != null)
                clear();
            return;
        }
        Target target = Target.prepare();
        if (target == null)
            return;
        Vec3 eye = event.getCamera().getPosition();
        long now = minecraft.level.getGameTime();
        discover(minecraft.level, eye, now);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = null;
        try {
            for (var camera : cameras) {
                if (renderedCameras >= MAX_CAMERAS)
                    break;
                if (camera.isRemoved() || camera.getLevel() != minecraft.level || !camera.haloEnabled
                        || !minecraft.level.hasChunkAt(camera.getBlockPos())
                        || minecraft.level.getBlockEntity(camera.getBlockPos()) != camera
                        || camera.getBlockPos().getCenter().distanceToSqr(eye) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE
                        || !event.getFrustum().isVisible(camera.fieldBounds()))
                    continue;
                Field field = fields.get(camera.instanceId);
                if (field == null || field.camera != camera || field.config != config(camera) || now < field.tick
                        || now - field.tick >= 2) {
                    field = sample(minecraft.level, camera, partial, now);
                    fields.put(camera.instanceId, field);
                }
                if (out == null)
                    out = buffers.getBuffer(TYPE);
                int color = colorFor(camera.stage, camera.stagesEnabled);
                int volumeBefore = volumeQuads;
                int surfaceBefore = surfaceQuads;
                volume(field, eye, pose, out, color);
                surface(field, pose, out, color);
                renderedCameras++;
                if (observer != null)
                    observer.accept(new HaloFrame(camera.getBlockPos(), camera.instanceId,
                            camera.stagesEnabled ? camera.stage : 1, color, field.lens, field.forward, field.range,
                            volumeQuads - volumeBefore, surfaceQuads - surfaceBefore, field.tick));
            }
        } finally {
            pose.popPose();
            if (out != null)
                flush(buffers, target);
        }
    }
    private static void flush(MultiBufferSource.BufferSource buffers, Target target) {
        // Le shader du halo écrit uniquement dans la couleur. Préserve les autres tampons d’Iris.
        try {
            target.begin();
            buffers.endBatch(TYPE);
        } finally {
            target.restore();
        }
    }
    /**
     * Échantillonne les coins et les centres dans le repère du faisceau.
     * Les subdivisions partagent les rayons des bords pour éviter les écarts entre surfaces voisines.
     */
    private static Field sample(ClientLevel level, SecurityCameraEntity camera, float partial, long now) {
        int range = Math.clamp(camera.range, 1, 16);
        Vec3 forward = camera.direction(partial), right = forward.cross(new Vec3(0, 1, 0)).normalize(),
             up = right.cross(forward).normalize();
        Vec3 origin = camera.lens(partial);
        Ray[][] rays = new Ray[HORIZONTAL_CELLS + 1][VERTICAL_CELLS + 1], centers = new Ray[HORIZONTAL_CELLS][VERTICAL_CELLS];
        LoadedGetter getter = new LoadedGetter(level);
        for (int x = 0; x <= HORIZONTAL_CELLS; x++)
            for (int y = 0; y <= VERTICAL_CELLS; y++)
                rays[x][y] = cast(getter, camera.getBlockPos(), origin, forward, right, up, range, u(x), v(y));
        for (int x = 0; x < HORIZONTAL_CELLS; x++)
            for (int y = 0; y < VERTICAL_CELLS; y++)
                centers[x][y] =
                        cast(getter, camera.getBlockPos(), origin, forward, right, up, range, u(x + .5), v(y + .5));
        Map<Integer, Ray> shared = new HashMap<>();
        for (int x = 0; x <= HORIZONTAL_CELLS; x++)
            for (int y = 0; y <= VERTICAL_CELLS; y++)
                shared.put(key(x * 8, y * 8), rays[x][y]);
        for (int x = 0; x < HORIZONTAL_CELLS; x++)
            for (int y = 0; y < VERTICAL_CELLS; y++)
                shared.put(key(x * 8 + 4, y * 8 + 4), centers[x][y]);
        List<Patch> patches = new ArrayList<>();
        for (int x = 0; x < HORIZONTAL_CELLS; x++)
            for (int y = 0; y < VERTICAL_CELLS; y++)
                refine(getter, camera.getBlockPos(), origin, forward, right, up, range, shared, patches, x * 8, y * 8,
                        8, 0);
        return new Field(
                camera, origin, forward, right, up, range, now, config(camera), rays, centers, List.copyOf(patches));
    }
    private static int key(int x, int y) {
        return x << 16 | y;
    }
    private static Ray sharedRay(LoadedGetter getter, BlockPos camera, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
            int range, Map<Integer, Ray> shared, int x, int y) {
        Ray found = shared.get(key(x, y));
        if (found != null)
            return found;
        if (shared.size() >= (HORIZONTAL_CELLS + 1) * (VERTICAL_CELLS + 1) + HORIZONTAL_CELLS * VERTICAL_CELLS + 512)
            return null;
        Ray ray = cast(getter, camera, origin, forward, right, up, range, u(x / 8.0), v(y / 8.0));
        shared.put(key(x, y), ray);
        return ray;
    }
    private static boolean coplanar(Ray center, Ray... rays) {
        if (center == null || !center.surface)
            return false;
        Vec3 normal = Vec3.atLowerCornerOf(center.normal.getNormal());
        for (Ray ray : rays)
            if (ray == null || !ray.surface || ray.normal != center.normal
                    || Math.abs(ray.point.subtract(center.point).dot(normal)) > .02
                    || ray.point.distanceToSqr(center.point) > 25)
                return false;
        return true;
    }
    private static void refine(LoadedGetter getter, BlockPos camera, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
            int range, Map<Integer, Ray> shared, List<Patch> patches, int x, int y, int step, int depth) {
        int[] xs = {x, x + step, x + step, x}, ys = {y, y, y + step, y + step};
        Ray[] corners = new Ray[4];
        boolean hit = false;
        for (int i = 0; i < 4; i++) {
            corners[i] = sharedRay(getter, camera, origin, forward, right, up, range, shared, xs[i], ys[i]);
            if (corners[i] == null)
                return;
            hit |= corners[i].surface;
        }
        Ray center = sharedRay(getter, camera, origin, forward, right, up, range, shared, x + step / 2, y + step / 2);
        if (center == null)
            return;
        hit |= center.surface;
        if (!hit)
            return;
        double[] us = {u(xs[0] / 8.0), u(xs[1] / 8.0), u(xs[2] / 8.0), u(xs[3] / 8.0)},
                 vs = {v(ys[0] / 8.0), v(ys[1] / 8.0), v(ys[2] / 8.0), v(ys[3] / 8.0)};
        if (coplanar(center, corners)) {
            patches.add(new Patch(corners, us, vs));
            return;
        }
        if (depth < 2) {
            int half = step / 2;
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 2; j++)
                    refine(getter, camera, origin, forward, right, up, range, shared, patches, x + i * half,
                            y + j * half, half, depth + 1);
            return;
        }
        // Final mixed cells retain their valid triangles instead of losing every
        // lit vertex when just one corner crosses a wall/floor junction.
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            if (coplanar(center, corners[i], corners[j]))
                patches.add(new Patch(new Ray[] {center, corners[i], corners[j]},
                        new double[] {u((x + step * .5) / 8.0), us[i], us[j]},
                        new double[] {v((y + step * .5) / 8.0), vs[i], vs[j]}));
        }
    }
    private static int config(SecurityCameraEntity camera) {
        return Objects.hash(camera.range, camera.yaw, camera.pitch, camera.sweepEnabled, camera.sweepAngle,
                camera.sweepPeriodTicks, camera.sweepEpoch, camera.getBlockState(), camera.trackingRevision);
    }
    private static Ray cast(LoadedGetter getter, BlockPos camera, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
            int range, double u, double v) {
        Vec3 end = point(origin, forward, right, up, range, u, v);
        return BlockGetter.traverseBlocks(origin, end, Boolean.TRUE, (ignored, pos) -> {
            if (pos.equals(camera))
                return null;
            // Check before reading any block state. Unknown space stops the light.
            getter.unknown = false;
            if (!getter.loaded(pos))
                return unknownRay(origin, end, forward, pos);
            var shape = getter.getBlockState(pos).getCollisionShape(getter, pos, CollisionContext.empty());
            // A multiblock's owner can live in another, unknown chunk. Its missing
            // collision must never be interpreted as permission to shine through.
            if (getter.unknown)
                return unknownRay(origin, end, forward, pos);
            BlockHitResult hit = shape.clip(origin, end, pos);
            if (hit == null)
                return null;
            return new Ray(hit.getLocation(), hit.getDirection(),
                    Math.max(0, hit.getLocation().subtract(origin).dot(forward)), true);
        }, ignored -> new Ray(end, null, range, false));
    }
    private static Ray unknownRay(Vec3 origin, Vec3 end, Vec3 forward, BlockPos pos) {
        BlockHitResult hit = Shapes.block().clip(origin, end, pos);
        Vec3 location = hit == null ? Vec3.atCenterOf(pos) : hit.getLocation();
        return new Ray(location, null, Math.max(0, location.subtract(origin).dot(forward)), false);
    }
    // u et v sont les décalages horizontal et vertical par unité de profondeur le long du faisceau.
    private static double u(double x) {
        return x * 2 / HORIZONTAL_CELLS - 1;
    }
    private static double v(double y) {
        return y * 2 / VERTICAL_CELLS - 1;
    }
    private static Vec3 point(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up, double depth, double u, double v) {
        return origin.add(forward.scale(depth)).add(right.scale(depth * u)).add(up.scale(depth * v * VERTICAL_HALF_ANGLE_TANGENT));
    }
    private static Vec3 point(Field field, double depth, double u, double v) {
        return point(field.lens, field.forward, field.right, field.up, depth, u, v);
    }
    private static double limit(Field field, double u, double v) {
        int x = Math.clamp((int) Math.floor((u + 1) * HORIZONTAL_CELLS * .5), 0, HORIZONTAL_CELLS - 1),
            y = Math.clamp((int) Math.floor((v + 1) * VERTICAL_CELLS * .5), 0, VERTICAL_CELLS - 1);
        return Math.min(field.centers[x][y].depth,
                Math.min(Math.min(field.rays[x][y].depth, field.rays[x + 1][y].depth),
                        Math.min(field.rays[x][y + 1].depth, field.rays[x + 1][y + 1].depth)));
    }
    /** Smooth separable falloff covers the complete rectangular 90 by 60 degree field. */
    private static double softness(double u, double v) {
        double x = Math.max(0, 1 - u * u), y = Math.max(0, 1 - v * v);
        return x * x * y * y;
    }
    private static int alpha(Field field, double depth, double u, double v, double limit) {
        if (depth < .12 || depth > field.range)
            return 0;
        double remaining = limit - depth;
        if (remaining < .025)
            return 0;
        double fade = Math.min(1, depth / .7) * Math.min(1, remaining / 2.0) * (1 - .65 * depth / field.range);
        return (int) Math.round(10 * softness(u, v) * fade);
    }
    private static void vertex(PoseStack pose, VertexConsumer out, Vec3 point, int color, int alpha) {
        out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, Math.clamp(alpha, 0, 255));
    }
    private static void cell(
            Field field, PoseStack pose, VertexConsumer out, int color, double[] depth, double[] us, double[] vs) {
        double midU = 0, midV = 0;
        for (int i = 0; i < 4; i++) {
            midU += us[i] * .25;
            midV += vs[i] * .25;
        }
        double bound = limit(field, midU, midV) - .025;
        boolean anyInside = false, allInside = true;
        for (int i = 0; i < 4; i++) {
            boolean inside = depth[i] <= bound;
            anyInside |= inside;
            allInside &= inside;
        }
        if (!anyInside)
            return;
        if (allInside) {
            int[] alphas = new int[4];
            int sum = 0;
            for (int i = 0; i < 4; i++) {
                alphas[i] = alpha(field, depth[i], us[i], vs[i], bound);
                sum += alphas[i];
            }
            if (sum == 0)
                return;
            for (int i = 0; i < 4; i++)
                vertex(pose, out, point(field, depth[i], us[i], vs[i]), color, alphas[i]);
            volumeQuads++;
            return;
        }
        List<VolumeVertex> polygon = new ArrayList<>(4);
        for (int i = 0; i < 4; i++)
            polygon.add(new VolumeVertex(
                    point(field, depth[i], us[i], vs[i]), depth[i], alpha(field, depth[i], us[i], vs[i], bound)));
        List<VolumeVertex> clipped = new ArrayList<>(5);
        VolumeVertex previous = polygon.getLast();
        for (VolumeVertex next : polygon) {
            boolean a = previous.depth <= bound, b = next.depth <= bound;
            if (a != b) {
                double t = (bound - previous.depth) / (next.depth - previous.depth);
                clipped.add(new VolumeVertex(previous.point.lerp(next.point, t), bound, 0));
            }
            if (b)
                clipped.add(next);
            previous = next;
        }
        int totalAlpha = 0;
        for (var vertex : clipped)
            totalAlpha += vertex.alpha;
        if (clipped.size() < 3 || totalAlpha == 0)
            return;
        if (clipped.size() == 4) {
            for (var vertex : clipped)
                vertex(pose, out, vertex.point, color, vertex.alpha);
            volumeQuads++;
        } else
            for (int i = 1; i + 1 < clipped.size(); i++) {
                VolumeVertex[] triangle = {clipped.getFirst(), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1)};
                for (var vertex : triangle)
                    vertex(pose, out, vertex.point, color, vertex.alpha);
                volumeQuads++;
            }
    }
    private static void volume(Field field, Vec3 eye, PoseStack pose, VertexConsumer out, int color) {
        Vec3 view = eye.subtract(field.lens).normalize();
        double z = Math.abs(view.dot(field.forward)), x = Math.abs(view.dot(field.right)),
               y = Math.abs(view.dot(field.up));
        if (z >= x && z >= y) {
            for (int s = 0; s < SLICES; s++) {
                double d = .15 + (field.range - .15) * (s + .5) / SLICES;
                for (int i = 0; i < HORIZONTAL_CELLS; i++)
                    for (int j = 0; j < VERTICAL_CELLS; j++)
                        cell(field, pose, out, color, new double[] {d, d, d, d},
                                new double[] {u(i), u(i + 1), u(i + 1), u(i)},
                                new double[] {v(j), v(j), v(j + 1), v(j + 1)});
            }
        } else if (x >= y) {
            for (int s = 0; s < SLICES; s++) {
                double u = -1 + 2 * (s + .5) / SLICES;
                for (int i = 0; i < SLICES; i++)
                    for (int j = 0; j < VERTICAL_CELLS; j++) {
                        double d0 = .15 + (field.range - .15) * i / SLICES,
                               d1 = .15 + (field.range - .15) * (i + 1) / SLICES;
                        cell(field, pose, out, color, new double[] {d0, d1, d1, d0}, new double[] {u, u, u, u},
                                new double[] {v(j), v(j), v(j + 1), v(j + 1)});
                    }
            }
        } else {
            for (int s = 0; s < VERTICAL_CELLS; s++) {
                double v = -1 + 2 * (s + .5) / VERTICAL_CELLS;
                for (int i = 0; i < SLICES; i++)
                    for (int j = 0; j < HORIZONTAL_CELLS; j++) {
                        double d0 = .15 + (field.range - .15) * i / SLICES,
                               d1 = .15 + (field.range - .15) * (i + 1) / SLICES;
                        cell(field, pose, out, color, new double[] {d0, d1, d1, d0},
                                new double[] {u(j), u(j), u(j + 1), u(j + 1)}, new double[] {v, v, v, v});
                    }
            }
        }
    }
    private static void surface(Field field, PoseStack pose, VertexConsumer out, int color) {
        for (Patch patch : field.patches) {
            Ray[] rays = patch.rays;
            Vec3 n = Vec3.atLowerCornerOf(rays[0].normal.getNormal());
            int[] alphas = new int[rays.length];
            int sum = 0;
            for (int i = 0; i < rays.length; i++) {
                double edgeFade = Math.clamp((field.range - rays[i].depth) / 2, 0, 1);
                alphas[i] = (int) Math.round(
                        112 * softness(patch.us[i], patch.vs[i]) * (1 - .7 * rays[i].depth / field.range) * edgeFade);
                sum += alphas[i];
            }
            if (sum == 0)
                continue;
            for (int i = 0; i < 4; i++) {
                int j = Math.min(i, rays.length - 1);
                vertex(pose, out, rays[j].point.add(n.scale(.006)), color, alphas[j]);
            }
            surfaceQuads++;
        }
    }
    private CameraHaloRenderer() {
    }
}
