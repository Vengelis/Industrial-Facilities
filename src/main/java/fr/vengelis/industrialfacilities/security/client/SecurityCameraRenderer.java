package fr.vengelis.industrialfacilities.security.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.security.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** The support stays fixed to its mounting surface; only the optical head follows the configured view. */
@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class SecurityCameraRenderer implements BlockEntityRenderer<SecurityCameraEntity> {
    private static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "block/security_camera_head"));
    public SecurityCameraRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(HEAD);
    }
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SecurityContent.CAMERA_TYPE, SecurityCameraRenderer::new);
    }
    @Override
    public AABB getRenderBoundingBox(SecurityCameraEntity camera) {
        return new AABB(camera.getBlockPos()).inflate(.5);
    }
    @Override
    public int getViewDistance() {
        return 64;
    }
    @Override
    public void render(SecurityCameraEntity camera, float partial, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        if (camera.getLevel() == null)
            return;
        Direction facing = camera.getBlockState().getValue(SecurityCameraBlock.FACING);
        float base = switch (facing) {
            case EAST -> 90;
            case NORTH -> 180;
            case WEST -> 270;
            default -> 0;
        }; float yaw = camera.effectiveYaw(partial), pitch = camera.effectivePitch(partial);
        if (!Float.isFinite(pitch))
            pitch = 0;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(base - yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(-.5, -.5, -.5);
        var model = Minecraft.getInstance().getModelManager().getModel(HEAD);
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        var random = RandomSource.create(42);
        for (int side = 0; side <= 6; side++) {
            random.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], random)) {
                int packed = quad.getSprite().contents().name().getPath().equals("block/camera_terminal_screen_32")
                        ? LightTexture.FULL_BRIGHT
                        : light;
                out.putBulkData(pose.last(), quad, 1, 1, 1, 1, packed, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
    }
}
