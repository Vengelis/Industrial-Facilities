package fr.vengelis.industrialfacilities.access;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.security.AutomationOutputs;
import fr.vengelis.industrialfacilities.security.CameraTerminalEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Vérifie les liaisons entre lecteurs et portes et applique les demandes d'ouverture côté serveur. */
public final class AccessDoorService {
    private static final double INTERACTION_RANGE_SQUARED = 8 * 8;
    private static final int ACCESS_DURATION_TICKS = 100;

    public static IndustrialDoorEntity resolveDoor(Level level, BlockPos pos) {
        if (!loaded(level, pos) || !(level.getBlockEntity(pos) instanceof IndustrialDoorEntity part)) {
            return null;
        }
        if (part.size > 0) {
            return part;
        }
        // Les autres blocs de la porte pointent vers le contrôleur qui porte son état.
        if (part.owner != null && loaded(level, part.owner)
            && level.getBlockEntity(part.owner) instanceof IndustrialDoorEntity controller && controller.size > 0) {
            return controller;
        }
        return null;
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? TerminalLinks.loaded(server, pos) : level.hasChunkAt(pos);
    }

    public static UUID ensureId(IndustrialDoorEntity door) {
        if (door.doorInstanceId == null) {
            // L'identifiant distingue une porte reconstruite de celle qui occupait les mêmes coordonnées.
            door.doorInstanceId = UUID.randomUUID();
            if (door.getLevel() instanceof ServerLevel level) {
                registerDoor(level, door);
            }
            door.sync();
        }
        return door.doorInstanceId;
    }

    public static void registerDoor(ServerLevel level, IndustrialDoorEntity door) {
        AccessRegistry.get(level.getServer()).registerDoor(
            level.dimension().location().toString(), door.getBlockPos(), door.doorInstanceId);
    }

    public static boolean isSecured(IndustrialDoorEntity door) {
        return door.getLevel() instanceof ServerLevel level
            && door.doorInstanceId != null
            && !AccessRegistry.get(level.getServer()).readers(door.doorInstanceId).isEmpty();
    }

    public static boolean canConfigure(Player player, IndustrialDoorEntity door) {
        return door.getLevel() instanceof ServerLevel level
            && player.level() == level
            && door.size != 0
            && canConfigureOwner(player.getUUID(), door);
    }

    public static boolean canConfigureOwner(UUID owner, IndustrialDoorEntity door) {
        if (owner == null || !(door.getLevel() instanceof ServerLevel level) || door.size == 0) {
            return false;
        }
        if (door.automationOwner != null && !door.automationOwner.equals(owner)) {
            return false;
        }
        // Les anciens réseaux peuvent avoir plusieurs lecteurs configurés indépendamment.
        if (door.doorInstanceId == null) {
            return true;
        }
        return AccessRegistry.get(level.getServer()).readers(door.doorInstanceId).stream()
            .allMatch(reader -> reader.owner.equals(owner));
    }

    public static boolean setAllowRedstone(Player player, IndustrialDoorEntity door, boolean allow) {
        if (!canChangeSettings(player, door)) {
            return false;
        }
        door.allowLinkedRedstone = allow;
        door.sync();
        return true;
    }

    public static boolean setAllowCameraAutomation(Player player, IndustrialDoorEntity door, boolean allow) {
        if (!canChangeSettings(player, door)) {
            return false;
        }
        door.allowCameraAutomation = allow;
        if (allow && door.automationOwner == null) {
            door.automationOwner = player.getUUID();
        }
        door.sync();
        return true;
    }

    private static boolean canChangeSettings(Player player, IndustrialDoorEntity door) {
        return player.isAlive() && !outsideInteractionRange(player, door.getBlockPos()) && canConfigure(player, door);
    }

