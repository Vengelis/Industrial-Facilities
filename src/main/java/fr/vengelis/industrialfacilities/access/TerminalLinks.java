package fr.vengelis.industrialfacilities.access;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.item.NetworkTabletItem;
import fr.vengelis.industrialfacilities.panel.PanelBlockEntity;
import fr.vengelis.industrialfacilities.security.CameraTerminalEntity;
import fr.vengelis.industrialfacilities.security.SecurityCameraEntity;
import fr.vengelis.industrialfacilities.security.SignalEmitterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Construit côté serveur l'instantané des liaisons visibles par le joueur tenant la tablette. */
public final class TerminalLinks {
    public static final int MAX_DISTANCE = 64;
    public static final int SOURCE_RADIUS = 96;
    public static final int TARGET_RADIUS = 128;
    public static final int MAX_LINKS = 512;
    public static final int MAX_SOURCES = 128;
    public static final int MAX_SCANNED_BLOCK_ENTITIES = 32768;
    public static final String KEYPASS_MANAGER = "keypass_manager";
    public static final String KEYPASS_READER = "keypass_reader";

    private static final Set<String> AUTOMATION_TARGETS = Set.of("door", "camera", "alarm", "emitter", "panel");

    public record Link(BlockPos sourceRoot, UUID sourceId, String sourceKind, BlockPos targetRoot,
        UUID targetId, String targetKind, String sourceName, String targetName) {
        public Link(BlockPos sourceRoot, UUID sourceId, String sourceKind, BlockPos targetRoot,
            UUID targetId, String targetKind) {
            this(sourceRoot, sourceId, sourceKind, targetRoot, targetId, targetKind, "", "");
        }

        public Link {
            sourceRoot = sourceRoot.immutable();
            targetRoot = targetRoot.immutable();
            Objects.requireNonNull(sourceName);
            Objects.requireNonNull(targetName);
        }

        public boolean validNames() {
            return validName(sourceName, "reader".equals(sourceKind))
                && validName(targetName, KEYPASS_READER.equals(targetKind));
        }

        private static boolean validName(String name, boolean permitted) {
            return name.isEmpty() || (permitted && name.equals(KeypassData.cleanReaderName(name)));
        }
    }

    public record Snapshot(List<Link> links, boolean truncated) {
        public Snapshot {
            links = List.copyOf(links);
        }
    }

    public static boolean validKinds(String source, String target) {
        return switch (source) {
            case "reader" -> target.equals("door");
            case "automation" -> AUTOMATION_TARGETS.contains(target);
            case KEYPASS_MANAGER -> target.equals(KEYPASS_READER);
            default -> false;
        };
    }

    public static boolean inRange(BlockPos source, BlockPos target) {
        return source.distSqr(target) <= (double) MAX_DISTANCE * MAX_DISTANCE;
    }

    /** Consulte uniquement les chunks complets déjà chargés par le serveur. */
    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    public static boolean holdingTablet(Player player) {
        return player.isAlive() && (player.getMainHandItem().getItem() instanceof NetworkTabletItem
            || player.getOffhandItem().getItem() instanceof NetworkTabletItem);
    }

    public static boolean admissible(ServerLevel level, BlockPos source, AccessRegistry.DoorLink link) {
        return link.dimension().equals(level.dimension().location().toString()) && inRange(source, link.position());
    }

    /** Retrouve le lecteur assemblé en vérifiant l'identité et le propriétaire enregistrés. */
    public static AccessTerminalEntity loadedReader(ServerLevel level, AccessRegistry.Entry entry) {
        if (entry == null || !entry.dimension.equals(level.dimension().location().toString())
            || !loaded(level, entry.position)) {
            return null;
        }
        AccessTerminalEntity terminal = AccessTerminalEntity.resolve(level, entry.position);
        if (terminal == null || terminal.isRemoved() || terminal.encoder || terminal.bank
            || !terminal.getBlockPos().equals(entry.position) || !entry.id.equals(terminal.terminalId)
            || !entry.owner.equals(terminal.creator)) {
            return null;
        }
        return terminal;
    }

