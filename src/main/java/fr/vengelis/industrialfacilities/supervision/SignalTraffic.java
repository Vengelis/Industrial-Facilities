package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.TerminalControlData;
import fr.vengelis.industrialfacilities.frequency.FrequencyNetwork;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-owned output lifetimes; renewal keeps the original animation phase. */
@EventBusSubscriber(modid = "industrial_facilities")
public final class SignalTraffic {
    public static final int LIFETIME = 60, MAX_SIGNALS = 128;
    private static final int LEASE = 15, MAX_HELD = MAX_SIGNALS * 4;
    public record Emission(UUID from, UUID to, NetworkView.SignalKind kind) {}
    private record Command(UUID owner, UUID from, UUID to) {}
    private record Held(NetworkView.SignalKind kind, long since, long until, long expires) {}
    private record Hop(UUID from, int fromSlot, UUID to, int toSlot, int channel) {}
    private static final class State {
        final Map<Hop, NetworkView.Signal> pulses = new LinkedHashMap<>();
        final Map<Command, Held> held = new LinkedHashMap<>();
        List<NetworkView.Signal> visible = List.of();
        long revision, calculatedTick = Long.MIN_VALUE;
        boolean dirty, changed;
    }
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    public static long revision(ServerLevel level) {
        State state = STATES.get(level); return state == null ? 0 : state.revision;
    }

    public static void record(ServerLevel level, UUID from, UUID to) {
        record(level, from, to, NetworkView.SignalKind.GENERIC);
    }

    /** Discrete events (camera detection, panel text) still use a short pulse. */
    public static void record(ServerLevel level, UUID from, UUID to, NetworkView.SignalKind kind) {
        State state = STATES.computeIfAbsent(level, key -> new State());
        long now = level.getGameTime();
        for (Hop hop : hops(level, from, to)) {
            var previous = state.pulses.get(hop);
            if (previous != null && previous.kind() == kind && now - previous.tick() < 4) continue;
            state.pulses.put(hop, signal(hop, now, kind, now + LIFETIME));
            state.changed = true;
        }
        while (state.pulses.size() > MAX_SIGNALS) state.pulses.remove(state.pulses.keySet().iterator().next());
        collect(level, state);
    }

    /** MAX_VALUE is a renewable output, finite deadlines are actual device timers. */
    public static void maintainUntil(ServerLevel level, UUID owner, UUID from, UUID to,
                                     NetworkView.SignalKind kind, long until) {
        if (owner == null || from == null || to == null || from.equals(to)) return;
        long now = level.getGameTime();
        if (until <= now) { stop(level, owner, from, to); return; }
        State state = STATES.computeIfAbsent(level, key -> new State());
        Command command = new Command(owner, from, to);
        Held previous = state.held.get(command);
        long since = previous != null && previous.kind == kind && previous.expires > now ? previous.since : now;
        Held next = new Held(kind, since, until, until == Long.MAX_VALUE ? now + LEASE : until);
        state.held.put(command, next);
        if (previous == null || previous.kind != kind || previous.since != since || previous.until != until)
            state.changed = true;
        for (Hop hop : hops(level, from, to)) {
            var pulse = state.pulses.get(hop);
            if (pulse != null && pulse.kind() == kind) { state.pulses.remove(hop); state.changed = true; }
        }
        while (state.held.size() > MAX_HELD) { state.held.remove(state.held.keySet().iterator().next()); state.changed = true; }
    }

    /** Reconcile a producer's complete set without disturbing other producers. */
    public static void maintainAll(ServerLevel level, UUID owner, Collection<Emission> emissions, long until) {
        State state = STATES.get(level);
        Set<Command> wanted = new HashSet<>();
        for (Emission emission : emissions) wanted.add(new Command(owner, emission.from, emission.to));
        if (state != null && state.held.keySet().removeIf(command -> command.owner.equals(owner) && !wanted.contains(command)))
            state.changed = true;
        for (Emission emission : emissions) maintainUntil(level, owner, emission.from, emission.to, emission.kind, until);
    }

    public static void stop(ServerLevel level, UUID owner, UUID from, UUID to) {
        State state = STATES.get(level);
        if (state != null && state.held.remove(new Command(owner, from, to)) != null) state.changed = true;
    }

