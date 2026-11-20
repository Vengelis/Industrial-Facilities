package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.access.client.TerminalGui;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.frequency.FrequencyEncoding;
import fr.vengelis.industrialfacilities.supervision.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SupervisionScreen extends AbstractContainerScreen<SupervisionMenu> {
    private static final int GRAPH_TAB = 3, SECTORS_TAB = 4, DISPLAYS_TAB = 5, SECTOR_ROWS = 5;
    private static final String[] TABS = {"doors", "terminals", "events", "graph", "sectors", "display_links"};
    private final Button[] tabButtons = new Button[TABS.length];
    private final Button[] sectorButtons = new Button[SECTOR_ROWS];
    private int tab, first, graphPage;
    private UUID selected;
    private EditBox search, sector, sectorName;
    private EditBox deviceName;
    private Button prev, next, pair, removeSector;
    private Button rename, resetLayout, renameSector, routeButton;
    private UUID nameTarget;
    private Drag drag;
    private int itemCount;
    private String displayedSector;
    private LinkedDisplaysPanel linkedDisplays;
    private GraphLayout.Canvas displayedCanvas = GraphLayout.Canvas.DEFAULT;
    private double zoom = 1, panX, panY;
    private Pan pan;
    private boolean panMoved;
    private NetworkView arrangedView;
    private int arrangedIndex;
    private GraphLayout.Page arrangedPage;
    private GraphLayout.Page paintedPage;
    private List<GraphLayout.Point> graphTimedNodes = List.of();
    private Drag paintedDrag;
    private String paintedSector;
    private WorldGraphSurface.Frame graphFrame;
    private List<GraphSignalPainter.Travel> graphSignals = List.of();
    private final GraphRouteEditor routeEditor = new GraphRouteEditor(this::saveRoute, this::saveSocket, this::joinRoutes);
    private boolean routeMode;
    private GraphRouteEditor.Preview paintedRoute;

    private static final int MARGIN = 14, CONTENT_Y = 55, MAP_X = MARGIN, MAP_Y = CONTENT_Y;
    private static final int LIST_Y = 78, LIST_ROW = 15, SECTOR_ROW = 21;
    private record Drag(GraphLayout.Point point, int page, double mouseX, double mouseY, int x, int y) {}
    private record Pan(int button, double mouseX, double mouseY, double x, double y) {}

    public SupervisionScreen(SupervisionMenu menu, Inventory player, Component title) {
        super(menu, player, title);
        imageWidth = 448;
        imageHeight = menu.mode == SupervisionMenu.TERMINAL ? 120 : hasEditor() ? 324 : 246;
    }
    private static Component tr(String name, Object... args) { return Component.translatable(NetworkView.UI + name, args); }
    private Button button(int x, int y, int width, Component text, Runnable action) {
        return addRenderableWidget(new Button(Button.builder(text, ignored -> action.run())
            .bounds(leftPos + x, topPos + y, width, TerminalGui.ACTION_HEIGHT)) {
            @Override public void renderString(GuiGraphics g, Font font, int color) {
                int textWidth = font.width(getMessage());
                int available = getWidth() - 6;
                if (textWidth <= available) {
                    super.renderString(g, font, color);
                    return;
                }
                float scale = (float) available / textWidth;
                g.pose().pushPose();
                g.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, getMessage(), -textWidth / 2, -font.lineHeight / 2 + 1, color, true);
                g.pose().popPose();
            }
        });
    }
    private int inventoryY() { return imageHeight - 81; }
    private int footerY() { return inventoryY() - 26; }
    private int navigationY() { return hasEditor() ? footerY() - 24 : imageHeight - 28; }
    private int listWidth() { return (imageWidth - 2 * MARGIN - 12) * 42 / 100; }
    private int detailsX() { return MARGIN + listWidth() + 12; }
    private int detailsWidth() { return imageWidth - MARGIN - detailsX(); }
    private boolean compactContent() { return navigationY() - CONTENT_Y < 56; }
    private boolean showsFrequencyButton() { return menu.edit && (menu.mode == SupervisionMenu.WORLD || menu.mode == SupervisionMenu.DISPLAY); }
    private int frequencyButtonWidth() { return imageWidth >= 400 ? 105 : 87; }
    private int frequencyButtonX() { return imageWidth - MARGIN - frequencyButtonWidth(); }
    private int headerWidth() { return Math.min(174, imageWidth * 37 / 100); }
    private int sectorRight() { return showsFrequencyButton() ? frequencyButtonX() - 10 : imageWidth - MARGIN; }
    private Component header() {
        return menu.mode == SupervisionMenu.DISPLAY && !menu.displayName.isEmpty() ? Component.literal(menu.displayName) : title;
    }
    private int sectorLeft() { return MARGIN + Math.min(font.width(header()), headerWidth()) + 10; }
    private Component sectorLabel() { return tr("sector_title", menu.sector.isEmpty() ? "—" : menu.sector); }

    @Override protected void init() {
        pan = null;
        imageWidth = Math.min(menu.mode == SupervisionMenu.TERMINAL ? 368 : 448, width - 12);
        imageHeight = Math.min(menu.mode == SupervisionMenu.TERMINAL ? 120 : hasEditor() ? 324 : 246, height - 10);
        super.init();
        panTo(panX, panY);
        linkedDisplays = null;
        paintedPage = null;
        displayedSector = menu.sector;
        if (menu.mode == SupervisionMenu.TERMINAL) {
            sectorField(MARGIN, 76, imageWidth - 116);
            button(imageWidth - 94, 75, 80, tr("save"), () -> send(SupervisionMenu.SAVE_SECTOR, sector.getValue()));
            return;
        }
        if (showsFrequencyButton())
            button(frequencyButtonX(), 3, frequencyButtonWidth(), Component.translatable(FrequencyContent.UI + "short_title"),
                () -> send(SupervisionMenu.FREQUENCIES, ""));
        int count = hasEditor() ? TABS.length : TABS.length - 1;
        int[] naturalWidths = new int[count];
        int naturalTotal = 0;
        for (int i = 0; i < count; i++) {
            naturalWidths[i] = font.width(Component.literal("› ").append(tr(TABS[i]))) + 12;
            naturalTotal += naturalWidths[i];
        }
        int gap = 3, space = imageWidth - 2 * MARGIN - (count - 1) * gap;
        int x = MARGIN, remaining = space;
        for (int i = 0; i < count; i++) {
            int index = i;
            int tabWidth = i == count - 1 ? remaining
                : space >= naturalTotal ? naturalWidths[i] + (space - naturalTotal) / count
                : space * naturalWidths[i] / naturalTotal;
            tabButtons[i] = button(x, 30, tabWidth, tr(TABS[i]), () -> select(index));
            x += tabWidth + gap;
            remaining -= tabWidth;
        }
        for (int row = 0; row < sectorButtons.length; row++) {
            int index = row;
            sectorButtons[row] = button(MARGIN, CONTENT_Y + row * SECTOR_ROW, listWidth(), Component.empty(), () -> {
                int item = first + index;
                if (item < menu.sectors.size()) send(SupervisionMenu.SELECT_SECTOR, menu.sectors.get(item));
            });
        }
        search = new EditBox(font, leftPos + MARGIN, topPos + CONTENT_Y, listWidth(), 17, tr("search"));
        search.setHint(tr("search"));
        search.setMaxLength(40);
        search.setResponder(value -> first = 0);
        addRenderableWidget(search);
        prev = button(MARGIN, navigationY(), 24, Component.literal("‹"), () -> step(-1));
        next = button(imageWidth - MARGIN - 24, navigationY(), 24, Component.literal("›"), () -> step(1));
        if (menu.mode == SupervisionMenu.PORTABLE) select(GRAPH_TAB);
        else if (menu.mode == SupervisionMenu.DISPLAY) select(menu.sector.isEmpty() ? SECTORS_TAB : GRAPH_TAB);
        else select(tab);
        if (hasEditor()) {
            int pairingX = imageWidth - MARGIN - 16, part = (imageWidth - 58) / 5;
            int pairX = pairingX - 8 - part, removeX = pairX - 6 - part, addX = removeX - 6 - part;
            menu.layoutSlots(imageWidth, inventoryY(), pairingX, footerY() + 2);
            sectorField(MARGIN, footerY() + 1, addX - MARGIN - 6);
            button(addX, footerY(), part, tr("add_sector"), () -> send(SupervisionMenu.SAVE_SECTOR, sector.getValue()));
            removeSector = button(removeX, footerY(), part, tr("remove_sector"), () -> send(SupervisionMenu.REMOVE_SECTOR, menu.sector));
            removeSector.active = !menu.sector.isEmpty();
            removeSector.setTooltip(Tooltip.create(tr("remove_sector_hint")));
            sectorName = new EditBox(font, leftPos + detailsX(), topPos + CONTENT_Y + (compactContent() ? 0 : 14), detailsWidth(), 18, tr("new_sector_name"));
            sectorName.setMaxLength(24);
            sectorName.setValue(menu.sector);
            sectorName.setHint(tr("new_sector_name"));
            sectorName.setResponder(value -> refreshSectorRename());
            addRenderableWidget(sectorName);
            renameSector = button(detailsX(), CONTENT_Y + (compactContent() ? 21 : 35), detailsWidth(), tr("rename_sector"), () ->
                sendEdit(SupervisionMenu.RENAME_SECTOR, null, sectorName.getValue(), null));
            refreshSectorRename();
            pair = button(pairX, footerY(), part, tr("pair"), () -> send(SupervisionMenu.PAIR, ""));
            int nameX = MARGIN + 31, renameX = imageWidth - MARGIN - 109;
            deviceName = new EditBox(font, leftPos + nameX, topPos + navigationY() + 1, renameX - nameX - 6, 17, tr("device_name"));
            deviceName.setMaxLength(32);
            deviceName.setHint(tr("device_name"));
            deviceName.setTooltip(Tooltip.create(tr("default_name_hint")));
            addRenderableWidget(deviceName);
            rename = button(renameX, navigationY(), 78, tr("rename"), () ->
                sendEdit(SupervisionMenu.RENAME, nameTarget, deviceName.getValue(), null));
            routeButton = button(imageWidth - MARGIN - 93, CONTENT_Y, 45, tr("route_mode"), () -> {
                routeMode = !routeMode;
                drag = null;
                routeEditor.clear();
                refreshEditor();
            });
            routeButton.setTooltip(Tooltip.create(tr("route_controls")));
            resetLayout = button(imageWidth - MARGIN - 45, CONTENT_Y, 45, tr("auto_layout"), () -> {
                if (routeMode) routeEditor.reset();
                else {
                    graphPage = 0;
                    sendEdit(SupervisionMenu.RESET_LAYOUT, null, "", null);
                }
            });
            nameTarget = null;
            drag = null;
            refreshEditor();
            linkedDisplays = new LinkedDisplaysPanel(menu, font, leftPos, topPos, imageWidth, CONTENT_Y, navigationY(),
                listWidth(), detailsX(), this::addRenderableWidget);
            refreshDisplayLinks();
        }
    }
    private void select(int index) {
        tab = index;
        for (int i = 0; i < tabButtons.length; i++)
            if (tabButtons[i] != null)
                tabButtons[i].setMessage(i == index ? Component.literal("› ").append(tr(TABS[i])) : tr(TABS[i]));
        first = tab == SECTORS_TAB ? Math.max(0, menu.sectors.indexOf(menu.sector)) / sectorRows() * sectorRows() : 0;
        drag = null;
        pan = null;
        routeMode = false;
        routeEditor.clear();
        search.visible = tab < 2;
        if (!search.visible) search.setFocused(false);
        refreshSectorButtons();
        refreshSectorRename();
        refreshEditor();
        refreshDisplayLinks();
    }
    private void sectorField(int x, int y, int w) {
        sector = new EditBox(font, leftPos + x, topPos + y, w, 17, tr("sector"));
        sector.setMaxLength(24);
        sector.setHint(tr("sector"));
        if (menu.mode == SupervisionMenu.TERMINAL) sector.setValue(menu.sector);
        addRenderableWidget(sector);
    }
    private void send(int kind, String value) {
        PacketDistributor.sendToServer(new SupervisionPackets.Action(menu.containerId, kind, value));
    }
    private void sendEdit(int kind, UUID node, String value, GraphLayout.Placement placement) {
        PacketDistributor.sendToServer(new SupervisionPackets.Action(menu.containerId, kind, value, menu.sector, node,
            placement == null ? 0 : placement.page(), placement == null ? 0 : placement.x(), placement == null ? 0 : placement.y()));
    }
    private void saveRoute(GraphRoutes.Key key, List<GraphRoutes.Bend> points) {
        PacketDistributor.sendToServer(new SupervisionPackets.EditRoute(menu.containerId, menu.sector, graphPage().index(), key, points));
    }
    private void saveSocket(GraphRoutes.SocketKey key, GraphRoutes.Socket socket) {
        PacketDistributor.sendToServer(new SupervisionPackets.EditSocket(menu.containerId, menu.sector, graphPage().index(), key, socket));
    }
    private void joinRoutes(GraphRoutes.Key child, GraphRoutes.Key parent) {
        PacketDistributor.sendToServer(new SupervisionPackets.JoinRoutes(menu.containerId, menu.sector, graphPage().index(), child, parent));
    }
    private void step(int direction) {
        if (tab == GRAPH_TAB) {
            routeEditor.clear();
            if (controlsDisplay()) {
                PacketDistributor.sendToServer(new SupervisionPackets.Action(menu.containerId, SupervisionMenu.DISPLAY_PAGE,
                    "", "", null, direction, 0, 0));
            } else graphPage += direction;
        }
        else if (tab == DISPLAYS_TAB) {
            linkedDisplays.page(direction);
            refreshDisplayLinks();
        }
        else if (tab == SECTORS_TAB) {
            first = Math.clamp(first + direction * sectorRows(), 0, lastSectorPage());
            refreshSectorButtons();
        } else first = Math.clamp(first + direction * rows(), 0, Math.max(0, itemCount - rows()));
    }
    private int rows() {
        return Math.max(1, (navigationY() - (tab == 2 ? CONTENT_Y : LIST_Y) - 3) / LIST_ROW);
    }
    private int sectorRows() {
        return Math.clamp((navigationY() - CONTENT_Y) / SECTOR_ROW, 1, SECTOR_ROWS);
    }
    private boolean hasEditor() { return menu.mode == SupervisionMenu.WORLD && menu.edit; }
    private boolean controlsDisplay() { return menu.mode == SupervisionMenu.DISPLAY && menu.edit; }
    private int mapWidth() { return imageWidth - 2 * MARGIN - (hasEditor() ? 104 : 0); }
    private int mapHeight() { return Math.max(22, navigationY() - MAP_Y - 4); }
    private double fitScale() {
        var canvas = menu.view.canvas();
        return Math.min(1, Math.min((double) mapWidth() / canvas.width(), (double) mapHeight() / canvas.height()));
    }
    private double mapScale() { return fitScale() * zoom; }
    private boolean insideMap(double x, double y) {
        return x >= leftPos + MAP_X && x < leftPos + MAP_X + mapWidth()
            && y >= topPos + MAP_Y && y < topPos + MAP_Y + mapHeight();
    }
    private void panTo(double x, double y) {
        var canvas = menu.view.canvas();
        double scaledWidth = canvas.width() * mapScale(), scaledHeight = canvas.height() * mapScale();
        double visibleX = Math.min(24, Math.min(mapWidth(), scaledWidth) / 2);
        double visibleY = Math.min(24, Math.min(mapHeight(), scaledHeight) / 2);
        panX = Math.clamp(x, visibleX - scaledWidth, mapWidth() - visibleX);
        panY = Math.clamp(y, visibleY - scaledHeight, mapHeight() - visibleY);
    }
    private GraphLayout.Page graphPage() {
        int page = controlsDisplay() ? menu.displayPage : graphPage;
        if (arrangedView != menu.view || arrangedIndex != page) {
            arrangedView = menu.view;
            arrangedIndex = page;
            arrangedPage = GraphLayout.page(menu.view, page);
        }
        return arrangedPage;
    }
    private boolean editing() { return menu.mode == SupervisionMenu.WORLD && menu.edit && !menu.sector.isEmpty(); }
    private List<NetworkView.Node> filtered() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        return menu.view.nodes().stream()
            .filter(n -> tab == 0 ? n.kind().equals("door") : !n.kind().equals("door"))
            .filter(n -> GraphInk.display(n).toLowerCase(Locale.ROOT).contains(query) || n.position().contains(query)).toList();
    }
    @Override protected void containerTick() {
        super.containerTick();
        syncSector();
        if (search != null) {
            search.visible = tab < 2;
            if (!search.visible) search.setFocused(false);
        }
        if (pair != null) pair.active = !menu.sector.isEmpty() && menu.getSlot(0).hasItem();
        if (removeSector != null) removeSector.active = !menu.sector.isEmpty();
        if (routeMode) routeEditor.tick(graphPage());
        refreshSectorButtons();
        refreshSectorRename();
        refreshEditor();
        refreshDisplayLinks();
    }

    private void refreshDisplayLinks() {
        if (linkedDisplays == null) return;
        linkedDisplays.refresh(tab == DISPLAYS_TAB);
        if (tab == DISPLAYS_TAB) {
            prev.active = linkedDisplays.hasPrevious();
            next.active = linkedDisplays.hasNext();
        }
    }

    private void syncSector() {
        if (!displayedCanvas.equals(menu.view.canvas())) {
            displayedCanvas = menu.view.canvas();
            zoom = 1;
            panTo(0, 0);
            drag = null;
            pan = null;
            routeEditor.clear();
        }
        if (Objects.equals(displayedSector, menu.sector)) return;
        boolean renamed = menu.consumeSectorRename(displayedSector);
        if (menu.mode == SupervisionMenu.TERMINAL && sector != null && sector.getValue().equals(displayedSector)) {
            sector.setValue(menu.sector);
        }
        displayedSector = menu.sector;
        if (sectorName != null) sectorName.setValue(menu.sector);
        drag = null;
        pan = null;
        routeEditor.clear();
        if (renamed) return;
        graphPage = 0;
        selected = null;
        if (tab != SECTORS_TAB) first = 0;
    }

    private void refreshSectorRename() {
        if (renameSector == null) return;
        boolean visible = tab == SECTORS_TAB && editing();
        sectorName.visible = renameSector.visible = visible;
        if (!visible) sectorName.setFocused(false);
        String value = sectorName.getValue().strip();
        renameSector.active = visible && !value.isEmpty() && !value.equalsIgnoreCase(menu.sector);
    }

    private int lastSectorPage() {
        return Math.max(0, menu.sectors.size() - 1) / sectorRows() * sectorRows();
    }

    private void refreshSectorButtons() {
        if (prev == null || next == null) return;
        boolean visible = tab == SECTORS_TAB;
        if (visible) first = Math.min(first, lastSectorPage());
        for (int row = 0; row < sectorButtons.length; row++) {
            var button = sectorButtons[row];
            int item = first + row;
            button.visible = visible && row < sectorRows() && item < menu.sectors.size();
            if (!button.visible) continue;
            String name = menu.sectors.get(item);
            button.active = menu.mode != SupervisionMenu.DISPLAY || menu.edit;
            var label = tr("sector_title", name);
            button.setMessage(name.equals(menu.sector) ? Component.literal("› ").append(label) : label);
        }
        boolean pages = !controlsDisplay() || tab != GRAPH_TAB || graphPage().total() > 1;
        prev.active = visible ? first > 0 : pages;
        next.active = visible ? first < lastSectorPage() : pages;
    }

    private NetworkView.Node chosen(List<NetworkView.Node> list) {
        return list.stream().filter(n -> n.id().equals(selected)).findFirst().orElse(list.isEmpty() ? null : list.getFirst());
    }

    private void refreshEditor() {
        if (deviceName == null) return;
        NetworkView.Node node = !editing() ? null
            : tab < 2 ? chosen(filtered())
            : tab == GRAPH_TAB && !routeMode ? menu.view.nodes().stream().filter(n -> n.id().equals(selected)).findFirst().orElse(null) : null;
        UUID id = node != null && node.canRename() ? node.id() : null;
        if (!Objects.equals(id, nameTarget)) {
            nameTarget = id;
            deviceName.setValue(id == null ? "" : node.name());
        }
        deviceName.visible = id != null;
        rename.visible = id != null;
        if (id == null) deviceName.setFocused(false);
        resetLayout.visible = editing() && tab == GRAPH_TAB;
        routeButton.visible = resetLayout.visible;
        routeButton.setMessage(routeMode ? Component.literal("› ").append(tr("route_mode")) : tr("route_mode"));
        resetLayout.setMessage(routeMode ? tr("route_auto") : tr("auto_layout"));
        resetLayout.setTooltip(Tooltip.create(tr(routeMode ? "route_auto_hint" : "reset_layout_hint")));
        resetLayout.active = routeMode ? routeEditor.hasManualRoute(graphPage()) : !menu.view.layout().isEmpty();
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        TerminalGui.panel(g,x,y,imageWidth,imageHeight,TerminalGui.TEAL_HEADER);
        if (menu.mode == SupervisionMenu.WORLD && menu.edit) {
            for (var slot : menu.slots) TerminalGui.slot(g,x+slot.x,y+slot.y);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        syncSector();
        displayLine(g, header().getString(), MARGIN, 9, headerWidth(), 0xFFFFFF);
        if (menu.mode == SupervisionMenu.TERMINAL) {
            g.drawWordWrap(font, tr("terminal_hint"), MARGIN, 31, imageWidth - 2 * MARGIN, GraphInk.MUTED);
            return;
        }
        int sectorWidth = sectorRight() - sectorLeft();
        String label = sectorLabel().getString();
        if (font.width(label) > sectorWidth && !menu.sector.isEmpty()) label = menu.sector;
        String sectorText = fitText(label, sectorWidth);
        g.drawString(font, sectorText, sectorRight() - font.width(sectorText), 9, TerminalGui.ACCENT, false);
        if (tab == DISPLAYS_TAB) {
            linkedDisplays.render(g);
        } else if (tab == SECTORS_TAB) {
            if (menu.mode == SupervisionMenu.DISPLAY && menu.sectors.isEmpty()) {
                g.drawWordWrap(font, tr("display_sector_setup"), MARGIN, CONTENT_Y + 4, imageWidth - 2 * MARGIN, TerminalGui.MUTED);
            }
            else if (menu.sectors.isEmpty()) g.drawString(font, tr("no_sector"), MARGIN, CONTENT_Y + 6, TerminalGui.MUTED, false);
            else if (editing()) {
                if (!compactContent()) displayLine(g, tr("new_sector_name").getString(), detailsX(), CONTENT_Y + 2, detailsWidth(), TerminalGui.MUTED);
                if (navigationY() - CONTENT_Y >= 90)
                    g.drawWordWrap(font, tr("sector_rename_hint"), detailsX(), CONTENT_Y + 61, detailsWidth(), TerminalGui.MUTED);
            }
            else g.drawWordWrap(font,tr(menu.mode == SupervisionMenu.DISPLAY ? "display_sector_hint"
                : menu.mode == SupervisionMenu.PORTABLE ? "sector_portable_hint" : "sector_facade_hint"),
                detailsX(), CONTENT_Y + 3, detailsWidth(), TerminalGui.MUTED);
        } else if (tab == GRAPH_TAB) {
            renderGraph(g);
            if (editing() && mapHeight() >= 76)
                g.drawWordWrap(font, tr(routeMode ? (routeEditor.selected() ? "route_points_hint" : "route_select_hint") : "move_hint"),
                    imageWidth - MARGIN - 92, CONTENT_Y + 26, 92, TerminalGui.MUTED);
        } else if (tab == 2) {
            itemCount = menu.view.events().size();
            first = Math.min(first, Math.max(0, itemCount - rows()));
            for (int i=0; i<rows() && first+i<itemCount; i++) {
                var e = menu.view.events().get(first+i);
                var n = e.node();
                int y = CONTENT_Y + i * LIST_ROW;
                displayLine(g, GraphInk.display(n), MARGIN, y, detailsX() - MARGIN - 8, GraphInk.INK);
                displayLine(g, GraphInk.tr("state." + n.status()), detailsX(), y, detailsWidth(), GraphInk.color(n.status()));
            }
        } else drawList(g);
    }

    private void renderGraph(GuiGraphics g) {
        var page = graphPage();
        var route = routeMode ? routeEditor.preview() : null;
        if (page != paintedPage || !Objects.equals(drag, paintedDrag) || !menu.sector.equals(paintedSector)
                || !Objects.equals(route, paintedRoute)) {
            paintedPage = page;
            paintedSector = menu.sector;
            paintedDrag = drag;
            paintedRoute = route;
            if (drag != null && drag.page() == page.index()) {
                var point = drag.point();
                var points = new ArrayList<>(page.points());
                points.removeIf(p -> p.node().id().equals(point.node().id()));
                points.add(new GraphLayout.Point(point.node(), drag.x(), drag.y(), point.remoteLinks()));
                page = page.withPoints(points);
            }
            if (routeMode) page = routeEditor.displayed(page);
            var surface = new WorldGraphSurface(page.canvas());
            var traces = GraphInk.draw(surface, font, menu.sector, page,
                menu.mode == SupervisionMenu.DISPLAY ? "display_no_sector" : "no_sector", route == null ? null : route.key());
            graphFrame = surface.finish();
            graphSignals = GraphSignalPainter.plan(page, traces);
            graphTimedNodes = GraphInk.timedNodes(page);
        }
        g.enableScissor(leftPos + MAP_X, topPos + MAP_Y, leftPos + MAP_X + mapWidth(), topPos + MAP_Y + mapHeight());
        g.pose().pushPose();
        g.pose().translate(MAP_X + panX, MAP_Y + panY, 0);
        float scale = (float) mapScale();
        g.pose().scale(scale, scale, 1);
        for (var rect : graphFrame.background())
            g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), rect.color());
        if (minecraft.level != null) GraphSignalPainter.draw((x, y, w, h, color) -> g.fill(x, y, x + w, y + h, color),
            graphSignals, minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false), page.canvas());
        drawGraphText(g, graphFrame.glyphs());
        if (minecraft.level != null) drawGraphText(g, GraphInk.countdowns(font, graphTimedNodes, minecraft.level.getGameTime()));
        if (routeMode) routeEditor.drawHandles(g, graphPage());
        g.pose().popPose();
        g.disableScissor();
    }

    private void drawGraphText(GuiGraphics g, List<WorldGraphSurface.Glyph> glyphs) {
        for (var glyph : glyphs) {
            g.pose().pushPose();
            g.pose().translate(glyph.x(), glyph.y(), 0);
            g.pose().scale(glyph.scale(), glyph.scale(), 1);
            g.drawString(font, glyph.text(), 0, 0, glyph.color(), false);
            g.pose().popPose();
        }
    }

    private void drawList(GuiGraphics g) {
        var list = filtered();
        itemCount = list.size();
        first = Math.min(first, Math.max(0, itemCount - rows()));
        var chosen = chosen(list);
        int panelBottom = navigationY() - 4, x = detailsX() + 9, textWidth = detailsWidth() - 18;
        g.fill(detailsX(), CONTENT_Y, imageWidth - MARGIN, panelBottom, 0xFF1B2026);
        for (int i = 0; i < rows() && first + i < itemCount; i++) {
            var node = list.get(first + i);
            int y = LIST_Y + i * LIST_ROW;
            g.fill(MARGIN, y, MARGIN + listWidth(), y + LIST_ROW - 1, node == chosen ? 0xFF315869 : 0xFF273642);
            g.fill(MARGIN + 3, y + 4, MARGIN + 7, y + 10, GraphInk.color(node.status()));
            displayLine(g, GraphInk.display(node), MARGIN + 11, y + 3, listWidth() - 15, GraphInk.INK);
        }
        if (chosen == null) {
            g.drawWordWrap(font, tr("no_nodes"), x, CONTENT_Y + 7, textWidth, GraphInk.MUTED);
            return;
        }
        displayLine(g, GraphInk.display(chosen), x, CONTENT_Y + 5, textWidth, GraphInk.INK);
        displayLine(g, GraphInk.tr("state." + chosen.status()), x, CONTENT_Y + 19, textWidth, GraphInk.color(chosen.status()));
        String detail = chosen.kind().equals("door") ? Component.translatable(chosen.titleKey()).getString() : chosen.position();
        displayLine(g, detail, x, CONTENT_Y + 32, textWidth, GraphInk.MUTED);
        int linkY = CONTENT_Y + 58;
        int lines = Math.min(6, (panelBottom - linkY - 6) / 12);
        if (lines < 1) return;
        g.drawString(font, tr("links"), x, CONTENT_Y + 45, GraphInk.CYAN, false);
        Set<UUID> connected = new LinkedHashSet<>();
        for (var edge : menu.view.edges()) {
            if (edge.source().equals(chosen.id())) connected.add(edge.target());
            if (edge.target().equals(chosen.id())) connected.add(edge.source());
        }
        var linked = menu.view.nodes().stream().filter(node -> connected.contains(node.id())).toList();
        int shown = linked.size() > lines ? lines - 1 : linked.size();
        for (int i = 0; i < shown; i++)
            displayLine(g, GraphInk.display(linked.get(i)), x, linkY + i * 12, textWidth, GraphInk.INK);
        if (linked.size() > lines) displayLine(g, "+" + (linked.size() - shown), x, linkY + shown * 12, textWidth, GraphInk.MUTED);
        else if (shown < lines) menu.view.events().stream().filter(event -> event.node().id().equals(chosen.id())).findFirst()
            .ifPresent(event -> displayLine(g, GraphInk.tr("last_event", GraphInk.tr("state." + event.node().status())),
                x, linkY + shown * 12, textWidth, GraphInk.MUTED));
    }

    private String fitText(String text, int width) {
        if (width < font.width("…")) return "";
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private void displayLine(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, fitText(text, width), x, y, color, false);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (routeMode && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            routeMode = false;
            routeEditor.clear();
            refreshEditor();
            return true;
        }
        return TerminalGui.handleInventoryKey(getFocused(),key,scan,modifiers) || super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        syncSector();
        if (tab == GRAPH_TAB && insideMap(x, y)) {
            if (button == 2) return beginPan(x, y, button);
            var page = graphPage();
            boolean blank = button == 0 && at(page, x, y) == null
                && !routeEditor.hitTest(page, chartX(x), chartY(y), 4 / mapScale());
            if (blank) return beginPan(x, y, button);
        }
        if (editing() && tab == GRAPH_TAB && routeMode && insideMap(x, y)) {
            var page = graphPage();
            if (routeEditor.click(page, chartX(x), chartY(y), 4 / mapScale(), button, at(page, x, y) != null, hasShiftDown())) {
                refreshEditor();
                return true;
            }
        }
        if (tab == GRAPH_TAB && button == 1 && insideMap(x, y)) {
            return beginPan(x, y, button);
        }
        if (editing() && tab == GRAPH_TAB && button == 0) {
            var page = graphPage();
            var point = at(page, x, y);
            if (point != null) {
                selected = point.node().id();
                drag = new Drag(point, page.index(), x, y, point.x(), point.y());
                deviceName.setFocused(false);
                refreshEditor();
                return true;
            }
        }
        if (menu.mode != SupervisionMenu.TERMINAL && tab < 2 && button == 0 && x >= leftPos + MARGIN && x < leftPos + MARGIN + listWidth() && y >= topPos+LIST_Y) {
            int row = (int) (y - topPos - LIST_Y) / LIST_ROW;
            var list = filtered();
            if (row < rows() && first+row < list.size()) {
                selected = list.get(first+row).id();
                if (deviceName != null) deviceName.setFocused(false);
                refreshEditor();
                return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    private GraphLayout.Point at(GraphLayout.Page page, double x, double y) {
        if (!insideMap(x, y)) return null;
        double px = chartX(x), py = chartY(y);
        return page.points().reversed().stream()
            .filter(point -> Math.abs(px - point.x()) <= 37 && py >= point.y() - 10 && py <= point.y() + 12)
            .findFirst().orElse(null);
    }
    private double chartX(double x) { return (x - leftPos - MAP_X - panX) / mapScale(); }
    private double chartY(double y) { return (y - topPos - MAP_Y - panY) / mapScale(); }

    private boolean beginPan(double x, double y, int button) {
        if (drag != null || routeEditor.dragging()) return true;
        pan = new Pan(button, x, y, panX, panY);
        panMoved = false;
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        syncSector();
        if (pan != null && pan.button() == button) {
            double moveX = x - pan.mouseX(), moveY = y - pan.mouseY();
            if (panMoved || button != 0 || moveX * moveX + moveY * moveY > 9) {
                panMoved = true;
                panTo(pan.x() + moveX, pan.y() + moveY);
            }
            return true;
        }
        if (routeMode && routeEditor.dragging() && button == 0) {
            routeEditor.drag(graphPage(), chartX(x), chartY(y));
            return true;
        }
        if (drag == null || button != 0) return super.mouseDragged(x,y,button,dx,dy);
        var move = dragPlacement(x,y,drag.page());
        drag = new Drag(drag.point(),drag.page(),drag.mouseX(),drag.mouseY(),move.x(),move.y());
        return true;
    }
    private GraphLayout.Placement dragPlacement(double x,double y,int page) {
        int xMove = (int) Math.round((x - drag.mouseX()) / mapScale());
        int yMove = (int) Math.round((y - drag.mouseY()) / mapScale());
        return GraphLayout.Placement.within(menu.view.canvas(), page, drag.point().x() + xMove, drag.point().y() + yMove);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        syncSector();
        if (tab != GRAPH_TAB || !insideMap(x, y) || drag != null || pan != null || routeEditor.dragging())
            return super.mouseScrolled(x, y, horizontal, vertical);
        double localX = x - leftPos - MAP_X, localY = y - topPos - MAP_Y;
        double previous = mapScale();
        zoom = Math.clamp(zoom * Math.pow(1.25, vertical), 1, 1.4 / fitScale());
        double ratio = mapScale() / previous;
        panTo(localX - (localX - panX) * ratio, localY - (localY - panY) * ratio);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        syncSector();
        if (pan != null && pan.button() == button) {
            var gesture = pan;
            pan = null;
            var canvas = menu.view.canvas();
            double px = chartX(gesture.mouseX()), py = chartY(gesture.mouseY());
            if (button == 0 && !panMoved && editing() && routeMode && insideMap(x, y)
                    && px >= 0 && px < canvas.width() && py >= 16 && py < canvas.height()) {
                routeEditor.click(graphPage(), px, py, 4 / mapScale(), button, false, hasShiftDown());
                refreshEditor();
            }
            return true;
        }
        if (routeMode && routeEditor.dragging() && button == 0) {
            routeEditor.release();
            return true;
        }
        if (drag == null || button != 0) return super.mouseReleased(x,y,button);
        boolean moved = Math.abs(x-drag.mouseX()) + Math.abs(y-drag.mouseY()) > 2;
        int page = drag.page();
        if (moved) {
            boolean previous = prev.isMouseOver(x,y) && page > 0;
            boolean following = next.isMouseOver(x,y);
            boolean inside = insideMap(x, y);
            if (inside || previous || following) {
                if (previous) page--;
                if (following) page++;
                var position = inside ? dragPlacement(x,y,page)
                    : GraphLayout.Placement.within(menu.view.canvas(), page, drag.point().x(), drag.point().y());
                sendEdit(SupervisionMenu.POSITION, drag.point().node().id(), "", position);
                graphPage = page;
            }
        }
        drag = null;
        return true;
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g,x,y,partial);
        renderTooltip(g,x,y);
        if (menu.mode != SupervisionMenu.TERMINAL && y >= topPos + 3 && y < topPos + 23
                && x >= leftPos + sectorLeft() && x < leftPos + sectorRight()
                && font.width(sectorLabel()) > sectorRight() - sectorLeft())
            g.renderTooltip(font, sectorLabel(), x, y);
        if (hasEditor() && !menu.getSlot(0).hasItem() && x >= leftPos + menu.getSlot(0).x && x < leftPos + menu.getSlot(0).x + 16
                && y >= topPos + menu.getSlot(0).y && y < topPos + menu.getSlot(0).y + 16)
            g.renderTooltip(font, Component.translatable(FrequencyEncoding.accepts(0, menu.getCarried())
                ? FrequencyContent.UI + "blank_destination" : "item.industrial_facilities.portable_console"), x, y);
        if (menu.mode != SupervisionMenu.TERMINAL && tab == GRAPH_TAB && drag == null) {
            var point = at(graphPage(), x, y);
            if (point != null) g.renderTooltip(font, Component.literal(GraphInk.display(point.node())), x, y);
        }
    }
}
