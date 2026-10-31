package fr.vengelis.industrialfacilities.door;

/** Shared texture coordinates and status timing, also usable by server-side verification. */
public final class IndustrialDoorVisualState {
    public static final int RED=0xFF1812, GREEN=0x25FF36, ORANGE=0xFF6000, OFF=0x000000;
    public static float panelV(double height,double originalY) {
        // One 32x32 image is shared by both halves; the seam is V=.5.
        return (float)((height-originalY)/height);
    }
    public static boolean transitioning(float progress,boolean targetOpen) {
        return targetOpen ? progress<1 : progress>0;
    }
    public static int lampColor(float progress,boolean targetOpen,long ticks) {
        if(transitioning(progress,targetOpen))return Math.floorMod(ticks,10)<5 ? ORANGE : OFF;
        return progress>=1 ? GREEN : RED;
    }
    /** Continuous samples of a single strip, with no per-block end caps. */
    public static float stripV(int index,int length) { return (float)index/length*.5f; }
    public static double railBottom(int size) { return size*.6-.125; }
    public static double railTop(int size) { return size-.125; }
    public static double railStart(int size,int row) { return Math.max(0,railBottom(size)-row); }
    public static double railEnd(int size,int row) { return Math.min(1,railTop(size)-row); }
    public static boolean hasRail(int size,int row) { return railEnd(size,row)>railStart(size,row); }
    private IndustrialDoorVisualState() {}
}
