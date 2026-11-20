package fr.vengelis.industrialfacilities.access.client;

import fr.vengelis.industrialfacilities.access.ui.AccessMenu;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.frequency.FrequencyEncoding;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent.CatalogEntry;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent.CatalogWindow;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

final class FrequencyCataloguePanel {
    private static final int ROW_Y = 46, BUTTON_HEIGHT = 17, ACTION_WIDTH = 60;
    private static final int SCROLLBAR_WIDTH = 6, ROW_GAP = 2, SCROLLBAR_GAP = 3;
    private final Font font;
    private final AccessMenu menu;
    private final int panelLeft, listTop, panelWidth, listHeight;
    private final CatalogueRow[] rows;
    private final Button rename, remove;
    private final EditBox nameInput;
    private final Consumer<EditBox> focus;
    private boolean editing, renderedEditing, needsFocus;
    private CatalogEntry selected, renderedSelection;
    private CatalogWindow renderedWindow;
    private Boolean renderedVisibility;
    private int offset, renderedOffset;
    private boolean requestPending, dragging;
    private double grabOffset;

    FrequencyCataloguePanel(Font font, AccessMenu menu, int left, int top, int width, int actionsY,
                            Consumer<AbstractWidget> add, Consumer<EditBox> focus, FrequencyCataloguePanel previous) {
        this.font = font;
        this.menu = menu;
        this.focus = focus;
        panelLeft = left;
        listTop = top + ROW_Y;
        panelWidth = width;
        int editorY = actionsY - BUTTON_HEIGHT - ROW_GAP;
        int available = editorY - listTop - SCROLLBAR_GAP;
        rows = new CatalogueRow[Math.max(4, available / 24)];
        int pitch = Math.min(24, available / rows.length);
        listHeight = rows.length * pitch - ROW_GAP;
        int rowWidth = width - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
        for (int i = 0; i < rows.length; i++) {
            int row = i;
            rows[i] = new CatalogueRow(left, listTop + i * pitch, rowWidth, pitch - ROW_GAP,
                button -> select(rows[row].entry));
            add.accept(rows[i]);
        }

        int nameWidth = 124;
        nameInput = new EditBox(font, left + (width - nameWidth) / 2, editorY, nameWidth, 17, tr("catalogue_name_input"));
        nameInput.setMaxLength(FrequencyEncoding.NAME_LENGTH);
        nameInput.setHint(tr("name_short"));
        add.accept(nameInput);

        rename = TerminalGui.button(left, actionsY, ACTION_WIDTH, BUTTON_HEIGHT, tr("catalogue_rename"), button -> {
            if (selected == null) return;
            if (editing) saveName();
            else {
                nameInput.setValue(selected.name());
                editing = true;
                needsFocus = true;
                refresh(true);
            }
        });
        remove = TerminalGui.button(left + width - ACTION_WIDTH, actionsY, ACTION_WIDTH, BUTTON_HEIGHT,
            tr("catalogue_remove"), button -> {
                if (editing) {
                    endEditing();
                    refresh(true);
                } else if (selected != null) {
                    sendEdit(true, "");
                    selected = null;
                    refresh(true);
                }
            });
        add.accept(rename);
        add.accept(remove);
        if (previous != null) {
            offset = previous.offset;
            selected = previous.selected;
            editing = previous.editing;
            needsFocus = editing && previous.nameInput.isFocused();
            if (editing) nameInput.setValue(previous.nameInput.getValue());
        }
    }

    CatalogEntry selection() { return selected; }

    private void select(CatalogEntry entry) {
        if (entry == null) return;
        if (!sameChannel(entry, selected)) endEditing();
        selected = entry;
        refresh(true);
    }

