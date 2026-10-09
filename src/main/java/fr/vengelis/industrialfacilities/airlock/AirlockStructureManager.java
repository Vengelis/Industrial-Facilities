
package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.AirlockControllerBlock;
import fr.vengelis.industrialfacilities.block.AirlockFrameBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AirlockStructureManager {

    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 25;

    private AirlockStructureManager() {
    }

    // Résultat de la détection.
    // origin : premier coin du rectangle.
    // axisA et axisB : directions parcourues depuis ce coin.
    public record AirlockStructure(
        BlockPos origin,
        Direction axisA,
        Direction axisB,
        int width,
        int height,
        String plane
    ) {
        public BlockPos getPosition(int a, int b) {
            return origin.relative(axisA, a)
                         .relative(axisB, b);
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

        for (int a = 0; a < structure.width(); a++) {
            for (int b = 0; b < structure.height(); b++) {

                BlockPos pos = structure.getPosition(a, b);

                // Ne charge pas automatiquement
                // un chunk manquant.
                if (!level.hasChunkAt(pos)) {
                    return false;
                }

                boolean border =
                    a == 0 ||
                    b == 0 ||
                    a == structure.width() - 1 ||
                    b == structure.height() - 1;

                if (border) {
                    if (pos.equals(controllerPos)) {
                        if (!isController(level, pos)) {
                            return false;
                        }
                        controllerCount++;
                    } else if (!isFrame(level, pos)) {
                        return false;
                    }
                } else {
                    // Pour ce premier test, l'intérieur
                    // doit être complètement vide.
                    if (!level.getBlockState(pos).isAir() && !(allowSeals && level.getBlockState(pos).is(fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks.AIRLOCK_SEAL.get()))) {
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


