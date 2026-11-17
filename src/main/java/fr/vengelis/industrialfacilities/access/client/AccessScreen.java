package fr.vengelis.industrialfacilities.access.client;

import java.util.ArrayList;
import java.util.List;
import fr.vengelis.industrialfacilities.access.AccessBadges;
import fr.vengelis.industrialfacilities.access.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A small adaptive panel: configuration stays on the server; widgets send intents only. */
public class AccessScreen extends AbstractContainerScreen<AccessMenu> {
    private static final int TEXT=0xDDE3E6,MUTED=TerminalGui.MUTED,ACCENT=TerminalGui.ACCENT;
    private boolean compact,confirmClearLinks,confirmClearBadge;
    private int selectedLevel=1,codeCursor;
    private EditBox codeInput,limitInput,codeDisplay,nameInput;
    private String codeDraft="",limitDraft="0",lastCode="",nameDraft="",lastCustomName="";
    private boolean nameDirty,updatingName;
    private ItemStack observedNameBadge=ItemStack.EMPTY;
    private Button clearLinks,clearBadge,redstone,nextCode,renameBadge,bankSharing,cameraAutomation;
    private Button openingHeight,forcedClosing;
    private EditBox irisPercent;
    private Button applyIrisPercent;
    private int lastIrisPercent = -1;
    private EditBox duration;
    private Button applyDuration;
    private int lastDuration;
    private boolean showingDevices;
    private Button devicesButton;
    private TerminalLinkList deviceList;
    private List<AbstractWidget> settings = List.of();
    private final List<Button> readerLevels=new ArrayList<>(),masterLevels=new ArrayList<>(),ownerButtons=new ArrayList<>(),encodingButtons=new ArrayList<>();
    public AccessScreen(AccessMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=menu.mode==AccessMenu.ENCODER?284:218;}
    private static Component tr(String key,Object... args){return Component.translatable("gui.industrial_facilities.access."+key,args);}
    private static Component status(String text){return text.startsWith("gui.industrial_facilities.")?Component.translatable(text):Component.literal(text);}
    private Button button(int x,int y,int width,int height,Component text,Button.OnPress action){return addRenderableWidget(TerminalGui.button(leftPos+x,topPos+y,width,height,text,action));}
    private void send(int action,String code,int value){PacketDistributor.sendToServer(new AccessMessages.Action(menu.containerId,action,code,value));}
    @Override protected void init(){
        compact=width<352||height<296;imageWidth=compact?300:340;imageHeight=menu.mode==AccessMenu.ENCODER?(compact?232:284):218;
        deviceList = null;
        super.init();ownerButtons.clear();readerLevels.clear();masterLevels.clear();encodingButtons.clear();menu.layoutSlots(compact);
        if(menu.mode==AccessMenu.ENCODER)initEncoder();
        else if(menu.mode==AccessMenu.READER)initReader();
        else if(menu.mode==AccessMenu.DOOR)initDoor();
        else initControl();
        if (menu.showsDevices()) {
            settings = children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
            deviceList = new TerminalLinkList(leftPos, topPos, imageWidth, imageHeight, this::addRenderableWidget,
                link -> PacketDistributor.sendToServer(new AccessMessages.UnlinkDevice(menu.containerId, link.id(), link.position())),
                () -> showDevices(false));
            showDevices(showingDevices);
        }
        refresh();
    }
    private static Component controlText(String key,Object... args) {
        return Component.translatable("gui.industrial_facilities.terminal_control." + key,args);
    }
    private void initControl() {
        if (menu.mode == AccessMenu.BUTTON) {
            lastDuration = -1;
            duration = field(14,87,76,20,controlText("seconds"));
            duration.setMaxLength(3);
            duration.setFilter(value -> value.matches("[0-9]{0,3}"));
            duration.setResponder(value -> refreshDuration());
            applyDuration = button(98,87,92,TerminalGui.ACTION_HEIGHT,tr("apply_iris_opening"),
                b -> send(AccessMenu.BUTTON_SECONDS,"",Integer.parseInt(duration.getValue())));
            duration.setTooltip(Tooltip.create(controlText("range")));
        }
        initLinkControls();
    }
    private void refreshDuration() {
        if (duration == null || applyDuration == null) return;
        int seconds = menu.buttonSeconds.get();
        if (seconds > 0 && seconds != lastDuration) {
            lastDuration = seconds;
            duration.setValue(Integer.toString(seconds));
        }
        String value = duration.getValue();
        int entered = value.isEmpty() ? 0 : Integer.parseInt(value);
        duration.setEditable(menu.editable);
        applyDuration.active = menu.editable && entered >= 1
            && entered <= fr.vengelis.industrialfacilities.access.ControlTerminalEntity.MAX_SECONDS;
    }
    private EditBox field(int x,int y,int w,int h,Component narration){
        EditBox box=new EditBox(font,leftPos+x,topPos+y,w,h,narration);box.setTextColor(TEXT);addRenderableWidget(box);return box;
    }
    private void initReader(){
        codeDisplay=field(14,48,imageWidth-28,18,tr("code"));codeDisplay.setMaxLength(64);codeDisplay.setEditable(false);codeDisplay.setValue(menu.code);lastCode=menu.code;
        button(14,72,110,20,tr("copy_code"),b->{if(!menu.code.isEmpty())minecraft.keyboardHandler.setClipboard(menu.code);});
        for(int i=1;i<=5;i++){int level=i;Button b=button(14+(i-1)*48,132,44,20,Component.literal(Integer.toString(i)),v->send(AccessMenu.SET_LEVEL,"",level));readerLevels.add(b);ownerButtons.add(b);}
        initLinkControls();
        clearLinks.setTooltip(Tooltip.create(tr("clear_links_hint")));
    }
    private void initLinkControls() {
        int w = (imageWidth - 114) / 2;
        devicesButton = button(14, 162, w, TerminalGui.ACTION_HEIGHT, tr("devices"), ignored -> showDevices(true));
        clearLinks=button(18+w,162,w,TerminalGui.ACTION_HEIGHT,tr("clear_links"),b->{
            if(!confirmClearLinks){confirmClearLinks=true;b.setMessage(tr("confirm"));}
            else{send(AccessMenu.CLEAR_LINKS,"",0);confirmClearLinks=false;b.setMessage(tr("clear_links"));}
        });ownerButtons.add(clearLinks);
        button(imageWidth-88,162,74,20,tr("close"),b->onClose());
    }
    private void showDevices(boolean visible) {
        if (getFocused() != null) getFocused().setFocused(false);
        setFocused(null);
        showingDevices = visible;
        for (var widget : settings) widget.visible = !visible;
        deviceList.update(menu.deviceLinks, visible, menu.editable);
        confirmClearLinks = false;
        clearLinks.setMessage(tr("clear_links"));
    }
    private void initEncoder(){
        bankSharing=button(imageWidth-(compact?144:156),4,compact?132:144,18,Component.empty(),
            b->send(AccessMenu.SET_BANK_SHARING,"",AccessBadges.foreignBankAllowed(menu.input.getItem(0))?0:1));
        int codeY=compact?33:43;
        codeInput=field(12,codeY,imageWidth-76,18,tr("terminal_code"));codeInput.setMaxLength(64);codeInput.setHint(tr("terminal_code"));codeInput.setValue(codeDraft);codeInput.setResponder(v->codeDraft=v);
        button(imageWidth-60,codeY,48,18,tr("paste"),b->codeInput.setValue(minecraft.keyboardHandler.getClipboard().strip()));
        int actionY=compact?55:67,w=(imageWidth-32)/3;
        encodingButtons.add(button(12,actionY,w,20,tr("add_code"),b->send(AccessMenu.ADD_CODE,codeInput.getValue(),0)));
        encodingButtons.add(button(16+w,actionY,w,20,tr("remove_code"),b->send(AccessMenu.REMOVE_CODE,codeInput.getValue(),0)));
        clearBadge=button(20+w*2,actionY,w,20,tr("clear_badge"),b->{
            if(!confirmClearBadge){confirmClearBadge=true;b.setMessage(tr("confirm"));}
            else{send(AccessMenu.CLEAR_BADGE,"",0);confirmClearBadge=false;b.setMessage(tr("clear_badge"));}
        });
        encodingButtons.add(clearBadge);
        int levelY=compact?77:97,start=compact?82:96,pitch=compact?21:23;
        for(int i=1;i<=5;i++){int level=i;masterLevels.add(button(start+(i-1)*pitch,levelY,compact?19:21,compact?16:18,Component.literal(Integer.toString(i)),b->{selectedLevel=level;refresh();}));}
        encodingButtons.add(button(imageWidth-100,levelY,88,compact?16:18,tr("make_master"),b->send(AccessMenu.MASTER,"",selectedLevel)));
        int limitY=compact?95:125;
        limitInput=field(start,limitY,compact?100:112,18,tr("limit"));limitInput.setMaxLength(7);limitInput.setFilter(s->s.matches("[0-9]{0,7}"));limitInput.setValue(limitDraft);limitInput.setResponder(v->limitDraft=v);
        encodingButtons.add(button(imageWidth-100,limitY,88,18,tr("set_limit"),b->{
            try{int count=Integer.parseInt(limitInput.getValue());if(count>=0&&count<=1000000){send(AccessMenu.LIMIT,"",count);return;}}catch(NumberFormatException ignored){}
            menu.status="gui.industrial_facilities.access.invalid_limit";
        }));
        int nameY=compact?116:151;
        nameInput=field(start,nameY,compact?100:112,18,tr("badge_name"));nameInput.setMaxLength(64);nameInput.setHint(tr("badge_name_hint"));
        nameInput.setValue(nameDraft);nameInput.setResponder(v->{nameDraft=v;if(!updatingName)nameDirty=!v.equals(lastCustomName);});
        renameBadge=button(imageWidth-100,nameY,88,18,tr("rename"),b->{
            send(AccessMenu.RENAME,nameInput.getValue(),0);
            if(nameInput.getValue().strip().equals(lastCustomName))nameDirty=false;
        });
        int invY=compact?150:202;
        nextCode=button(12,invY+54,50,18,tr("next_code"),b->{
            var codes=AccessBadges.codes(menu.input.getItem(0));if(codes.isEmpty())return;
            codeCursor=Math.floorMod(codeCursor,codes.size());codeInput.setValue(codes.get(codeCursor).toString());codeCursor=(codeCursor+1)%codes.size();
        });
    }
    private void initDoor(){
        redstone=button(14,50,imageWidth-28,22,Component.empty(),b->send(AccessMenu.REDSTONE,"",menu.allowRedstone?0:1));ownerButtons.add(redstone);
        cameraAutomation=button(14,75,imageWidth-28,22,Component.empty(),b->send(AccessMenu.CAMERA_AUTOMATION,"",menu.allowCameraAutomation?0:1));ownerButtons.add(cameraAutomation);
        forcedClosing=button(14,100,imageWidth-28,22,Component.empty(),b->send(AccessMenu.FORCED_CLOSING,"",menu.forcedClosing()?0:1));
        forcedClosing.setTooltip(Tooltip.create(tr("forced_closing_hint")));
        ownerButtons.add(forcedClosing);
        openingHeight=button(14,125,imageWidth-28,22,Component.empty(),b->send(AccessMenu.HALF_OPENING,"",menu.halfOpening?0:1));
        openingHeight.setTooltip(Tooltip.create(tr("opening_height_hint")));
        irisPercent=field(138,127,52,18,tr("iris_opening"));
        irisPercent.setMaxLength(3);
        irisPercent.setFilter(value->value.matches("[0-9]{0,3}"));
        irisPercent.setResponder(value->refreshIrisControls());
        applyIrisPercent=button(imageWidth-100,125,86,22,tr("apply_iris_opening"),
            b->send(AccessMenu.IRIS_OPENING,"",Integer.parseInt(irisPercent.getValue())));
        irisPercent.setTooltip(Tooltip.create(tr("iris_opening_hint")));
        lastIrisPercent=-1;
        button(imageWidth-88,177,74,20,tr("close"),b->onClose());
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    private void refresh(){
        if (deviceList != null) {
            deviceList.update(menu.deviceLinks, showingDevices, menu.editable);
            devicesButton.active = menu.editable;
        }
        for(Button b:ownerButtons)b.active=menu.editable;
        for(int i=0;i<readerLevels.size();i++)readerLevels.get(i).setMessage(Component.literal((i+1==menu.accessLevel?"[":"")+(i+1)+(i+1==menu.accessLevel?"]":"")));
        for(int i=0;i<masterLevels.size();i++)masterLevels.get(i).setMessage(Component.literal((i+1==selectedLevel?"[":"")+(i+1)+(i+1==selectedLevel?"]":"")));
        if(codeDisplay!=null&&!menu.code.equals(lastCode)){codeDisplay.setValue(menu.code);lastCode=menu.code;}
        if(redstone!=null)redstone.setMessage(tr(menu.allowRedstone?"redstone_on":"redstone_off"));
        if(cameraAutomation!=null)cameraAutomation.setMessage(tr(menu.allowCameraAutomation?"camera_automation_on":"camera_automation_off"));
        if(forcedClosing!=null)forcedClosing.setMessage(tr(menu.forcedClosing()?"forced_closing_on":"forced_closing_off"));
        if(openingHeight!=null){
            openingHeight.visible=menu.meshDoor;
            openingHeight.active=menu.meshDoor&&menu.editable;
            openingHeight.setMessage(tr(menu.halfOpening?"opening_height_half":"opening_height_full"));
        }
        refreshIrisControls();
        refreshDuration();
        if(nextCode!=null)nextCode.active=!AccessBadges.codes(menu.input.getItem(0)).isEmpty();
        if(nameInput!=null&&menu.mode==AccessMenu.ENCODER){
            boolean canEncode=AccessMenu.badge(menu.input.getItem(0));
            var badgeData=AccessBadges.read(menu.input.getItem(0));
            bankSharing.active=canEncode&&(!badgeData.hasUUID("Issuer")||badgeData.getUUID("Issuer").equals(menu.inventory.player.getUUID()));
            bankSharing.setMessage(tr(AccessBadges.foreignBankAllowed(menu.input.getItem(0))?"bank_shared":"bank_private"));
            for(Button b:encodingButtons)b.active=canEncode;
            for(Button b:masterLevels)b.active=canEncode;
            codeInput.setEditable(canEncode);limitInput.setEditable(canEncode);refreshName();
        }
    }
    private void refreshIrisControls(){
        if(irisPercent==null||applyIrisPercent==null)return;
        boolean shown=menu.irisOpeningPercent>=0;
        if(shown&&menu.irisOpeningPercent!=lastIrisPercent){
            lastIrisPercent=menu.irisOpeningPercent;
            irisPercent.setValue(Integer.toString(lastIrisPercent));
        }
        irisPercent.visible=shown;
        irisPercent.setEditable(shown&&menu.editable);
        applyIrisPercent.visible=shown;
        String value=irisPercent.getValue();
        applyIrisPercent.active=shown&&menu.editable&&!value.isEmpty()&&Integer.parseInt(value)<=100;
    }
    private void refreshName(){
        ItemStack badge=menu.input.getItem(0);Component custom=badge.get(DataComponents.CUSTOM_NAME);
        String name=custom==null?"":custom.getString();
        var oldData=AccessBadges.read(observedNameBadge);var newData=AccessBadges.read(badge);
        boolean changedIdentity=oldData.hasUUID("BadgeId")&&newData.hasUUID("BadgeId")&&!oldData.getUUID("BadgeId").equals(newData.getUUID("BadgeId"));
        if(badge.isEmpty()!=observedNameBadge.isEmpty()||changedIdentity||!name.equals(lastCustomName)||!nameDirty){
            nameDraft=name;
            if(!nameInput.getValue().equals(name)){updatingName=true;nameInput.setValue(name);updatingName=false;}
            nameDirty=false;
        }
        lastCustomName=name;observedNameBadge=badge.copy();
        nameInput.setEditable(AccessMenu.renameable(badge));renameBadge.active=AccessMenu.renameable(badge);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;
        TerminalGui.panel(g,x,y,imageWidth,imageHeight,
            menu.mode==AccessMenu.READER?0xFF733F42:menu.mode==AccessMenu.DOOR?0xFF745F38:TerminalGui.TEAL_HEADER);
        if (showingDevices) deviceList.renderBackground(g, x, y);
        if(menu.mode==AccessMenu.ENCODER){
            int invY=compact?150:202,invX=compact?69:89,inputX=compact?24:30;
            g.fill(x+inputX-2,y+invY+24,x+inputX+18,y+invY+44,0xFF71838C);g.fill(x+inputX-1,y+invY+25,x+inputX+17,y+invY+43,0xFF11171B);
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)TerminalGui.slot(g,x+invX+col*18,y+invY+row*18);
            for(int col=0;col<9;col++)TerminalGui.slot(g,x+invX+col*18,y+invY+58);
        }
    }
    private void drawFit(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),x,y,color,false);}
    private void wrapped(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawWordWrap(font,text,x,y,width,color);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        drawFit(g,title,14,9,menu.mode==AccessMenu.ENCODER?imageWidth-(compact?172:184):imageWidth-28,0xFFFFFF);
        if (showingDevices) {
            deviceList.render(g, font, menu.status);
            return;
        }
        if(menu.mode==AccessMenu.READER){
            g.drawString(font,tr(menu.code.isEmpty()?"code_unassigned":"code"),14,34,MUTED,false);
            drawFit(g,tr("owner",menu.editable?tr("you").getString():menu.creator),14,101,imageWidth-28,MUTED);
            g.drawString(font,tr("level",menu.accessLevel),14,118,ACCENT,false);
            g.drawString(font,tr("linked",menu.links),142,77,MUTED,false);
            drawFit(g,status(menu.status.isEmpty()?(menu.editable?"gui.industrial_facilities.access.reader_hint":"gui.industrial_facilities.access.read_only"):menu.status),14,197,imageWidth-28,TEXT);
        }else if(menu.mode==AccessMenu.ENCODER){
            int levelY=compact?82:102,limitY=compact?100:130,nameY=compact?121:156,invY=compact?150:202;
            drawFit(g,tr("master"),12,levelY,compact?66:80,MUTED);drawFit(g,tr("uses"),12,limitY,compact?66:80,MUTED);
            drawFit(g,tr("badge_name"),12,nameY,compact?66:80,MUTED);
            String hint=!menu.input.isEmpty()&&!AccessMenu.badge(menu.input.getItem(0))?"gui.industrial_facilities.access.link_card_name_hint":"gui.industrial_facilities.access.encoder_hint";
            int statusY=compact?137:177;drawFit(g,status(menu.status.isEmpty()?hint:menu.status),12,statusY,imageWidth-24,TEXT);
            g.drawString(font,tr("card_slot"),14,invY+10,ACCENT,false);if(!compact)g.drawString(font,playerInventoryTitle,89,invY-11,MUTED,false);
        }else if(menu.mode==AccessMenu.DOOR){
            g.drawString(font,tr(menu.secured?"secured":"unsecured"),14,35,ACCENT,false);
            boolean hasOpeningOption=menu.meshDoor||menu.irisOpeningPercent>=0;
            if(menu.irisOpeningPercent>=0)g.drawString(font,tr("iris_opening"),14,132,TEXT,false);
            wrapped(g,tr(menu.secured?(menu.allowRedstone?"door_redstone_hint":"door_badge_hint"):"door_unsecured_hint"),14,hasOpeningOption?150:126,imageWidth-28,MUTED);
            if(!menu.editable)drawFit(g,tr("read_only"),14,182,imageWidth-110,0xD6A97B);
            drawFit(g,status(menu.status),14,197,imageWidth-28,TEXT);
        }else{
            g.drawString(font,tr("device_count",menu.links),14,42,ACCENT,false);
            if(menu.mode==AccessMenu.BUTTON)g.drawString(font,controlText("seconds"),14,73,TEXT,false);
            wrapped(g,controlText(menu.mode==AccessMenu.BUTTON?"button_hint":"lockdown_hint"),14,119,imageWidth-28,MUTED);
            drawFit(g,status(menu.status),14,197,imageWidth-28,TEXT);
        }
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){if(showingDevices)showDevices(false);else onClose();return true;}
        if (TerminalGui.handleInventoryKey(getFocused(),key,scan,modifiers)) return true;
        if(codeInput!=null&&codeInput.isFocused())return codeInput.keyPressed(key,scan,modifiers);
        if(limitInput!=null&&limitInput.isFocused())return limitInput.keyPressed(key,scan,modifiers);
        if(nameInput!=null&&nameInput.isFocused())return nameInput.keyPressed(key,scan,modifiers);
        if(duration!=null&&duration.isFocused())return duration.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
        int sy=menu.mode==AccessMenu.ENCODER?(compact?137:177):197;
        if(!menu.status.isEmpty()&&mouseX>=leftPos+12&&mouseX<leftPos+imageWidth-12&&mouseY>=topPos+sy&&mouseY<topPos+sy+10)
            g.renderTooltip(font,font.split(status(menu.status),imageWidth-24),mouseX,mouseY);
        if(menu.mode==AccessMenu.ENCODER&&limitInput!=null&&limitInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("zero_unlimited"),mouseX,mouseY);
        if(nameInput!=null&&nameInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("name_hint"),mouseX,mouseY);
        if(bankSharing!=null&&bankSharing.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("bank_sharing_hint"),240),mouseX,mouseY);
        if(cameraAutomation!=null&&cameraAutomation.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("camera_automation_hint"),240),mouseX,mouseY);
        if(nextCode!=null&&nextCode.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("next_code_hint"),mouseX,mouseY);
    }
}
