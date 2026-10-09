package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.AirlockControllerBlock;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.Set;

/** One validity check shared by the GUI and the controller's block model. */
public final class AirlockStatusManager {
    private AirlockStatusManager() {}

    public enum Status {
        OPEN("Air Lock Open", true), CLOSED("Air Lock Closed", true),
        INVALID("Invalid Frame / Obstructed", false), INCOMPLETE("Incomplete Air Lock", false),
        MISSING("No controller", false);
        private final String text;
        private final boolean valid;
        Status(String text, boolean valid) { this.text=text; this.valid=valid; }
        public String text() { return text; }
        public boolean valid() { return valid; }
    }

    public static Status evaluate(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller)) return Status.MISSING;
        var data=controller.getPersistentData();
        if (!data.getBoolean("IFAirlockClosed")) {
            return AirlockStructureManager.detect(level,pos)!=null ? Status.OPEN : Status.INVALID;
        }
        var structure=AirlockStructureManager.detectForStatus(level,pos);
        if (structure==null) return Status.INVALID;
        Set<Long> saved=new HashSet<>();
        for (long panel : data.getLongArray("IFAirlockPanels")) saved.add(panel);
        if (saved.size()!=structure.getInteriorWidth()*structure.getInteriorHeight()) return Status.INCOMPLETE;
        for (int a=1;a<structure.width()-1;a++) for (int b=1;b<structure.height()-1;b++) {
            BlockPos panel=structure.getPosition(a,b);
            if (!saved.contains(panel.asLong()) || !level.getBlockState(panel).is(IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get())) return Status.INCOMPLETE;
        }
        return Status.CLOSED;
    }

    public static void updateAppearance(ServerLevel level, BlockPos pos) {
        var state=level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirlockControllerBlock)) return;
        boolean valid=evaluate(level,pos).valid();
        if (state.getValue(AirlockControllerBlock.STRUCTURE_VALID)!=valid) {
            // Same block and block entity; only synchronize the model property.
            level.setBlock(pos,state.setValue(AirlockControllerBlock.STRUCTURE_VALID,valid),Block.UPDATE_CLIENTS);
        }
    }
}
