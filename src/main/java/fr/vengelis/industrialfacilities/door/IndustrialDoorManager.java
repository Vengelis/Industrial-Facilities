package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.access.AccessDoorService;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** Assemble, anime et démonte les portes industrielles à deux vantaux côté serveur. */
public final class IndustrialDoorManager {
    static final int MIN_FRAME_SIZE = 5;
    static final int MAX_FRAME_SIZE = 15;
    private static final int FRAME_CHECK_INTERVAL = 10;
    private static final double PANEL_INSET = 3.0 / 8.0;

    private IndustrialDoorManager() {}

    /** Convertit une colonne et une ligne du cadre en coordonnées du monde. */
    public static BlockPos at(BlockPos origin, Direction facing, int column, int row) {
        return origin.relative(facing.getCounterClockWise(), column).above(row);
    }

    public static boolean edge(int column, int row, int size) {
        return isSideColumn(column, size) || row == 0 || row == size - 1;
    }

    private static boolean isSideColumn(int column, int size) {
        return column == 0 || column == size - 1;
    }

    public static boolean assemble(ServerLevel level, BlockPos controller) {
        if (!(level.getBlockEntity(controller) instanceof IndustrialDoorEntity door)
                || !(door.getBlockState().getBlock() instanceof IndustrialDoorBlock block)
                || !block.controller || door.size != 0) {
            return false;
        }

        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        // Le contrôleur peut occuper toute case du contour. Tester les petits cadres en premier
        // conserve le choix du cadre si plusieurs carrés valides partagent le même contrôleur.
        for (int size = MIN_FRAME_SIZE; size <= MAX_FRAME_SIZE; size++) {
            for (int controllerColumn = 0; controllerColumn < size; controllerColumn++) {
                for (int controllerRow = 0; controllerRow < size; controllerRow++) {
                    if (!edge(controllerColumn, controllerRow, size)) {
                        continue;
                    }
                    BlockPos origin = at(controller, facing, -controllerColumn, -controllerRow);
                    if (isValidFrame(level, controller, origin, facing, size, block)) {
                        assembleFrame(level, door, origin, facing, size);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isValidFrame(ServerLevel level, BlockPos controller, BlockPos origin,
                                        Direction facing, int size, IndustrialDoorBlock controllerBlock) {
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + size > level.getMaxBuildHeight()) {
            return false;
        }
        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                BlockPos pos = at(origin, facing, column, row);
                if (!level.hasChunkAt(pos)) {
                    return false;
                }
                if (pos.equals(controller)) {
                    continue;
                }

                BlockState state = level.getBlockState(pos);
                if (edge(column, row, size)) {
                    if (!(state.getBlock() instanceof IndustrialDoorBlock frame) || frame.controller
                            || frame.style != controllerBlock.style
                            || !(level.getBlockEntity(pos) instanceof IndustrialDoorEntity member)
                            || member.owner != null) {
                        return false;
                    }
                } else if (!state.isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void assembleFrame(ServerLevel level, IndustrialDoorEntity door, BlockPos origin,
                                      Direction facing, int size) {
        BlockPos controller = door.getBlockPos().immutable();
        door.origin = origin.immutable();
        door.size = size;
        door.owner = controller;
        // Chaque assemblage reçoit une nouvelle identité pour les liaisons des lecteurs.
        door.doorInstanceId = UUID.randomUUID();
        door.allowLinkedRedstone = false;
        door.accessOpenUntil = 0;
        door.allowCameraAutomation = false;
        door.automationOwner = null;
        AccessDoorService.registerDoor(level, door);
        door.progress = 0;
        door.previousProgress = 0;
        door.targetOpen = false;
        door.paused = false;

        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                BlockPos pos = at(origin, facing, column, row);
                if (edge(column, row, size)) {
                    boolean sideColumn = isSideColumn(column, size);
                    BlockState frame = level.getBlockState(pos)
                        .setValue(IndustrialDoorBlock.FACING, facing)
                        .setValue(IndustrialDoorBlock.ACTIVE, true)
                        .setValue(IndustrialDoorBlock.OPEN, false)
                        .setValue(IndustrialDoorBlock.RAIL, sideColumn && IndustrialDoorVisualState.hasRail(size, row))
                        .setValue(IndustrialDoorBlock.VERTICAL, sideColumn);
                    level.setBlock(pos, frame, 3);
                } else {
                    level.setBlock(pos, IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_PART.get().defaultBlockState(), 3);
                }
                if (level.getBlockEntity(pos) instanceof IndustrialDoorEntity member) {
                    member.owner = controller;
                    member.sync();
                }
            }
        }
        door.sync();
    }

    public static boolean allLoaded(ServerLevel level, IndustrialDoorEntity door) {
        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        BlockPos origin = door.doorOrigin();
        for (int column = 0; column < door.size; column++) {
            for (int row = 0; row < door.size; row++) {
                if (!level.hasChunkAt(at(origin, facing, column, row))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void tick(ServerLevel level, IndustrialDoorEntity door) {
        if (door.size == 0 || door.tearingDown) {
            return;
        }
        // Attendre les chunks absents évite de démonter un cadre simplement déchargé.
        if (!allLoaded(level, door)) {
            if (!door.paused) {
                door.paused = true;
                door.sync();
            }
            return;
        }

        if (level.getGameTime() % FRAME_CHECK_INTERVAL == 0) {
            if (!hasAllMembers(level, door)) {
                dismantle(level, door);
                return;
            }
            updateAppearance(level, door);
        }

        boolean shouldOpen = AccessDoorService.shouldOpen(level, door);
        if (shouldOpen != door.targetOpen) {
            door.targetOpen = shouldOpen;
            door.sync();
        }

        float nextProgress = IndustrialDoorEntity.advance(door.progress, door.targetOpen);
        boolean blocked = nextProgress < door.progress && obstructed(level, door, nextProgress);
        if (door.paused != blocked) {
            door.paused = blocked;
            door.sync();
        }
        if (blocked) {
            return;
        }
        if (nextProgress != door.progress) {
            door.progress = nextProgress;
            door.sync();
        }
        if (door.getBlockState().getValue(IndustrialDoorBlock.OPEN) != (door.progress >= 1)) {
            updateAppearance(level, door);
        }
    }

    private static boolean hasAllMembers(ServerLevel level, IndustrialDoorEntity door) {
        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        BlockPos origin = door.doorOrigin();
        for (int column = 0; column < door.size; column++) {
            for (int row = 0; row < door.size; row++) {
                BlockPos pos = at(origin, facing, column, row);
                if (!(level.getBlockEntity(pos) instanceof IndustrialDoorEntity member)
                        || !door.getBlockPos().equals(member.owner)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void updateAppearance(ServerLevel level, IndustrialDoorEntity door) {
        int size = door.size;
        if (size == 0) {
            return;
        }
        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                if (!edge(column, row, size)) {
                    continue;
                }
                BlockPos pos = at(door.doorOrigin(), facing, column, row);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof IndustrialDoorBlock) {
                    boolean rail = isSideColumn(column, size) && IndustrialDoorVisualState.hasRail(size, row);
                    BlockState next = state.setValue(IndustrialDoorBlock.RAIL, rail)
                        .setValue(IndustrialDoorBlock.OPEN, door.progress >= 1);
                    if (next != state) {
                        level.setBlock(pos, next, 3);
                    }
                }
            }
        }
    }

    private static boolean obstructed(ServerLevel level, IndustrialDoorEntity door, float nextProgress) {
        int innerSize = door.size - 2;
        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        BlockPos first = at(door.doorOrigin(), facing, 1, 1);
        BlockPos last = at(door.doorOrigin(), facing, innerSize, innerSize);
        double minX = Math.min(first.getX(), last.getX());
        double maxX = Math.max(first.getX(), last.getX()) + 1;
        double minZ = Math.min(first.getZ(), last.getZ());
        double maxZ = Math.max(first.getZ(), last.getZ()) + 1;

        // Les vantaux occupent l'épaisseur centrale du cadre. Réduire uniquement l'axe normal à la porte.
        if (facing.getAxis() == Direction.Axis.Z) {
            minZ += PANEL_INSET;
            maxZ -= PANEL_INSET;
        } else {
            minX += PANEL_INSET;
            maxX -= PANEL_INSET;
        }

        // La progression règle la demi-hauteur du passage libre entre les deux vantaux.
        double bottom = first.getY();
        double halfHeight = innerSize / 2.0;
        double halfGap = halfHeight * nextProgress;
        AABB lowerPanel = new AABB(minX, bottom, minZ, maxX, bottom + halfHeight - halfGap, maxZ);
        AABB upperPanel = new AABB(minX, bottom + halfHeight + halfGap, minZ, maxX, bottom + innerSize, maxZ);
        return hasObstruction(level, lowerPanel) || hasObstruction(level, upperPanel);
    }

    private static boolean hasObstruction(ServerLevel level, AABB panel) {
        return !level.getEntities((Entity) null, panel, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    public static void breakMember(ServerLevel level, IndustrialDoorEntity member) {
        if (member.tearingDown) {
            return;
        }
        if (member.size > 0) {
            dismantle(level, member);
        } else if (member.owner != null && level.getBlockEntity(member.owner) instanceof IndustrialDoorEntity door) {
            dismantle(level, door);
        }
    }

    public static void dismantle(ServerLevel level, IndustrialDoorEntity door) {
        if (door.tearingDown || door.size == 0) {
            return;
        }
        AccessDoorService.dismantle(level, door);
        door.tearingDown = true;
        int size = door.size;
        Direction facing = door.getBlockState().getValue(IndustrialDoorBlock.FACING);
        BlockPos origin = door.doorOrigin();

        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                BlockPos pos = at(origin, facing, column, row);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof IndustrialDoorEntity member
                        && door.getBlockPos().equals(member.owner)) {
                    dismantleMember(level, pos, member);
                }
            }
        }
        resetAssembly(door);
    }

    private static void dismantleMember(ServerLevel level, BlockPos pos, IndustrialDoorEntity member) {
        // La suppression des blocs déclenche leurs callbacks de destruction. Le marqueur coupe la récursion.
        member.tearingDown = true;
        member.owner = null;
        BlockState state = level.getBlockState(pos);
        if (state.is(IndustrialFacilitiesModBlocks.INDUSTRIAL_DOOR_PART.get())) {
            level.removeBlock(pos, false);
        } else if (state.getBlock() instanceof IndustrialDoorBlock) {
            level.setBlock(pos, state.setValue(IndustrialDoorBlock.ACTIVE, false)
                .setValue(IndustrialDoorBlock.OPEN, false)
                .setValue(IndustrialDoorBlock.RAIL, false), 3);
            resetAssembly(member);
        }
    }

    private static void resetAssembly(IndustrialDoorEntity member) {
        member.size = 0;
        member.origin = null;
        member.owner = null;
        member.progress = 0;
        member.targetOpen = false;
        member.paused = false;
        member.tearingDown = false;
        member.sync();
    }
}
