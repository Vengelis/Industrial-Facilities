package fr.vengelis.industrialfacilities.fabricator;

/** Shared inventory coordinates; the compact layout also fits Minecraft's minimum GUI size. */
public record FabricatorLayout(int width, int height, boolean large) {
    public static FabricatorLayout forScreen(int width, int height) {
        return width >= 452 && height >= 318 ? new FabricatorLayout(440, 306, true)
            : new FabricatorLayout(Math.max(312, Math.min(360, width - 8)), 238, false);
    }
    public int catalogueX() { return large ? 12 : 10; }
    public int catalogueWidth() { return large ? 240 : width - 172; }
    public int detailX() { return width - (large ? 176 : 152); }
    public int detailWidth() { return large ? 164 : 138; }
    public int tabsX() { return large ? 20 : 16; }
    public int tabsY() { return large ? 34 : 31; }
    public int tabsWidth() { return catalogueWidth() - (large ? 16 : 12); }
    public int searchY() { return large ? 58 : 49; }
    public int gridY() { return large ? 80 : 65; }
    public int columns() { return large ? 7 : (catalogueWidth() - 12) / 25; }
    public int rows() { return large ? 3 : 2; }
    public int pitch() { return large ? 32 : 25; }
    public int tileSize() { return large ? 30 : 23; }
    public int pageSize() { return columns() * rows(); }
    public int footerY() { return large ? 179 : 127; }
    public int previewY() { return large ? 30 : 28; }
    public int previewHeight() { return large ? 91 : 46; }
    public int modesY() { return large ? 125 : 77; }
    public int materialsY() { return large ? 147 : 94; }
    public int materialsHeight() { return large ? 48 : 28; }
    public int materialRows() { return large ? 3 : 2; }
    public int materialPitch() { return large ? 14 : 11; }
    public int actionY() { return large ? 199 : 125; }
    public int inventoryX() { return (width - 162) / 2; }
    public int inventoryY() { return large ? 224 : 155; }
    public int hotbarY() { return large ? 282 : 213; }
}
