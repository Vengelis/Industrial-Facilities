package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.access.client.TerminalGui;
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
    private static final int[] LIST_ROWS = {7, 7, 7};
    private static final int GRAPH_TAB = 3, SECTORS_TAB = 4, DISPLAYS_TAB = 5, SECTOR_ROWS = 5;
    private static final String[] TABS = {"doors", "terminals", "events", "graph", "sectors", "display_links"};
    private static final int[] TAB_WIDTHS = {51,60,69,77,59};
    private static final int[] EDITOR_TAB_WIDTHS = {39,52,59,61,49,65};
    private final Button[] tabButtons = new Button[TABS.length];
    private final Button[] sectorButtons = new Button[SECTOR_ROWS];
    private int tab, first, graphPage;
    private UUID selected;
    private EditBox search, sector, sectorName;
    private EditBox deviceName;
    private Button prev, next, pair, removeSector;
    private Button rename, resetLayout, renameSector;
    private UUID nameTarget;
    private Drag drag;
    private int itemCount;
    private String displayedSector;
    private LinkedDisplaysPanel linkedDisplays;
    private GraphLayout.Canvas displayedCanvas = GraphLayout.Canvas.DEFAULT;
    private double zoom = 1, panX, panY;
    private boolean panning;
    private NetworkView arrangedView;
    private int arrangedIndex;
    private GraphLayout.Page arrangedPage;
    private GraphLayout.Page paintedPage;
    private Drag paintedDrag;
    private String paintedSector;
    private WorldGraphSurface.Frame graphFrame;

    private static final int MAP_X = 57, MAP_Y = 54;
    private static final int LIST_Y = 75;
    private record Drag(GraphLayout.Point point, int page, double mouseX, double mouseY, int x, int y) {}

    public SupervisionScreen(SupervisionMenu menu, Inventory player, Component title) {
        super(menu, player, title);
        imageWidth = 348;
        imageHeight = menu.mode == SupervisionMenu.TERMINAL ? 110
            : menu.mode == SupervisionMenu.WORLD && menu.edit ? 290 : 201;
    }
    private static Component tr(String name, Object... args) { return Component.translatable(NetworkView.UI + name, args); }
    private Button button(int x, int y, int w, Component text, Runnable action) {
        return addRenderableWidget(TerminalGui.button(leftPos+x, topPos+y, w, TerminalGui.ACTION_HEIGHT, text, b -> action.run()));
    }
    private Button tabButton(int x, int width, Component text, Runnable action) {
        if (!hasEditor()) return button(x, 30, width, text, action);
        return addRenderableWidget(new Button(Button.builder(text, ignored -> action.run())
            .bounds(leftPos + x, topPos + 30, width, TerminalGui.ACTION_HEIGHT)) {
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
    @Override protected void init() {
        super.init();
        linkedDisplays = null;
        paintedPage = null;
        displayedSector = menu.sector;
        if (menu.mode == SupervisionMenu.TERMINAL) {
            sectorField(14, 63, 230);
            button(254, 63, 80, tr("save"), () -> send(SupervisionMenu.SAVE_SECTOR, sector.getValue()));
            return;
        }
        int x = hasEditor() ? 6 : 12;
        int[] widths = hasEditor() ? EDITOR_TAB_WIDTHS : TAB_WIDTHS;
        for (int i = 0; i < widths.length; i++) {
            int index = i;
            tabButtons[i] = tabButton(x, widths[i], tr(TABS[i]), () -> select(index));
            x += widths[i] + 2;
        }
        for (int row = 0; row < sectorButtons.length; row++) {
            int index = row;
            sectorButtons[row] = button(14, 55 + row * 21, 200, Component.empty(), () -> {
                int item = first + index;
                if (item < menu.sectors.size()) send(SupervisionMenu.SELECT_SECTOR, menu.sectors.get(item));
            });
        }
        search = new EditBox(font, leftPos + 14, topPos + 55, 136, 16, tr("search"));
        search.setHint(tr("search"));
        search.setMaxLength(40);
        search.setResponder(value -> first = 0);
        addRenderableWidget(search);
        prev = button(12, 171, 24, Component.literal("‹"), () -> step(-1));
        next = button(312, 171, 24, Component.literal("›"), () -> step(1));
        if (menu.mode == SupervisionMenu.PORTABLE) select(GRAPH_TAB);
        else if (menu.mode == SupervisionMenu.DISPLAY) select(menu.sector.isEmpty() ? SECTORS_TAB : GRAPH_TAB);
        else select(tab);
        if (menu.mode == SupervisionMenu.WORLD && menu.edit) {
            sectorField(13, 184, 92);
            prev.setY(topPos + 161);
            next.setY(topPos + 161);
            button(109, 183, 58, tr("add_sector"), () -> send(SupervisionMenu.SAVE_SECTOR, sector.getValue()));
            removeSector = button(172, 183, 61, tr("remove_sector"), () -> send(SupervisionMenu.REMOVE_SECTOR, menu.sector));
            removeSector.active = !menu.sector.isEmpty();
            removeSector.setTooltip(Tooltip.create(tr("remove_sector_hint")));
            sectorName = new EditBox(font, leftPos+224, topPos+73, 110, 18, tr("new_sector_name"));
            sectorName.setMaxLength(24);
            sectorName.setValue(menu.sector);
            sectorName.setHint(tr("new_sector_name"));
            sectorName.setResponder(value -> refreshSectorRename());
            addRenderableWidget(sectorName);
            renameSector = button(224,95,110,tr("rename_sector"), () ->
                sendEdit(SupervisionMenu.RENAME_SECTOR, null, sectorName.getValue(), null));
            refreshSectorRename();
            pair = button(237, 183, 73, tr("pair"), () -> send(SupervisionMenu.PAIR, ""));
            deviceName = new EditBox(font, leftPos + 45, topPos + 162, 173, 16, tr("device_name"));
            deviceName.setMaxLength(32);
            deviceName.setHint(tr("device_name"));
            deviceName.setTooltip(Tooltip.create(tr("default_name_hint")));
            addRenderableWidget(deviceName);
            rename = button(225, 161, 79, tr("rename"), () ->
                sendEdit(SupervisionMenu.RENAME, nameTarget, deviceName.getValue(), null));
            resetLayout = button(272, 55, 64, tr("auto_layout"), () -> {
                graphPage = 0;
                sendEdit(SupervisionMenu.RESET_LAYOUT, null, "", null);
            });
            resetLayout.setTooltip(Tooltip.create(tr("reset_layout_hint")));
            nameTarget = null;
            drag = null;
            refreshEditor();
            linkedDisplays = new LinkedDisplaysPanel(menu, font, leftPos, topPos, this::addRenderableWidget);
            refreshDisplayLinks();
        }
    }
    private void select(int index) {
        tab = index;
        for (int i = 0; i < tabButtons.length; i++)
            if (tabButtons[i] != null)
                tabButtons[i].setMessage(i == index ? Component.literal("› ").append(tr(TABS[i])) : tr(TABS[i]));
        first = tab == SECTORS_TAB ? Math.max(0, menu.sectors.indexOf(menu.sector)) / SECTOR_ROWS * SECTOR_ROWS : 0;
        drag = null;
        panning = false;
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
    private void step(int direction) {
        if (tab == GRAPH_TAB) {
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
            first = Math.clamp(first + direction * SECTOR_ROWS, 0, lastSectorPage());
            refreshSectorButtons();
        } else first = Math.clamp(first + direction * rows(), 0, Math.max(0, itemCount - rows()));
    }
    private int rows() { return menu.mode == SupervisionMenu.WORLD && menu.edit ? 6 : LIST_ROWS[Math.min(tab, 2)]; }
    private boolean hasEditor() { return menu.mode == SupervisionMenu.WORLD && menu.edit; }
    private boolean controlsDisplay() { return menu.mode == SupervisionMenu.DISPLAY && menu.edit; }
    private int mapWidth() { return hasEditor() ? 207 : 234; }
    private int mapHeight() { return hasEditor() ? 105 : 116; }
    private double fitScale() {
        var canvas = menu.view.canvas();
        double original = hasEditor() ? .75 : .84;
        return Math.min(original, Math.min((double) mapWidth() / canvas.width(), (double) mapHeight() / canvas.height()));
    }
    private double mapScale() { return fitScale() * zoom; }
    private boolean insideMap(double x, double y) {
        var canvas = menu.view.canvas();
        return x >= leftPos + MAP_X && x < leftPos + MAP_X + Math.min(mapWidth(), canvas.width() * mapScale())
            && y >= topPos + MAP_Y && y < topPos + MAP_Y + Math.min(mapHeight(), canvas.height() * mapScale());
    }
    private void panTo(double x, double y) {
        var canvas = menu.view.canvas();
        panX = Math.clamp(x, Math.min(0, mapWidth() - canvas.width() * mapScale()), 0);
        panY = Math.clamp(y, Math.min(0, mapHeight() - canvas.height() * mapScale()), 0);
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
            panning = false;
        }
        if (Objects.equals(displayedSector, menu.sector)) return;
        boolean renamed = menu.consumeSectorRename(displayedSector);
        if (menu.mode == SupervisionMenu.TERMINAL && sector != null && sector.getValue().equals(displayedSector)) {
            sector.setValue(menu.sector);
        }
        displayedSector = menu.sector;
        if (sectorName != null) sectorName.setValue(menu.sector);
        drag = null;
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
        return Math.max(0, menu.sectors.size()-1) / SECTOR_ROWS * SECTOR_ROWS;
    }

    private void refreshSectorButtons() {
        if (prev == null || next == null) return;
        boolean visible = tab == SECTORS_TAB;
        if (visible) first = Math.min(first, lastSectorPage());
        for (int row = 0; row < sectorButtons.length; row++) {
            var button = sectorButtons[row];
            int item = first + row;
            button.visible = visible && item < menu.sectors.size();
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
            : tab == GRAPH_TAB ? menu.view.nodes().stream().filter(n -> n.id().equals(selected)).findFirst().orElse(null) : null;
        UUID id = node != null && node.canRename() ? node.id() : null;
        if (!Objects.equals(id, nameTarget)) {
            nameTarget = id;
            deviceName.setValue(id == null ? "" : node.name());
        }
        deviceName.visible = id != null;
        rename.visible = id != null;
        if (id == null) deviceName.setFocused(false);
        resetLayout.visible = editing() && tab == GRAPH_TAB;
        resetLayout.active = !menu.view.layout().isEmpty();
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
        var header = menu.mode == SupervisionMenu.DISPLAY && !menu.displayName.isEmpty()
            ? Component.literal(menu.displayName) : title;
        displayLine(g, header.getString(), 14, 9, 168, 0xFFFFFF);
        if (menu.mode == SupervisionMenu.TERMINAL) {
            g.drawWordWrap(font, tr("terminal_hint"), 14, 29, 320, GraphInk.MUTED);
            return;
        }
        String sectorLabel = GraphInk.tr("sector_title", menu.sector.isEmpty() ? "—" : menu.sector);
        sectorLabel = font.plainSubstrByWidth(sectorLabel, 148);
        g.drawString(font, sectorLabel, 334-font.width(sectorLabel), 9, TerminalGui.ACCENT, false);
        if (tab == DISPLAYS_TAB) {
            linkedDisplays.render(g);
        } else if (tab == SECTORS_TAB) {
            if (menu.mode == SupervisionMenu.DISPLAY && menu.sectors.isEmpty()) {
                g.drawWordWrap(font, tr("display_sector_setup"), 14, 59, 315, TerminalGui.MUTED);
            }
            else if (menu.sectors.isEmpty()) g.drawString(font,tr("no_sector"),14,61,TerminalGui.MUTED,false);
            else if (editing()) {
                g.drawString(font,tr("new_sector_name"),224,59,TerminalGui.MUTED,false);
                g.drawWordWrap(font,tr("sector_rename_hint"),224,124,110,TerminalGui.MUTED);
            }
            else g.drawWordWrap(font,tr(menu.mode == SupervisionMenu.DISPLAY ? "display_sector_hint"
                : menu.mode == SupervisionMenu.PORTABLE ? "sector_portable_hint" : "sector_facade_hint"),
                224,58,110,TerminalGui.MUTED);
        } else if (tab == GRAPH_TAB) {
            renderGraph(g);
            if (editing()) g.drawWordWrap(font,tr("move_hint"),273,81,63,TerminalGui.MUTED);
        } else if (tab == 2) {
            itemCount = menu.view.events().size();
            first = Math.min(first, Math.max(0, itemCount - rows()));
            for (int i=0; i<rows() && first+i<itemCount; i++) {
                var e = menu.view.events().get(first+i);
                var n = e.node();
                int y = 56+i*15;
                displayLine(g, GraphInk.display(n), 13, y, 191, GraphInk.INK);
                displayLine(g, GraphInk.tr("state." + n.status()), 218, y, 116, GraphInk.color(n.status()));
            }
        } else drawList(g);
    }

    private void renderGraph(GuiGraphics g) {
        var page = graphPage();
        if (page != paintedPage || !Objects.equals(drag, paintedDrag) || !menu.sector.equals(paintedSector)) {
            paintedPage = page;
            paintedSector = menu.sector;
            paintedDrag = drag;
            if (drag != null && drag.page() == page.index()) {
                var point = drag.point();
                var points = new ArrayList<>(page.points());
                points.removeIf(p -> p.node().id().equals(point.node().id()));
                points.add(new GraphLayout.Point(point.node(), drag.x(), drag.y(), point.remoteLinks()));
                page = new GraphLayout.Page(points, page.edges(), page.index(), page.total(), page.canvas());
            }
            var surface = new WorldGraphSurface(page.canvas());
            GraphInk.draw(surface, font, menu.sector, page,
                menu.mode == SupervisionMenu.DISPLAY ? "display_no_sector" : "no_sector");
            graphFrame = surface.finish();
        }
        g.enableScissor(leftPos + MAP_X, topPos + MAP_Y, leftPos + MAP_X + mapWidth(), topPos + MAP_Y + mapHeight());
        g.pose().pushPose();
        g.pose().translate(MAP_X + panX, MAP_Y + panY, 0);
        float scale = (float) mapScale();
        g.pose().scale(scale, scale, 1);
        for (var rect : graphFrame.background())
            g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), rect.color());
        for (var glyph : graphFrame.glyphs()) {
            g.pose().pushPose();
            g.pose().translate(glyph.x(), glyph.y(), 0);
            g.pose().scale(glyph.scale(), glyph.scale(), 1);
            g.drawString(font, glyph.text(), 0, 0, glyph.color(), false);
            g.pose().popPose();
        }
        g.pose().popPose();
        g.disableScissor();
    }

    private void drawList(GuiGraphics g) {
        var list = filtered();
        itemCount = list.size();
        first = Math.min(first, Math.max(0, itemCount - rows()));
        var chosen = chosen(list);
        g.fill(160, 55, 336, 158, 0xFF1B2026);
        for (int i=0; i<rows() && first+i<itemCount; i++) {
            var n = list.get(first+i);
            int y = LIST_Y+i*13;
            g.fill(13,y,151,y+12,n == chosen ? 0xFF315869 : 0xFF273642);
            g.fill(16,y+3,20,y+9,GraphInk.color(n.status()));
            displayLine(g, GraphInk.display(n), 23, y+2, 126, GraphInk.INK);
        }
        if (chosen == null) {
            displayLine(g, GraphInk.tr("no_nodes"), 169, 62, 157, GraphInk.MUTED);
            return;
        }
        displayLine(g, GraphInk.display(chosen), 169, 59, 155, GraphInk.INK);
        if (chosen.kind().equals("door")) {
            displayLine(g, Component.translatable(chosen.titleKey()).getString(), 169, 84, 155, GraphInk.MUTED);
        } else {
            displayLine(g, chosen.position(), 169, 84, 155, GraphInk.MUTED);
        }
        displayLine(g, GraphInk.tr("state." + chosen.status()), 169, 72, 155, GraphInk.color(chosen.status()));
        g.drawString(font,tr("links"),169,99,GraphInk.CYAN,false);
        Set<UUID> connected = new LinkedHashSet<>();
        for (var e : menu.view.edges()) {
            if (e.source().equals(chosen.id())) connected.add(e.target());
            if (e.target().equals(chosen.id())) connected.add(e.source());
        }
        var linked = menu.view.nodes().stream().filter(n -> connected.contains(n.id())).toList();
        for (int i=0;i<Math.min(3,linked.size());i++)
            displayLine(g,GraphInk.display(linked.get(i)),169,112+i*11,152,GraphInk.INK);
        if (linked.size()>3) displayLine(g,"+"+(linked.size()-3),169,145,150,GraphInk.MUTED);
        else if (linked.size()<3) menu.view.events().stream().filter(e -> e.node().id().equals(chosen.id())).findFirst()
            .ifPresent(e -> displayLine(g,GraphInk.tr("last_event", GraphInk.tr("state."+e.node().status())),
                169,145,159,GraphInk.MUTED));
    }
    private void displayLine(GuiGraphics g,String txt,int x,int y,int limit,int color){
        g.drawString(font,font.plainSubstrByWidth(txt,limit),x,y,color,false);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        return TerminalGui.handleInventoryKey(getFocused(),key,scan,modifiers) || super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        syncSector();
        if (tab == GRAPH_TAB && button == 1 && insideMap(x, y)) {
            panning = true;
            return true;
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
        if (menu.mode != SupervisionMenu.TERMINAL && tab < 2 && button == 0 && x >= leftPos+13 && x < leftPos+151 && y >= topPos+LIST_Y) {
            int row = (int)(y-topPos-LIST_Y)/13;
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
        double px = (x - leftPos - MAP_X - panX) / mapScale();
        double py = (y - topPos - MAP_Y - panY) / mapScale();
        return page.points().reversed().stream()
            .filter(point -> Math.abs(px - point.x()) <= 37 && py >= point.y() - 10 && py <= point.y() + 12)
            .findFirst().orElse(null);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        syncSector();
        if (panning && button == 1) {
            panTo(panX + dx, panY + dy);
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
        if (tab != GRAPH_TAB || !insideMap(x, y) || drag != null || panning)
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
        if (button == 1 && panning) {
            panning = false;
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
        if (menu.mode != SupervisionMenu.TERMINAL && tab == GRAPH_TAB && drag == null) {
            var point = at(graphPage(), x, y);
            if (point != null) g.renderTooltip(font, Component.literal(GraphInk.display(point.node())), x, y);
        }
    }
}
