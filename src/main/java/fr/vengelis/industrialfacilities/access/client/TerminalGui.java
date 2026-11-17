package fr.vengelis.industrialfacilities.access.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared panel and action controls for the industrial terminals. */
public final class TerminalGui {
    public static final int ACTION_HEIGHT = 20;
    public static final int TEAL_HEADER = 0xFF285B66;
    public static final int MUTED = 0x97A6AE;
    public static final int ACCENT = 0x7BD3DB;

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress action) {
        return Button.builder(label, action).bounds(x, y, width, height).build();
    }

    public static boolean handleInventoryKey(GuiEventListener focused, int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || !(focused instanceof EditBox input) || !input.canConsumeInput()) return false;
        if (!Minecraft.getInstance().options.keyInventory.isActiveAndMatches(InputConstants.getKey(key, scan))) return false;

        // Keep editing commands such as paste. Printable characters arrive separately through charTyped.
        input.keyPressed(key, scan, modifiers);
        return true;
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int header) {
        graphics.fill(x-1, y-1, x+width+1, y+height+1, 0xFF6D777E);
        graphics.fill(x, y, x+width, y+height, 0xFF1B2026);
        graphics.fill(x, y, x+width, y+26, header);
        graphics.fill(x+6, y+29, x+width-6, y+height-6, 0xFF242B32);
    }

    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x-1, y-1, x+17, y+17, 0xFF59636A);
        graphics.fill(x, y, x+16, y+16, 0xFF11171B);
    }

    private TerminalGui() {}
}
