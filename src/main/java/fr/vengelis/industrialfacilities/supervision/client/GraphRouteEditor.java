package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.supervision.GraphLayout;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.Bend;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.Socket;
import fr.vengelis.industrialfacilities.supervision.GraphRoutes.SocketKey;
import fr.vengelis.industrialfacilities.supervision.client.GraphInk.Corner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;

final class GraphRouteEditor {
    record Preview(GraphRoutes.Key key, List<Bend> route, SocketKey socketKey, Socket socket, boolean socketChanged) {}

    private sealed interface Drag {
        record Handle(int index) implements Drag {}
        record Segment(Corner[] path, int index, boolean horizontal, double pointer,
                       List<Bend> previousDraft, int previousPending) implements Drag {}
        record Connector(GraphLayout.Point point) implements Drag {}
    }
    private record SegmentHit(GraphInk.Trace trace, int index) {}

    private final BiConsumer<GraphRoutes.Key, List<Bend>> saveRoute;
    private final BiConsumer<SocketKey, Socket> saveSocket;
    private final BiConsumer<GraphRoutes.Key, GraphRoutes.Key> joinRoutes;
    private GraphRoutes.Key selected;
    private SocketKey selectedSocket;
    private List<Bend> routeDraft;
    private Socket socketDraft;
    private boolean socketChanged, moved;
    private Drag dragging;
    private int pendingTicks;

    GraphRouteEditor(BiConsumer<GraphRoutes.Key, List<Bend>> saveRoute, BiConsumer<SocketKey, Socket> saveSocket,
                     BiConsumer<GraphRoutes.Key, GraphRoutes.Key> joinRoutes) {
        this.saveRoute = saveRoute;
        this.saveSocket = saveSocket;
        this.joinRoutes = joinRoutes;
    }

    void clear() {
        selected = null;
        selectedSocket = null;
        routeDraft = null;
        socketDraft = null;
        socketChanged = false;
        moved = false;
        dragging = null;
        pendingTicks = 0;
    }

    Preview preview() { return new Preview(selected, routeDraft, selectedSocket, socketDraft, socketChanged); }
    boolean dragging() { return dragging != null; }
    boolean selected() { return selected != null || selectedSocket != null; }

    boolean hitTest(GraphLayout.Page page, double x, double y, double tolerance) {
        if (handle(points(page), x, y, tolerance) >= 0) return true;
        var displayed = displayed(page);
        var docks = GraphRouting.docks(displayed);
        double socketTolerance = Math.min(tolerance, Math.max(3, tolerance * 0.55));
        for (var dock : docks.values()) {
            var edge = dock.edge();
            if (squared(x - edge.x(), y - edge.y()) <= socketTolerance * socketTolerance) return true;
        }
        return segment(GraphInk.traces(displayed, docks), x, y, tolerance) != null;
    }

    void tick(GraphLayout.Page page) {
        if (selected != null && !page.contains(selected) || selectedSocket != null && !page.contains(selectedSocket)) {
            clear();
        } else if (pendingTicks > 0 && (--pendingTicks == 0 || synchronizedWith(page))) {
            routeDraft = null;
            socketDraft = null;
            socketChanged = false;
            pendingTicks = 0;
        }
    }

    private boolean synchronizedWith(GraphLayout.Page page) {
        return selected == null
            ? Objects.equals(socketDraft, page.sockets().get(selectedSocket))
            : routeDraft.equals(page.routes().getOrDefault(selected, List.of()));
    }

    private List<Bend> points(GraphLayout.Page page) {
        return selected == null ? List.of() : routeDraft == null ? page.routes().getOrDefault(selected, List.of()) : routeDraft;
    }

    GraphLayout.Page displayed(GraphLayout.Page page) {
        if (selected != null && routeDraft != null) return page.withoutJoin(selected).withRoute(selected, routeDraft);
        if (selectedSocket != null && socketChanged) return page.withSocket(selectedSocket, socketDraft);
        return page;
    }

    boolean hasManualRoute(GraphLayout.Page page) {
        return selectedSocket == null ? !points(page).isEmpty() || selected != null && page.joins().containsKey(selected)
            : displayed(page).sockets().containsKey(selectedSocket);
    }

    void reset() {
        if (selected != null) commitRoute(List.of());
        if (selectedSocket != null) commitSocket(null);
    }

    boolean click(GraphLayout.Page page, double x, double y, double tolerance, int button, boolean onNode, boolean join) {
        if (button != 0 && button != 1) return false;
        if (join && button == 0 && selected != null && !onNode) {
            var compatible = GraphInk.traces(displayed(page)).stream()
                .filter(trace -> GraphRoutes.canJoin(selected, trace.key(), page.joins())).toList();
            var hit = segment(compatible, x, y, tolerance);
            if (hit != null) {
                joinRoutes.accept(selected, hit.trace.key());
                clear();
            }
            return true;
        }
        var bends = points(page);
        int handle = handle(bends, x, y, tolerance);
        if (handle >= 0) {
            if (button == 1) {
                var next = new ArrayList<>(bends);
                next.remove(handle);
                commitRoute(next);
            } else {
                start(new Drag.Handle(handle));
                routeDraft = bends;
            }
            return true;
        }
        if (button != 0) return false;

        var displayed = displayed(page);
        var docks = GraphRouting.docks(displayed);
        SocketKey socket = null;
        double socketTolerance = Math.min(tolerance, Math.max(3, tolerance * 0.55));
        double best = socketTolerance * socketTolerance;
        for (var entry : docks.entrySet()) {
            var edge = entry.getValue().edge();
            double distance = squared(x - edge.x(), y - edge.y());
            if (distance <= best) { best = distance; socket = entry.getKey(); }
        }
        if (socket != null) {
            if (!socket.equals(selectedSocket)) clear();
            selectedSocket = socket;
            start(new Drag.Connector(docks.get(socket).point()));
            return true;
        }
        if (onNode) return true;

        var hit = segment(GraphInk.traces(displayed, docks), x, y, tolerance);
        if (hit != null) {
            if (!hit.trace.key().equals(selected)) clear();
            selected = hit.trace.key();
            var path = hit.trace.corners();
            boolean horizontal = path[hit.index - 1].y() == path[hit.index].y();
            start(new Drag.Segment(path, hit.index, horizontal, horizontal ? y : x, routeDraft, pendingTicks));
        } else if (selected != null && bends.size() < GraphRoutes.MAX_POINTS) {
            var next = new ArrayList<>(bends);
            next.add(position(page, x, y));
            commitRoute(next);
        }
        return true;
    }

