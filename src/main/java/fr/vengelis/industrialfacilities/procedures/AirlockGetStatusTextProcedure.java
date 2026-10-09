package fr.vengelis.industrialfacilities.procedures;
import fr.vengelis.industrialfacilities.airlock.AirlockStatusManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
public class AirlockGetStatusTextProcedure {
    public static String execute(LevelAccessor world, double x, double y, double z) {
        return world instanceof Level level
            ? AirlockStatusManager.evaluate(level,BlockPos.containing(x,y,z)).text()
            : AirlockStatusManager.Status.MISSING.text();
    }
}
