package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

public class AirlockDetectStructureProcedure {
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
				String result = fr.vengelis.industrialfacilities.airlock.AirlockDoorManager.toggleDoor(level, pos);
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result));
			}
		}
	}
}