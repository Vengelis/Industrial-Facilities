package fr.vengelis.industrialfacilities.door;

/** Material family is determined by the registered block; existing saved blocks keep their IDs. */
public enum IndustrialDoorStyle {
    LIGHT("",32), DARK("dark",32), RESTRICTED("restricted",64), GLASS("glass",32), RUSTY("rusty",32),
    SAFE("safe",64), RADIATION("radiation",64), FIRE("fire",64), BLAST("blast",64), MESH("mesh",32);
    public static final float NORMAL_BLAST_RESISTANCE = 5;
    public static final float REINFORCED_BLAST_RESISTANCE = 3600000;
    public final String id;
    public final int panelPixels;
    IndustrialDoorStyle(String id,int panelPixels){this.id=id;this.panelPixels=panelPixels;}
    public String configName() { return id.isEmpty() ? "light" : id; }
    public boolean reinforced() { return this == BLAST || this == RADIATION; }
    public boolean lifts() { return reinforced() || this == MESH; }
    public boolean hasClassic() { return this != MESH; }
    public float blastResistance() { return reinforced() ? REINFORCED_BLAST_RESISTANCE : NORMAL_BLAST_RESISTANCE; }
    public String texturePath(String part){
        return "textures/block/industrial_door_"+(id.isEmpty()?"":id+"_")+part+"_"+(part.equals("panel")?panelPixels:32)+".png";
    }
}
