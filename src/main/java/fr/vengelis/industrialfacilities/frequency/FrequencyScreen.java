package fr.vengelis.industrialfacilities.frequency;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.client.TerminalGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID, value = Dist.CLIENT)
public final class FrequencyScreen extends AbstractContainerScreen<FrequencyMenu> {
    private static final int COLUMN_WIDTH = 110, FIRST_ROW_Y = 82;
    private final List<Button> modes = new ArrayList<>();
    private boolean compact;

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(FrequencyContent.MENU, FrequencyScreen::new);
    }

    public FrequencyScreen(FrequencyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = FrequencyMenu.WIDTH;
        imageHeight = FrequencyMenu.HEIGHT;
        inventoryLabelX = 41;
        inventoryLabelY = FrequencyMenu.INVENTORY_Y - 12;
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(FrequencyContent.UI + key, args);
    }

    @Override protected void init() {
        compact = height < FrequencyMenu.HEIGHT + 12;
        imageHeight = compact ? 234 : FrequencyMenu.HEIGHT;
        int inventoryY = compact ? 150 : FrequencyMenu.INVENTORY_Y;
        menu.layoutInventory(inventoryY);
        inventoryLabelY = inventoryY - 12;
        super.init();
        modes.clear();
        for (int slot = 0; slot < menu.deviceSlots; slot++) {
            int index = slot, buttonWidth = modeWidth();
            var button = addRenderableWidget(TerminalGui.button(leftPos + rowX(slot) + rowWidth() - buttonWidth,
                topPos + rowY(slot) - (compact ? 2 : 4), buttonWidth, compact ? 12 : 18, Component.empty(),
                ignored -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index)));
            modes.add(button);
        }
        refreshModes();
    }

    @Override protected void containerTick() { super.containerTick(); refreshModes(); }

    private void refreshModes() {
        for (int slot = 0; slot < modes.size(); slot++) {
            var stack = menu.getSlot(slot).getItem();
            var button = modes.get(slot);
            button.active = FrequencyChip.read(stack) != null;
            var mode = FrequencyChip.mode(stack);
            button.setMessage(Component.literal(mode.shortName));
            button.setTooltip(Tooltip.create(Component.empty().append(mode.label()).append("\n").append(mode.hint())
                .append("\n").append(label("mode_cycle_hint"))));
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        TerminalGui.panel(graphics, x, y, imageWidth, imageHeight, TerminalGui.TEAL_HEADER);
        for (var slot : menu.slots) TerminalGui.slot(graphics, x + slot.x, y + slot.y);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 8, 0xffe5ecf0, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xffc7d2da, false);
        if (FrequencyEncoding.accepts(0, menu.getCarried()))
            graphics.drawWordWrap(font, label("blank_destination"), 10, 31, imageWidth - 20, 0xffb4c6d0);
        else graphics.drawCenteredString(font, label("insert"), imageWidth / 2, 35, 0xffb4c6d0);
        for (int slot = 0; slot < menu.deviceSlots; slot++) {
            Component channel = channelLabel(slot);
            if (channel == null) continue;
            String line = channel.getString();
            int width = channelLabelWidth();
            if (font.width(line) > width) line = font.plainSubstrByWidth(line, width - font.width("…")) + "…";
            int x = rowX(slot);
            graphics.drawString(font, line, x, rowY(slot), TerminalGui.ACCENT, false);
        }
    }

    private static int rowX(int slot) { return 10 + (slot / 4) * 116; }
    private int rowY(int slot) { return FIRST_ROW_Y + (slot % 4) * (compact ? 13 : 22); }
    private int rowWidth() { return menu.deviceSlots <= FrequencyEndpoint.TERMINAL_SLOTS ? imageWidth - 20 : COLUMN_WIDTH; }
    private int modeWidth() { return menu.deviceSlots <= FrequencyEndpoint.TERMINAL_SLOTS ? 64 : 40; }
    private int channelLabelWidth() { return rowWidth() - modeWidth() - 5; }

    Component channelLabel(int slot) {
        var stack = menu.getSlot(slot).getItem();
        var channel = FrequencyChip.read(stack);
        if (channel == null) return null;
        String name = FrequencyContent.displayName(stack, channel);
        return name.isEmpty() ? label("unnamed_channel", slot + 1, Integer.toString(channel.number()))
            : label("named_channel", slot + 1, Integer.toString(channel.number()), name);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
        super.render(graphics, mouseX, mouseY, ticks);
        renderTooltip(graphics, mouseX, mouseY);
        for (int slot = 0; slot < menu.deviceSlots; slot++) {
            Component channel = channelLabel(slot);
            if (channel == null) continue;
            int width = channelLabelWidth();
            int x = leftPos + rowX(slot), y = topPos + rowY(slot);
            if (font.width(channel) > width && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12)
                graphics.renderTooltip(font, channel, mouseX, mouseY);
        }
    }
}

