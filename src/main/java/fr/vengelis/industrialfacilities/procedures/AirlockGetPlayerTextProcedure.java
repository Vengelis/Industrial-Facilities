package fr.vengelis.industrialfacilities.procedures;
import fr.vengelis.industrialfacilities.airlock.AirlockPlayerAccessManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
public class AirlockGetPlayerTextProcedure {
    public static String execute(LevelAccessor world, double x, double y, double z) {
        return world instanceof Level level ? AirlockPlayerAccessManager.getPlayersText(level,BlockPos.containing(x,y,z)) : "No controller";
    }
}