    boolean keyPressed(int key) {
        if (!editing) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            endEditing();
            refresh(true);
            return true;
        }
        if (nameInput.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            saveName();
            return true;
        }
        return false;
    }

    private void saveName() {
        if (selected != null) sendEdit(false, nameInput.getValue());
        endEditing();
        refresh(true);
    }

    private void sendEdit(boolean remove, String name) {
        PacketDistributor.sendToServer(new FrequencyContent.EditCatalogue(menu.containerId, selected.number(),
            selected.privateFrequency(), remove, name));
    }

    private void endEditing() {
        editing = needsFocus = false;
        nameInput.setFocused(false);
    }

    private int maxOffset() {
        CatalogWindow window = menu.catalogue();
        return window == null ? 0 : Math.max(0, window.total() - rows.length);
    }

    private int thumbHeight() {
        return Math.max(8, listHeight * rows.length / menu.catalogue().total());
    }

    private int thumbY() {
        return (int) Math.round((double) offset * (listHeight - thumbHeight()) / maxOffset());
    }

    boolean mouseScrolled(double x, double y, double vertical) {
        if (vertical == 0 || maxOffset() == 0 || x < panelLeft || x >= panelLeft + panelWidth
                || y < listTop || y >= listTop + listHeight) return false;
        int rows = (int) Math.copySign(Math.ceil(Math.abs(vertical)), vertical);
        scrollTo(offset - rows);
        return true;
    }

    boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || maxOffset() == 0 || x < panelLeft + panelWidth - SCROLLBAR_WIDTH
                || x >= panelLeft + panelWidth || y < listTop || y >= listTop + listHeight) return false;
        dragging = true;
        double insideTrack = y - listTop;
        int thumbTop = thumbY();
        if (insideTrack >= thumbTop && insideTrack < thumbTop + thumbHeight())
            grabOffset = insideTrack - thumbTop;
        else {
            grabOffset = thumbHeight() / 2.0;
            dragThumb(y);
        }
        return true;
    }

    boolean mouseDragged(double y, int button) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        dragThumb(y);
        return true;
    }

    boolean mouseReleased(int button) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        dragging = false;
        return true;
    }

    private void dragThumb(double y) {
        if (maxOffset() == 0) return;
        double fraction = Math.clamp((y - listTop - grabOffset) / (listHeight - thumbHeight()), 0.0, 1.0);
        scrollTo((int) Math.round(fraction * maxOffset()));
    }

    private void scrollTo(int requested) {
        offset = Math.clamp(requested, 0, maxOffset());
        refresh(true);
    }

    void refresh(boolean show) {
        CatalogWindow window = menu.catalogue();
        if (window != renderedWindow || !show) requestPending = false;
        if (!show) dragging = false;
        offset = Math.clamp(offset, 0, maxOffset());
        if (show && window != null && !window.covers(offset, rows.length) && !requestPending) {
            requestPending = true;
            PacketDistributor.sendToServer(new FrequencyContent.BrowseCatalogue(menu.containerId, offset));
        }
        if (renderedVisibility != null && renderedVisibility == show && renderedWindow == window
                && renderedSelection == selected && renderedEditing == editing && renderedOffset == offset) {
            if (show && needsFocus) {
                needsFocus = false;
                focus.accept(nameInput);
            }
            return;
        }
        if (window != null && selected != null)
            selected = window.total() == 0 ? null : window.entries().stream()
                .filter(entry -> sameChannel(entry, selected)).findFirst().orElse(selected);
        if (selected == null) endEditing();
        renderedVisibility = show;
        renderedWindow = window;
        renderedOffset = offset;
        renderedSelection = selected;
        renderedEditing = editing;
        for (int i = 0; i < rows.length; i++) {
            CatalogueRow row = rows[i];
            CatalogEntry entry = window == null ? null : window.entryAt(offset + i);
            row.entry = entry;
            row.visible = show && entry != null;
            if (!row.visible) continue;
            String name = entry.name().isEmpty() ? tr("catalogue_unnamed").getString() : entry.name();
            boolean active = sameChannel(entry, selected);
            String mark = entry.privateFrequency() ? "◆" : "◇";
            row.setFrequency(mark + " " + entry.number(), name, active);
            row.setTooltip(Tooltip.create(Component.literal(name).append("\n")
                .append(Component.translatable(FrequencyContent.UI + (entry.privateFrequency() ? "private_chip" : "public_chip"), entry.number()))));
        }
        nameInput.visible = show && editing;
        rename.visible = remove.visible = show && selected != null;
        rename.setMessage(tr(editing ? "catalogue_save_name" : "catalogue_rename"));
        rename.setTooltip(editing ? null : Tooltip.create(tr("catalogue_rename_hint")));
        remove.setMessage(tr(editing ? "catalogue_cancel" : "catalogue_remove"));
        remove.setTooltip(editing ? null : Tooltip.create(tr("catalogue_remove_hint")));
    }

    void render(GuiGraphics graphics, int left) {
        graphics.drawString(font, tr("catalogue"), left, 34, TerminalGui.ACCENT, false);
        var window = menu.catalogue();
        if (window == null || window.total() == 0 || window.entryAt(offset) == null)
            graphics.drawWordWrap(font, tr(window != null && window.total() == 0 ? "catalogue_empty" : "catalogue_loading"),
                left, ROW_Y + 1, panelWidth, TerminalGui.MUTED);
        if (maxOffset() > 0) {
            int right = left + panelWidth;
            int x = right - SCROLLBAR_WIDTH;
            graphics.fill(x, ROW_Y, right, ROW_Y + listHeight, 0xFF151B20);
            int thumb = ROW_Y + thumbY();
            graphics.fill(x + 1, thumb, right - 1, thumb + thumbHeight(), dragging ? 0xFF7BD3DB : 0xFF608491);
        }
    }

    private static boolean sameChannel(CatalogEntry first, CatalogEntry second) {
        return second != null && first.number() == second.number() && first.privateFrequency() == second.privateFrequency();
    }

    private static Component tr(String key) { return Component.translatable(FrequencyContent.UI + key); }

    private final class CatalogueRow extends Button {
        private CatalogEntry entry;
        private String frequency = "", name = "";
        private boolean selected;

        CatalogueRow(int x, int y, int width, int height, OnPress action) {
            super(Button.builder(Component.empty(), action).bounds(x, y, width, height));
        }

        void setFrequency(String frequency, String name, boolean selected) {
            this.frequency = frequency;
            this.name = name;
            this.selected = selected;
            Component label = Component.literal(frequency + " · " + name);
            setMessage(selected ? label.copy().withStyle(ChatFormatting.AQUA) : label);
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xFF305466 : isHoveredOrFocused() ? 0xFF34414B : 0xFF202930;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            if (isFocused())
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF000000 | TerminalGui.ACCENT);
            int x = getX() + 6;
            int width = getWidth() - 12;
            int labelColor = selected ? TerminalGui.ACCENT : 0xDDE3E6;
            if (getHeight() >= 20) {
                int y = getY() + (getHeight() - font.lineHeight * 2) / 2;
                graphics.drawString(font, frequency, x, y, selected ? TerminalGui.ACCENT : TerminalGui.MUTED, false);
                graphics.drawString(font, fit(font, name, width), x, y + font.lineHeight, 0xDDE3E6, false);
            } else {
                int y = getY() + (getHeight() - font.lineHeight) / 2;
                graphics.drawString(font, fit(font, getMessage().getString(), width), x, y, labelColor, false);
            }
        }

        private static String fit(Font font, String text, int width) {
            if (font.width(text) <= width) return text;
            return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
        }
    }
}

