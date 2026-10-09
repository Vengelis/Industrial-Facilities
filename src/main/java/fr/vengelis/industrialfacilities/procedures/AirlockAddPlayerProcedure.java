package fr.vengelis.industrialfacilities.procedures;
import fr.vengelis.industrialfacilities.airlock.AirlockGuiSupport;
import fr.vengelis.industrialfacilities.airlock.AirlockPlayerAccessManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
public class AirlockAddPlayerProcedure {
    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        var menu=AirlockGuiSupport.menu(world,x,y,z,entity);
        if (menu==null) return;
        ServerPlayer player=(ServerPlayer)entity;
        String result=AirlockPlayerAccessManager.addPlayer(player.serverLevel(),BlockPos.containing(x,y,z),menu.getPlayerInput());
        player.sendSystemMessage(Component.literal(result));
        menu.refreshState();
    }
}
