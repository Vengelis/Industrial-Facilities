package fr.vengelis.industrialfacilities.door.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorPlane;
import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import fr.vengelis.industrialfacilities.door.IndustrialIrisDoorMotion;
import fr.vengelis.industrialfacilities.door.IndustrialJawDoorMotion;
import net.minecraft.core.BlockPos;

final class IndustrialDoorFrameGroove {
    static final float DEPTH = 1f / 16;
    static final float PANEL_BACK = 6f / 16;
    static final float PANEL_FRONT = 10f / 16;
    private static final float BEVEL = 1f / 32;
    private static final float HALF_TEXEL = .5f / 32;
    private static final float DIAGONAL_NORMAL = (float) Math.sqrt(.5);

    record Section(int face, int index, int length, boolean grooved) {}

    static Section section(IndustrialDoorEntity door, BlockPos pos, IndustrialDoorPlane plane) {
        int column = plane.column(door.doorOrigin(), pos), row = plane.row(door.doorOrigin(), pos);
        int width = door.size - 2, height = door.height() - 2;
        boolean jaw = door.style() == IndustrialDoorStyle.JAW;
        boolean sideways = jaw && IndustrialJawDoorMotion.layout(door, plane).sideways();
        // The jambs guide vertical sliding panels, and the sill seats them when the door closes.
        boolean jambGroove = !jaw || sideways;
        if (column >= 1 && column <= width) {
            if (row == 0) return new Section(1, column - 1, width, !sideways);
            if (row == height + 1) return new Section(4, width - column, width, !sideways);
        }
        if (row >= 1 && row <= height) {
            if (column == 0) return new Section(2, height - row, height, jambGroove);
            if (column == width + 1) return new Section(8, row - 1, height, jambGroove);
        }
        return null;
    }

    static void render(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int lightAtMouth,
                       IndustrialDoorStyle style, Section section, float nx, float ny) {
        if (!section.grooved()) {
            flat(pose, out, light, lightAtMouth, nx, ny, 0, 1, 0);
            return;
        }
        // Give sliding panels one texel of clearance against the walls of their tracks.
        // The standard groove also spans both tracks of the mesh door.
        float back = switch (style) {
            case IRIS -> (float) IndustrialIrisDoorMotion.BACK;
            case JAW -> (float) IndustrialJawDoorMotion.BACK - 1f / 32;
            default -> PANEL_BACK - 1f / 32;
        };
        float front = 1 - back;
        flat(pose, out, light, lightAtMouth, nx, ny, 0, back - BEVEL, 0);
        flat(pose, out, light, lightAtMouth, nx, ny, back, front, DEPTH);
        flat(pose, out, light, lightAtMouth, nx, ny, front + BEVEL, 1, 0);
        edge(pose, out, light, lightAtMouth, nx, ny, back, false);
        edge(pose, out, light, lightAtMouth, nx, ny, front, true);

        // Each straight run ends against a corner or an uncut intersecting member.
        if (section.index() == 0) cap(pose,out,light,lightAtMouth,nx,ny,0,back,front);
        if (section.index() == section.length() - 1) cap(pose,out,light,lightAtMouth,nx,ny,1,back,front);
    }

    private static void edge(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int packedLight,
                             float nx, float ny, float z, boolean near) {
        float nz = near ? -1 : 1;
        float outer = z - nz * BEVEL;
        float[][] chamfer = near ? strip(nx, ny, outer, 0, z, BEVEL) : strip(nx, ny, z, BEVEL, outer, 0);
        IndustrialDoorRenderer.textured(pose, out, chamfer, constantDepthUv(z - nz * HALF_TEXEL),
            light, nx * DIAGONAL_NORMAL, ny * DIAGONAL_NORMAL, nz * DIAGONAL_NORMAL, packedLight);

        float[][] wall = strip(nx, ny, z, near ? BEVEL : DEPTH, z, near ? DEPTH : BEVEL);
        IndustrialDoorRenderer.textured(pose, out, wall, constantDepthUv(z + nz * HALF_TEXEL),
            light, 0, 0, nz, packedLight);
    }

    private static void flat(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int packedLight,
                             float nx, float ny, float back, float front, float inset) {
        float[][] points = strip(nx, ny, front, inset, back, inset);
        float[][] uv = {{0,front},{1,front},{1,back},{0,back}};
        IndustrialDoorRenderer.textured(pose,out,points,uv,light,nx,ny,0,packedLight);
    }

    private static void cap(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int packedLight,
                            float nx, float ny, float u, float back, float front) {
        float[][] points = {point(nx,ny,u,back,0), point(nx,ny,u,front,0),
            point(nx,ny,u,front,DEPTH), point(nx,ny,u,back,DEPTH)};
        float[][] uv = {{u,back},{u,front},{u,front},{u,back}};
        float normal = u == 0 ? -1 : 1;
        float capX = normal * ny, capY = -normal * nx;
        IndustrialDoorRenderer.textured(pose, out, points, uv, light, capX, capY, 0, packedLight);

        float[] backCorner = point(nx, ny, u, back, BEVEL), frontCorner = point(nx, ny, u, front, BEVEL);
        float[][] backWing = {point(nx, ny, u, back - BEVEL, 0), points[0], backCorner, backCorner};
        float[][] frontWing = {points[1], point(nx, ny, u, front + BEVEL, 0), frontCorner, frontCorner};
        float backV = back - HALF_TEXEL, frontV = front + HALF_TEXEL;
        IndustrialDoorRenderer.textured(pose, out, backWing, new float[][]{{u,backV},{u,backV},{u,backV},{u,backV}},
            light, capX, capY, 0, packedLight);
        IndustrialDoorRenderer.textured(pose, out, frontWing, new float[][]{{u,frontV},{u,frontV},{u,frontV},{u,frontV}},
            light, capX, capY, 0, packedLight);
    }

    private static float[][] strip(float nx, float ny, float front, float frontInset, float back, float backInset) {
        return new float[][]{point(nx, ny, 0, front, frontInset), point(nx, ny, 1, front, frontInset),
            point(nx, ny, 1, back, backInset), point(nx, ny, 0, back, backInset)};
    }

    private static float[][] constantDepthUv(float v) {
        return new float[][]{{0,v},{1,v},{1,v},{0,v}};
    }

    private static float[] point(float nx, float ny, float u, float z, float inset) {
        // U follows the perimeter, starting with the sill from left to right.
        float x = ny > 0 ? u : ny < 0 ? 1 - u : nx < 0 ? 0 : 1;
        float y = ny > 0 ? 1 : ny < 0 ? 0 : nx < 0 ? u : 1 - u;
        return new float[]{x - nx * inset, y - ny * inset, z};
    }

    private IndustrialDoorFrameGroove() {}
}
