package fr.vengelis.industrialfacilities.procedures;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import fr.vengelis.industrialfacilities.door.IndustrialDoorManager;
public class IndustrialDoorAssembleProcedure {
    public static void execute(LevelAccessor world,double x,double y,double z) {
        if(world instanceof ServerLevel server)IndustrialDoorManager.assemble(server,BlockPos.containing(x,y,z));
    }
}
