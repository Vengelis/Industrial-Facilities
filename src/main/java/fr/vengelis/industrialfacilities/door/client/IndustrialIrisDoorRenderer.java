package fr.vengelis.industrialfacilities.door.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.vengelis.industrialfacilities.door.IndustrialIrisDoorMotion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

final class IndustrialIrisDoorRenderer {
    private static final int PETALS = 8;
    private static final float EDGE_WIDTH = .075f;
    private static final float BLADE_RISE = .105f;
    private static final float HALF_THICKNESS = .013f;

    private record Point(float x, float y, float depth, float u, float v) {
        Point mix(Point other, float t) {
            return new Point(x + (other.x - x) * t, y + (other.y - y) * t, depth + (other.depth - depth) * t,
                u + (other.u - u) * t, v + (other.v - v) * t);
        }

        Point uv(float u, float v) { return new Point(x, y, depth, u, v); }

        float distance(Point other) { return (float) Math.hypot(other.x - x, other.y - y); }

        float coordinate(boolean xAxis) { return xAxis ? x : y; }

        float[] at(float faceOffset) { return new float[]{x + 1, y + 1, depth + faceOffset}; }
    }
    private record Edge(List<Point> vertices, Blade blade) {}

    private record Blade(float hingeX, float hingeY, int size, float cos, float sin, float depth) {
        Point point(float radial, float side) {
            float fromHinge = side + size;
            return new Point(hingeX + cos * radial - sin * fromHinge,
                hingeY + sin * radial + cos * fromHinge, depth + slope() * side, radial / 2, -side / 2);
        }

        float slope() { return BLADE_RISE / size; }
    }

    static void render(PoseStack pose, MultiBufferSource buffers, VertexConsumer concrete, int size, float progress,
                       IndustrialDoorLighting light, ResourceLocation stripes) {
        if (progress >= 1) return;
        var pivot = IndustrialIrisDoorMotion.pivot(progress);
        List<Edge> edges = new ArrayList<>(PETALS);
        for (int index = 0; index < PETALS; index++) {
            double angle = index * Math.PI / 4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            float hingeX = (float) (size * (.5 + sin)), hingeY = (float) (size * (.5 - cos));
            float depth = .5f + (index - (PETALS - 1) / 2f) * .00025f;
            Blade blade = new Blade(hingeX, hingeY, size, (float) (cos * pivot.cos() + sin * pivot.sin()),
                (float) (sin * pivot.cos() - cos * pivot.sin()), depth);
            renderBlade(pose, concrete, light, size, blade, (index & 1) == 0 ? 251 : 221, edges);
        }
        var trim = buffers.getBuffer(RenderType.entityCutout(stripes));
        for (var edge : edges) faces(pose, trim, light, edge.vertices(), 255, edge.blade());
    }

    private static void renderBlade(PoseStack pose, VertexConsumer concrete, IndustrialDoorLighting light, int size,
                                    Blade blade, int shade, List<Edge> edges) {
        // Eight identical right triangles overlap in a pinwheel. Their long
        // trailing sides cover the square corners until the octagonal opening reaches them.
        float trailing = -size, tip = .39f * size;
        float leg = tip - trailing;
        Point a = blade.point(0, trailing);
        Point b = blade.point(leg, trailing);
        Point c = blade.point(0, tip);
        Point trimA = blade.point(EDGE_WIDTH, trailing);
        Point trimB = blade.point(EDGE_WIDTH, tip - EDGE_WIDTH);
        faces(pose, concrete, light, clip(List.of(trimA, b, trimB), size), shade, blade);
        edges.add(new Edge(clip(List.of(a.uv(0, 0), trimA.uv(0, 1),
            trimB.uv((leg - EDGE_WIDTH) * 1.5f, 1), c.uv(leg * 1.5f, 0)), size), blade));
        side(pose, concrete, light, size, a, b);
        side(pose, concrete, light, size, b, c);
        side(pose, concrete, light, size, c, a);
    }

    private static List<Point> clip(List<Point> points, int size) {
        points = clipEdge(points, true, 0, true);
        points = clipEdge(points, true, size, false);
        points = clipEdge(points, false, 0, true);
        return clipEdge(points, false, size, false);
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

    private static void faces(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, List<Point> points, int shade, Blade blade) {
        if (points.size() < 3) return;
        float slope = blade.slope();
        // The same tangential incline on every rigid petal gives the overlap its spiral at either face.
        float nz = 1f / (float) Math.sqrt(1 + slope * slope);
        float nx = blade.sin() * slope * nz, ny = -blade.cos() * slope * nz;
        float front = HALF_THICKNESS, back = -HALF_THICKNESS;
        Point a = points.getFirst();
        for (int index = 1; index + 1 < points.size(); index++) {
            Point b = points.get(index), c = points.get(index + 1);
            if (Math.abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) < 1e-6f) continue;
            quad(pose, out, light, shade, nx, ny, nz, a, b, c, c, front, front, front, front);
            quad(pose, out, light, shade, -nx, -ny, -nz, a, c, b, b, back, back, back, back);
        }
    }

    private static void side(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int size, Point a, Point b) {
        float length = a.distance(b);
        if (length < .0001f) return;
        float from = 0, to = 1;
        for (boolean xAxis : new boolean[]{true, false}) {
            float start = a.coordinate(xAxis), delta = b.coordinate(xAxis) - start;
            if (Math.abs(delta) < 1e-6f) {
                if (start < 0 || start > size) return;
                continue;
            }
            float t0 = -start / delta, t1 = (size - start) / delta;
            from = Math.max(from, Math.min(t0, t1));
            to = Math.min(to, Math.max(t0, t1));
        }
        if (from >= to) return;
        Point start = a.mix(b, from), end = a.mix(b, to);
        float nx = (b.y - a.y) / length, ny = (a.x - b.x) / length;
        float front = HALF_THICKNESS, back = -HALF_THICKNESS;
        quad(pose, out, light, 174, nx, ny, 0, start.uv(from * length, 0), end.uv(to * length, 0),
            end.uv(to * length, .25f), start.uv(from * length, .25f), back, back, front, front);
    }

    private static void quad(PoseStack pose, VertexConsumer out, IndustrialDoorLighting light, int shade, float nx, float ny, float nz,
                             Point a, Point b, Point c, Point d, float za, float zb, float zc, float zd) {
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
