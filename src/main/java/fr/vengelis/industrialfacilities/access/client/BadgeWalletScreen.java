package fr.vengelis.industrialfacilities.access.client;

import fr.vengelis.industrialfacilities.access.ui.BadgeWalletMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BadgeWalletScreen extends AbstractContainerScreen<BadgeWalletMenu> {
    public BadgeWalletScreen(BadgeWalletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 164;
        inventoryLabelY = 71;
        titleLabelX = 8;
        titleLabelY = 7;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, 0xFF8A7257);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF242932);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 19, 0xFF503F32);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF62666F);
            graphics.fill(x, y, x + 16, y + 16, 0xFF15191E);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFEBD2, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE0E3ED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
