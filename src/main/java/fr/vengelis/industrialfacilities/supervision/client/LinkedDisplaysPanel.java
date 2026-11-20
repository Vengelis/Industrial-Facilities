package fr.vengelis.industrialfacilities.supervision.client;

import fr.vengelis.industrialfacilities.access.client.TerminalGui;
import fr.vengelis.industrialfacilities.supervision.NetworkView;
import fr.vengelis.industrialfacilities.supervision.SupervisionData;
import fr.vengelis.industrialfacilities.supervision.SupervisionMenu;
import fr.vengelis.industrialfacilities.supervision.SupervisionPackets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class LinkedDisplaysPanel {
    private static final int MAX_ROWS = 5, ROW_HEIGHT = 21, MARGIN = 14;
    private final SupervisionMenu menu;
    private final Font font;
    private final Button[] rows;
    private final UUID[] rowIds;
    private final int width, contentY, navY, listWidth, detailsX, detailsWidth, sectorY;
    private final boolean compact;
    private final EditBox name;
    private final Button rename, previousSector, nextSector, applySector, unlink;
    private int first;
    private SupervisionData.LinkedDisplay selected;
    private UUID selectedId;
    private String sector = "";
    private boolean shown;

    LinkedDisplaysPanel(SupervisionMenu menu, Font font, int left, int top, int width, int contentY, int navY,
                        int listWidth, int detailsX, Consumer<AbstractWidget> add) {
        this.menu = menu;
        this.font = font;
        this.width = width;
        this.contentY = contentY;
        this.navY = navY;
        this.listWidth = listWidth;
        this.detailsX = detailsX;
        this.detailsWidth = width - MARGIN - detailsX;
        compact = navY - contentY < 110;
        int count = Math.clamp((navY - contentY) / ROW_HEIGHT, 1, MAX_ROWS);
        rows = new Button[count];
        rowIds = new UUID[count];
        for (int row = 0; row < rows.length; row++) {
            int index = row;
            rows[row] = TerminalGui.button(left + MARGIN, top + contentY + row * ROW_HEIGHT, listWidth, 20,
                Component.empty(), ignored -> {
                    selectedId = rowIds[index];
                    refresh(true);
                });
            add.accept(rows[row]);
        }
        int nameY = contentY + (compact ? 1 : 43);
        int renameWidth = 68;
        name = new EditBox(font, left + detailsX, top + nameY, detailsWidth - renameWidth - 6, 18, tr("display_name"));
        name.setMaxLength(SupervisionData.MAX_NAME);
        name.setHint(tr("display_name"));
        name.setTooltip(Tooltip.create(tr("default_name_hint")));
        add.accept(name);
        rename = TerminalGui.button(left + width - MARGIN - renameWidth, top + nameY - 1, renameWidth, 20, tr("rename"),
            ignored -> send(SupervisionMenu.RENAME_DISPLAY, name.getValue(), ""));
        sectorY = contentY + (compact ? 23 : 66);
        previousSector = TerminalGui.button(left + detailsX, top + sectorY, 22, 20, Component.literal("‹"),
            ignored -> chooseSector(-1));
        nextSector = TerminalGui.button(left + width - MARGIN - 22, top + sectorY, 22, 20, Component.literal("›"),
            ignored -> chooseSector(1));
        int actionY = compact ? navY : contentY + 88;
        int actionWidth = detailsWidth - (compact ? 31 : 0);
        int applyWidth = Math.min(100, (actionWidth - 5) * 56 / 100);
        applySector = TerminalGui.button(left + detailsX, top + actionY, applyWidth, 20, tr("display_set_sector"),
            ignored -> send(SupervisionMenu.SELECT_DISPLAY_SECTOR, "", sector));
        unlink = TerminalGui.button(left + detailsX + applyWidth + 5, top + actionY, actionWidth - applyWidth - 5, 20, tr("display_unlink"),
            ignored -> send(SupervisionMenu.UNLINK_DISPLAY, "", ""));
        for (var button : new Button[]{rename, previousSector, nextSector, applySector, unlink}) add.accept(button);
        refresh(false);
    }

    private void send(int action, String value, String sector) {
        if (selected != null)
            PacketDistributor.sendToServer(new SupervisionPackets.Action(menu.containerId, action, value, sector, selected.id(), 0, 0, 0));
    }

    private void chooseSector(int direction) {
        if (menu.sectors.isEmpty()) return;
        int index = menu.sectors.indexOf(sector);
        sector = menu.sectors.get(Math.floorMod(index + direction, menu.sectors.size()));
        refresh(shown);
    }

    boolean hasPrevious() { return first > 0; }
    boolean hasNext() { return first + rows.length < menu.linkedDisplays.size(); }

    void page(int direction) {
        first += direction * rows.length;
        selectedId = null;
        refresh(shown);
    }

    void refresh(boolean show) {
        shown = show;
        if (!shown) {
            name.setFocused(false);
            for (var row : rows) row.visible = false;
            setControlsVisible(false);
            return;
        }
        var displays = menu.linkedDisplays;
        first = Math.clamp(first, 0, Math.max(0, displays.size() - 1) / rows.length * rows.length);
        var page = displays.subList(first, Math.min(first + rows.length, displays.size()));
        var display = page.stream().filter(row -> row.id().equals(selectedId)).findFirst().orElse(page.isEmpty() ? null : page.getFirst());
        selectedId = display == null ? null : display.id();
        if (display != null) {
            boolean different = selected == null || !selected.id().equals(display.id());
            if (different || !Objects.equals(selected.name(), display.name())) name.setValue(display.name());
            if (different || !Objects.equals(selected.sector(), display.sector()) || !menu.sectors.contains(sector)) sector = display.sector();
        }
        selected = display;
        for (int row = 0; row < rows.length; row++) {
            var button = rows[row];
            button.visible = row < page.size();
            rowIds[row] = button.visible ? page.get(row).id() : null;
            if (!button.visible) continue;
            var item = page.get(row);
            var label = (item.id().equals(selectedId) ? "› " : "") + label(item).getString();
            button.setMessage(Component.literal(font.plainSubstrByWidth(label, listWidth - 8)));
            button.setTooltip(Tooltip.create(label(item).copy().append("\n").append(location(item)).append("\n").append(status(item))));
        }
        setControlsVisible(selected != null);
        if (selected != null) {
            rename.active = !name.getValue().strip().equals(selected.name());
            previousSector.active = nextSector.active = menu.sectors.size() > 1;
            applySector.active = menu.sectors.contains(sector) && !selected.sector().equals(sector);
            unlink.visible = !selected.online();
        }
    }

    private void setControlsVisible(boolean visible) {
        name.visible = rename.visible = previousSector.visible = nextSector.visible = applySector.visible = unlink.visible = visible;
        if (!visible) name.setFocused(false);
    }

    void render(GuiGraphics g) {
        if (selected == null) {
            line(g, tr("display_links_empty"), MARGIN, contentY + 4, width - 2 * MARGIN, TerminalGui.MUTED);
        } else {
            if (!compact) {
                line(g, label(selected), detailsX, contentY + 2, detailsWidth, GraphInk.INK);
                line(g, location(selected), detailsX, contentY + 15, detailsWidth, TerminalGui.MUTED);
                var sectorLabel = tr("sector_title", selected.sector().isEmpty() ? "—" : selected.sector());
                line(g, status(selected).copy().append(" · ").append(sectorLabel), detailsX, contentY + 28, detailsWidth,
                    GraphInk.color(selected.online() ? "online" : "offline"));
            }
            line(g, tr("sector_title", sector.isEmpty() ? "—" : sector), detailsX + 28, sectorY + 6,
                detailsWidth - 54, GraphInk.INK);
        }
        if (!menu.displayStatus.isEmpty())
            line(g, tr(menu.displayStatus), MARGIN + 31, navY + 6, (compact ? detailsX : width - MARGIN - 26) - MARGIN - 35,
                TerminalGui.MUTED);
    }

    private void line(GuiGraphics g, Component text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color, false);
    }
    private static Component label(SupervisionData.LinkedDisplay display) {
        if (!display.name().isEmpty()) return Component.literal(display.name());
        String label = display.location() == null ? display.id().toString().substring(0, 8) : display.location().position().toShortString();
        return tr("display_default_name", label);
    }
    private static Component location(SupervisionData.LinkedDisplay display) {
        var location = display.location();
        if (location == null) return tr("display_location_unknown");
        var dimension = location.dimension();
        String dimensionName = dimension.getNamespace().equals("minecraft") ? dimension.getPath() : dimension.toString();
        return Component.literal(location.position().toShortString() + " · " + dimensionName);
    }
    private static Component status(SupervisionData.LinkedDisplay display) {
        return tr(display.online() ? "state.online" : "state.offline");
    }
    private static Component tr(String key, Object... arguments) { return Component.translatable(NetworkView.UI + key, arguments); }
}
