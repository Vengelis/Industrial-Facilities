package fr.vengelis.industrialfacilities.panel;

import fr.vengelis.industrialfacilities.block.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Orthonormal screen axes shared by connection detection and the LED renderer. */
public record PanelBasis(Direction normal, Direction right, Direction up, double offset) {
    public static PanelBasis of(BlockState s) {
        var block=(LuminousPanelBlock)s.getBlock();
        Direction heading=s.getValue(LuminousPanelBlock.FACING);
        if (block instanceof LuminousPanelSurfaceBlock) {
            Direction normal=s.getValue(LuminousPanelSurfaceBlock.CEILING)?Direction.DOWN:Direction.UP;
            if (normal==Direction.DOWN) heading=heading.getOpposite();
            // right = up cross normal, ensuring right cross up = outward normal.
            int x=-heading.getStepZ()*normal.getStepY(), z=heading.getStepX()*normal.getStepY();
            Direction right=Direction.fromDelta(x,0,z);
            return new PanelBasis(normal,right,heading,0);
        }
        return new PanelBasis(heading,heading.getCounterClockWise(),Direction.UP,block.halfDepth()?0:.5);
    }
    public Direction[] neighbors() { return new Direction[]{up,up.getOpposite(),right,right.getOpposite()}; }
}