    public static IndustrialDoorEntity loadedDoor(ServerLevel level, AccessRegistry.DoorLink link) {
        if (!link.dimension().equals(level.dimension().location().toString()) || !loaded(level, link.position())) {
            return null;
        }
        var door = AccessDoorService.resolveDoor(level, link.position());
        if (door == null || door.isRemoved() || !door.getBlockPos().equals(link.position())
            || !link.instance().equals(door.doorInstanceId)) {
            return null;
        }
        return door;
    }

    private static boolean near(Player player, BlockPos pos, int radius) {
        return player.distanceToSqr(pos.getCenter()) <= (double) radius * radius;
    }

    private static boolean keypassTarget(ServerLevel level, UUID owner, KeypassData.Reader link) {
        return loaded(level, link.position())
            && AccessTerminalEntity.resolve(level, link.position()) instanceof KeypassTerminalEntity reader
            && !reader.isRemoved() && !reader.manager() && reader.getBlockPos().equals(link.position())
            && link.id().equals(reader.instanceId) && owner.equals(reader.creator);
    }

    private static boolean automationTarget(ServerLevel level, UUID owner, CameraTerminalEntity.LinkView link) {
        if (!loaded(level, link.pos())) {
            return false;
        }
        var target = level.getBlockEntity(link.pos());
        if (target == null || target.isRemoved()) {
            return false;
        }
        return switch (link.kind()) {
            case "camera" -> target instanceof SecurityCameraEntity camera
                && link.id().equals(camera.instanceId) && owner.equals(camera.creator);
            case "door" -> target instanceof IndustrialDoorEntity door && door.size > 0
                && link.id().equals(door.doorInstanceId) && owner.equals(door.automationOwner)
                && AccessDoorService.canConfigureOwner(owner, door);
            case "alarm" -> target instanceof AlarmBlockEntity alarm
                && link.id().equals(alarm.automationInstanceId) && owner.equals(alarm.automationOwner);
            case "panel" -> link.available() && target instanceof PanelBlockEntity panel
                && panel.automationGroup(owner, link.id()) != null;
            case "emitter" -> target instanceof SignalEmitterEntity emitter
                && link.id().equals(emitter.instanceId) && owner.equals(emitter.creator);
            default -> false;
        };
    }

    public static Snapshot snapshot(ServerPlayer player) {
        if (!holdingTablet(player)) {
            return new Snapshot(List.of(), false);
        }

        ServerLevel level = player.serverLevel();
        List<AccessTerminalEntity> candidates = new ArrayList<>();
        boolean truncated = collectSources(level, player, candidates);
        // En cas de limite, favorise les terminaux proches. La position départage les égalités.
        candidates.sort(Comparator
            .comparingDouble((AccessTerminalEntity terminal) -> player.distanceToSqr(terminal.getBlockPos().getCenter()))
            .thenComparingLong(terminal -> terminal.getBlockPos().asLong()));

        List<Link> links = new ArrayList<>();
        int sourceCount = 0;
        AccessRegistry registry = AccessRegistry.get(level.getServer());
        for (var terminal : candidates) {
            if (AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal) {
                continue;
            }
            if (++sourceCount > MAX_SOURCES) {
                truncated = true;
                break;
            }

            if (terminal instanceof CameraTerminalEntity automation) {
                truncated |= appendAutomationLinks(level, player, automation, links);
            } else if (terminal instanceof KeypassTerminalEntity manager && manager.manager()) {
                truncated |= appendKeypassLinks(level, player, manager, links);
            } else {
                truncated |= appendDoorLinks(level, player, registry, terminal, links);
            }
            if (links.size() >= MAX_LINKS) {
                truncated = true;
                break;
            }
        }
        return new Snapshot(links, truncated);
    }

