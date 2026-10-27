package fr.vengelis.industrialfacilities.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import fr.vengelis.industrialfacilities.fabricator.*;
import fr.vengelis.industrialfacilities.fabricator.FabricatorCategoryConfig.Category;
import fr.vengelis.industrialfacilities.network.FabricatorCraftMessage;
import fr.vengelis.industrialfacilities.world.inventory.FabricatorGUIMenu;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

public class FabricatorGUIScreen extends AbstractContainerScreen<FabricatorGUIMenu> {
    private static final int TEXT = 0xffd6dfe0, MUTED = 0xff829698, AMBER = 0xffc6a567, CYAN = 0xff87b7b8;
    private FabricatorLayout layout = FabricatorLayout.forScreen(640, 360);
    private EditBox search;
    private ResourceLocation selected;
    private String selectedCategory = "*", draft = "";
    private int page, preview, batch = 1, materialScroll, categoryStart;
    private Button craft, previous, next, categoryPrevious, categoryNext;
    private final List<Button> recipeButtons = new ArrayList<>(), categoryButtons = new ArrayList<>();
    private List<RecipeHolder<FabricatorRecipe>> shown = List.of();
    private List<Category> categories = List.of();

    public FabricatorGUIScreen(FabricatorGUIMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = layout.width(); imageHeight = layout.height();
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.industrial_facilities.fabricator." + key, args); }
    private RecipeHolder<FabricatorRecipe> selection() {
        return shown.stream().filter(holder -> holder.id().equals(selected)).findFirst().orElse(null);
    }
    private final class SteelButton extends Button {
        private final java.util.function.BooleanSupplier selectedState;
        SteelButton(int x, int y, int w, int h, Component text, OnPress action, java.util.function.BooleanSupplier selectedState) {
            super(leftPos + x, topPos + y, w, h, text, action, DEFAULT_NARRATION);
            this.selectedState = selectedState;
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            boolean chosen = selectedState.getAsBoolean(), hovered = active && isHoveredOrFocused();
            g.fill(x, y, x + width, y + height, hovered ? 0xff647174 : 0xff465256);
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                !active ? 0xff1d2528 : hovered ? 0xff344044 : chosen ? 0xff35372f : 0xff283236);
            if (chosen) g.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, AMBER);
            String caption = font.plainSubstrByWidth(getMessage().getString(), width - 2);
            g.drawString(font, caption, x + (width - font.width(caption)) / 2, y + (height - 8) / 2,
                !active ? 0xff637073 : chosen ? AMBER : TEXT, false);
        }
    }
    private Button button(int x, int y, int w, int h, Component text, Button.OnPress action, java.util.function.BooleanSupplier state) {
        return addRenderableWidget(new SteelButton(x, y, w, h, text, action, state));
    }
    @Override protected void init() {
        layout = FabricatorLayout.forScreen(width, height);
        imageWidth = layout.width(); imageHeight = layout.height();
        super.init(); menu.layoutClientSlots(layout);
        recipeButtons.clear(); categoryButtons.clear();
        int tx = layout.tabsX(), tw = layout.tabsWidth(), ty = layout.tabsY();
        int arrowWidth = layout.large() ? 14 : 12, tabWidth = (tw - 2 * arrowWidth - 8) / 3;
        int tabHeight = layout.large() ? 18 : 14;
        categoryPrevious = button(tx, ty, arrowWidth, tabHeight, Component.literal("<"), b -> { categoryStart--; refreshCatalogue(); }, () -> false);
        categoryNext = button(tx + tw - arrowWidth, ty, arrowWidth, tabHeight, Component.literal(">"), b -> { categoryStart++; refreshCatalogue(); }, () -> false);
        for (int index = 0; index < 3; index++) {
            int cell = index;
            categoryButtons.add(button(tx + arrowWidth + 3 + index * (tabWidth + 1), ty, tabWidth, tabHeight, Component.empty(), b -> {
                if (categoryStart + cell >= categories.size()) return;
                selectedCategory = categories.get(categoryStart + cell).id(); page = 0; materialScroll = 0; refreshCatalogue();
            }, () -> categoryStart + cell < categories.size() && categories.get(categoryStart + cell).id().equals(selectedCategory)));
        }
        search = new EditBox(font, leftPos + tx + 3, topPos + layout.searchY() + 3, tw - 6, 12, tr("search"));
        search.setBordered(false); search.setTextColor(TEXT); search.setHint(tr("search")); search.setMaxLength(64); search.setValue(draft);
        search.setResponder(value -> { draft = value; page = 0; refreshCatalogue(); }); addRenderableWidget(search);
        for (int index = 0; index < layout.pageSize(); index++) {
            int cell = index;
            recipeButtons.add(button(tx + index % layout.columns() * layout.pitch(), layout.gridY() + index / layout.columns() * layout.pitch(),
                layout.tileSize(), layout.tileSize(), Component.empty(), b -> {
                    int at = page * layout.pageSize() + cell;
                    if (at < shown.size()) { selected = shown.get(at).id(); materialScroll = 0; }
                }, () -> {
                    int at = page * layout.pageSize() + cell;
                    return at < shown.size() && shown.get(at).id().equals(selected);
                }));
        }
        previous = button(tx, layout.footerY(), 23, 12, Component.literal("<"), b -> { page--; refreshCatalogue(); }, () -> false);
        next = button(tx + tw - 23, layout.footerY(), 23, 12, Component.literal(">"), b -> { page++; refreshCatalogue(); }, () -> false);
        int dx = layout.detailX(), dw = layout.detailWidth(), modeWidth = (dw - 4) / 3;
        String[] modes = {"cube", "line", "wall"};
        for (int i = 0; i < 3; i++) {
            int mode = i;
            button(dx + i * (modeWidth + 2), layout.modesY(), modeWidth, layout.large() ? 18 : 14, tr(modes[i]), b -> preview = mode, () -> preview == mode);
        }
        int[] amounts = {1, 16, 64};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i], pitch = layout.large() ? 30 : 24;
            button(dx + i * pitch, layout.actionY(), pitch - 2, layout.large() ? 18 : 16, Component.literal("x" + amount), b -> batch = amount, () -> batch == amount);
        }
        int craftWidth = layout.large() ? 72 : 64;
        craft = button(dx + dw - craftWidth, layout.actionY(), craftWidth, layout.large() ? 18 : 16, tr("craft"), b -> {
            var holder = selection();
            if (holder != null && menu.status(holder.value(), batch) == FabricatorCrafting.Status.READY)
                PacketDistributor.sendToServer(new FabricatorCraftMessage(menu.containerId, holder.id(), batch));
        }, () -> false);
        refreshCatalogue();
    }
    private void refreshCatalogue() {
        var all = menu.recipes();
        categories = FabricatorCategoryResources.current().tabs(all.stream().map(holder -> holder.value().category()).toList());
        if (categories.stream().noneMatch(category -> category.id().equals(selectedCategory))) selectedCategory = "*";
        categoryStart = Math.max(0, Math.min(categoryStart, Math.max(0, categories.size() - 3)));
        String query = draft.strip().toLowerCase(Locale.ROOT);
        shown = all.stream().filter(holder -> selectedCategory.equals("*") || holder.value().category().equals(selectedCategory))
            .filter(holder -> query.isEmpty() || holder.value().output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || holder.id().toString().toLowerCase(Locale.ROOT).contains(query)).toList();
        page = Math.max(0, Math.min(page, Math.max(0, (shown.size() - 1) / layout.pageSize())));
        if (selection() == null) { selected = shown.isEmpty() ? null : shown.getFirst().id(); materialScroll = 0; }
        for (int index = 0; index < recipeButtons.size(); index++) recipeButtons.get(index).visible = page * layout.pageSize() + index < shown.size();
        for (int index = 0; index < categoryButtons.size(); index++) {
            Button button = categoryButtons.get(index); button.visible = categoryStart + index < categories.size();
            if (button.visible) button.setMessage(Component.literal(categories.get(categoryStart + index).label()));
        }
        if (categoryPrevious != null) categoryPrevious.active = categoryStart > 0;
        if (categoryNext != null) categoryNext.active = categoryStart + 3 < categories.size();
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = (page + 1) * layout.pageSize() < shown.size();
        if (craft != null) craft.active = selection() != null && menu.status(selection().value(), batch) == FabricatorCrafting.Status.READY;
    }
    @Override protected void containerTick() { super.containerTick(); refreshCatalogue(); }
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, 0xff4a5658);
        g.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + w - 1, topPos + y + h - 1, 0xff172125);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff0d1316);
        g.fillGradient(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xff354045, 0xff242d31);
        g.fill(leftPos + 10, topPos + 24, leftPos + imageWidth - 10, topPos + 25, 0xff526163);
        g.fill(leftPos + 11, topPos + 9, leftPos + 14, topPos + 19, AMBER);
        for (int x : new int[]{5, imageWidth - 8}) for (int y : new int[]{5, imageHeight - 8}) {
            g.fill(leftPos + x, topPos + y, leftPos + x + 3, topPos + y + 3, 0xff7c8787);
            g.fill(leftPos + x + 1, topPos + y, leftPos + x + 2, topPos + y + 3, 0xff283234);
        }
        panel(g, layout.catalogueX(), 28, layout.catalogueWidth(), layout.large() ? 168 : 114);
        g.fill(leftPos + layout.tabsX(), topPos + layout.searchY(), leftPos + layout.tabsX() + layout.tabsWidth(), topPos + layout.searchY() + (layout.large() ? 17 : 14), 0xff283539);
        int dx = layout.detailX(), dw = layout.detailWidth(), py = layout.previewY(), ph = layout.previewHeight();
        panel(g, dx, py, dw, ph);
        for (int x = dx + 6; x < dx + dw - 2; x += 10) g.vLine(leftPos + x, topPos + py + 14, topPos + py + ph - 3, 0xff253438);
        for (int y = py + 16; y < py + ph - 1; y += 10) g.hLine(leftPos + dx + 2, leftPos + dx + dw - 3, topPos + y, 0xff253438);
        panel(g, dx, layout.materialsY(), dw, layout.materialsHeight());
        panel(g, layout.inventoryX() - 4, layout.inventoryY() - 4, 170, layout.hotbarY() - layout.inventoryY() + 24);
        for (Slot slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff536164);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff1b2529);
        }
        for (int x = 14; x < layout.inventoryX() - 12; x += 12) {
            g.fill(leftPos + x, topPos + imageHeight - 15, leftPos + x + 6, topPos + imageHeight - 11, AMBER);
            g.fill(leftPos + x + 6, topPos + imageHeight - 15, leftPos + x + 12, topPos + imageHeight - 11, 0xff1b2428);
        }
        var holder = selection(); if (holder != null) renderPreview(g, holder.value().output());
    }
    private void renderPreview(GuiGraphics g, ItemStack output) {
        int columns = preview == 0 ? 1 : 3, rows = preview == 2 ? 3 : 1;
        int dx = layout.detailX(), py = layout.previewY(), dw = layout.detailWidth(), ph = layout.previewHeight();
        g.flush(); g.enableScissor(leftPos + dx + 1, topPos + py + 13, leftPos + dx + dw - 1, topPos + py + ph - 1);
        var pose = g.pose(); pose.pushPose();
        pose.translate(leftPos + dx + dw / 2f, topPos + py + 13 + (ph - 13) / 2f, 150);
        float scale = layout.large() ? (preview == 0 ? 52 : preview == 1 ? 37 : 23) : (preview == 0 ? 25 : preview == 1 ? 22 : 10);
        pose.scale(scale, -scale, scale); pose.mulPose(Axis.XP.rotationDegrees(18)); pose.mulPose(Axis.YP.rotationDegrees(135));
        if (output.getItem() instanceof BlockItem block) {
            Lighting.setupForEntityInInventory(); var buffers = minecraft.renderBuffers().bufferSource();
            for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
                pose.pushPose(); pose.translate(col - columns / 2f, rows / 2f - row - 1, -.5f);
                minecraft.getBlockRenderer().renderSingleBlock(block.getBlock().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch(); Lighting.setupFor3DItems();
        }
        pose.popPose();
        if (!(output.getItem() instanceof BlockItem)) g.renderItem(output, leftPos + dx + dw / 2 - 8, topPos + py + ph / 2 - 2);
        g.disableScissor();
    }
    @Override protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x187e8d91);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "FABRICATOR", 20, 10, TEXT, false);
        String categoryName = categories.stream().filter(category -> category.id().equals(selectedCategory)).map(Category::label).findFirst().orElse("");
        g.drawString(font, font.plainSubstrByWidth(categoryName + " / " + shown.size(), imageWidth - 122), 110, 11, MUTED, false);
        g.drawString(font, tr("inventory"), layout.inventoryX(), layout.inventoryY() - 12, MUTED, false);
        String pageText = (page + 1) + " / " + Math.max(1, (shown.size() + layout.pageSize() - 1) / layout.pageSize());
        g.drawString(font, pageText, layout.tabsX() + (layout.tabsWidth() - font.width(pageText)) / 2, layout.footerY() + 2, MUTED, false);
        for (int index = 0; index < layout.pageSize() && page * layout.pageSize() + index < shown.size(); index++) {
            var recipe = shown.get(page * layout.pageSize() + index).value();
            int x = layout.tabsX() + index % layout.columns() * layout.pitch(), y = layout.gridY() + index / layout.columns() * layout.pitch();
            int inset = (layout.tileSize() - 16) / 2; g.renderItem(recipe.output(), x + inset, y + inset);
            int status = menu.status(recipe, 1) == FabricatorCrafting.Status.READY ? 0xff82aa8a : 0xff59656a;
            g.fill(x + layout.tileSize() - 5, y + layout.tileSize() - 5, x + layout.tileSize() - 3, y + layout.tileSize() - 3, status);
        }
        if (shown.isEmpty()) g.drawString(font, tr("no_recipes"), layout.tabsX() + 8, layout.gridY() + 15, MUTED, false);
        var holder = selection(); craft.active = holder != null && menu.status(holder.value(), batch) == FabricatorCrafting.Status.READY;
        if (holder == null) return;
        var recipe = holder.value(); int dx = layout.detailX(), dw = layout.detailWidth();
        g.drawString(font, font.plainSubstrByWidth(recipe.output().getHoverName().getString(), dw - 8), dx + 4, layout.previewY() + 3, CYAN, false);
        var materials = new ArrayList<>(recipe.requirements().entrySet());
        materialScroll = Math.max(0, Math.min(materialScroll, Math.max(0, materials.size() - layout.materialRows())));
        for (int row = 0; row < layout.materialRows() && materialScroll + row < materials.size(); row++) {
            var material = materials.get(materialScroll + row);
            int needed = material.getValue() * batch, available = FabricatorCrafting.count(menu.inventory, material.getKey());
            int y = layout.materialsY() + (layout.large() ? 5 : 3) + row * layout.materialPitch(); float scale = layout.large() ? .75f : .625f;
            g.pose().pushPose(); g.pose().translate(dx + 4, y - 1, 0); g.pose().scale(scale, scale, 1);
            g.renderItem(new ItemStack(material.getKey()), 0, 0); g.pose().popPose();
            String amount = available + "/" + needed; int amountX = dx + dw - 4 - font.width(amount), nameX = dx + (layout.large() ? 19 : 17);
            String name = font.plainSubstrByWidth(new ItemStack(material.getKey()).getHoverName().getString(), Math.max(8, amountX - nameX - 6));
            g.drawString(font, name, nameX, y, MUTED, false); g.drawString(font, amount, amountX, y, available >= needed ? 0xff8bb195 : 0xffd49a83, false);
        }
        var status = menu.status(recipe, batch);
        g.drawString(font, tr(status == FabricatorCrafting.Status.READY ? "ready" : status == FabricatorCrafting.Status.INVENTORY_FULL ? "full" : "missing"),
            layout.inventoryX() + 180, layout.large() ? 230 : 146, status == FabricatorCrafting.Status.READY ? CYAN : 0xffd49a83, false);
        float outputScale = layout.large() ? 2.5f : 2; int centerX = layout.inventoryX() / 2, outputY = layout.inventoryY() + 9;
        g.pose().pushPose(); g.pose().translate(centerX - 8 * outputScale, outputY, 0); g.pose().scale(outputScale, outputScale, 1);
        g.renderItem(recipe.output(), 0, 0); g.pose().popPose();
        Component caption = tr("output", recipe.output().getCount() * batch);
        g.drawString(font, caption, centerX - font.width(caption) / 2, outputY + (int)(16 * outputScale) + 6, AMBER, false);
        g.drawString(font, tr("no_pattern"), layout.inventoryX() + 180, imageHeight - 19, MUTED, false);
    }
    private void flatTooltip(GuiGraphics g, List<Component> lines, int mouseX, int mouseY) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(font.split(line, Math.min(260, width - 20)));
        int w = wrapped.stream().mapToInt(font::width).max().orElse(0) + 10, h = wrapped.size() * 11 + 7;
        int x = Math.max(4, Math.min(mouseX + 10, width - w - 4)), y = mouseY + 12;
        if (y + h > height - 4) y = Math.max(4, mouseY - h - 6);
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        g.fill(x, y, x + w, y + h, 0xff5e6a6e); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xfa1c2529);
        for (int i = 0; i < wrapped.size(); i++) g.drawString(font, wrapped.get(i), x + 5, y + 4 + i * 11, TEXT, false);
        g.pose().popPose();
    }
    @Override protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.hasItem()) flatTooltip(g, getTooltipFromContainerItem(hoveredSlot.getItem()), mouseX, mouseY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY);
        for (int index = 0; index < categoryButtons.size(); index++) {
            Button button = categoryButtons.get(index);
            if (button.visible && button.isMouseOver(mouseX, mouseY)) flatTooltip(g, List.of(Component.literal(categories.get(categoryStart + index).label())), mouseX, mouseY);
        }
        for (int index = 0; index < recipeButtons.size(); index++) {
            Button button = recipeButtons.get(index); int at = page * layout.pageSize() + index;
            if (button.visible && button.isMouseOver(mouseX, mouseY) && at < shown.size()) {
                var recipe = shown.get(at).value(); flatTooltip(g, List.of(recipe.output().getHoverName(), tr("output", recipe.output().getCount())), mouseX, mouseY);
            }
        }
        var holder = selection();
        if (holder != null && insideMaterials(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>(); lines.add(tr("materials"));
            for (var material : holder.value().requirements().entrySet()) lines.add(Component.literal(
                new ItemStack(material.getKey()).getHoverName().getString() + " : " + FabricatorCrafting.count(menu.inventory, material.getKey()) + "/" + material.getValue() * batch));
            if (holder.value().requirements().size() > layout.materialRows()) lines.add(tr("scroll")); flatTooltip(g, lines, mouseX, mouseY);
        }
    }
    private boolean insideMaterials(double x, double y) {
        return x >= leftPos + layout.detailX() && x < leftPos + layout.detailX() + layout.detailWidth()
            && y >= topPos + layout.materialsY() && y < topPos + layout.materialsY() + layout.materialsHeight();
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if (insideMaterials(x, y)) { materialScroll += vertical < 0 ? 1 : -1; return true; }
        if (x >= leftPos + layout.tabsX() && x < leftPos + layout.tabsX() + layout.tabsWidth()
                && y >= topPos + layout.tabsY() && y < topPos + layout.tabsY() + (layout.large() ? 18 : 14)) {
            categoryStart += vertical < 0 ? 1 : -1; refreshCatalogue(); return true;
        }
        if (x >= leftPos + layout.catalogueX() && x < leftPos + layout.catalogueX() + layout.catalogueWidth()
                && y >= topPos + layout.gridY() && y < topPos + layout.footerY() + 14) {
            page += vertical < 0 ? 1 : -1; refreshCatalogue(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        if (search.isFocused()) return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
