package fr.vengelis.industrialfacilities.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Bounded local groups of at most three tiles. No world scan, block entity or client-only state. */
public class RoadTextMarkingBlock extends RoadMarkingBlock {
    public static final IntegerProperty WIDTH = IntegerProperty.create("width", 1, 3);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    private record Group(BlockPos start, int size, Direction facing) {
        Direction right() { return facing.getClockWise(); }
        BlockPos at(int index) { return start.relative(right(), index); }
    }

    public RoadTextMarkingBlock() {
        super();
        registerDefaultState(defaultBlockState().setValue(WIDTH, 1).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WIDTH, PART);
    }
    private boolean compatible(ServerLevel world, BlockPos pos, Direction facing) {
        if (!world.hasChunkAt(pos)) return false;
        BlockState state = world.getBlockState(pos);
        return state.is(this) && state.getValue(FACING) == facing;
    }
    private void schedule(Level world, BlockPos pos) {
        if (!world.isClientSide && world.hasChunkAt(pos) && world.getBlockState(pos).is(this))
            world.scheduleTick(pos, this, 1);
    }
    private void wake(Level world, BlockPos pos, Direction facing) {
        schedule(world, pos);
        schedule(world, pos.relative(facing.getClockWise()));
        schedule(world, pos.relative(facing.getCounterClockWise()));
    }
    @Override public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, world, pos, oldState, moving);
        if (!world.isClientSide) wake(world, pos, state.getValue(FACING));
    }
    @Override public void onRemove(BlockState state, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this)) wake(world, pos, state.getValue(FACING));
        super.onRemove(state, world, pos, next, moving);
    }
    private void write(ServerLevel world, Group group) {
        boolean changed = false;
        for (int part = 0; part < group.size(); part++) {
            BlockPos pos = group.at(part);
            BlockState before = world.getBlockState(pos);
            BlockState after = before.setValue(WIDTH, group.size()).setValue(PART, part);
            if (before != after) {
                // Send each segment to clients without exposing an incomplete group to neighbor updates.
                world.setBlock(pos, after, Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        if (changed) {
            schedule(world, group.at(-1));
            schedule(world, group.at(group.size()));
        }
    }
    private boolean hasRoot(ServerLevel world, BlockPos pos, Direction facing, BlockPos root, int size) {
        if (!compatible(world, pos, facing)) return false;
        BlockState state = world.getBlockState(pos);
        return state.getValue(WIDTH) == size && pos.relative(facing.getClockWise(), -state.getValue(PART)).equals(root);
    }
    private Group normalize(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        int size = state.getValue(WIDTH), part = state.getValue(PART);
        BlockPos root = pos.relative(right, -part);
        Group declared = new Group(root, size, facing);
        boolean valid = part < size;
        for (int i = 0; valid && i < size; i++) {
            if (!compatible(world, declared.at(i), facing)) valid = false;
            else {
                BlockState member = world.getBlockState(declared.at(i));
                valid = member.getValue(WIDTH) == size && member.getValue(PART) == i;
            }
        }
        if (valid) return declared;
        // A removed tile can split one group into two. Repair only its surviving members.
        int left = 0, rightCount = 0;
        while (left < 2 && hasRoot(world, pos.relative(right, -left - 1), facing, root, size)) left++;
        while (left + rightCount < 2 && hasRoot(world, pos.relative(right, rightCount + 1), facing, root, size)) rightCount++;
        Group repaired = new Group(pos.relative(right, -left), left + rightCount + 1, facing);
        write(world, repaired);
        return repaired;
    }
    @Override public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!world.getBlockState(pos).is(this)) return;
        Group group = normalize(world, pos);
        BlockPos leftPos = group.at(-1);
        if (compatible(world, leftPos, group.facing())) {
            Group left = normalize(world, leftPos);
            if (left.at(left.size()).equals(group.start()) && left.size() + group.size() <= 3) {
                group = new Group(left.start(), left.size() + group.size(), group.facing());
                write(world, group);
            }
        }
        BlockPos rightPos = group.at(group.size());
        if (compatible(world, rightPos, group.facing())) {
            Group right = normalize(world, rightPos);
            if (right.start().equals(group.at(group.size())) && group.size() + right.size() <= 3) {
                group = new Group(group.start(), group.size() + right.size(), group.facing());
                write(world, group);
            }
        }
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? result : result.setValue(PART, Math.max(0, state.getValue(WIDTH) - 1 - state.getValue(PART)));
    }
}
