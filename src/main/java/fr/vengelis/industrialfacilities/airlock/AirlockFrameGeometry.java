package fr.vengelis.industrialfacilities.airlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Recover the exact rectangular border from a closed door's recorded panels. */
public final class AirlockFrameGeometry {
    private AirlockFrameGeometry() {}

    public static List<BlockPos> border(long[] panels, Direction.Axis normal) {
        if (panels.length==0) return List.of();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        var unique=new HashSet<Long>();
        for (long encoded:panels) {
            BlockPos p=BlockPos.of(encoded);
            unique.add(encoded);
            minX=Math.min(minX,p.getX()); maxX=Math.max(maxX,p.getX());
            minY=Math.min(minY,p.getY()); maxY=Math.max(maxY,p.getY());
            minZ=Math.min(minZ,p.getZ()); maxZ=Math.max(maxZ,p.getZ());
        }
        int aMin=normal==Direction.Axis.X ? minZ : minX;
        int aMax=normal==Direction.Axis.X ? maxZ : maxX;
        int bMin=normal==Direction.Axis.Y ? minZ : minY;
        int bMax=normal==Direction.Axis.Y ? maxZ : maxY;
        int fixed=normal==Direction.Axis.X ? minX : normal==Direction.Axis.Y ? minY : minZ;
        int fixedMax=normal==Direction.Axis.X ? maxX : normal==Direction.Axis.Y ? maxY : maxZ;
        long width=(long)aMax-aMin+1, height=(long)bMax-bMin+1;
        if (fixed!=fixedMax || width>23 || height>23 || width*height!=unique.size()) return List.of();
        List<BlockPos> result=new ArrayList<>();
        for (int a=aMin-1;a<=aMax+1;a++) for (int b=bMin-1;b<=bMax+1;b++) {
            if (a!=aMin-1 && a!=aMax+1 && b!=bMin-1 && b!=bMax+1) continue;
            result.add(normal==Direction.Axis.X ? new BlockPos(fixed,b,a)
                : normal==Direction.Axis.Y ? new BlockPos(a,fixed,b) : new BlockPos(a,b,fixed));
        }
        return result;
    }
}
