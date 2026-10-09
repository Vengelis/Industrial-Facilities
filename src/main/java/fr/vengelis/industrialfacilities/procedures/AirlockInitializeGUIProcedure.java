package fr.vengelis.industrialfacilities.procedures;
import fr.vengelis.industrialfacilities.airlock.AirlockGuiSupport;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
public class AirlockInitializeGUIProcedure {
    public static void execute(LevelAccessor world, double x, double y, double z) {
        if (!(world instanceof ServerLevel level)) return;
        BlockPos pos=BlockPos.containing(x,y,z);
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller)) return;
        var data=controller.getPersistentData();
        boolean changed=false;
        if (!data.contains("IFPlayerDetection",Tag.TAG_BYTE)) { data.putBoolean("IFPlayerDetection",true); changed=true; }
        for (String key : new String[]{"IFRedstoneEnabled","IFWhitelistEnabled","IFInvertSelection"}) {
            if (!data.contains(key,Tag.TAG_BYTE)) { data.putBoolean(key,false); changed=true; }
        }
        int range=data.contains("IFDetectionRange",Tag.TAG_ANY_NUMERIC) ? data.getInt("IFDetectionRange") : 5;
        range=Math.max(1,Math.min(10,range));
        if (!data.contains("IFDetectionRange",Tag.TAG_INT) || data.getInt("IFDetectionRange")!=range) {
            data.putInt("IFDetectionRange",range); changed=true;
        }
        // Never reset the UUID list, door state or stored panels on GUI opening.
        if (changed) AirlockGuiSupport.sync(controller);
    }
}