    private void start(Drag drag) {
        dragging = drag;
        moved = false;
    }

    void drag(GraphLayout.Page page, double x, double y) {
        switch (dragging) {
            case Drag.Handle point -> {
                var next = new ArrayList<>(routeDraft);
                next.set(point.index, position(page, x, y));
                routeDraft = List.copyOf(next);
            }
            case Drag.Segment segment -> {
                var start = segment.path[segment.index - 1];
                double delta = (segment.horizontal ? y : x) - segment.pointer;
                int snapped = segment.horizontal ? GraphRoutes.snapY(start.y() + delta, page.canvas())
                    : GraphRoutes.snapX(start.x() + delta, page.canvas());
                if (Math.abs(delta) < 0.5 || snapped == (segment.horizontal ? start.y() : start.x())) {
                    routeDraft = segment.previousDraft;
                    pendingTicks = segment.previousPending;
                    moved = false;
                    return;
                }
                var next = moveSegment(page, segment, snapped);
                if (next == null) return;
                routeDraft = next;
            }
            case Drag.Connector socket -> {
                socketDraft = GraphRouting.closestSocket(socket.point, x, y);
                socketChanged = true;
            }
            case null -> { return; }
        }
        moved = true;
        pendingTicks = 0;
    }

    private static List<Bend> moveSegment(GraphLayout.Page page, Drag.Segment segment, int snapped) {
        var path = segment.path;
        var a = path[segment.index - 1];
        var b = path[segment.index];
        var shiftedA = segment.horizontal ? new Corner(a.x(), snapped) : new Corner(snapped, a.y());
        var shiftedB = segment.horizontal ? new Corner(b.x(), snapped) : new Corner(snapped, b.y());
        var next = new ArrayList<Corner>();
        for (int i = 0; i < path.length; i++) {
            if (i == segment.index - 1) {
                if (i == 0) next.add(a);
                next.add(shiftedA);
            } else if (i == segment.index) {
                next.add(shiftedB);
                if (i == path.length - 1) next.add(b);
            } else next.add(path[i]);
        }
        var simplified = GraphRouting.simplified(next);
        // A single grid-aligned segment is still a manual route.
        if (simplified.size() == 2) return List.of(new Bend(shiftedA.x(), shiftedA.y()).within(page.canvas()));
        if (simplified.size() - 2 > GraphRoutes.MAX_POINTS) return null;
        return simplified.subList(1, simplified.size() - 1).stream()
            .map(corner -> new Bend(corner.x(), corner.y()).within(page.canvas())).toList();
    }

    void release() {
        dragging = null;
        if (!moved) return;
        if (selectedSocket != null) commitSocket(socketDraft);
        else commitRoute(routeDraft);
    }

    private void commitRoute(List<Bend> points) {
        routeDraft = List.copyOf(points);
        pendingTicks = 40;
        saveRoute.accept(selected, routeDraft);
    }

    private void commitSocket(Socket socket) {
        socketDraft = socket;
        socketChanged = true;
        pendingTicks = 40;
        saveSocket.accept(selectedSocket, socket);
    }

    void drawHandles(GuiGraphics graphics, GraphLayout.Page page) {
        for (var point : points(page)) drawHandle(graphics, point.x(), point.y());
        if (selectedSocket != null) {
            var dock = GraphRouting.docks(displayed(page)).get(selectedSocket);
            if (dock != null) drawHandle(graphics, dock.edge().x(), dock.edge().y());
        }
    }

    private static void drawHandle(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF183041);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, GraphInk.INK);
    }

    private static SegmentHit segment(List<GraphInk.Trace> traces, double x, double y, double tolerance) {
        SegmentHit closest = null;
        double best = tolerance * tolerance;
        for (var trace : traces) {
            var path = trace.corners();
            for (int i = 1; i < path.length; i++) {
                var a = path[i - 1];
                var b = path[i];
                double dx = x - Math.clamp(x, Math.min(a.x(), b.x()), Math.max(a.x(), b.x()));
                double dy = y - Math.clamp(y, Math.min(a.y(), b.y()), Math.max(a.y(), b.y()));
                double distance = squared(dx, dy);
                if (distance <= best) { best = distance; closest = new SegmentHit(trace, i); }
            }
        }
        return closest;
    }

    private static int handle(List<Bend> bends, double x, double y, double tolerance) {
        int closest = -1;
        double best = tolerance * tolerance;
        for (int i = 0; i < bends.size(); i++) {
            var point = bends.get(i);
            double distance = squared(x - point.x(), y - point.y());
            if (distance <= best) { best = distance; closest = i; }
        }
        return closest;
    }

    private static double squared(double x, double y) { return x * x + y * y; }

    private static Bend position(GraphLayout.Page page, double x, double y) {
        return new Bend(GraphRoutes.snapX(x, page.canvas()), GraphRoutes.snapY(y, page.canvas()));
    }
}
