package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.GraphLayout;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes;
import fr.vengelis.industrialfacilities.supervision.client.GraphInk.Corner;
import fr.vengelis.industrialfacilities.supervision.client.GraphInk.Trace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GraphJunctions {
    record Branch(Corner[] path, Corner junction) {}
    private record Candidate(int segment, Corner point, int cost) {}

    static Branch connect(GraphLayout.Page page, GraphRoutes.Key child, Trace parent,
                          Corner destination, List<Trace> occupied) {
        var common = GraphRoutes.common(child, parent.key());
        var trunk = List.of(parent.corners());
        if (!common.equals(GraphRoutes.from(parent.key()))) trunk = trunk.reversed();
        if (trunk.size() == 1) {
            var path = List.of(GraphRouting.automatic(page, trunk.getFirst(), destination, occupied, child.channel()));
            if (!common.equals(GraphRoutes.from(child))) path = path.reversed();
            return new Branch(path.toArray(Corner[]::new), trunk.getFirst());
        }
        List<Candidate> candidates = new ArrayList<>();
        int distance = 0;
        for (int i = 1; i < trunk.size(); i++) {
            var a = trunk.get(i - 1);
            var b = trunk.get(i);
            boolean horizontal = a.y() == b.y();
            int x = horizontal ? Math.clamp(GraphRoutes.snapX(destination.x(), page.canvas()),
                Math.min(a.x(), b.x()), Math.max(a.x(), b.x())) : a.x();
            int y = horizontal ? a.y() : Math.clamp(GraphRoutes.snapY(destination.y(), page.canvas()),
                Math.min(a.y(), b.y()), Math.max(a.y(), b.y()));
            var point = new Corner(x, y);
            int cost = Math.abs(x - destination.x()) + Math.abs(y - destination.y())
                + (distance + Math.abs(x - a.x()) + Math.abs(y - a.y())) / 8;
            candidates.add(new Candidate(i, point, cost));
            distance += Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
        }
        var branch = candidates.stream().min(Comparator.comparingInt(Candidate::cost)).orElseThrow();
        var path = new ArrayList<>(trunk.subList(0, branch.segment));
        path.add(branch.point);
        var tail = GraphRouting.automatic(page, branch.point, destination, occupied, child.channel());
        path.addAll(List.of(tail).subList(1, tail.length));
        var result = GraphRouting.simplified(path);
        if (!common.equals(GraphRoutes.from(child))) result = result.reversed();
        return new Branch(result.toArray(Corner[]::new), branch.point);
    }

    private GraphJunctions() {}
}
