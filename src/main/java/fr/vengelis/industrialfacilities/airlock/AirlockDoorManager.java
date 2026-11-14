package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.block.AirlockControllerBlock;
import fr.vengelis.industrialfacilities.block.AirlockFrameBlock;
import fr.vengelis.industrialfacilities.block.AirlockSealBlock;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import fr.vengelis.industrialfacilities.world.inventory.AirlockControllerGUIMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Gère les panneaux du sas et les positions sauvegardées par son contrôleur. */
public final class AirlockDoorManager {
    private static final String KEY_CLOSED = "IFAirlockClosed";
    private static final String KEY_PANELS = "IFAirlockPanels";
    private static final String KEY_FRAME = "IFAirlockFrame";

    private AirlockDoorManager() {}

    /** Un cadre cassé ouvre le passage. Les réglages du contrôleur restent disponibles. */
    public static boolean cleanupBrokenFrame(ServerLevel level, BlockPos origin) {
        if (!(level.getBlockEntity(origin) instanceof AirlockControllerBlockEntity controller)) {
            return false;
        }
        CompoundTag data = controller.getPersistentData();
        if (!data.getBoolean(KEY_CLOSED)) {
            return false;
        }

        long[] frame = data.getLongArray(KEY_FRAME);
        if (frame.length == 0) {
            frame = recoverLegacyFrame(level, controller);
        }
        for (long encoded : frame) {
            BlockPos part = BlockPos.of(encoded);
            // Un chunk déchargé ne permet pas de savoir si le cadre est encore présent.
            if (part.equals(origin) || !nearController(origin, part) || !level.hasChunkAt(part)) {
                continue;
            }
            if (!(level.getBlockState(part).getBlock() instanceof AirlockFrameBlock)) {
                removeRecordedPanels(level, controller);
                markOpen(level, controller, data);
                return true;
            }
        }
        return false;
    }

    private static long[] recoverLegacyFrame(ServerLevel level, AirlockControllerBlockEntity controller) {
        // Les anciennes sauvegardes contiennent les panneaux, mais aucun contour.
        BlockPos origin = controller.getBlockPos();
        CompoundTag data = controller.getPersistentData();
        long[] panels = data.getLongArray(KEY_PANELS);
        for (long encoded : panels) {
            BlockPos panel = BlockPos.of(encoded);
            if (!nearController(origin, panel) || !level.hasChunkAt(panel) || !isSeal(level, panel)) {
                continue;
            }
            Direction.Axis normal = level.getBlockState(panel).getValue(AirlockSealBlock.FACING).getAxis();
            List<BlockPos> border = AirlockFrameGeometry.border(panels, normal);
            if (!border.contains(origin)) {
                break;
            }
            long[] frame = border.stream().mapToLong(BlockPos::asLong).toArray();
            data.putLongArray(KEY_FRAME, frame);
            controller.setChanged();
            return frame;
        }
        return new long[0];
    }

    private static boolean nearController(BlockPos origin, BlockPos pos) {
        int limit = AirlockStructureManager.MAX_SIZE - 1;
        return Math.abs((long) pos.getX() - origin.getX()) <= limit
            && Math.abs((long) pos.getY() - origin.getY()) <= limit
            && Math.abs((long) pos.getZ() - origin.getZ()) <= limit;
    }

