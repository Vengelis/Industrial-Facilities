
package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AirlockAutomationManager {

    private AirlockAutomationManager() {}

    public static void tick(
        ServerLevel level,
        BlockPos controllerPos
    ) {
        // The controller already schedules this call every 10 ticks.
        if (!level.getBlockState(controllerPos).is(
                IndustrialFacilitiesModBlocks.AIRLOCK_CONTROLLER.get())) {
            return;
        }

        if (!(level.getBlockEntity(controllerPos)
                instanceof AirlockControllerBlockEntity controller)) {
            return;
        }

        CompoundTag data = controller.getPersistentData();

        boolean redstoneEnabled =
            data.getBoolean("IFRedstoneEnabled");

        boolean playerDetection =
            !data.contains("IFPlayerDetection")
            || data.getBoolean("IFPlayerDetection");

        int range = data.contains("IFDetectionRange")
            ? data.getInt("IFDetectionRange") : 5;

        range = Math.max(1, Math.min(10, range));

        boolean redstoneRequest = redstoneEnabled
            && level.hasNeighborSignal(controllerPos);

        boolean playerRequest = false;

        if (playerDetection) {
            Vec3 center = Vec3.atCenterOf(controllerPos);

            AABB searchBox =
                new AABB(controllerPos).inflate(range);

            for (ServerPlayer player :
                    level.getEntitiesOfClass(
                        ServerPlayer.class, searchBox
                    )) {

                if (player.isSpectator()) {
                    continue;
                }

                if (player.distanceToSqr(center)
                        > range * range) {
                    continue;
                }

                if (AirlockPlayerAccessManager.isAllowed(
                        data, player)) {
                    playerRequest = true;
                    break;
                }
            }
        }

        // Both automatic inputs disabled: preserve the manual door state.
        if (!redstoneEnabled && !playerDetection) return;

        boolean shouldOpen = redstoneRequest || playerRequest;
        boolean closed = data.getBoolean("IFAirlockClosed");

        // La porte est fermee et doit s'ouvrir.
        if (shouldOpen && closed) {
            AirlockDoorManager.toggleDoor(
                level, controllerPos
            );
            return;
        }

        // La porte est ouverte et doit se fermer.
        if (!shouldOpen && !closed) {
            AirlockStructureManager.AirlockStructure structure =
                AirlockStructureManager.detect(
                    level, controllerPos
                );

            if (structure == null) {
                return;
            }

            if (!isClear(level, structure)) {
                return;
            }

            AirlockDoorManager.toggleDoor(
                level, controllerPos
            );
        }
    }

    // Verifie que la fermeture ne traverse
    // aucune entite vivante ou autre entite presente.
    private static boolean isClear(
        ServerLevel level,
        AirlockStructureManager.AirlockStructure structure
    ) {
        for (int a = 1; a < structure.width() - 1; a++) {
            for (int b = 1; b < structure.height() - 1; b++) {

                BlockPos pos = structure.getPosition(a, b);
                AABB panel;

                switch (structure.plane()) {
                    case "XY" -> panel = new AABB(
                        pos.getX(), pos.getY(),
                        pos.getZ() + 7.0 / 16.0,
                        pos.getX() + 1, pos.getY() + 1,
                        pos.getZ() + 9.0 / 16.0
                    );

                    case "YZ" -> panel = new AABB(
                        pos.getX() + 7.0 / 16.0,
                        pos.getY(), pos.getZ(),
                        pos.getX() + 9.0 / 16.0,
                        pos.getY() + 1, pos.getZ() + 1
                    );

                    case "XZ" -> panel = new AABB(
                        pos.getX(),
                        pos.getY() + 7.0 / 16.0,
                        pos.getZ(),
                        pos.getX() + 1,
                        pos.getY() + 9.0 / 16.0,
                        pos.getZ() + 1
                    );

                    default -> {
                        return false;
                    }
                }

                if (!level.getEntitiesOfClass(
                        Entity.class, panel,
                        entity -> entity.isAlive()
                    ).isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }
}

