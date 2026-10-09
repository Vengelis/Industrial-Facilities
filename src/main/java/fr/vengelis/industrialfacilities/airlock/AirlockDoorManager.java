
package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.AirlockControllerBlock;
import fr.vengelis.industrialfacilities.block.AirlockSealBlock;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class AirlockDoorManager {

    private static final String KEY_CLOSED =
        "IFAirlockClosed";

    private static final String KEY_PANELS =
        "IFAirlockPanels";

    private static final String KEY_FRAME = "IFAirlockFrame";

    private AirlockDoorManager() {
    }

    /** A missing loaded border block opens the passage without resetting controller settings. */
    public static boolean cleanupBrokenFrame(ServerLevel level, BlockPos origin) {
        if (!(level.getBlockEntity(origin) instanceof AirlockControllerBlockEntity controller)) return false;
        CompoundTag data=controller.getPersistentData();
        if (!data.getBoolean(KEY_CLOSED)) return false;
        long[] frame=data.getLongArray(KEY_FRAME);
        if (frame.length==0) {
            // Migration of doors closed before border positions were recorded.
            for (long encoded:data.getLongArray(KEY_PANELS)) {
                BlockPos panel=BlockPos.of(encoded);
                if (!nearController(origin,panel) || !level.hasChunkAt(panel) || !isSeal(level,panel)) continue;
                var border=AirlockFrameGeometry.border(data.getLongArray(KEY_PANELS),
                    level.getBlockState(panel).getValue(AirlockSealBlock.FACING).getAxis());
                if (!border.contains(origin)) return false;
                frame=border.stream().mapToLong(BlockPos::asLong).toArray();
                data.putLongArray(KEY_FRAME,frame);
                controller.setChanged();
                break;
            }
        }
        for (long encoded:frame) {
            BlockPos part=BlockPos.of(encoded);
            if (part.equals(origin) || !nearController(origin,part) || !level.hasChunkAt(part)) continue;
            // An unloaded chunk alone is not proof that the border was destroyed.
            if (!(level.getBlockState(part).getBlock() instanceof fr.vengelis.industrialfacilities.block.AirlockFrameBlock)) {
                removeRecordedPanels(level,controller);
                data.putBoolean(KEY_CLOSED,false);
                data.remove(KEY_PANELS);
                data.remove(KEY_FRAME);
                AirlockStatusManager.updateAppearance(level,origin);
                AirlockGuiSupport.sync(controller);
                return true;
            }
        }
        return false;
    }

    private static boolean nearController(BlockPos origin, BlockPos panel) {
        int limit=AirlockStructureManager.MAX_SIZE-1;
        return Math.abs((long)panel.getX()-origin.getX())<=limit
            && Math.abs((long)panel.getY()-origin.getY())<=limit
            && Math.abs((long)panel.getZ()-origin.getZ())<=limit;
    }

    private static void removeRecordedPanels(ServerLevel level, AirlockControllerBlockEntity controller) {
        var visited=new java.util.HashSet<Long>();
        BlockPos origin=controller.getBlockPos();
        for (long encoded:controller.getPersistentData().getLongArray(KEY_PANELS)) {
            BlockPos panel=BlockPos.of(encoded);
            if (!visited.add(encoded) || panel.equals(origin) || !nearController(origin,panel)
                    || level.isOutsideBuildHeight(panel)) continue;
            level.getChunkAt(panel);
            if (isSeal(level,panel)) level.setBlock(panel,Blocks.AIR.defaultBlockState(),3);
        }
    }

    /** Destruction cleanup, independent of frame validity and automatic settings. */
    public static void removeController(ServerLevel level, AirlockControllerBlockEntity controller) {
        BlockPos origin = controller.getBlockPos();
        CompoundTag data = controller.getPersistentData();
        long[] panels = data.getLongArray(KEY_PANELS);

        for (var player : level.players()) {
            if (player.containerMenu instanceof fr.vengelis.industrialfacilities.world.inventory.AirlockControllerGUIMenu menu
                    && menu.world == level && menu.matches(origin)) {
                player.closeContainer();
            }
        }

        removeRecordedPanels(level, controller);

        // No settings are copied into the dropped controller item. The block entity
        // itself is removed by the block's superclass immediately after this call.
        for (String key : new ArrayList<>(data.getAllKeys())) data.remove(key);
        controller.clearContent();
        controller.setChanged();
    }

    // Alterne entre ouverture et fermeture.
    public static String toggleDoor(
        ServerLevel level,
        BlockPos controllerPos
    ) {
        if (!(level.getBlockState(controllerPos)
                .getBlock() instanceof AirlockControllerBlock)) {
            return "Airlock : controleur introuvable.";
        }

        if (!(level.getBlockEntity(controllerPos)
                instanceof AirlockControllerBlockEntity controller)) {
            return "Airlock : Block Entity introuvable.";
        }

        CompoundTag data = controller.getPersistentData();

        if (data.getBoolean(KEY_CLOSED)) {
            return openDoor(level, controller, data);
        }

        return closeDoor(level, controller, data);
    }

    // Ferme la porte.
    private static String closeDoor(
        ServerLevel level,
        AirlockControllerBlockEntity controller,
        CompoundTag data
    ) {
        BlockPos controllerPos = controller.getBlockPos();

        AirlockStructureManager.AirlockStructure structure =
            AirlockStructureManager.detect(level, controllerPos);

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

        List<BlockPos> positions = new ArrayList<>();

        // Verifie toute la surface avant de placer
        // le moindre panneau.
        for (int a = 1; a < structure.width() - 1; a++) {
            for (int b = 1; b < structure.height() - 1; b++) {

                BlockPos pos = structure.getPosition(a, b);

                if (!level.hasChunkAt(pos)) {
                    return "Airlock : une partie du cadre est dechargee.";
                }

                if (!level.getBlockState(pos).isAir()) {
                    return "Airlock : passage obstrue.";
                }

                positions.add(pos.immutable());
            }
        }

        BlockState sealState =
            IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get()
                .defaultBlockState()
                .setValue(AirlockSealBlock.FACING, facing);

        List<BlockPos> placed = new ArrayList<>();

        // Generation des panneaux.
        for (BlockPos pos : positions) {
            if (!level.setBlock(pos, sealState, 3)) {

                // Annulation si un placement echoue.
                for (BlockPos rollbackPos : placed) {
                    if (isSeal(level, rollbackPos)) {
                        level.setBlock(
                            rollbackPos,
                            Blocks.AIR.defaultBlockState(),
                            3
                        );
                    }
                }

                return "Airlock : fermeture impossible.";
            }

            placed.add(pos);
        }

        // Enregistre les positions des panneaux.
        long[] storedPositions = new long[positions.size()];

        for (int i = 0; i < positions.size(); i++) {
            storedPositions[i] = positions.get(i).asLong();
        }

        data.putLongArray(KEY_PANELS, storedPositions);
        List<BlockPos> border = new ArrayList<>();
        for (int a=0;a<structure.width();a++) for (int b=0;b<structure.height();b++) {
            if (a==0 || b==0 || a==structure.width()-1 || b==structure.height()-1)
                border.add(structure.getPosition(a,b));
        }
        data.putLongArray(KEY_FRAME, border.stream().mapToLong(BlockPos::asLong).toArray());
        data.putBoolean(KEY_CLOSED, true);
        AirlockStatusManager.updateAppearance(level, controllerPos);
        AirlockGuiSupport.sync(controller);
        playDoorSound(level, controllerPos, "airlock_close");

        return "Airlock ferme ! "
            + positions.size() + " panneaux generes."
            + " Plan : " + structure.plane();
    }

    // Ouvre la porte.
    private static String openDoor(
        ServerLevel level,
        AirlockControllerBlockEntity controller,
        CompoundTag data
    ) {
        long[] storedPositions = data.getLongArray(KEY_PANELS);

        if (storedPositions.length == 0) {
            return "Airlock : aucune position de panneau sauvegardee.";
        }

        // Verifie tous les panneaux avant toute
        // modification du monde.
        for (long encodedPos : storedPositions) {
            BlockPos pos = BlockPos.of(encodedPos);

            if (!level.hasChunkAt(pos)) {
                return "Airlock : des panneaux sont dans un chunk decharge.";
            }

            BlockState state = level.getBlockState(pos);

            // Accepte l'air si un panneau a deja disparu,
            // mais jamais un bloc etranger.
            if (!state.isAir() && !isSeal(level, pos)) {
                return "Airlock : un panneau a ete remplace.";
            }
        }

        int removed = 0;

        for (long encodedPos : storedPositions) {
            BlockPos pos = BlockPos.of(encodedPos);

            if (isSeal(level, pos)) {
                if (!level.setBlock(
                    pos,
                    Blocks.AIR.defaultBlockState(),
                    3
                )) {
                    return "Airlock : ouverture partielle, reessayez.";
                }

                removed++;
            }
        }

        data.putBoolean(KEY_CLOSED, false);
        data.remove(KEY_PANELS);
        data.remove(KEY_FRAME);
        AirlockStatusManager.updateAppearance(level, controller.getBlockPos());
        AirlockGuiSupport.sync(controller);
        playDoorSound(level, controller.getBlockPos(), "airlock_open");

        return "Airlock ouvert ! "
            + removed + " panneaux retires.";
    }

    private static void playDoorSound(ServerLevel level, BlockPos pos, String name) {
        // Sound registrations and sounds.json are generated from MCreator's sound_elements.
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("industrial_facilities", name);
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound != null) {
            // Server broadcast to all nearby players, with no duplicate client playback.
            level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            fr.vengelis.industrialfacilities.IndustrialFacilitiesMod.LOGGER.warn("Missing Airlock sound event {}. Regenerate the MCreator workspace sound registrations.", id);
        }
    }

    private static boolean isSeal(
        ServerLevel level,
        BlockPos pos
    ) {
        return level.getBlockState(pos).is(
            IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get()
        );
    }
}