    private static void removeRecordedPanels(ServerLevel level, AirlockControllerBlockEntity controller) {
        Set<Long> visited = new HashSet<>();
        BlockPos origin = controller.getBlockPos();
        for (long encoded : controller.getPersistentData().getLongArray(KEY_PANELS)) {
            BlockPos panel = BlockPos.of(encoded);
            if (!visited.add(encoded) || panel.equals(origin) || !nearController(origin, panel)
                    || level.isOutsideBuildHeight(panel)) {
                continue;
            }
            // Le nettoyage définitif charge les positions connues pour éviter de laisser des panneaux orphelins.
            level.getChunkAt(panel);
            if (isSeal(level, panel)) {
                level.setBlock(panel, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** La destruction du contrôleur nettoie le sas, quel que soit l'état actuel du cadre. */
    public static void removeController(ServerLevel level, AirlockControllerBlockEntity controller) {
        BlockPos origin = controller.getBlockPos();
        for (var player : level.players()) {
            if (player.containerMenu instanceof AirlockControllerGUIMenu menu
                    && menu.world == level && menu.matches(origin)) {
                player.closeContainer();
            }
        }

        removeRecordedPanels(level, controller);

        // Le contrôleur récupéré au sol repart sans les données du bloc détruit.
        CompoundTag data = controller.getPersistentData();
        for (String key : new ArrayList<>(data.getAllKeys())) {
            data.remove(key);
        }
        controller.clearContent();
        controller.setChanged();
    }

    public static String toggleDoor(ServerLevel level, BlockPos controllerPos) {
        if (!(level.getBlockState(controllerPos).getBlock() instanceof AirlockControllerBlock)) {
            return "Airlock : controleur introuvable.";
        }
        if (!(level.getBlockEntity(controllerPos) instanceof AirlockControllerBlockEntity controller)) {
            return "Airlock : Block Entity introuvable.";
        }

        CompoundTag data = controller.getPersistentData();
        return data.getBoolean(KEY_CLOSED) ? openDoor(level, controller, data) : closeDoor(level, controller, data);
    }

    private static String closeDoor(ServerLevel level, AirlockControllerBlockEntity controller, CompoundTag data) {
        BlockPos controllerPos = controller.getBlockPos();
        AirlockStructureManager.AirlockStructure structure = AirlockStructureManager.detect(level, controllerPos);
        if (structure == null) {
            return "Airlock : structure invalide ou interieur occupe.";
        }

        Direction facing = switch (structure.plane()) {
            case "XY" -> Direction.NORTH;
            case "YZ" -> Direction.EAST;
            case "XZ" -> Direction.UP;
            default -> null;
        };
        if (facing == null) {
            return "Airlock : orientation inconnue.";
        }

        // Vérifier tout le passage avant de placer le premier panneau.
        List<BlockPos> positions = new ArrayList<>();
        for (int column = 1; column < structure.width() - 1; column++) {
            for (int row = 1; row < structure.height() - 1; row++) {
                BlockPos pos = structure.getPosition(column, row);
                if (!level.hasChunkAt(pos)) {
                    return "Airlock : une partie du cadre est dechargee.";
                }
                if (!level.getBlockState(pos).isAir()) {
                    return "Airlock : passage obstrue.";
                }
                positions.add(pos.immutable());
            }
        }

        BlockState seal = IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get().defaultBlockState()
            .setValue(AirlockSealBlock.FACING, facing);
        if (!placePanels(level, positions, seal)) {
            return "Airlock : fermeture impossible.";
        }

        data.putLongArray(KEY_PANELS, positions.stream().mapToLong(BlockPos::asLong).toArray());
        data.putLongArray(KEY_FRAME, framePositions(structure));
        data.putBoolean(KEY_CLOSED, true);
        AirlockStatusManager.updateAppearance(level, controllerPos);
        AirlockGuiSupport.sync(controller);
        playDoorSound(level, controllerPos, "airlock_close");

        return "Airlock ferme ! " + positions.size() + " panneaux generes." + " Plan : " + structure.plane();
    }

    private static boolean placePanels(ServerLevel level, List<BlockPos> positions, BlockState seal) {
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!level.setBlock(pos, seal, 3)) {
                // Annuler les placements de cette fermeture. La sauvegarde n'a pas encore été modifiée.
                for (BlockPos rollbackPos : placed) {
                    if (isSeal(level, rollbackPos)) {
                        level.setBlock(rollbackPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                return false;
            }
            placed.add(pos);
        }
        return true;
    }

    private static long[] framePositions(AirlockStructureManager.AirlockStructure structure) {
        List<BlockPos> border = new ArrayList<>();
        for (int column = 0; column < structure.width(); column++) {
            for (int row = 0; row < structure.height(); row++) {
                if (structure.isBorder(column, row)) {
                    border.add(structure.getPosition(column, row));
                }
            }
        }
        return border.stream().mapToLong(BlockPos::asLong).toArray();
    }

    private static String openDoor(ServerLevel level, AirlockControllerBlockEntity controller, CompoundTag data) {
        long[] panels = data.getLongArray(KEY_PANELS);
        if (panels.length == 0) {
            return "Airlock : aucune position de panneau sauvegardee.";
        }

        // Accepter un panneau déjà retiré permet de reprendre une ouverture partielle.
        for (long encoded : panels) {
            BlockPos pos = BlockPos.of(encoded);
            if (!level.hasChunkAt(pos)) {
                return "Airlock : des panneaux sont dans un chunk decharge.";
            }
            if (!level.getBlockState(pos).isAir() && !isSeal(level, pos)) {
                return "Airlock : un panneau a ete remplace.";
            }
        }

        int removed = 0;
        for (long encoded : panels) {
            BlockPos pos = BlockPos.of(encoded);
            if (isSeal(level, pos)) {
                if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                    return "Airlock : ouverture partielle, reessayez.";
                }
                removed++;
            }
        }

        markOpen(level, controller, data);
        playDoorSound(level, controller.getBlockPos(), "airlock_open");
        return "Airlock ouvert ! " + removed + " panneaux retires.";
    }

    private static void markOpen(ServerLevel level, AirlockControllerBlockEntity controller, CompoundTag data) {
        data.putBoolean(KEY_CLOSED, false);
        data.remove(KEY_PANELS);
        data.remove(KEY_FRAME);
        AirlockStatusManager.updateAppearance(level, controller.getBlockPos());
        AirlockGuiSupport.sync(controller);
    }

    private static void playDoorSound(ServerLevel level, BlockPos pos, String name) {
        // MCreator génère le registre des sons. Le serveur diffuse le son aux joueurs à proximité.
        var id = ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, name);
        var sound = BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            IndustrialFacilitiesMod.LOGGER.warn(
                "Missing Airlock sound event {}. Regenerate the MCreator workspace sound registrations.", id);
        }
    }

    private static boolean isSeal(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get());
    }
}
