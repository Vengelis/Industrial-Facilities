package fr.vengelis.industrialfacilities.panel;

import fr.vengelis.industrialfacilities.block.LuminousPanelBlock;
import fr.vengelis.industrialfacilities.block.LuminousPanelSurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Groupe de panneaux contigus d'un même type et dans un même plan. */
public record PanelStructure(BlockPos origin, Direction facing, Direction right, Direction up,
                             int width, int height, List<BlockPos> blocks, boolean valid) {
    public static final int MAX_WIDTH = 7;
    public static final int MAX_HEIGHT = 3;

    // Ces bits sont lus par les modèles de bloc dans les ressources.
    private static final int TOP_EDGE = 1;
    private static final int BOTTOM_EDGE = 2;
    private static final int LEFT_EDGE = 4;
    private static final int RIGHT_EDGE = 8;

    public int column(BlockPos pos) {
        return coordinate(pos, right) - coordinate(origin, right);
    }

    public int row(BlockPos pos) {
        return coordinate(pos, up) - coordinate(origin, up);
    }

    public static Direction[] directions(Direction facing) {
        return new Direction[]{Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};
    }

    public static Direction[] directions(BlockState state) {
        return PanelBasis.of(state).neighbors();
    }

    /** Projette une position du monde sur un axe du panneau, y compris au sol ou au plafond. */
    public static int coordinate(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getY() * axis.getStepY() + pos.getZ() * axis.getStepZ();
    }

    public static boolean matches(Level level, BlockPos pos, Direction facing) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof LuminousPanelBlock
            && level.getBlockState(pos).getValue(LuminousPanelBlock.FACING) == facing;
    }

    public static boolean matches(Level level, BlockPos pos, BlockState template) {
        if (!matches(level, pos, template.getValue(LuminousPanelBlock.FACING))) {
            return false;
        }
        BlockState candidate = level.getBlockState(pos);
        if (!candidate.is(template.getBlock())) {
            return false;
        }
        return !(template.getBlock() instanceof LuminousPanelSurfaceBlock)
            || candidate.getValue(LuminousPanelSurfaceBlock.CEILING)
                == template.getValue(LuminousPanelSurfaceBlock.CEILING);
    }

    public static PanelStructure find(Level level, BlockPos start) {
        BlockState state = level.getBlockState(start);
        if (!(state.getBlock() instanceof LuminousPanelBlock)) {
            return new PanelStructure(start, Direction.NORTH, Direction.WEST, Direction.UP, 1, 1, List.of(), false);
        }

        PanelBasis basis = PanelBasis.of(state);
        Direction right = basis.right();
        Direction up = basis.up();
        BlockPos first = start.immutable();
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        seen.add(first);
        pending.add(first);

        int startColumn = coordinate(start, right);
        int startRow = coordinate(start, up);
        int left = startColumn;
        int rightmost = startColumn;
        int bottom = startRow;
        int top = startRow;
        boolean overflow = false;

        // Parcours en largeur limité à la surface maximale. matches ignore les chunks déchargés.
        Direction[] neighbors = basis.neighbors();
        while (!pending.isEmpty() && !overflow) {
            BlockPos pos = pending.remove();
            int column = coordinate(pos, right);
            int row = coordinate(pos, up);
            left = Math.min(left, column);
            rightmost = Math.max(rightmost, column);
            bottom = Math.min(bottom, row);
            top = Math.max(top, row);
            for (Direction direction : neighbors) {
                BlockPos neighbor = pos.relative(direction);
                if (!matches(level, neighbor, state) || seen.contains(neighbor)) {
                    continue;
                }
                if (seen.size() == MAX_WIDTH * MAX_HEIGHT) {
                    overflow = true;
                    break;
                }
                seen.add(neighbor);
                pending.add(neighbor);
            }
        }

        int width = rightmost - left + 1;
        int height = top - bottom + 1;
        BlockPos origin = start.relative(right, left - startColumn).relative(up, bottom - startRow);
        // Un rectangle doit contenir autant de panneaux que de cases. Son coin sert de contrôleur.
        boolean valid = !overflow && width <= MAX_WIDTH && height <= MAX_HEIGHT && seen.size() == width * height;
        return new PanelStructure(origin, basis.normal(), right, up, width, height, List.copyOf(seen), valid);
    }

    public int edges(BlockPos pos) {
        if (!valid) {
            return TOP_EDGE | BOTTOM_EDGE | LEFT_EDGE | RIGHT_EDGE;
        }
        int row = row(pos);
        int column = column(pos);
        return (row == height - 1 ? TOP_EDGE : 0) | (row == 0 ? BOTTOM_EDGE : 0)
            | (column == 0 ? LEFT_EDGE : 0) | (column == width - 1 ? RIGHT_EDGE : 0);
    }

    public static void refresh(Level level, BlockPos pos) {
        PanelStructure group = find(level, pos);
        for (BlockPos member : group.blocks) {
            BlockState state = level.getBlockState(member);
            int edgeMask = group.edges(member);
            if (state.getValue(LuminousPanelBlock.EDGES) != edgeMask) {
                level.setBlock(member, state.setValue(LuminousPanelBlock.EDGES, edgeMask), 3);
            }
        }
    }

    public PanelBlockEntity controller(Level level) {
        return level.getBlockEntity(origin) instanceof PanelBlockEntity entity ? entity : null;
    }
}
