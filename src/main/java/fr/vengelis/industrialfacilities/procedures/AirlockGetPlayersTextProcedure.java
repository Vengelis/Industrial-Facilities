package fr.vengelis.industrialfacilities.procedures;
import net.minecraft.world.level.LevelAccessor;
/** Compatibility with the existing plural MCreator element. */
public class AirlockGetPlayersTextProcedure {
    public static String execute(LevelAccessor world, double x, double y, double z) {
        return AirlockGetPlayerTextProcedure.execute(world,x,y,z);
    }
}
