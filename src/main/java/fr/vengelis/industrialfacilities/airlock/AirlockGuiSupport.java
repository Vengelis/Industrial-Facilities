package fr.vengelis.industrialfacilities.airlock;
import fr.vengelis.industrialfacilities.world.inventory.AirlockControllerGUIMenu;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
public final class AirlockGuiSupport {
    private AirlockGuiSupport() {}
    public static AirlockControllerGUIMenu menu(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player.level() != world
            || !(player.containerMenu instanceof AirlockControllerGUIMenu menu)
            || !menu.matches(BlockPos.containing(x,y,z)) || !menu.stillValid(player)) return null;
        return menu;
    }
    public static void sync(AirlockControllerBlockEntity controller) {
        controller.setChanged();
        var level = controller.getLevel();
        if (level != null && !level.isClientSide()) {
            var state = controller.getBlockState();
            level.sendBlockUpdated(controller.getBlockPos(), state, state, 3);
        }
    }
}