    /** Recense les terminaux proches et renvoie true si la limite de parcours est atteinte. */
    private static boolean collectSources(ServerLevel level, ServerPlayer player, List<AccessTerminalEntity> candidates) {
        int minChunkX = ((int) Math.floor(player.getX() - SOURCE_RADIUS)) >> 4;
        int maxChunkX = ((int) Math.floor(player.getX() + SOURCE_RADIUS)) >> 4;
        int minChunkZ = ((int) Math.floor(player.getZ() - SOURCE_RADIUS)) >> 4;
        int maxChunkZ = ((int) Math.floor(player.getZ() + SOURCE_RADIUS)) >> 4;
        int inspected = 0;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (var entity : chunk.getBlockEntities().values()) {
                    if (++inspected > MAX_SCANNED_BLOCK_ENTITIES) {
                        return true;
                    }
                    if (entity instanceof AccessTerminalEntity terminal && !terminal.isRemoved()
                        && terminal.isOwner(player) && near(player, terminal.getBlockPos(), SOURCE_RADIUS)
                        && (terminal instanceof CameraTerminalEntity || terminal instanceof KeypassTerminalEntity
                            || (!terminal.encoder && !terminal.bank))) {
                        candidates.add(terminal);
                    }
                }
            }
        }
        return false;
    }

    private static boolean appendAutomationLinks(ServerLevel level, ServerPlayer player, CameraTerminalEntity terminal,
        List<Link> links) {
        BlockPos source = terminal.getBlockPos();
        for (var link : terminal.linkViews()) {
            if (!inRange(source, link.pos()) || !near(player, link.pos(), TARGET_RADIUS)
                || !automationTarget(level, player.getUUID(), link)) {
                continue;
            }
            if (links.size() >= MAX_LINKS) {
                return true;
            }
            links.add(new Link(source, terminal.instanceId, "automation", link.pos(), link.id(), link.kind()));
        }
        return false;
    }

    private static boolean appendKeypassLinks(ServerLevel level, ServerPlayer player, KeypassTerminalEntity manager,
        List<Link> links) {
        BlockPos source = manager.getBlockPos();
        for (var link : KeypassData.get(level).readers(manager)) {
            if (!inRange(source, link.position()) || !near(player, link.position(), TARGET_RADIUS)
                || !keypassTarget(level, player.getUUID(), link)) {
                continue;
            }
            if (links.size() >= MAX_LINKS) {
                return true;
            }
            links.add(new Link(source, manager.instanceId, KEYPASS_MANAGER,
                link.position(), link.id(), KEYPASS_READER, "", link.name()));
        }
        return false;
    }

    private static boolean appendDoorLinks(ServerLevel level, ServerPlayer player, AccessRegistry registry,
        AccessTerminalEntity terminal, List<Link> links) {
        var entry = terminal.terminalId == null ? null : registry.find(terminal.terminalId);
        if (loadedReader(level, entry) != terminal) {
            return false;
        }

        int doorCount = 0;
        BlockPos source = terminal.getBlockPos();
        String name = terminal instanceof KeypassTerminalEntity reader ? KeypassData.get(level).readerName(reader) : "";
        for (var link : entry.doors()) {
            if (++doorCount > AccessRegistry.MAX_DOORS) {
                return true;
            }
            if (!admissible(level, source, link) || !near(player, link.position(), TARGET_RADIUS)) {
                continue;
            }
            var door = loadedDoor(level, link);
            if (door == null || !AccessDoorService.canConfigureOwner(player.getUUID(), door)) {
                continue;
            }
            if (links.size() >= MAX_LINKS) {
                return true;
            }
            links.add(new Link(source, terminal.instanceId, "reader",
                door.getBlockPos(), door.doorInstanceId, "door", name, ""));
        }
        return false;
    }

    private TerminalLinks() {}
}
