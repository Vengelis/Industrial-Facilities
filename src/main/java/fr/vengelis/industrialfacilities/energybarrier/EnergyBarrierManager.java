package fr.vengelis.industrialfacilities.energybarrier;

import fr.vengelis.industrialfacilities.block.EnergyBarrierBaseBlock;
import fr.vengelis.industrialfacilities.block.EnergyBarrierFieldBlock;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Reconstruit le cadre à partir des blocs chargés, sans conserver d'état global par monde. */
public final class EnergyBarrierManager {
    public static final int MAX_WIDTH = 5;
    public static final int MAX_HEIGHT = 4;
    private static final Direction[] SCAN_DIRECTIONS = {Direction.EAST, Direction.SOUTH};

    private EnergyBarrierManager() {}

    // L'ancrage est la première base intérieure. La ligne 0 désigne le sol du cadre.
    public record Structure(BlockPos anchor, Direction along, int width, int height) {
        public BlockPos at(int column, int row) {
            return anchor.relative(along, column).above(row);
        }

        public Direction.Axis axis() {
            return along.getAxis();
        }

        public BlockState field(int column, int row) {
            return IndustrialFacilitiesModBlocks.ENERGY_BARRIER_FIELD.get().defaultBlockState()
                .setValue(EnergyBarrierFieldBlock.AXIS, axis())
                .setValue(EnergyBarrierFieldBlock.COLUMN, column)
                .setValue(EnergyBarrierFieldBlock.ROW, row);
        }
    }

    public static BlockPos owner(BlockPos pos, BlockState state) {
        Direction along = fieldDirection(state);
        return pos.below(state.getValue(EnergyBarrierFieldBlock.ROW))
            .relative(along, -state.getValue(EnergyBarrierFieldBlock.COLUMN));
    }

    private static Direction fieldDirection(BlockState state) {
        return state.getValue(EnergyBarrierFieldBlock.AXIS) == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }

