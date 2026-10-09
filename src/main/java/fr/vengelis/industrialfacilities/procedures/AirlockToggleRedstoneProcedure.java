package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

public class AirlockToggleRedstoneProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		boolean load = false;
		Entity ent = null;
		if (!world.isClientSide()) {
			load = (world.getBlockState(BlockPos.containing(x, y, z))).canSurvive(world, BlockPos.containing(x, y, z));
			ent = entity;
			if (world instanceof net.minecraft.server.level.ServerLevel level && entity instanceof net.minecraft.server.level.ServerPlayer player) {
				net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
				if (level.getBlockEntity(pos) instanceof fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity controller) {
					var data = controller.getPersistentData();
					boolean enabled = !data.getBoolean("IFRedstoneEnabled");
					data.putBoolean("IFRedstoneEnabled", enabled);
					controller.setChanged();
					var state = level.getBlockState(pos);
					level.sendBlockUpdated(pos, state, state, 3);
					player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Airlock Redstone: " + (enabled ? "Enabled" : "Disabled")));
				}
			}
		}
	}
}