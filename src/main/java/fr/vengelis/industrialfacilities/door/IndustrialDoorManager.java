package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.AccessDoorService;
import fr.vengelis.industrialfacilities.alarm.FireAlarmControls;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Assemble, anime et démonte les portes industrielles à deux vantaux côté serveur. */
public final class IndustrialDoorManager {
    static final int MIN_FRAME_SIZE = 5;
    static final int MAX_FRAME_SIZE = 15;
    private static final int MAX_JAW_WIDTH = 25;
    private static final int MAX_JAW_HEIGHT = 10;
    private static final int FRAME_CHECK_INTERVAL = 10;
    private static final double PANEL_INSET = 3.0 / 8.0;
    private static final float CRUSH_DAMAGE = 20.0F;
    private static final int CRUSH_INTERVAL_TICKS = 20;
    private static final int CRUSH_IDLE_CHECK_TICKS = 5;
    private static final ResourceKey<DamageType> DOOR_CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, "door_crush"));

    private IndustrialDoorManager() {}

    /** Convertit une colonne et une ligne du cadre en coordonnées du monde. */
    public static BlockPos at(BlockPos origin, Direction facing, int column, int row) {
        return IndustrialDoorPlane.of(facing).at(origin, column, row);
    }

    public static AABB frameBounds(BlockPos origin, Direction facing, int width, int height) {
        return IndustrialDoorPlane.of(facing).bounds(origin, width, height);
    }

    public static boolean edge(int column, int row, int size) {
        return edge(column, row, size, size);
    }

    public static boolean edge(int column, int row, int width, int height) {
        return isSideColumn(column, width) || row == 0 || row == height - 1;
    }

    static boolean isSavedFrameSizeValid(IndustrialDoorStyle style, int width, int height) {
        if (width < MIN_FRAME_SIZE || height < MIN_FRAME_SIZE) return false;
        // Earlier versions allowed square jaw doors up to 15 blocks on each side.
        if (width == height && width <= MAX_FRAME_SIZE) return true;
        return style == IndustrialDoorStyle.JAW && width <= MAX_JAW_WIDTH && height <= MAX_JAW_HEIGHT;
    }

    private static boolean isSideColumn(int column, int size) {
        return column == 0 || column == size - 1;
    }

    public static boolean fireAlarmActive(ServerLevel level, IndustrialDoorEntity door) {
        if (door.size == 0 || !(door.getBlockState().getBlock() instanceof IndustrialDoorBlock block)
            || block.style != IndustrialDoorStyle.FIRE) return false;
        BlockPos origin = door.doorOrigin();
        return FireAlarmControls.blocksOpening(level, origin,
            door.plane().at(origin, door.size - 1, door.height() - 1));
    }

    public static boolean assemble(ServerLevel level, BlockPos controller) {
        if (!(level.getBlockEntity(controller) instanceof IndustrialDoorEntity door)
                || !(door.getBlockState().getBlock() instanceof IndustrialDoorBlock block)
                || !block.controller || door.size != 0) {
            return false;
        }

        Direction preferred = IndustrialDoorBlock.facing(door.getBlockState());
        if (tryFacing(level, door, block, preferred)) return true;
        if (block.style != IndustrialDoorStyle.JAW && block.style != IndustrialDoorStyle.IRIS) return false;

        // The supporting face only gives a preference. A wall controller is often placed on the floor
        // or against a frame block in the plane of the opening.
        for (Direction facing : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.UP}) {
            if (facing.getAxis() != preferred.getAxis() && tryFacing(level, door, block, facing)) return true;
        }
        return false;
    }

    private static boolean tryFacing(ServerLevel level, IndustrialDoorEntity door, IndustrialDoorBlock block, Direction facing) {
        if (tryAssemble(level, door, block, IndustrialDoorPlane.of(facing))) return true;
        return block.style == IndustrialDoorStyle.JAW && facing.getAxis().isVertical()
            && tryAssemble(level, door, block, IndustrialDoorPlane.of(facing, true));
    }

    private static boolean tryAssemble(ServerLevel level, IndustrialDoorEntity door, IndustrialDoorBlock block,
                                       IndustrialDoorPlane plane) {
        BlockPos controller = door.getBlockPos();
        boolean jaw = block.style == IndustrialDoorStyle.JAW;
        int maxWidth = jaw ? MAX_JAW_WIDTH : MAX_FRAME_SIZE;
        // Le contrôleur peut occuper toute case du contour. Tester les petits cadres en premier.
        for (int width = MIN_FRAME_SIZE; width <= maxWidth; width++) {
            int minHeight = jaw ? MIN_FRAME_SIZE : width;
            int maxHeight = jaw ? MAX_JAW_HEIGHT : width;
            for (int height = minHeight; height <= maxHeight; height++) {
                for (int column = 0; column < width; column++) {
                    for (int row = 0; row < height; row++) {
                        if (!edge(column, row, width, height)) continue;
                        BlockPos origin = plane.at(controller, -column, -row);
                        if (isValidFrame(level, controller, origin, plane, width, height, block)) {
                            assembleFrame(level, door, origin, plane, width, height);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isValidFrame(ServerLevel level, BlockPos controller, BlockPos origin,
                                        IndustrialDoorPlane plane, int width, int height, IndustrialDoorBlock controllerBlock) {
        if (level.isOutsideBuildHeight(origin) || level.isOutsideBuildHeight(plane.at(origin, width - 1, height - 1))) {
            return false;
        }
        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                BlockPos pos = plane.at(origin, column, row);
                if (!level.hasChunkAt(pos)) {
                    return false;
                }
                if (pos.equals(controller)) {
                    continue;
                }

                BlockState state = level.getBlockState(pos);
                if (edge(column, row, width, height)) {
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
                                      IndustrialDoorPlane plane, int width, int height) {
        BlockPos controller = door.getBlockPos().immutable();
        door.origin = origin.immutable();
        door.setDimensions(width, height);
        door.owner = controller;
        // Chaque assemblage reçoit une nouvelle identité pour les liaisons des lecteurs.
        door.doorInstanceId = UUID.randomUUID();
        door.allowLinkedRedstone = false;
        door.accessOpenUntil = 0;
        door.accessRequester = null;
        door.allowCameraAutomation = false;
        door.automationOwner = null;
        AccessDoorService.registerDoor(level, door);
        door.progress = 0;
        door.previousProgress = 0;
        door.targetOpen = false;
        door.paused = false;

        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                BlockPos pos = plane.at(origin, column, row);
                if (edge(column, row, width, height)) {
                    boolean sideColumn = isSideColumn(column, width);
                    BlockState frame = IndustrialDoorBlock.withPlane(level.getBlockState(pos), plane)
                        .setValue(IndustrialDoorBlock.ACTIVE, true)
                        .setValue(IndustrialDoorBlock.OPEN, false)
                        .setValue(IndustrialDoorBlock.RAIL, sideColumn && IndustrialDoorVisualState.hasRail(height, row))
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
        IndustrialDoorPlane plane = door.plane();
        BlockPos origin = door.doorOrigin();
        for (int column = 0; column < door.size; column++) {
            for (int row = 0; row < door.height(); row++) {
                if (!level.hasChunkAt(plane.at(origin, column, row))) {
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

        float target = door.targetProgress();
        boolean opening = target > door.progress;
        int motionTicks = door.motionTicks(opening);
        float nextProgress = IndustrialDoorEntity.advance(door.progress, target, motionTicks);
        boolean closing = nextProgress < door.progress;
        boolean blocked = closing && !door.forcedClosing && !findObstructions(level, door, nextProgress, false).isEmpty();
        if (!door.forcedClosing || nextProgress >= 1) {
            door.crushCooldowns = null;
        } else {
            long now = level.getGameTime();
            // Detect contacts during closure immediately and stagger checks on stationary doors.
            if (closing || (now + door.getBlockPos().asLong()) % CRUSH_IDLE_CHECK_TICKS == 0) {
                crushEntities(level, door, findObstructions(level, door, nextProgress, closing), now);
            }
        }
        if (door.paused != blocked) {
            door.paused = blocked;
            door.sync();
        }
        if (blocked) {
            return;
        }
        if (nextProgress != door.progress) {
            // A complete movement can arrive at the client as a single block entity update.
            if (motionTicks == 1) level.playSound(null, door.plane().at(door.doorOrigin(), door.size / 2, door.height() / 2),
                IndustrialDoorSounds.event(door.style(), false, opening), SoundSource.BLOCKS, .8F, 1F);
            door.progress = nextProgress;
            door.sync();
        }
        if (door.getBlockState().getValue(IndustrialDoorBlock.OPEN) != door.isAtOpenLimit()) {
            updateAppearance(level, door);
        }
    }

    private static boolean hasAllMembers(ServerLevel level, IndustrialDoorEntity door) {
        IndustrialDoorPlane plane = door.plane();
        BlockPos origin = door.doorOrigin();
        for (int column = 0; column < door.size; column++) {
            for (int row = 0; row < door.height(); row++) {
                BlockPos pos = plane.at(origin, column, row);
                if (!(level.getBlockEntity(pos) instanceof IndustrialDoorEntity member)
                        || !door.getBlockPos().equals(member.owner)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void updateAppearance(ServerLevel level, IndustrialDoorEntity door) {
        int width = door.size;
        int height = door.height();
        if (width == 0) {
            return;
        }
        IndustrialDoorPlane plane = door.plane();
        boolean open = door.isAtOpenLimit();
        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                if (!edge(column, row, width, height)) {
                    continue;
                }
                BlockPos pos = plane.at(door.doorOrigin(), column, row);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof IndustrialDoorBlock) {
                    boolean rail = isSideColumn(column, width) && IndustrialDoorVisualState.hasRail(height, row);
                    BlockState next = state.setValue(IndustrialDoorBlock.RAIL, rail)
                        .setValue(IndustrialDoorBlock.OPEN, open);
                    if (next != state) {
                        level.setBlock(pos, next, 3);
                    }
                }
            }
        }
    }

    private static List<Entity> findObstructions(ServerLevel level, IndustrialDoorEntity door, float nextProgress, boolean sweepMesh) {
        int innerSize = door.size - 2;
        int innerHeight = door.height() - 2;
        IndustrialDoorPlane plane = door.plane();
        Direction facing = plane.facing();
        BlockPos first = plane.at(door.doorOrigin(), 1, 1);
        var style = door.style();
        if (style == IndustrialDoorStyle.IRIS || style == IndustrialDoorStyle.JAW) {
            var jaws = style == IndustrialDoorStyle.JAW ? IndustrialJawDoorMotion.layout(door, plane) : null;
            AABB slab = jaws == null ? IndustrialIrisDoorMotion.slab(innerSize)
                : IndustrialJawDoorMotion.slab(innerSize, innerHeight);
            return level.getEntities((Entity) null, plane.worldBox(first, slab), entity -> {
                if (!canObstruct(entity)) return false;
                AABB local = plane.localBox(first, entity.getBoundingBox());
                return !(jaws == null ? IndustrialIrisDoorMotion.isClear(innerSize, nextProgress, local)
                    : jaws.isClear(nextProgress, local));
            });
        }
        BlockPos last = plane.at(door.doorOrigin(), innerSize, innerSize);
        double minX = Math.min(first.getX(), last.getX());
        double maxX = Math.max(first.getX(), last.getX()) + 1;
        double minZ = Math.min(first.getZ(), last.getZ());
        double maxZ = Math.max(first.getZ(), last.getZ()) + 1;
        double bottom = first.getY();

        if (style == IndustrialDoorStyle.MESH) {
            var lowerPanel = IndustrialMeshDoorMotion.lower(innerSize, nextProgress);
            var upperPanel = IndustrialMeshDoorMotion.upper(innerSize, nextProgress);
            var hazardBeam = IndustrialMeshDoorMotion.hazardBeam(lowerPanel);
            if (sweepMesh) {
                var previousLower = IndustrialMeshDoorMotion.lower(innerSize, door.progress);
                lowerPanel = lowerPanel.sweptFrom(previousLower);
                upperPanel = upperPanel.sweptFrom(IndustrialMeshDoorMotion.upper(innerSize, door.progress));
                hazardBeam = hazardBeam.sweptFrom(IndustrialMeshDoorMotion.hazardBeam(previousLower));
            }
            return entitiesIntersectingPanels(level, minX, bottom, minZ,
                lowerPanel.bounds(innerSize, innerSize, facing), upperPanel.bounds(innerSize, innerSize, facing),
                hazardBeam.bounds(innerSize, innerSize, facing));
        }

        // Les vantaux occupent l'épaisseur centrale du cadre. Réduire uniquement l'axe normal à la porte.
        if (facing.getAxis() == Direction.Axis.Z) {
            minZ += PANEL_INSET;
            maxZ -= PANEL_INSET;
        } else {
            minX += PANEL_INSET;
            maxX -= PANEL_INSET;
        }

        // La progression règle la demi-hauteur du passage libre entre les deux vantaux.
        if (door.lifts()) {
            double lowerEdge = bottom + innerSize * nextProgress;
            var panel = new AABB(minX, lowerEdge, minZ, maxX, bottom + innerSize, maxZ);
            return level.getEntities((Entity) null, panel, IndustrialDoorManager::canObstruct);
        }
        double halfHeight = innerSize / 2.0;
        double halfGap = halfHeight * nextProgress;
        AABB lowerPanel = new AABB(minX, bottom, minZ, maxX, bottom + halfHeight - halfGap, maxZ);
        AABB upperPanel = new AABB(minX, bottom + halfHeight + halfGap, minZ, maxX, bottom + innerSize, maxZ);
        return entitiesIntersectingPanels(level, 0, 0, 0, lowerPanel, upperPanel);
    }

    private static boolean canObstruct(Entity entity) {
        return entity.isAlive() && !entity.isSpectator();
    }

    private static List<Entity> entitiesIntersectingPanels(ServerLevel level, double x, double y, double z, AABB... panels) {
        AABB bounds = null;
        for (int index = 0; index < panels.length; index++) {
            if (panels[index] == null) continue;
            AABB panel = panels[index].move(x, y, z);
            panels[index] = panel;
            bounds = bounds == null ? panel : bounds.minmax(panel);
        }
        if (bounds == null) return List.of();
        return level.getEntities((Entity) null, bounds, entity -> {
            if (!canObstruct(entity)) return false;
            AABB hitbox = entity.getBoundingBox();
            for (AABB panel : panels) if (panel != null && panel.intersects(hitbox)) return true;
            return false;
        });
    }

    private static void crushEntities(ServerLevel level, IndustrialDoorEntity door, List<Entity> obstructions, long now) {
        if (door.crushCooldowns != null) door.crushCooldowns.values().removeIf(until -> until <= now);
        if (obstructions.isEmpty()) return;
        if (door.crushCooldowns == null) door.crushCooldowns = new HashMap<>();
        var damageType = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DOOR_CRUSH);
        var source = new DamageSource(damageType);
        for (Entity entity : obstructions) {
            UUID id = entity.getUUID();
            if (!door.crushCooldowns.containsKey(id) && entity.hurt(source, CRUSH_DAMAGE)) {
                door.crushCooldowns.put(id, now + CRUSH_INTERVAL_TICKS);
            }
        }
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
        int width = door.size;
        int height = door.height();
        IndustrialDoorPlane plane = door.plane();
        BlockPos origin = door.doorOrigin();

        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                BlockPos pos = plane.at(origin, column, row);
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
        member.setDimensions(0, 0);
        member.origin = null;
        member.owner = null;
        member.progress = 0;
        member.targetOpen = false;
        member.paused = false;
        member.crushCooldowns = null;
        member.tearingDown = false;
        member.sync();
    }
}