    private static boolean isBase(BlockState state) {
        return state.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_BASE.get());
    }

    private static boolean isPillar(BlockState state) {
        return state.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_PILLAR.get());
    }

    private static boolean belongsToField(BlockPos pos, BlockState state, BlockPos anchor, Direction.Axis axis) {
        return state.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_FIELD.get())
            && owner(pos, state).equals(anchor)
            && state.getValue(EnergyBarrierFieldBlock.AXIS) == axis;
    }

    private static boolean isSearchAreaLoaded(ServerLevel level, BlockPos pos, Direction along) {
        // Vérifier aussi la marge nécessaire pour reconnaître un cadre trop large ou trop haut.
        for (int column = -MAX_WIDTH - 1; column <= MAX_WIDTH + 1; column++) {
            for (int row = 0; row <= MAX_HEIGHT + 1; row++) {
                if (!level.hasChunkAt(pos.relative(along, column).above(row))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Structure detect(ServerLevel level, BlockPos pos, Direction along) {
        if (!isSearchAreaLoaded(level, pos, along) || !isBase(level.getBlockState(pos))) {
            return null;
        }

        Direction backward = along.getOpposite();
        BlockPos anchor = pos;
        int basesBehind = 1;
        while (isBase(level.getBlockState(anchor.relative(backward)))) {
            if (++basesBehind > MAX_WIDTH) {
                return null;
            }
            anchor = anchor.relative(backward);
        }

        BlockPos firstPillar = anchor.relative(backward);
        if (!isPillar(level.getBlockState(firstPillar))) {
            return null;
        }
        int width = 0;
        while (isBase(level.getBlockState(anchor.relative(along, width)))) {
            if (++width > MAX_WIDTH) {
                return null;
            }
        }

        BlockPos lastPillar = anchor.relative(along, width);
        if (!isPillar(level.getBlockState(lastPillar))) {
            return null;
        }
        int firstHeight = pillarHeight(level, firstPillar);
        int lastHeight = pillarHeight(level, lastPillar);
        if (firstHeight < 1 || firstHeight > MAX_HEIGHT || firstHeight != lastHeight
                || anchor.getY() + firstHeight >= level.getMaxBuildHeight()) {
            return null;
        }
        return new Structure(anchor.immutable(), along, width, firstHeight);
    }

    private static int pillarHeight(ServerLevel level, BlockPos foot) {
        int height = 0;
        while (height <= MAX_HEIGHT && isPillar(level.getBlockState(foot.above(height + 1)))) {
            height++;
        }
        return height;
    }

    public static boolean powered(ServerLevel level, Structure structure) {
        for (int column = 0; column < structure.width; column++) {
            if (level.hasNeighborSignal(structure.at(column, 0))) {
                return true;
            }
        }
        for (int row = 0; row <= structure.height; row++) {
            if (level.hasNeighborSignal(structure.at(-1, row))
                    || level.hasNeighborSignal(structure.at(structure.width, row))) {
                return true;
            }
        }
        return false;
    }

    public static boolean clear(ServerLevel level, Structure structure) {
        for (int column = 0; column < structure.width; column++) {
            for (int row = 1; row <= structure.height; row++) {
                BlockPos pos = structure.at(column, row);
                BlockState state = level.getBlockState(pos);
                // Un champ déjà installé peut rester en place si son ancrage et son axe correspondent.
                if (!state.isAir() && !belongsToField(pos, state, structure.anchor, structure.axis())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clearOwned(ServerLevel level, BlockPos anchor, Direction along, Structure keep) {
        // Parcourir les dimensions maximales pour retirer aussi les cellules hors d'un cadre rétréci.
        for (int column = 0; column < MAX_WIDTH; column++) {
            for (int row = 1; row <= MAX_HEIGHT; row++) {
                BlockPos pos = anchor.relative(along, column).above(row);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (belongsToField(pos, state, anchor, along.getAxis())
                        && (keep == null || column >= keep.width || row > keep.height)) {
                    level.removeBlock(pos, false);
                }
            }
        }
    }

    private static void setActive(ServerLevel level, Structure structure, boolean active) {
        for (int column = 0; column < structure.width; column++) {
            BlockPos pos = structure.at(column, 0);
            BlockState before = level.getBlockState(pos);
            BlockState after = before.setValue(EnergyBarrierBaseBlock.ACTIVE, active)
                .setValue(EnergyBarrierBaseBlock.AXIS, structure.axis());
            after = EnergyBarrierBaseBlock.connected(after, level, pos);
            if (before != after) {
                level.setBlock(pos, after, 3);
            }
        }
    }

    public static void update(ServerLevel level, BlockPos pos) {
        boolean foundStructure = false;
        for (Direction along : SCAN_DIRECTIONS) {
            if (!isSearchAreaLoaded(level, pos, along)) {
                continue;
            }
            Structure structure = detect(level, pos, along);
            if (structure == null) {
                clearOwned(level, pos, along, null);
                continue;
            }

            foundStructure = true;
            // Seule la base d'ancrage met le cadre entier à jour.
            if (!structure.anchor.equals(pos)) {
                continue;
            }
            boolean active = powered(level, structure) && clear(level, structure);
            clearOwned(level, pos, along, active ? structure : null);
            setActive(level, structure, active);
            if (active) {
                placeField(level, structure);
            }
        }

        if (!foundStructure) {
            BlockState state = level.getBlockState(pos);
            if (isBase(state) && state.getValue(EnergyBarrierBaseBlock.ACTIVE)) {
                level.setBlock(pos, state.setValue(EnergyBarrierBaseBlock.ACTIVE, false), 3);
            }
        }
    }

    private static void placeField(ServerLevel level, Structure structure) {
        for (int column = 0; column < structure.width; column++) {
            for (int row = 1; row <= structure.height; row++) {
                BlockPos pos = structure.at(column, row);
                BlockState desired = structure.field(column, row);
                if (level.getBlockState(pos) != desired) {
                    level.setBlock(pos, desired, 3);
                }
            }
        }
    }

    /** Retire une cellule orpheline même si son ancrage a disparu avant le rechargement du monde. */
    public static void checkField(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos anchor = owner(pos, state);
        Direction along = fieldDirection(state);
        if (!isSearchAreaLoaded(level, anchor, along)) {
            return;
        }
        Structure structure = detect(level, anchor, along);
        int column = state.getValue(EnergyBarrierFieldBlock.COLUMN);
        int row = state.getValue(EnergyBarrierFieldBlock.ROW);
        if (structure == null || !structure.anchor.equals(anchor) || column >= structure.width
                || row > structure.height || !powered(level, structure) || !clear(level, structure)) {
            level.removeBlock(pos, false);
        }
    }
}
