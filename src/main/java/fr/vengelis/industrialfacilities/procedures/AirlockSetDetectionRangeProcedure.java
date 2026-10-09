package fr.vengelis.industrialfacilities.procedures;
import fr.vengelis.industrialfacilities.airlock.AirlockGuiSupport;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
public class AirlockSetDetectionRangeProcedure {
    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        var menu = AirlockGuiSupport.menu(world,x,y,z,entity);
        if (menu == null) return;
        Object input = menu.getMenuState().get("2:slider_detect_range");
        if (!(input instanceof Number n) || !Double.isFinite(n.doubleValue())) return;
        int range = (int) Math.round(Math.max(1,Math.min(10,n.doubleValue())));
        if (world.getBlockEntity(BlockPos.containing(x,y,z)) instanceof AirlockControllerBlockEntity controller) {
            var data = controller.getPersistentData();
            if (!data.contains("IFDetectionRange") || data.getInt("IFDetectionRange") != range) {
                data.putInt("IFDetectionRange",range);
                AirlockGuiSupport.sync(controller);
            }
        }
    }
}
