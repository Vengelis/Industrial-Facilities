package fr.vengelis.industrialfacilities.client.gui;

import fr.vengelis.industrialfacilities.world.inventory.AirlockControllerGUIMenu;
import fr.vengelis.industrialfacilities.network.AirlockControllerGUIButtonMessage;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModScreens;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.gui.widget.ExtendedSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

public class AirlockControllerGUIScreen extends AbstractContainerScreen<AirlockControllerGUIMenu>
        implements IndustrialFacilitiesModScreens.ScreenAccessor {
    private static final ResourceLocation BACKGROUND=ResourceLocation.parse("industrial_facilities:textures/screens/airlock_controller_gui.png");
    private boolean menuStateUpdateActive;
    private EditBox text_input_restricted_access;
    private ExtendedSlider slider_detect_range;
    private Button button_enabled,button_enabled1,button_disabled,button_invert,button_close;
    private final List<Button> controls=new ArrayList<>();
    private String draft="";

    public AirlockControllerGUIScreen(AirlockControllerGUIMenu container,Inventory inventory,Component title) {
        super(container,inventory,title);
        imageWidth=256; imageHeight=220;
    }
    private boolean flag(String name) { return Boolean.TRUE.equals(menu.menuState.get("1:"+name)); }
    private String text(String name,String fallback) {
        Object value=menu.menuState.get("0:"+name);
        return value instanceof String s ? s : fallback;
    }
    private double range() {
        Object value=menu.menuState.get("2:slider_detect_range");
        return value instanceof Number n && Double.isFinite(n.doubleValue()) ? Math.max(1,Math.min(10,n.doubleValue())) : 5;
    }
    private void send(int id) {
        if (flag("ready")) PacketDistributor.sendToServer(new AirlockControllerGUIButtonMessage(id,menu.x,menu.y,menu.z));
    }
    private Button button(int id,int x,int y,int width,String caption) {
        Button b=Button.builder(Component.literal(caption),ignored->send(id)).bounds(leftPos+x,topPos+y,width,20).build();
        controls.add(b); return addRenderableWidget(b);
    }

    @Override public void init() {
        super.init();
        controls.clear();
        menuStateUpdateActive=true;
        try {
            button_enabled=button(0,172,25,74,"Disabled");
            button_enabled1=button(1,172,69,74,"Disabled");
            button_disabled=button(4,172,91,74,"Disabled");
            button_invert=button(5,172,113,74,"Whitelist");
            text_input_restricted_access=new EditBox(font,leftPos+86,topPos+137,160,18,Component.literal("Player name"));
            text_input_restricted_access.setMaxLength(16);
            text_input_restricted_access.setValue(draft);
            text_input_restricted_access.setResponder(content->{
                draft=content;
                if (!menuStateUpdateActive) menu.sendMenuStateUpdate(menu.entity,0,"text_input_restricted_access",content,false);
            });
            addRenderableWidget(text_input_restricted_access);
            button(2,128,158,52,"Add");
            button(6,184,158,62,"Remove");
            button_close=button(3,172,197,74,"Close Door");
            slider_detect_range=new ExtendedSlider(leftPos+145,topPos+47,101,20,Component.empty(),Component.literal(" m"),1,10,range(),1,0,true) {
                @Override protected void applyValue() {
                    if (!menuStateUpdateActive && flag("ready"))
                        menu.sendMenuStateUpdate(menu.entity,2,"slider_detect_range",getValue(),false);
                }
            };
            addRenderableWidget(slider_detect_range);
        } finally { menuStateUpdateActive=false; }
        refreshControls();
    }
    @Override public void updateMenuState(int type,String name,Object value) {
        menuStateUpdateActive=true;
        try {
            if (type==2 && "slider_detect_range".equals(name) && slider_detect_range!=null && value instanceof Number n && Double.isFinite(n.doubleValue()))
                slider_detect_range.setValue(Math.max(1,Math.min(10,n.doubleValue())));
            if (type==0 && "text_input_restricted_access".equals(name) && value instanceof String s && text_input_restricted_access!=null)
                text_input_restricted_access.setValue(s);
        } finally { menuStateUpdateActive=false; }
        refreshControls();
    }
    private void refreshControls() {
        if (button_enabled==null) return;
        boolean ready=flag("ready");
        for (Button b:controls) b.active=ready;
        if (slider_detect_range!=null) slider_detect_range.active=ready;
        if (text_input_restricted_access!=null) text_input_restricted_access.setEditable(ready);
        button_enabled.setMessage(Component.literal(flag("player_detection") ? "Enabled" : "Disabled"));
        button_enabled1.setMessage(Component.literal(flag("redstone") ? "Enabled" : "Disabled"));
        button_disabled.setMessage(Component.literal(flag("restricted") ? "Enabled" : "Disabled"));
        button_invert.setMessage(Component.literal(flag("inverted") ? "Blacklist" : "Whitelist"));
        button_close.setMessage(Component.literal(flag("closed") ? "Open Door" : "Close Door"));
    }
    @Override public void resize(Minecraft minecraft,int width,int height) {
        if (text_input_restricted_access!=null) draft=text_input_restricted_access.getValue();
        super.resize(minecraft,width,height);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        if (key==256) { onClose(); return true; }
        if (text_input_restricted_access!=null && text_input_restricted_access.isFocused()) {
            if (text_input_restricted_access.keyPressed(key,scanCode,modifiers)) return true;
            // Do not close the inventory when typing its shortcut into a player name.
            return key!=258 || super.keyPressed(key,scanCode,modifiers);
        }
        return super.keyPressed(key,scanCode,modifiers);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        return getFocused()!=null && isDragging() && button==0
            ? getFocused().mouseDragged(x,y,button,dx,dy) : super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTicks) {
        refreshControls();
        super.render(graphics,mouseX,mouseY,partialTicks);
        renderTooltip(graphics,mouseX,mouseY);
        if (mouseX>=leftPos+10 && mouseX<=leftPos+246 && mouseY>=topPos+182 && mouseY<topPos+194) {
            // Wrap the full list: names remain readable even when the preview is clipped.
            graphics.renderTooltip(font,font.split(Component.literal(text("players_text","Loading...")),240),mouseX,mouseY);
        }
        if (mouseX>=leftPos+10 && mouseX<leftPos+168 && mouseY>=topPos+201 && mouseY<topPos+214)
            graphics.renderTooltip(font,Component.literal(text("status_text","Loading...")),mouseX,mouseY);
    }
    @Override protected void renderBg(GuiGraphics graphics,float partialTicks,int mouseX,int mouseY) {
        graphics.blit(BACKGROUND,leftPos,topPos,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
    }
    private void label(GuiGraphics g,String value,int x,int y) {
        g.drawString(font,value,x,y,0xFF3C3C3C,false);
    }
    private String clipped(String value,int width) {
        return font.width(value)<=width ? value : font.plainSubstrByWidth(value,width-font.width("..."))+"...";
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {
        label(graphics,"AIRLOCK CONTROLLER",10,9);
        label(graphics,"Player Detection",10,31);
        label(graphics,"Detection range",10,53);
        label(graphics,"Redstone detection",10,75);
        label(graphics,"Restrict Access",10,97);
        label(graphics,"Invert Selection",10,119);
        label(graphics,"Player name",10,142);
        label(graphics,"Players:",10,164);
        label(graphics,clipped(text("players_text","Loading..."),236),10,183);
        String status=text("status_text","Loading...");
        int statusColor=switch (status) {
            case "Air Lock Open" -> 0xFF16803C;
            case "Air Lock Closed" -> 0xFFB91C1C;
            default -> 0xFF3C3C3C;
        };
        graphics.drawString(font,clipped(status,156),10,203,statusColor,false);
    }
}

