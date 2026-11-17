package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.GraphLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bakes the graph fills in paint order into a single layer of disjoint quads. */
final class WorldGraphSurface implements GraphInk.Surface {
    private final int canvasWidth, canvasHeight;
    private final int[] pixels;
    private final List<Glyph> glyphs = new ArrayList<>();

    record Rect(int x, int y, int width, int height, int color) {}
    record Glyph(String text, int x, int y, int color, float scale) {}
    record Frame(List<Rect> background, List<Glyph> glyphs) {}

    WorldGraphSurface(GraphLayout.Canvas canvas) {
        canvasWidth = canvas.width();
        canvasHeight = canvas.height();
        pixels = new int[canvasWidth * canvasHeight];
    }

    @Override public void rect(int x, int y, int width, int height, int color) {
        int left = Math.max(0, x), right = Math.min(canvasWidth, x + width);
        if (left >= right) return;
        for (int row = Math.max(0, y); row < Math.min(canvasHeight, y + height); row++) {
            Arrays.fill(pixels, row * canvasWidth + left, row * canvasWidth + right, color);
        }
    }

    @Override public void text(String text, int x, int y, int color) { scaledText(text, x, y, color, 1); }

    @Override public void scaledText(String text, int x, int y, int color, float scale) {
        glyphs.add(new Glyph(text, x, y, color, scale));
    }

    Frame finish() {
        List<Rect> background = new ArrayList<>();
        for (int y = 0; y < canvasHeight; y++) {
            for (int x = 0; x < canvasWidth; x++) {
                int color = pixels[y * canvasWidth + x];
                if (color == 0) continue;
                int right = x + 1;
                while (right < canvasWidth && pixels[y * canvasWidth + right] == color) right++;
                int bottom = y + 1;
                while (bottom < canvasHeight && hasColor(bottom, x, right, color)) bottom++;
                background.add(new Rect(x, y, right - x, bottom - y, color));
                for (int row = y; row < bottom; row++) {
                    Arrays.fill(pixels, row * canvasWidth + x, row * canvasWidth + right, 0);
                }
            }
        }
        return new Frame(List.copyOf(background), List.copyOf(glyphs));
    }

    private boolean hasColor(int row, int left, int right, int color) {
        for (int at = row * canvasWidth + left; at < row * canvasWidth + right; at++) {
            if (pixels[at] != color) return false;
        }
        return true;
    }
}
