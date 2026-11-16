package fr.vengelis.industrialfacilities.door.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.vengelis.industrialfacilities.door.IndustrialIrisDoorMotion;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class IndustrialIrisDoorRenderer {
    private static final int PETALS = 8;
    private static final float OPENING_BEVEL_WIDTH = .075f;
    private static final float OUTLINE_WIDTH = 1f / 32;
    private static final int OUTLINE_SHADE = 205;
    private static final float TIP_RATIO = .39f;
    private static final float BLADE_RISE = .05f;
    private static final float HALF_THICKNESS = 11f / 128;
    private static final float EDGE_HALF_THICKNESS = HALF_THICKNESS - 1f / 32;

    private record Point(float x, float y, float depth, float u, float v, float halfThickness) {
        Point mix(Point other, float t) {
            return new Point(x + (other.x - x) * t, y + (other.y - y) * t, depth + (other.depth - depth) * t,
                u + (other.u - u) * t, v + (other.v - v) * t, halfThickness + (other.halfThickness - halfThickness) * t);
        }

        Point uv(float u, float v) { return new Point(x, y, depth, u, v, halfThickness); }

        Point atRim() { return new Point(x, y, depth, u, v, EDGE_HALF_THICKNESS); }

        float distance(Point other) { return (float) Math.hypot(other.x - x, other.y - y); }

        float coordinate(boolean xAxis) { return xAxis ? x : y; }

        float surface(int face) { return depth + face * halfThickness; }

        float[] at(int face) { return new float[]{x + 1, y + 1, surface(face)}; }
    }

    private record Blade(float hingeX, float hingeY, int size, float cos, float sin, float depth) {
        Point point(float radial, float side) {
            float fromHinge = side + size;
            // Centre the tilted solid between the existing front and back collision planes.
            float middle = size * (TIP_RATIO - 1) / 2;
            return new Point(hingeX + cos * radial - sin * fromHinge,
                hingeY + sin * radial + cos * fromHinge, depth + BLADE_RISE * (side - middle) / size,
                radial / 2, -side / 2, HALF_THICKNESS);
        }
    }

    static void render(PoseStack pose, VertexConsumer steel, int size, float progress, IndustrialDoorLighting light) {
        if (progress >= 1) return;
        var pivot = IndustrialIrisDoorMotion.pivot(progress);
        for (int index = 0; index < PETALS; index++) {
            double angle = index * Math.PI / 4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            float hingeX = (float) (size * (.5 + sin)), hingeY = (float) (size * (.5 - cos));
            float depth = .5f + (index - (PETALS - 1) / 2f) * .00025f;
            Blade blade = new Blade(hingeX, hingeY, size, (float) (cos * pivot.cos() + sin * pivot.sin()),
                (float) (sin * pivot.cos() - cos * pivot.sin()), depth);
            renderBlade(pose, steel, light, size, blade);
        }
    }

    private static void renderBlade(PoseStack pose, VertexConsumer steel, IndustrialDoorLighting light, int size,
                                    Blade blade) {
        // Eight identical right triangles overlap in a pinwheel. Their long
        // trailing sides cover the square corners until the octagonal opening reaches them.
        float trailing = -size, tip = TIP_RATIO * size;
        float leg = tip - trailing;
        Point a = blade.point(0, trailing).atRim();
        Point b = blade.point(leg, trailing).atRim();
        Point c = blade.point(0, tip).atRim();
        // The three steel chamfers meet the thick face and the rim without overlapping surfaces.
        float diagonalInset = OUTLINE_WIDTH * (float) Math.sqrt(2);
        Point innerA = blade.point(OPENING_BEVEL_WIDTH, trailing + OUTLINE_WIDTH);
        Point innerB = blade.point(leg - OUTLINE_WIDTH - diagonalInset, trailing + OUTLINE_WIDTH);
        Point innerC = blade.point(OPENING_BEVEL_WIDTH, tip - OPENING_BEVEL_WIDTH - diagonalInset);
        faces(pose, steel, light, clip(List.of(innerA, innerB, innerC), size), 255);
        faces(pose, steel, light, clip(List.of(a, b, innerB, innerA), size), OUTLINE_SHADE);
        faces(pose, steel, light, clip(List.of(b, c, innerC, innerB), size), OUTLINE_SHADE);
        faces(pose, steel, light, clip(List.of(c, a, innerA, innerC), size), OUTLINE_SHADE);
        side(pose, steel, light, size, a, b);
        side(pose, steel, light, size, b, c);
        side(pose, steel, light, size, c, a);
    }

    private static List<Point> clip(List<Point> points, int size) {
        float depth = IndustrialDoorFrameGroove.DEPTH;
        points = clipEdge(points, true, -depth, true);
        points = clipEdge(points, true, size + depth, false);
        points = clipEdge(points, false, -depth, true);
        return clipEdge(points, false, size + depth, false);
    }

    private static List<Point> clipEdge(List<Point> points, boolean xAxis, float edge, boolean minimum) {
        if (points.isEmpty()) return points;
        List<Point> result = new ArrayList<>();
        Point previous = points.getLast();
        for (Point next : points) {
            float a = previous.coordinate(xAxis), b = next.coordinate(xAxis);
            boolean insideA = minimum ? a >= edge : a <= edge;
            boolean insideB = minimum ? b >= edge : b <= edge;
            if (insideA != insideB) result.add(previous.mix(next, (edge - a) / (b - a)));
            if (insideB) result.add(next);
            previous = next;
        }
        return result;
    }

    private static void faces(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, List<Point> points, int shade) {
        if (points.size() < 3) return;
        Point a = points.getFirst();
        for (int index = 1; index + 1 < points.size(); index++) {
            Point b = points.get(index), c = points.get(index + 1);
            if (Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) < 1e-6f) continue;
            triangle(pose, out, light, shade, a, b, c, 1);
            triangle(pose, out, light, shade, a, c, b, -1);
        }
    }

    private static void triangle(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int shade,
                                 Point a, Point b, Point c, int face) {
        float bx = b.x - a.x, by = b.y - a.y, bz = b.surface(face) - a.surface(face);
        float cx = c.x - a.x, cy = c.y - a.y, cz = c.surface(face) - a.surface(face);
        float nx = by * cz - bz * cy, ny = bz * cx - bx * cz, nz = bx * cy - by * cx;
        float inverseLength = 1f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        quad(pose, out, light, shade, nx * inverseLength, ny * inverseLength, nz * inverseLength,
            a, b, c, c, face, face, face, face);
    }

    private static void side(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int size, Point a, Point b) {
        float length = a.distance(b);
        if (length < .0001f) return;
        float from = 0, to = 1;
        float min = -IndustrialDoorFrameGroove.DEPTH, max = size + IndustrialDoorFrameGroove.DEPTH;
        for (boolean xAxis : new boolean[]{true, false}) {
            float start = a.coordinate(xAxis), delta = b.coordinate(xAxis) - start;
            if (Math.abs(delta) < 1e-6f) {
                if (start < min || start > max) return;
                continue;
            }
            float t0 = (min - start) / delta, t1 = (max - start) / delta;
            from = Math.max(from, Math.min(t0, t1));
            to = Math.min(to, Math.max(t0, t1));
        }
        if (from >= to) return;
        Point start = a.mix(b, from), end = a.mix(b, to);
        float nx = (b.y - a.y) / length, ny = (a.x - b.x) / length;
        quad(pose, out, light, 174, nx, ny, 0, start.uv(from * length, 0), end.uv(to * length, 0),
            end.uv(to * length, .25f), start.uv(from * length, .25f), -1, -1, 1, 1);
    }

    private static void quad(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int shade, float nx, float ny, float nz,
                             Point a, Point b, Point c, Point d, int za, int zb, int zc, int zd) {
        Point[] uv = {a, b, c, d};
        float[][] points = {a.at(za), b.at(zb), c.at(zc), d.at(zd)};
        int faceLight = light.face(points, nx, ny, nz);
        for (int i = 0; i < points.length; i++) {
            float[] point = points[i];
            out.addVertex(pose.last(), point[0], point[1], point[2]).setColor(shade, shade, shade, 255)
                .setUv(uv[i].u, uv[i].v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light.vertex(point, nz, faceLight))
                .setNormal(pose.last(), nx, ny, nz);
        }
    }

    private IndustrialIrisDoorRenderer() {}
}
