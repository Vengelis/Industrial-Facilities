package fr.vengelis.industrialfacilities.access.client;

import fr.vengelis.industrialfacilities.access.TerminalDevices;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

final class TerminalLinkList {
    private static final int PAGE_SIZE = 4;
    private static final int ROW_TOP = 44, ROW_HEIGHT = 26;
    private static final int INK = 0xDDE3E6;
    private final int width, height;
    private final List<Button> unlink = new ArrayList<>();
    private final Button back, previous, next;
    private List<TerminalDevices.Link> links = List.of();
    private boolean shown, editable;
    private int page;

    TerminalLinkList(int left, int top, int width, int height, Consumer<Button> add,
                     Consumer<TerminalDevices.Link> remove, Runnable close) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int row = i;
            var button = TerminalGui.button(left + width - 79, top + ROW_TOP + i * ROW_HEIGHT + 2, 65,
                TerminalGui.ACTION_HEIGHT, tr("unlink_device"), ignored -> {
                    var link = at(row);
                    if (shown && editable && link != null && !link.available()) remove.accept(link);
                });
            unlink.add(button);
            add.accept(button);
        }
        back = TerminalGui.button(left + 14, top + 153, 70, 20, tr("devices_back"), ignored -> close.run());
        previous = TerminalGui.button(left + width - 110, top + 153, 25, 20, Component.literal("‹"),
            ignored -> { page--; refresh(); });
        next = TerminalGui.button(left + width - 39, top + 153, 25, 20, Component.literal("›"),
            ignored -> { page++; refresh(); });
        add.accept(back);
        add.accept(previous);
        add.accept(next);
        refresh();
    }

    void update(List<TerminalDevices.Link> devices, boolean shown, boolean editable) {
        if (links == devices && this.shown == shown && this.editable == editable) return;
        this.links = devices;
        this.shown = shown;
        this.editable = editable;
        refresh();
    }

    private void refresh() {
        page = Math.clamp(page, 0, pages() - 1);
        for (int i = 0; i < unlink.size(); i++) {
            var link = at(i);
            var button = unlink.get(i);
            button.visible = shown && editable && link != null && !link.available();
            button.active = button.visible;
            button.setTooltip(button.visible ? Tooltip.create(Component.literal(link.name().isEmpty()
                ? link.position().toShortString() : link.name() + "\n" + link.position().toShortString())) : null);
        }
        back.visible = shown;
        previous.visible = next.visible = shown && pages() > 1;
        previous.active = page > 0;
        next.active = page + 1 < pages();
    }

    private TerminalDevices.Link at(int row) {
        int index = page * PAGE_SIZE + row;
        return index < links.size() ? links.get(index) : null;
    }

    private int pages() {
        return Math.max(1, (links.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    void renderBackground(GuiGraphics g, int left, int top) {
        for (int i = 0; i < PAGE_SIZE && at(i) != null; i++) {
            int y = top + ROW_TOP + i * ROW_HEIGHT;
            g.fill(left + 14, y, left + width - 14, y + ROW_HEIGHT - 2, 0xFF1B2026);
        }
    }

    void render(GuiGraphics g, Font font, String status) {
        g.drawString(font, tr("devices"), 14, 32, TerminalGui.ACCENT, false);
        if (links.isEmpty()) g.drawString(font, tr("devices_empty"), 14, 59, TerminalGui.MUTED, false);
        for (int i = 0; i < PAGE_SIZE; i++) {
            var link = at(i);
            if (link == null) break;
            int y = ROW_TOP + i * ROW_HEIGHT;
            String label = switch (link.kind()) {
                case DOOR -> "device_door";
                case TERMINAL -> "device_terminal";
                case EMITTER -> "device_emitter";
            };
            String location = tr(label, link.position().toShortString()).getString();
            String state = tr(link.available() ? "device_online" : "device_offline").getString();
            String title = link.name().isEmpty() ? location : link.name();
            String detail = link.name().isEmpty() ? state : state + " · " + location;
            int textWidth = unlink.get(i).visible ? width - 104 : width - 40;
            g.drawString(font, font.plainSubstrByWidth(title, textWidth), 20, y + 4, INK, false);
            g.drawString(font, font.plainSubstrByWidth(detail, textWidth), 20, y + 14,
                link.available() ? TerminalGui.ACCENT : 0xD8AA92, false);
        }
        if (pages() > 1) g.drawCenteredString(font, (page + 1) + "/" + pages(), width - 62, 159, INK);
        var message = status.startsWith(TerminalDevices.UI) ? Component.translatable(status) : tr("devices_hint");
        g.drawString(font, font.plainSubstrByWidth(message.getString(), width - 28), 14, height - 21, INK, false);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(TerminalDevices.UI + key, args);
    }
}
