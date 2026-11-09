package fr.vengelis.industrialfacilities.panel;

import fr.vengelis.industrialfacilities.block.LuminousPanelBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Bounded flood fill, always restricted to a single plane and orientation; never loads chunks. */
public record PanelStructure(BlockPos origin, Direction facing, Direction right, Direction up, int width, int height, List<BlockPos> blocks, boolean valid) {
    public static final int MAX_WIDTH = 7, MAX_HEIGHT = 3;
    public int column(BlockPos p) { return coordinate(p, right()) - coordinate(origin, right()); }
    public int row(BlockPos p) { return coordinate(p,up)-coordinate(origin,up); }
    public static Direction[] directions(Direction f) { return new Direction[]{Direction.UP, Direction.DOWN, f.getClockWise(), f.getCounterClockWise()}; }
    public static Direction[] directions(net.minecraft.world.level.block.state.BlockState s) { return PanelBasis.of(s).neighbors(); }
    public static int coordinate(BlockPos p, Direction d) { return p.getX()*d.getStepX() + p.getY()*d.getStepY() + p.getZ()*d.getStepZ(); }
    public static boolean matches(Level l, BlockPos p, Direction f) {
        return l.hasChunkAt(p) && l.getBlockState(p).getBlock() instanceof LuminousPanelBlock
            && l.getBlockState(p).getValue(LuminousPanelBlock.FACING) == f;
    }
    public static boolean matches(Level l, BlockPos p, net.minecraft.world.level.block.state.BlockState template) {
        if (!matches(l,p,template.getValue(LuminousPanelBlock.FACING)) || !l.getBlockState(p).is(template.getBlock())) return false;
        return !(template.getBlock() instanceof fr.vengelis.industrialfacilities.block.LuminousPanelSurfaceBlock)
            || l.getBlockState(p).getValue(fr.vengelis.industrialfacilities.block.LuminousPanelSurfaceBlock.CEILING)
                == template.getValue(fr.vengelis.industrialfacilities.block.LuminousPanelSurfaceBlock.CEILING);
    }
    public static PanelStructure find(Level l, BlockPos start) {
        var state = l.getBlockState(start);
        if (!(state.getBlock() instanceof LuminousPanelBlock))
            return new PanelStructure(start, Direction.NORTH, Direction.WEST, Direction.UP, 1, 1, List.of(), false);
        var basis=PanelBasis.of(state);
        Direction right=basis.right(), up=basis.up();
        Set<BlockPos> seen = new LinkedHashSet<>();
        var pending = new ArrayDeque<BlockPos>(); seen.add(start.immutable()); pending.add(start.immutable());
        int min = coordinate(start, right), max = min, bottom = coordinate(start,up), top = bottom;
        boolean overflow = false;
        while (!pending.isEmpty() && !overflow) {
            BlockPos p = pending.remove();
            min = Math.min(min, coordinate(p, right)); max = Math.max(max, coordinate(p, right));
            bottom = Math.min(bottom, coordinate(p,up)); top = Math.max(top, coordinate(p,up));
            for (Direction d : directions(state)) {
                BlockPos n = p.relative(d);
                if (matches(l, n, state) && !seen.contains(n)) {
                    if (seen.size() == MAX_WIDTH*MAX_HEIGHT) { overflow = true; break; }
                    seen.add(n); pending.add(n);
                }
            }
        }
        int w = max-min+1, h = top-bottom+1;
        BlockPos origin = start.relative(right, min-coordinate(start, right)).relative(up,bottom-coordinate(start,up));
        boolean valid = !overflow && w <= MAX_WIDTH && h <= MAX_HEIGHT && seen.size() == w*h;
        return new PanelStructure(origin, basis.normal(), right, up, w, h, List.copyOf(seen), valid);
    }
    public int edges(BlockPos p) {
        if (!valid) return 15;
        return (row(p) == height-1 ? 1 : 0) | (row(p) == 0 ? 2 : 0)
            | (column(p) == 0 ? 4 : 0) | (column(p) == width-1 ? 8 : 0);
    }
    public static void refresh(Level l, BlockPos p) {
        PanelStructure group = find(l, p);
        for (BlockPos member : group.blocks) {
            var s = l.getBlockState(member);
            int edge = group.edges(member);
            if (s.getValue(LuminousPanelBlock.EDGES) != edge)
                l.setBlock(member, s.setValue(LuminousPanelBlock.EDGES, edge), 3);
        }
    }
    public PanelBlockEntity controller(Level l) {
        return l.getBlockEntity(origin) instanceof PanelBlockEntity be ? be : null;
    }
}
