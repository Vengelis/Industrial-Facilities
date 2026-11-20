package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.TerminalLinks;
import fr.vengelis.industrialfacilities.frequency.FrequencyChip;
import fr.vengelis.industrialfacilities.frequency.FrequencyEndpoint;
import fr.vengelis.industrialfacilities.frequency.FrequencyNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FrequencyTopology {
    private record Vertex(UUID node, int slot) {}
    private record Connection(FrequencyEndpoint endpoint, int slot) {
        Vertex vertex() { return new Vertex(endpoint.id(), slot); }
    }
    private record Hop(Connection from, Connection to, double distance) {}

    static NetworkView view(FrequencyNetwork network, UUID viewer, List<NetworkView.Node> nodes, List<NetworkView.Edge> edges,
                            List<NetworkView.Notice> events) {
        Map<FrequencyChip, Map<Vertex, Connection>> channels = new LinkedHashMap<>();
        List<NetworkView.Node> equipped = new ArrayList<>();
        for (var node : nodes) {
            var endpoint = network.endpoint(node.id());
            List<NetworkView.Port> ports = new ArrayList<>();
            if (endpoint != null) {
                for (var port : network.ports(node.id())) {
                    if (!port.frequency().permits(viewer)) continue;
                    ports.add(new NetworkView.Port(port.slot(), port.frequency().hashCode(), port.mode().ordinal()));
                    channels.computeIfAbsent(port.frequency(), chip -> new LinkedHashMap<>())
                        .putIfAbsent(new Vertex(node.id(), port.slot()), new Connection(endpoint, port.slot()));
                }
            }
            equipped.add(ports.isEmpty() ? node : node.withPorts(List.copyOf(ports)));
        }
        List<NetworkView.Wire> wires = new ArrayList<>();
        channels.forEach((channel, connections) -> wireChannel(List.copyOf(connections.values()), channel.hashCode(), wires));
        return new NetworkView(List.copyOf(equipped), edges, List.copyOf(wires), events);
    }

    private static void wireChannel(List<Connection> connections, int channel, List<NetworkView.Wire> wires) {
        List<Hop> hops = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            Connection from = connections.get(i);
            for (int j = i + 1; j < connections.size(); j++) {
                Connection to = connections.get(j);
                if (!from.endpoint.id().equals(to.endpoint.id()) && (from.endpoint.type().terminal() || to.endpoint.type().terminal())
                        && TerminalLinks.inRange(from.endpoint.pos(), to.endpoint.pos())) {
                    hops.add(new Hop(from, to, from.endpoint.pos().distSqr(to.endpoint.pos())));
                }
            }
        }
        // A spanning forest shows a shared channel without drawing a line for every pair.
        hops.sort(Comparator.comparingDouble(Hop::distance));
        Map<Vertex, Vertex> groups = new HashMap<>();
        for (var hop : hops) {
            UUID from = hop.from.endpoint.id(), to = hop.to.endpoint.id();
            Vertex fromGroup = root(groups, hop.from.vertex()), toGroup = root(groups, hop.to.vertex());
            if (fromGroup.equals(toGroup)) continue;
            groups.put(fromGroup, toGroup);
            wires.add(new NetworkView.Wire(from, hop.from.slot, to, hop.to.slot, channel));
        }
    }

    private static Vertex root(Map<Vertex, Vertex> groups, Vertex id) {
        Vertex next = groups.get(id);
        while (next != null) {
            Vertex parent = groups.get(next);
            if (parent != null) groups.put(id, parent);
            id = next;
            next = parent;
        }
        return id;
    }

    private FrequencyTopology() {}
}
