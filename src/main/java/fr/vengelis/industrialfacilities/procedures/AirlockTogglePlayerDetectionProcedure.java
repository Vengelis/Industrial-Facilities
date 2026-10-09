package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

public class AirlockTogglePlayerDetectionProcedure {
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
					net.minecraft.nbt.CompoundTag data = controller.getPersistentData();
					// Par defaut, la detection est activee.
					boolean current = !data.contains("IFPlayerDetection") || data.getBoolean("IFPlayerDetection");
					// Inverse la valeur.
					boolean enabled = !current;
					data.putBoolean("IFPlayerDetection", enabled);
					// Sauvegarde et synchronisation.
					controller.setChanged();
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
					level.sendBlockUpdated(pos, state, state, 3);
					player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Airlock - Player Detection : " + (enabled ? "Enabled" : "Disabled")));
				}
			}
		}
	}
}