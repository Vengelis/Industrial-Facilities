package fr.vengelis.industrialfacilities.alarm.client;

import fr.vengelis.industrialfacilities.alarm.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public class AlarmScreen extends AbstractContainerScreen<AlarmMenu> {
    private int selected,offset,radius;
    private final List<Button> rows=new ArrayList<>();
    private SimpleSoundInstance preview;
    private int previewTicks;
    public AlarmScreen(AlarmMenu menu,Inventory inv,Component title) {
        super(menu,inv,title); imageWidth=300; imageHeight=252;
        for (int i=0;i<menu.choices.size();i++) if (menu.choices.get(i).id().equals(menu.selected)) selected=i;
        offset=Math.max(0,Math.min(selected,menu.choices.size()-6)); radius=menu.range;
    }
    private static Component tr(String key,Object... args) { return Component.translatable("gui.industrial_facilities.alarm."+key,args); }
    @Override protected void init() {
        super.init(); rows.clear();
        for (int i=0;i<6;i++) {
            final int row=i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(),b->{ selected=offset+row; stopPreview(); refresh(); }).bounds(leftPos+14,topPos+43+i*20,244,20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("▲"),b->{ offset=Math.max(0,offset-1); refresh(); }).bounds(leftPos+264,topPos+43,22,20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"),b->{ offset=Math.min(Math.max(0,menu.choices.size()-6),offset+1); refresh(); }).bounds(leftPos+264,topPos+143,22,20).build());
        addRenderableWidget(new AbstractSliderButton(leftPos+14,topPos+181,272,20,Component.empty(),(radius-1)/127.0) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(tr("range",radius)); }
            @Override protected void applyValue() { radius=1+(int)Math.round(value*127); updateMessage(); }
        });
        addRenderableWidget(Button.builder(tr("preview"),b->preview()).bounds(leftPos+14,topPos+218,84,20).build());
        addRenderableWidget(Button.builder(tr("cancel"),b->onClose()).bounds(leftPos+102,topPos+218,84,20).build());
        addRenderableWidget(Button.builder(tr("save"),b->{
            stopPreview(); PacketDistributor.sendToServer(new AlarmSettings(menu.containerId,menu.choices.get(selected).id(),radius));
        }).bounds(leftPos+190,topPos+218,96,20).build());
        refresh();
    }
    private void refresh() {
        for (int i=0;i<rows.size();i++) {
            int index=offset+i; var row=rows.get(i); row.visible=index<menu.choices.size();
            if (row.visible) { row.setMessage(Component.literal((index==selected?"▶ ":"  ")+font.plainSubstrByWidth(menu.choices.get(index).name(),222))); row.active=index!=selected; }
        }
    }
    private void preview() {
        stopPreview(); var sound=menu.choices.get(selected).sound();
        if (minecraft.getSoundManager().getSoundEvent(sound)==null) return;
        preview=SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound),1,0.6F);
        minecraft.getSoundManager().play(preview); previewTicks=60;
    }
    private void stopPreview() { if (preview!=null) minecraft.getSoundManager().stop(preview); preview=null; previewTicks=0; }
    @Override public void removed() { stopPreview(); super.removed(); }
    @Override protected void containerTick() { if (previewTicks>0 && --previewTicks==0) stopPreview(); }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if (x>=leftPos+14 && x<=leftPos+286 && y>=topPos+43 && y<=topPos+163) {
            offset=Math.clamp(offset-(int)Math.signum(dy),0,Math.max(0,menu.choices.size()-6)); refresh(); return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        g.fill(leftPos-1,topPos-1,leftPos+301,topPos+253,0xFF71828A);
        g.fill(leftPos,topPos,leftPos+300,topPos+252,0xFF1B2329);
        g.fill(leftPos,topPos,leftPos+300,topPos+29,0xFF963F3B);
        g.fill(leftPos+10,topPos+39,leftPos+290,topPos+167,0xFF11171C);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,title,14,11,0xFFFFFF,false);
        g.drawString(font,tr("sounds"),14,32,0xB9C9D1,false);
        boolean missing=minecraft.getSoundManager().getSoundEvent(menu.choices.get(selected).sound())==null;
        g.drawString(font,tr(missing?"missing":"redstone"),14,205,missing?0xFFAA66:0xB9C9D1,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) { super.render(g,x,y,partial); renderTooltip(g,x,y); }
}
