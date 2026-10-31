package fr.vengelis.industrialfacilities.door;

/** Each face samples an entire native 32px tile, independently of door dimensions. */
public final class IndustrialDoorFrameLayout {
    public record Tile(String part,int turns) {}
    // Mask directions, clockwise in the door plane: up, right, down, left.
    public static int rotateMask(int mask,int turns){
        turns=Math.floorMod(turns,4);return ((mask<<turns)|(mask>>>(4-turns)))&15;
    }
    public static Tile tile(int mask){
        mask&=15;
        if(mask==5)return new Tile("frame_middle",0);
        if(mask==10)return new Tile("frame_middle",1);
        if(Integer.bitCount(mask)==1)return new Tile("frame_end",Math.floorMod(Integer.numberOfTrailingZeros(mask)-2,4));
        if(Integer.bitCount(mask)==2)for(int turns=0;turns<4;turns++)
            if(rotateMask(3,turns)==mask)return new Tile("frame_corner",turns);
        return new Tile("frame",0);
    }
    /** Inverse texture rotation: the painted connection turns clockwise with the tile. */
    public static float[] uv(float u,float v,int turns){
        for(int i=0;i<Math.floorMod(turns,4);i++){float next=v;v=1-u;u=next;}
        return new float[]{u,v};
    }
    private IndustrialDoorFrameLayout(){}
}
