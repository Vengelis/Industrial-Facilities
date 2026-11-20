package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.supervision.SupervisionData;
import java.util.Comparator;
import fr.vengelis.industrialfacilities.frequency.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TerminalDevices {
    public static final String UI = "gui.industrial_facilities.access.";
    public static final int MAX_LINKS = Math.max(AccessRegistry.MAX_DOORS, TerminalControlData.MAX_LINKS);

    public enum Kind { DOOR, TERMINAL, EMITTER }
    public record Link(UUID id, BlockPos position, Kind kind, boolean available, String name) {}

    public static List<Link> links(ServerLevel level, AccessTerminalEntity terminal) {
        var names = SupervisionData.get(level);
        if (terminal instanceof ControlTerminalEntity control) {
            return TerminalControlData.get(level).links(control).stream()
                .sorted(Comparator.comparing(TerminalControlData.Target::position).thenComparing(TerminalControlData.Target::id))
                .map(target -> new Link(target.id(), target.position(), target.door() ? Kind.DOOR : target.emitter() ? Kind.EMITTER : Kind.TERMINAL,
                    TerminalControlData.available(level, control, target), deviceName(level, names, terminal.creator, target)))
                .limit(MAX_LINKS).toList();
        }

        var reader = reader(level, terminal);
        if (reader == null) return List.of();
        return AccessDoorService.doors(level, reader).stream()
            .map(link -> new Link(link.instance(), link.position(), Kind.DOOR, doorAvailable(level, terminal, link),
                names.customName(terminal.creator, link.instance())))
            .limit(MAX_LINKS).toList();
    }

    private static String deviceName(ServerLevel level, SupervisionData names, UUID owner, TerminalControlData.Target target) {
        String custom = names.customName(owner, target.id());
        return custom.isEmpty() && target.terminal() ? KeypassData.get(level).readerName(target.id()) : custom;
    }

    public static String unlinkOffline(ServerPlayer player, AccessTerminalEntity terminal, UUID id, BlockPos position) {
        var level = player.serverLevel();
        if (!terminal.isOwner(player) || terminal.getLevel() != level || !player.isAlive() || terminal.isLockedDown()
                || player.distanceToSqr(terminal.getBlockPos().getCenter()) > 64
                || AccessTerminalEntity.resolve(level, terminal.getBlockPos()) != terminal)
            return UI + "denied";
        if (FrequencyNetwork.get(level).connected(level, terminal.instanceId, id))
            return FrequencyContent.UI + "remove_chip";
        if (terminal instanceof ControlTerminalEntity control)
            return UI + TerminalControlData.get(level).unlinkOffline(level, control, id, position);

        var reader = reader(level, terminal);
        if (reader == null) return UI + "device_not_linked";
        for (var link : reader.doors()) {
            if (!link.instance().equals(id) || !link.position().equals(position)) continue;
            if (doorAvailable(level, terminal, link)) return UI + "device_back_online";
            var registry = AccessRegistry.get(level.getServer());
            for (var member : registry.unlinkDoor(reader, link)) {
                var loaded = TerminalLinks.loadedReader(level, member);
                if (loaded != null) loaded.updateAccessState();
            }
            return UI + "device_unlinked";
        }
        return UI + "device_not_linked";
    }

    private static AccessRegistry.Entry reader(ServerLevel level, AccessTerminalEntity terminal) {
        var entry = AccessRegistry.get(level.getServer()).find(terminal.terminalId);
        return TerminalLinks.loadedReader(level, entry) == terminal ? entry : null;
    }

    private static boolean doorAvailable(ServerLevel level, AccessTerminalEntity terminal, AccessRegistry.DoorLink link) {
        return TerminalLinks.admissible(level, terminal.getBlockPos(), terminal.instanceId, link)
            && TerminalLinks.loadedDoor(level, link) != null;
    }

    private TerminalDevices() {}
}
