package fr.vengelis.industrialfacilities.door;

/** Material family is determined by the registered block; existing saved blocks keep their IDs. */
public enum IndustrialDoorStyle {
    LIGHT("",32), DARK("dark",32), RESTRICTED("restricted",64), GLASS("glass",32), RUSTY("rusty",32), SAFE("safe",64), RADIATION("radiation",64);
    public final String id;
    public final int panelPixels;
    IndustrialDoorStyle(String id,int panelPixels){this.id=id;this.panelPixels=panelPixels;}
    public String texturePath(String part){
        return "textures/block/industrial_door_"+(id.isEmpty()?"":id+"_")+part+"_"+(part.equals("panel")?panelPixels:32)+".png";
    }
}
