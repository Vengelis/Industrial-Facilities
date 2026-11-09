package fr.vengelis.industrialfacilities.door.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Samples the world immediately outside each visible surface, in door-local coordinates. */
public final class IndustrialDoorLighting {
    private static final double EPSILON = .001;
    private final Level level;
    private final BlockPos origin;
    private final Direction facing, right;
    private final boolean panel;
    private final Map<BlockPos,Integer> samples = new HashMap<>();

    public IndustrialDoorLighting(Level level, BlockPos origin, Direction facing) {
        this(level,origin,facing,false);
    }

    public IndustrialDoorLighting(Level level, BlockPos origin, Direction facing, boolean panel) {
        this.level = level;
        this.origin = origin;
        this.facing = facing;
        this.right = facing.getCounterClockWise();
        this.panel = panel;
    }

    public int face(float[][] points, float nx, float ny, float nz) {
        double x=0,y=0,z=0;
        for(float[] p:points){x+=p[0]/4d;y+=p[1]/4d;z+=p[2]/4d;}
        // The panel is recessed inside transparent aperture cells. Its front and
        // back sample their respective exterior, never the opaque controller.
        x+=nx*EPSILON;y+=ny*EPSILON;
        z+=nz*EPSILON;
        if(panel)z=nz>0?Math.max(z,1+EPSILON):nz<0?Math.min(z,-EPSILON):z;
        return sample(x,y,z);
    }

    public int vertex(float[] point,float nz,int faceLight) {
        if(!panel||nz==0)return faceLight;
        // Interpolate the four exterior cell centres at each panel vertex. The
        // shared vertices then have identical light, avoiding a visible light grid.
        double x=point[0]-.5,y=point[1]-.5;
        int left=(int)Math.floor(x),bottom=(int)Math.floor(y);
        double u=x-left,v=y-bottom,block=0,sky=0;
        for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++){
            double weight=(dx==0?1-u:u)*(dy==0?1-v:v);
            int light=sample(left+dx+.5,bottom+dy+.5,nz>0?1+EPSILON:-EPSILON);
            block+=LightTexture.block(light)*weight;sky+=LightTexture.sky(light)*weight;
        }
        return LightTexture.pack((int)Math.round(block),(int)Math.round(sky));
    }

    private int sample(double x,double y,double z) {
        BlockPos pos=BlockPos.containing(origin.getX()+.5+right.getStepX()*(x-.5)+facing.getStepX()*(z-.5),
            origin.getY()+y,origin.getZ()+.5+right.getStepZ()*(x-.5)+facing.getStepZ()*(z-.5));
        return samples.computeIfAbsent(pos,p->LevelRenderer.getLightColor(level,p));
    }
}
