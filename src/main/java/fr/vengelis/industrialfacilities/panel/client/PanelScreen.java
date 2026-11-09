package fr.vengelis.industrialfacilities.panel.client;

import fr.vengelis.industrialfacilities.panel.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private EditBox text;
    private boolean right;
    private Button left, rightButton;
    public PanelScreen(PanelMenu m, Inventory inv, Component title) {
        super(m, inv, title); imageWidth=310; imageHeight=232; right=m.toRight;
    }
    public static Component tr(String key, Object... args) { return Component.translatable("gui.industrial_facilities.panel."+key, args); }
    @Override protected void init() {
        String saved=text == null ? menu.text : text.getValue();
        super.init();
        text = addRenderableWidget(new EditBox(font, leftPos+16, topPos+106, 278, 20, tr("text")));
        text.setMaxLength(PanelBlockEntity.MAX_TEXT); text.setValue(saved); text.setTextColor(0xEAEEB4);
        left = addRenderableWidget(Button.builder(tr("left"), b -> {right=false; refresh();}).bounds(leftPos+16,topPos+150,136,20).build());
        rightButton = addRenderableWidget(Button.builder(tr("right"), b -> {right=true; refresh();}).bounds(leftPos+158,topPos+150,136,20).build());
        addRenderableWidget(Button.builder(tr("cancel"), b -> onClose()).bounds(leftPos+16,topPos+198,134,20).build());
        var save = addRenderableWidget(Button.builder(tr("save"), b -> save()).bounds(leftPos+160,topPos+198,134,20).build());
        save.active=menu.valid; refresh(); setInitialFocus(text);
    }
    private void refresh() { left.active=right; rightButton.active=!right; }
    private void save() {
        if (menu.valid) PacketDistributor.sendToServer(new PanelSettings(menu.containerId, text.getValue(), right));
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key==256) { onClose(); return true; }
        if (key==257 || key==335) { save(); return true; }
        // Inventory key must remain ordinary text input while editing the message.
        if (text.isFocused()) return text.keyPressed(key,scan,modifiers) || text.canConsumeInput() || super.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int l=leftPos,t=topPos;
        g.fill(l-2,t-2,l+312,t+234,0xFF85834A); g.fill(l,t,l+310,t+232,0xFF292C1E);
        g.fill(l+3,t+3,l+307,t+28,0xFF484A2B);
        for(int x : new int[]{6,300}) for(int y : new int[]{6,222}) { g.fill(l+x,t+y,l+x+4,t+y+4,0xFF11140D); g.fill(l+x+1,t+y+1,l+x+3,t+y+2,0xFF727343); }
        g.fill(l+14,t+39,l+296,t+87,0xFF0A0E09);
        if (text != null) {
            var led=new LedText(text.getValue());
            long ticks=minecraft.level==null?0:minecraft.level.getGameTime();
            for(int y=0;y<16;y++) for(int x=0;x<92;x++) {
                int xx=l+17+x*3, yy=t+39+y*3;
                g.fill(xx,yy,xx+2,yy+2,led.lit(x,y,1,ticks,right)?0xFFEEFF3D:0xFF202619);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,16,11,0xECECC9,false);
        g.drawString(font,tr("text"),16,94,0xD4D6AD,false);
        g.drawString(font,tr("direction"),16,138,0xD4D6AD,false);
        g.drawString(font,tr(menu.valid?(menu.halfDepth?"size_half":"size"):"invalid",menu.width,menu.height),16,180,menu.valid?0xB5B78A:0xFFBD6E,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) { super.render(g,x,y,partial); renderTooltip(g,x,y); }
}
