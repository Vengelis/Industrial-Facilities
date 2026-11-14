
package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.AirlockControllerBlock;
import fr.vengelis.industrialfacilities.block.AirlockFrameBlock;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AirlockStructureManager {

    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 25;

    private AirlockStructureManager() {
    }

    /** Rectangle repéré par un coin et deux directions locales dans le plan du sas. */
    public record AirlockStructure(
        BlockPos origin,
        Direction axisA,
        Direction axisB,
        int width,
        int height,
        String plane
    ) {
        public BlockPos getPosition(int column, int row) {
            return origin.relative(axisA, column).relative(axisB, row);
        }

        boolean isBorder(int column, int row) {
            return column == 0 || row == 0 || column == width - 1 || row == height - 1;
        }

        public int getInteriorWidth() {
            return width - 2;
        }

        public int getInteriorHeight() {
            return height - 2;
        }
    }

    // Recherche une structure autour du contrôleur.
    public static AirlockStructure detect(
        Level level,
        BlockPos controllerPos
    ) {
        return detect(level, controllerPos, false);
    }

    public static AirlockStructure detectForStatus(Level level, BlockPos controllerPos) {
        return detect(level, controllerPos, true);
    }

    private static AirlockStructure detect(Level level, BlockPos controllerPos, boolean allowSeals) {
        if (!isController(level, controllerPos)) {
            return null;
        }

        // Porte verticale dans le plan XY
        AirlockStructure result = searchPlane(
            level, controllerPos,
            Direction.EAST, Direction.UP, "XY", allowSeals
        );

        if (result != null) return result;

        // Porte verticale dans le plan YZ
        result = searchPlane(
            level, controllerPos,
            Direction.SOUTH, Direction.UP, "YZ", allowSeals
        );

        if (result != null) return result;

        // Porte horizontale dans le plan XZ
        return searchPlane(
            level, controllerPos,
            Direction.EAST, Direction.SOUTH, "XZ", allowSeals
        );
    }

    private static AirlockStructure searchPlane(
        Level level,
        BlockPos controllerPos,
        Direction axisA,
        Direction axisB,
        String plane, boolean allowSeals
    ) {
        // Le contrôleur peut être sur n'importe
        // lequel des quatre côtés.
        Direction[] primaryAxes = {axisA, axisB};

        for (Direction primary : primaryAxes) {
            Direction secondary =
                primary == axisA ? axisB : axisA;

            int backward = countFrames(
                level,
                controllerPos,
                primary.getOpposite()
            );

            int forward = countFrames(
                level,
                controllerPos,
                primary
            );

            int width = backward + forward + 1;

            if (width < MIN_SIZE || width > MAX_SIZE) {
                continue;
            }

            BlockPos start = controllerPos.relative(
                primary.getOpposite(), backward
            );

            // Cherche le côté opposé du rectangle
            // dans les deux directions possibles.
            Direction[] sides = {
                secondary,
                secondary.getOpposite()
            };

            for (Direction side : sides) {
                for (int height = MIN_SIZE;
                     height <= MAX_SIZE;
                     height++) {

                    AirlockStructure candidate =
                        new AirlockStructure(
                            start,
                            primary,
                            side,
                            width,
                            height,
                            plane
                        );

                    if (validate(
                        level, controllerPos, candidate, allowSeals
                    )) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    // Compte les blocs du cadre dans une direction.
    private static int countFrames(
        Level level,
        BlockPos origin,
        Direction direction
    ) {
        int count = 0;

        for (int i = 1; i < MAX_SIZE; i++) {
            BlockPos pos = origin.relative(direction, i);

            if (!level.hasChunkAt(pos) ||
                !isFrame(level, pos)) {
                break;
            }

            count++;
        }

        return count;
    }

    // Vérifie le contour et l'intérieur du rectangle.
    private static boolean validate(
        Level level,
        BlockPos controllerPos,
        AirlockStructure structure, boolean allowSeals
    ) {
        int controllerCount = 0;

        for (int column = 0; column < structure.width(); column++) {
            for (int row = 0; row < structure.height(); row++) {

                BlockPos pos = structure.getPosition(column, row);

                // Ne charge pas automatiquement
                // un chunk manquant.
                if (!level.hasChunkAt(pos)) {
                    return false;
                }

                if (structure.isBorder(column, row)) {
                    if (pos.equals(controllerPos)) {
                        if (!isController(level, pos)) {
                            return false;
                        }
                        controllerCount++;
                    } else if (!isFrame(level, pos)) {
                        return false;
                    }
                } else {
                    // L'affichage du statut reconnaît également un sas fermé.
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !(allowSeals && state.is(IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get()))) {
                        return false;
                    }
                }
            }
        }

        return controllerCount == 1;
    }

    private static boolean isFrame(
        Level level,
        BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AirlockFrameBlock;
    }

    private static boolean isController(
        Level level,
        BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AirlockControllerBlock;
    }
}
