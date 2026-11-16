package fr.vengelis.industrialfacilities.block;

import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;

public class IndustrialDoorIrisBlock extends IndustrialDoorFaceMountedBlock {
    protected IndustrialDoorIrisBlock(boolean controller) {
        super(controller, IndustrialDoorStyle.IRIS);
    }
}