    public static void stopOrigin(ServerLevel level, UUID owner) {
        State state = STATES.get(level); if (state == null) return;
        boolean changed = state.held.keySet().removeIf(command -> command.owner.equals(owner));
        changed |= state.pulses.keySet().removeIf(hop -> hop.from.equals(owner));
        state.changed |= changed;
    }

    private static List<Hop> hops(ServerLevel level, UUID from, UUID to) {
        if (from == null || to == null || from.equals(to)) return List.of();
        var network = FrequencyNetwork.get(level);
        var path = network.path(level, from, to);
        if (path.isEmpty()) return network.legacy(from, to) ? List.of(new Hop(from, -1, to, -1, 0)) : List.of();
        var result = new ArrayList<Hop>();
        UUID owner = path.getFirst().owner();
        for (int index = 1; index < path.size(); index++) {
            var source = path.get(index - 1); var target = path.get(index);
            boolean found = false;
            for (var output : network.ports(source.id())) {
                if (!output.mode().sends() || output.frequency().issuer() != null && !output.frequency().issuer().equals(owner)) continue;
                for (var input : network.ports(target.id())) {
                    if (!input.mode().receives() || !output.frequency().equals(input.frequency())) continue;
                    result.add(new Hop(source.id(), output.slot(), target.id(), input.slot(), output.frequency().hashCode()));
                    found = true; break;
                }
                if (found) break;
            }
        }
        return result;
    }

    private static NetworkView.Signal signal(Hop hop, long since, NetworkView.SignalKind kind, long until) {
        return new NetworkView.Signal(hop.from, hop.fromSlot, hop.to, hop.toSlot, hop.channel, since, kind, until);
    }

    private static boolean valid(ServerLevel level, NetworkView.Signal signal) {
        var network = FrequencyNetwork.get(level);
        if (signal.fromSlot() < 0) return network.legacy(signal.from(), signal.to());
        for (var output : network.ports(signal.from())) {
            if (output.slot() != signal.fromSlot() || !output.mode().sends() || output.frequency().hashCode() != signal.channel()) continue;
            for (var input : network.ports(signal.to()))
                if (input.slot() == signal.toSlot() && input.mode().receives() && input.frequency().equals(output.frequency())) return true;
        }
        return false;
    }

    private static void collect(ServerLevel level, State state) {
        long now = level.getGameTime();
        if (state.calculatedTick == now && !state.changed) return;
        state.pulses.values().removeIf(signal -> now >= signal.until() || !valid(level, signal));
        state.held.values().removeIf(held -> now >= held.expires);
        Map<Hop, NetworkView.Signal> visible = new LinkedHashMap<>(state.pulses);
        for (var entry : state.held.entrySet()) {
            Command command = entry.getKey(); Held held = entry.getValue();
            for (Hop hop : hops(level, command.from, command.to)) {
                var previous = visible.get(hop);
                if (previous == null || held.kind == NetworkView.SignalKind.LOCKDOWN
                    || previous.kind() != NetworkView.SignalKind.LOCKDOWN && held.since >= previous.tick())
                    visible.put(hop, signal(hop, held.since, held.kind, held.until));
            }
        }
        List<NetworkView.Signal> next = visible.values().stream().limit(MAX_SIGNALS).toList();
        if (!next.equals(state.visible)) { state.visible = next; state.revision++; state.dirty = true; }
        state.calculatedTick = now; state.changed = false;
    }

    static List<NetworkView.Signal> snapshot(ServerLevel level, List<NetworkView.Node> nodes) {
        State state = STATES.get(level); if (state == null) return List.of();
        collect(level, state);
        Set<UUID> ids = new HashSet<>(); nodes.forEach(node -> ids.add(node.id()));
        return state.visible.stream().filter(signal -> ids.contains(signal.from()) && ids.contains(signal.to())).toList();
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % 4 != 0) continue;
            TerminalControlData.get(level).refreshSignalTraffic(level);
            State state = STATES.get(level); if (state == null) continue;
            collect(level, state);
            if (state.dirty) { state.dirty = false; SupervisionData.get(level).refreshTrafficViews(level); }
        }
    }
    private SignalTraffic() {}
}
