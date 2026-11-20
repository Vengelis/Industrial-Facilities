package fr.vengelis.industrialfacilities.access;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import fr.vengelis.industrialfacilities.frequency.*;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import fr.vengelis.industrialfacilities.door.DoorMotionConfig;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorManager;
import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import fr.vengelis.industrialfacilities.item.BadgeWalletItem;
import fr.vengelis.industrialfacilities.security.AutomationOutputs;
import fr.vengelis.industrialfacilities.security.CameraTerminalEntity;
import fr.vengelis.industrialfacilities.supervision.SignalTraffic;
import fr.vengelis.industrialfacilities.supervision.NetworkView.SignalKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Vérifie les liaisons entre lecteurs et portes et applique les demandes d'ouverture côté serveur. */
public final class AccessDoorService {
    private static final double INTERACTION_RANGE_SQUARED = 8 * 8;

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
            && !readers(level, door.doorInstanceId).isEmpty();
    }

    public static List<AccessRegistry.DoorLink> doors(ServerLevel level, AccessRegistry.Entry reader) {
        var network = FrequencyNetwork.get(level);
        var result = new LinkedHashSet<AccessRegistry.DoorLink>();
        for (var door : reader.doors())
            if (network.legacy(reader.instanceId(), door.instance())) result.add(door);
        for (var device : network.peers(level, reader.instanceId()))
            if (device.type() == FrequencyEndpoint.Type.DOOR)
                result.add(new AccessRegistry.DoorLink(reader.dimension, device.pos(), device.id()));
        return List.copyOf(result);
    }

    public static Collection<AccessRegistry.Entry> readers(ServerLevel level, UUID door) {
        var registry = AccessRegistry.get(level.getServer());
        var network = FrequencyNetwork.get(level);
        var result = new LinkedHashSet<AccessRegistry.Entry>();
        for (var reader : registry.readers(door))
            if (network.legacy(reader.instanceId(), door)) result.add(reader);
        for (var device : network.senders(level, door)) {
            if (!device.type().reader()) continue;
            var reader = registry.byInstance(device.id());
            if (reader != null && reader.owner.equals(device.owner()) && reader.position.equals(device.pos())
                && reader.dimension.equals(level.dimension().location().toString()))
                result.add(reader);
        }
        return result;
    }

    public static boolean canConfigure(Player player, IndustrialDoorEntity door) {
        return door.getLevel() instanceof ServerLevel level
            && player.level() == level
            && door.size != 0
            && (door.placedBy == null || door.placedBy.equals(player.getUUID()))
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

    public static boolean setForcedClosing(Player player, IndustrialDoorEntity door, boolean enabled) {
        if (!canChangeSettings(player, door)) return false;
        door.forcedClosing = enabled;
        door.sync();
        return true;
    }

    public static boolean setHalfOpening(Player player, IndustrialDoorEntity door, boolean enabled) {
        if (door.style() != IndustrialDoorStyle.MESH || !canChangeSettings(player, door)) return false;
        door.halfOpening = enabled;
        door.sync();
        return true;
    }

    public static boolean setIrisOpening(Player player, IndustrialDoorEntity door, int percent) {
        if (percent < 0 || percent > 100 || door.style() != IndustrialDoorStyle.IRIS || !canChangeSettings(player, door)) return false;
        door.irisOpeningPercent = percent;
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
        var controls = TerminalControlData.get(level);
        if (controls.blocksDoor(level, door)) {
            controls.cancelDoorRequests(door.doorInstanceId);
            cancelAccessRequest(door);
            return false;
        }
        if (IndustrialDoorManager.fireAlarmActive(level, door)) {
            SignalTraffic.stop(level, door.accessRequester, door.accessRequester, door.doorInstanceId);
            return false;
        }
        if (level.getGameTime() < door.accessOpenUntil) {
            if (!controls.locked(door.accessRequester) && stillLinkedRequester(level, door)) {
                SignalTraffic.maintainUntil(level, door.accessRequester, door.accessRequester, door.doorInstanceId,
                    SignalKind.OPEN, door.accessOpenUntil);
                return true;
            }
        }
        cancelAccessRequest(door);
        if (controls.buttonRequests(level, door)) return true;
        if ((!isSecured(door) || door.allowLinkedRedstone) && level.hasNeighborSignal(door.getBlockPos())) {
            return true;
        }
        return door.allowCameraAutomation
            && AutomationOutputs.active(level, door.getBlockPos(), "door", door.doorInstanceId);
    }

    private static void cancelAccessRequest(IndustrialDoorEntity door) {
        if (door.accessOpenUntil == 0 && door.accessRequester == null) return;
        if (door.getLevel() instanceof ServerLevel level)
            SignalTraffic.stop(level, door.accessRequester, door.accessRequester, door.doorInstanceId);
        door.accessOpenUntil = 0;
        door.accessRequester = null;
        door.sync();
    }

    /** Timed holds only; redstone and maintained camera outputs have no countdown. */
    public static long holdOpenUntil(ServerLevel level, IndustrialDoorEntity door) {
        var controls = TerminalControlData.get(level);
        if (controls.blocksDoor(level, door) || IndustrialDoorManager.fireAlarmActive(level, door)) return 0;
        long access = door.accessOpenUntil > level.getGameTime() && !controls.locked(door.accessRequester)
            && stillLinkedRequester(level, door) ? door.accessOpenUntil : 0;
        return Math.max(access, controls.buttonOpenUntil(level, door));
    }

    private static boolean stillLinkedRequester(ServerLevel level, IndustrialDoorEntity door) {
        return door.accessRequester == null || readers(level, door.doorInstanceId).stream()
            .anyMatch(reader -> reader.instanceId() == null || door.accessRequester.equals(reader.instanceId()));
    }

    record ReaderScreen(boolean granted, long until) {
        private static final ReaderScreen IDLE = new ReaderScreen(false, 0);
    }

    public static boolean readerGranted(AccessTerminalEntity terminal) {
        return readerScreen(terminal).granted();
    }

    /** The lock icon can stay green during closing, while the timer only shows a timed hold. */
    static ReaderScreen readerScreen(AccessTerminalEntity terminal) {
        if (!(terminal.getLevel() instanceof ServerLevel level) || terminal.encoder || terminal.bank
            || terminal.isLockedDown() || terminal.terminalId == null || AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal) {
            return ReaderScreen.IDLE;
        }

        var reader = AccessRegistry.get(level.getServer()).find(terminal.terminalId);
        if (TerminalLinks.loadedReader(level, reader) != terminal) return ReaderScreen.IDLE;

        long now = level.getGameTime();
        long until = 0;
        boolean granted = false;
        boolean hasUnloadedDoor = false;
        for (var link : doors(level, reader)) {
            if (!TerminalLinks.admissible(level, terminal.getBlockPos(), terminal.instanceId, link)) continue;
            if (!TerminalLinks.loaded(level, link.position())) {
                hasUnloadedDoor = true;
                continue;
            }
            var door = TerminalLinks.loadedDoor(level, link);
            if (door != null && (door.accessOpenUntil > now || door.targetOpen || door.progress > 0)) {
                granted = true;
                if (door.accessOpenUntil > now || door.targetOpen) until = Math.max(until, holdOpenUntil(level, door));
            }
        }

        // Conserve le dernier état connu tant qu'une porte liée n'est pas chargée.
        return new ReaderScreen(granted || hasUnloadedDoor && terminal.accessGranted, until);
    }

    public static void clearControllerLinks(ServerLevel level, BlockPos position, UUID oldInstance) {
        FrequencyNetwork.remove(level, oldInstance, position);
        TerminalControlData.get(level).forgetDoor(oldInstance);
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
        door.accessRequester = null;
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
        return activate(player, terminal, List.of(badge), false);
    }

    public static AccessBadges.Result activateFromWallet(Player player, AccessTerminalEntity terminal, List<ItemStack> badges) {
        return activate(player, terminal, badges, true);
    }

    private static AccessBadges.Result activate(Player player, AccessTerminalEntity terminal, List<ItemStack> badges, boolean wallet) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(terminal.getLevel() instanceof ServerLevel level)
            || terminal.encoder || terminal.bank || terminal instanceof KeypassTerminalEntity || terminal.terminalId == null) {
            return AccessBadges.Result.fail("Ce lecteur n'est pas configuré.");
        }
        if (!player.isAlive() || player.level() != level || outsideInteractionRange(player, terminal.getBlockPos())
            || AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal) {
            return AccessBadges.Result.fail("Le terminal est hors de portée.");
        }
        if (terminal.isLockedDown()) return AccessBadges.Result.fail(ControlTerminalBlock.UI + "locked");

        var registry = AccessRegistry.get(level.getServer());
        var reader = registry.find(terminal.terminalId);
        if (TerminalLinks.loadedReader(level, reader) != terminal) {
            return AccessBadges.Result.fail("Ce lecteur n'est plus enregistré.");
        }
        for (ItemStack badge : badges) {
            if (!AccessBadges.canUse(badge, terminal)) continue;
            if (wallet && !AccessBadges.read(badge).hasUUID("BadgeId")) continue;
            return openDoors(level, registry, reader, terminal, () -> AccessBadges.consume(serverPlayer, badge));
        }
        if (wallet) return AccessBadges.Result.fail(BadgeWalletItem.DENIED);
        return AccessBadges.Result.fail(AccessBadges.remaining(badges.getFirst()) == 0
            ? "Ce badge est épuisé."
            : "Accès refusé : badge ou niveau insuffisant.");
    }

    public static String activateKeypass(ServerPlayer player, KeypassTerminalEntity terminal, String pin) {
        if (terminal.manager() || !KeypassData.validDevice(player, terminal)
            || outsideInteractionRange(player, terminal.getBlockPos())) {
            return "link_denied";
        }
        var level = player.serverLevel();
        if (terminal.isLockedDown()) return "link_denied";
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
        var controls = TerminalControlData.get(level);
        boolean lockdown = false;
        boolean fireAlarmActive = false;
        for (var link : doors(level, reader)) {
            if (!TerminalLinks.admissible(level, terminal.getBlockPos(), terminal.instanceId, link)) continue;
            var door = TerminalLinks.loadedDoor(level, link);
            if (door == null) continue;
            if (controls.blocksDoor(level, door)) {
                lockdown = true;
            } else if (IndustrialDoorManager.fireAlarmActive(level, door)) {
                fireAlarmActive = true;
            } else {
                doorsToOpen.add(door);
            }
        }

        // Une seule utilisation par validation, même pour plusieurs portes déjà ouvertes.
        if (doorsToOpen.isEmpty()) {
            if (lockdown) return AccessBadges.Result.fail(ControlTerminalBlock.UI + "door_locked");
            return AccessBadges.Result.fail(fireAlarmActive
                ? "Porte verrouillée par une alarme incendie." : "Aucune porte liée disponible à ouvrir.");
        }
        if (!consumeAccess.getAsBoolean()) {
            return AccessBadges.Result.fail("Ce badge est épuisé.");
        }
        for (var door : doorsToOpen) {
            int openingAllowance = door.isAtOpenLimit() ? 0 : Math.max(0, door.motionTicks(true) - DoorMotionConfig.DEFAULT_TICKS);
            int duration = terminal.openSeconds() * 20 + openingAllowance;
            if (door.accessRequester != null && !door.accessRequester.equals(terminal.instanceId))
                SignalTraffic.stop(level, door.accessRequester, door.accessRequester, door.doorInstanceId);
            door.accessOpenUntil = level.getGameTime() + duration;
            door.accessRequester = terminal.instanceId;
            SignalTraffic.maintainUntil(level, terminal.instanceId, terminal.instanceId, door.doorInstanceId,
                SignalKind.OPEN, door.accessOpenUntil);
            door.sync();
        }
        terminal.updateAccessState();
        refreshLinkedReaders(level, registry, reader.id, doorsToOpen);
        return AccessBadges.Result.success("Accès autorisé.");
    }

    private static void refreshLinkedReaders(ServerLevel level, AccessRegistry registry, UUID activeReader,
        List<IndustrialDoorEntity> openedDoors) {
        // Chaque voyant est mis à jour une fois, même si son lecteur commande plusieurs de ces portes.
        Set<UUID> refreshed = new HashSet<>();
        refreshed.add(activeReader);
        for (var door : openedDoors) {
            for (var entry : readers(level, door.doorInstanceId)) {
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
