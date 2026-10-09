package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.BlockPos;

public class AirlockAutomaticTickProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z) {
		boolean load = false;
		if (!world.isClientSide()) {
			load = (world.getBlockState(BlockPos.containing(x, y, z))).canSurvive(world, BlockPos.containing(x, y, z));
			if (world instanceof net.minecraft.server.level.ServerLevel level) {
				net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
				fr.vengelis.industrialfacilities.airlock.AirlockAutomationManager.tick(level, pos);
			}
		}
	}
}