    private static boolean outsideInteractionRange(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) > INTERACTION_RANGE_SQUARED;
    }

    public static boolean shouldOpen(ServerLevel level, IndustrialDoorEntity door) {
        if (level.getGameTime() < door.accessOpenUntil) {
            return true;
        }
        if ((!isSecured(door) || door.allowLinkedRedstone) && level.hasNeighborSignal(door.getBlockPos())) {
            return true;
        }
        return door.allowCameraAutomation
            && AutomationOutputs.active(level, door.getBlockPos(), "door", door.doorInstanceId);
    }

    /** Le voyant reste vert pendant la fermeture ou si un obstacle maintient la porte ouverte. */
    public static boolean readerGranted(AccessTerminalEntity terminal) {
        if (!(terminal.getLevel() instanceof ServerLevel level) || terminal.encoder || terminal.bank
            || terminal.terminalId == null || AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal) {
            return false;
        }

        var reader = AccessRegistry.get(level.getServer()).find(terminal.terminalId);
        if (TerminalLinks.loadedReader(level, reader) != terminal || reader.doors().isEmpty()) {
            return false;
        }

        boolean hasUnloadedDoor = false;
        for (var link : reader.doors()) {
            if (!TerminalLinks.admissible(level, terminal.getBlockPos(), link)) {
                continue;
            }
            if (!TerminalLinks.loaded(level, link.position())) {
                hasUnloadedDoor = true;
                continue;
            }
            var door = TerminalLinks.loadedDoor(level, link);
            if (door != null && (door.accessOpenUntil > level.getGameTime() || door.targetOpen || door.progress > 0)) {
                return true;
            }
        }

        // Conserve le dernier état connu tant qu'une porte liée n'est pas chargée.
        return hasUnloadedDoor && terminal.accessGranted;
    }

    public static void clearControllerLinks(ServerLevel level, BlockPos position, UUID oldInstance) {
        var readers = AccessRegistry.get(level.getServer())
            .invalidateDoor(level.dimension().location().toString(), position, oldInstance);
        for (var entry : readers) {
            var reader = TerminalLinks.loadedReader(level, entry);
            if (reader != null) {
                reader.updateAccessState();
            }
        }
        AutomationOutputs.forgetDoor(level, position);
        CameraTerminalEntity.doorRemoved(level, position);
    }

    public static void dismantle(ServerLevel level, IndustrialDoorEntity door) {
        clearControllerLinks(level, door.getBlockPos(), door.doorInstanceId);
        door.doorInstanceId = null;
        door.accessOpenUntil = 0;
        door.allowLinkedRedstone = false;
        door.allowCameraAutomation = false;
        door.automationOwner = null;
    }

    public static AccessBadges.Result linkReaders(ServerPlayer player, UUID selected, AccessTerminalEntity target) {
        if (!(target.getLevel() instanceof ServerLevel level) || player.level() != level || !player.isAlive()
            || target.encoder || target.bank || outsideInteractionRange(player, target.getBlockPos())
            || !TerminalLinks.loaded(level, target.getBlockPos())
            || AccessTerminalEntity.resolve(level, target.getBlockPos()) != target) {
            return AccessBadges.Result.fail("Le lecteur ciblé doit être assemblé et à portée.");
        }

        var registry = AccessRegistry.get(level.getServer());
        var source = registry.find(selected);
        if (!target.isOwner(player) || source == null || !source.owner.equals(player.getUUID())) {
            return AccessBadges.Result.fail("Les deux lecteurs doivent t'appartenir.");
        }
        if (TerminalLinks.loadedReader(level, source) == null) {
            return AccessBadges.Result.fail("Le lecteur sélectionné doit être assemblé et chargé dans cette dimension.");
        }
        if (!TerminalLinks.inRange(source.position, target.getBlockPos())) {
            return AccessBadges.Result.fail("Les lecteurs doivent être à 64 blocs maximum les uns des autres.");
        }
        UUID targetId = target.ensureCode();
        if (TerminalLinks.loadedReader(level, registry.find(targetId)) != target) {
            return AccessBadges.Result.fail("Le lecteur ciblé n'est pas enregistré à cet emplacement.");
        }
        return switch (registry.groupReaders(selected, targetId)) {
            case LINKED -> {
                target.updateAccessState();
                int readerCount = registry.find(targetId).readerCount();
                yield AccessBadges.Result.success("Lecteurs liés : " + readerCount + " lecteurs partagent les mêmes portes.");
            }
            case ALREADY_LINKED -> AccessBadges.Result.fail("Ces lecteurs sont déjà dans le même groupe.");
            case TOO_MANY_READERS -> AccessBadges.Result.fail("Ce groupe a atteint la limite de 64 lecteurs.");
            case TOO_FAR -> AccessBadges.Result.fail(
                "Chaque lecteur doit être à 64 blocs maximum des autres lecteurs et de chaque porte du groupe.");
            case DIFFERENT_DOORS -> AccessBadges.Result.fail(
                "Ces lecteurs commandent des portes différentes. Efface les liaisons du lecteur à déplacer avant de le joindre au groupe.");
            case INCOMPATIBLE -> AccessBadges.Result.fail("Impossible de regrouper ces lecteurs.");
        };
    }

    public static AccessBadges.Result link(Player player, UUID source, IndustrialDoorEntity door) {
        if (!(player instanceof ServerPlayer) || !(door.getLevel() instanceof ServerLevel level)) {
            return AccessBadges.Result.fail("Liaison impossible.");
        }
        AccessRegistry registry = AccessRegistry.get(level.getServer());
        AccessRegistry.Entry reader = registry.find(source);
        BlockPos doorPos = door.getBlockPos();
        if (reader == null || !reader.owner.equals(player.getUUID())) {
            return AccessBadges.Result.fail("Vous n'êtes pas le créateur de ce terminal.");
        }
        if (!player.isAlive() || player.level() != level || TerminalLinks.loadedReader(level, reader) == null) {
            return AccessBadges.Result.fail("Le terminal lecteur doit être assemblé et chargé dans cette dimension.");
        }
        if (!TerminalLinks.loaded(level, doorPos) || resolveDoor(level, doorPos) != door) {
            return AccessBadges.Result.fail("La porte doit être assemblée et chargée.");
        }
        if (!TerminalLinks.inRange(reader.position, doorPos)) {
            return AccessBadges.Result.fail("La porte doit se trouver à 64 blocs maximum du terminal lecteur.");
        }
        if (!canConfigure(player, door)) {
            return AccessBadges.Result.fail("Cette porte appartient déjà à un autre réseau d'accès.");
        }

        UUID doorId = ensureId(door);
        var link = new AccessRegistry.DoorLink(level.dimension().location().toString(), doorPos, doorId);
        if (reader.doors().contains(link)) {
            return AccessBadges.Result.fail("Cette porte est déjà liée au terminal.");
        }
        if (!registry.allReadersInRange(reader, link)) {
            return AccessBadges.Result.fail("La porte doit être à 64 blocs maximum de chaque lecteur du groupe.");
        }

        boolean firstBinding = !isSecured(door);
        if (!registry.link(source, link)) {
            return AccessBadges.Result.fail("Ce terminal a atteint la limite de 64 portes.");
        }
        if (firstBinding) {
            // Une fois la porte sécurisée, son propriétaire doit réautoriser la redstone explicitement.
            door.allowLinkedRedstone = false;
        }
        door.sync();
        int readerCount = reader.readerCount();
        return AccessBadges.Result.success(readerCount > 1
            ? "Porte liée aux " + readerCount + " lecteurs du groupe."
            : "Porte liée au terminal " + source + ".");
    }

    public static AccessBadges.Result activate(Player player, AccessTerminalEntity terminal, ItemStack badge) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(terminal.getLevel() instanceof ServerLevel level)
            || terminal.encoder || terminal.bank || terminal instanceof KeypassTerminalEntity || terminal.terminalId == null) {
            return AccessBadges.Result.fail("Ce lecteur n'est pas configuré.");
        }
        if (!player.isAlive() || player.level() != level || outsideInteractionRange(player, terminal.getBlockPos())
            || AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal) {
            return AccessBadges.Result.fail("Le terminal est hors de portée.");
        }

        var registry = AccessRegistry.get(level.getServer());
        var reader = registry.find(terminal.terminalId);
        if (TerminalLinks.loadedReader(level, reader) != terminal) {
            return AccessBadges.Result.fail("Ce lecteur n'est plus enregistré.");
        }
        if (!AccessBadges.canUse(badge, terminal)) {
            return AccessBadges.Result.fail(AccessBadges.remaining(badge) == 0
                ? "Ce badge est épuisé."
                : "Accès refusé : badge ou niveau insuffisant.");
        }
        return openDoors(level, registry, reader, terminal, () -> AccessBadges.consume(serverPlayer, badge));
    }

    public static String activateKeypass(ServerPlayer player, KeypassTerminalEntity terminal, String pin) {
        if (terminal.manager() || !KeypassData.validDevice(player, terminal)
            || outsideInteractionRange(player, terminal.getBlockPos())) {
            return "link_denied";
        }
        var level = player.serverLevel();
        var registry = AccessRegistry.get(level.getServer());
        var reader = registry.find(terminal.terminalId);
        if (TerminalLinks.loadedReader(level, reader) != terminal) {
            return "not_configured";
        }
        String rejected = terminal.checkPin(level, pin);
        if (!rejected.isEmpty()) {
            return rejected;
        }
        return openDoors(level, registry, reader, terminal, () -> true).ok() ? "granted" : "no_door";
    }

    private static AccessBadges.Result openDoors(ServerLevel level, AccessRegistry registry, AccessRegistry.Entry reader,
        AccessTerminalEntity terminal, BooleanSupplier consumeAccess) {
        List<IndustrialDoorEntity> doorsToOpen = new ArrayList<>();
        for (var link : reader.doors()) {
            if (!TerminalLinks.admissible(level, terminal.getBlockPos(), link)) {
                continue;
            }
            var door = TerminalLinks.loadedDoor(level, link);
            if (door != null && door.accessOpenUntil <= level.getGameTime() && !door.targetOpen) {
                doorsToOpen.add(door);
            }
        }

        // Le badge n'est débité qu'une fois, après avoir trouvé au moins une porte à ouvrir.
        if (doorsToOpen.isEmpty()) {
            return AccessBadges.Result.fail("Aucune porte liée disponible à ouvrir.");
        }
        if (!consumeAccess.getAsBoolean()) {
            return AccessBadges.Result.fail("Ce badge est épuisé.");
        }
        for (var door : doorsToOpen) {
            door.accessOpenUntil = door.getLevel().getGameTime() + ACCESS_DURATION_TICKS;
            door.sync();
        }
        terminal.grantedUntil = level.getGameTime() + ACCESS_DURATION_TICKS;
        terminal.accessGranted = true;
        terminal.sync();
        refreshLinkedReaders(level, registry, reader.id, doorsToOpen);
        return AccessBadges.Result.success("Accès autorisé.");
    }

    private static void refreshLinkedReaders(ServerLevel level, AccessRegistry registry, UUID activeReader,
        List<IndustrialDoorEntity> openedDoors) {
        // Chaque voyant est mis à jour une fois, même si son lecteur commande plusieurs de ces portes.
        Set<UUID> refreshed = new HashSet<>();
        refreshed.add(activeReader);
        for (var door : openedDoors) {
            for (var entry : registry.readers(door.doorInstanceId)) {
                if (!refreshed.add(entry.id)) {
                    continue;
                }
                var reader = TerminalLinks.loadedReader(level, entry);
                if (reader != null) {
                    reader.updateAccessState();
                }
            }
        }
    }

    private AccessDoorService() {}
}
