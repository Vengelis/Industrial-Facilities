package fr.vengelis.industrialfacilities.access.client;

import java.util.ArrayList;
import java.util.List;
import fr.vengelis.industrialfacilities.access.AccessBadges;
import fr.vengelis.industrialfacilities.access.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A small adaptive panel: configuration stays on the server; widgets send intents only. */
public class AccessScreen extends AbstractContainerScreen<AccessMenu> {
    private static final int TEXT=0xDDE3E6,MUTED=0x97A6AE,ACCENT=0x7BD3DB;
    private boolean compact,confirmClearLinks,confirmClearBadge;
    private int selectedLevel=1,codeCursor;
    private EditBox codeInput,limitInput,codeDisplay,nameInput;
    private String codeDraft="",limitDraft="0",lastCode="",nameDraft="",lastCustomName="";
    private boolean nameDirty,updatingName;
    private ItemStack observedNameBadge=ItemStack.EMPTY;
    private Button clearLinks,clearBadge,redstone,nextCode,renameBadge,bankSharing,cameraAutomation;
    private final List<Button> readerLevels=new ArrayList<>(),masterLevels=new ArrayList<>(),ownerButtons=new ArrayList<>(),encodingButtons=new ArrayList<>();
    public AccessScreen(AccessMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=menu.mode==AccessMenu.ENCODER?284:218;}
    private static Component tr(String key,Object... args){return Component.translatable("gui.industrial_facilities.access."+key,args);}
    private static Component status(String text){return text.startsWith("gui.industrial_facilities.")?Component.translatable(text):Component.literal(text);}
    private Button button(int x,int y,int width,int height,Component text,Button.OnPress action){return addRenderableWidget(Button.builder(text,action).bounds(leftPos+x,topPos+y,width,height).build());}
    private void send(int action,String code,int value){PacketDistributor.sendToServer(new AccessMessages.Action(menu.containerId,action,code,value));}
    @Override protected void init(){
        compact=width<352||height<296;imageWidth=compact?300:340;imageHeight=menu.mode==AccessMenu.ENCODER?(compact?232:284):218;
        super.init();ownerButtons.clear();readerLevels.clear();masterLevels.clear();encodingButtons.clear();menu.layoutSlots(compact);
        if(menu.mode==AccessMenu.ENCODER)initEncoder();else if(menu.mode==AccessMenu.READER)initReader();else initDoor();
        refresh();
    }
    private EditBox field(int x,int y,int w,int h,Component narration){
        EditBox box=new EditBox(font,leftPos+x,topPos+y,w,h,narration);box.setTextColor(TEXT);addRenderableWidget(box);return box;
    }
    private void initReader(){
        codeDisplay=field(14,48,imageWidth-28,18,tr("code"));codeDisplay.setMaxLength(64);codeDisplay.setEditable(false);codeDisplay.setValue(menu.code);lastCode=menu.code;
        button(14,72,110,20,tr("copy_code"),b->{if(!menu.code.isEmpty())minecraft.keyboardHandler.setClipboard(menu.code);});
        for(int i=1;i<=5;i++){int level=i;Button b=button(14+(i-1)*48,132,44,20,Component.literal(Integer.toString(i)),v->send(AccessMenu.SET_LEVEL,"",level));readerLevels.add(b);ownerButtons.add(b);}
        clearLinks=button(14,162,174,20,tr("clear_links"),b->{
            if(!confirmClearLinks){confirmClearLinks=true;b.setMessage(tr("confirm_clear_links"));}
            else{send(AccessMenu.CLEAR_LINKS,"",0);confirmClearLinks=false;b.setMessage(tr("clear_links"));}
        });ownerButtons.add(clearLinks);
        button(imageWidth-88,162,74,20,tr("close"),b->onClose());
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
        redstone=button(14,62,imageWidth-28,22,Component.empty(),b->send(AccessMenu.REDSTONE,"",menu.allowRedstone?0:1));ownerButtons.add(redstone);
        cameraAutomation=button(14,88,imageWidth-28,22,Component.empty(),b->send(AccessMenu.CAMERA_AUTOMATION,"",menu.allowCameraAutomation?0:1));ownerButtons.add(cameraAutomation);
        button(imageWidth-88,168,74,20,tr("close"),b->onClose());
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    private void refresh(){
        for(Button b:ownerButtons)b.active=menu.editable;
        for(int i=0;i<readerLevels.size();i++)readerLevels.get(i).setMessage(Component.literal((i+1==menu.accessLevel?"[":"")+(i+1)+(i+1==menu.accessLevel?"]":"")));
        for(int i=0;i<masterLevels.size();i++)masterLevels.get(i).setMessage(Component.literal((i+1==selectedLevel?"[":"")+(i+1)+(i+1==selectedLevel?"]":"")));
        if(codeDisplay!=null&&!menu.code.equals(lastCode)){codeDisplay.setValue(menu.code);lastCode=menu.code;}
        if(redstone!=null)redstone.setMessage(tr(menu.allowRedstone?"redstone_on":"redstone_off"));
        if(cameraAutomation!=null)cameraAutomation.setMessage(tr(menu.allowCameraAutomation?"camera_automation_on":"camera_automation_off"));
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
        g.fill(x-1,y-1,x+imageWidth+1,y+imageHeight+1,0xFF6D777E);g.fill(x,y,x+imageWidth,y+imageHeight,0xFF1B2026);
        g.fill(x,y,x+imageWidth,y+26,menu.mode==AccessMenu.READER?0xFF733F42:menu.mode==AccessMenu.DOOR?0xFF745F38:0xFF285B66);
        g.fill(x+6,y+29,x+imageWidth-6,y+imageHeight-6,0xFF242B32);
        if(menu.mode==AccessMenu.ENCODER){
            int invY=compact?150:202,invX=compact?69:89,inputX=compact?24:30;
            g.fill(x+inputX-2,y+invY+24,x+inputX+18,y+invY+44,0xFF71838C);g.fill(x+inputX-1,y+invY+25,x+inputX+17,y+invY+43,0xFF11171B);
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+invX+col*18,y+invY+row*18);
            for(int col=0;col<9;col++)slot(g,x+invX+col*18,y+invY+58);
        }
    }
    private static void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xFF59636A);g.fill(x,y,x+16,y+16,0xFF11171B);}
    private void drawFit(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),x,y,color,false);}
    private void wrapped(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawWordWrap(font,text,x,y,width,color);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        drawFit(g,title,14,9,menu.mode==AccessMenu.ENCODER?imageWidth-(compact?172:184):imageWidth-28,0xFFFFFF);
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
        }else{
            g.drawString(font,tr(menu.secured?"secured":"unsecured"),14,41,ACCENT,false);
            wrapped(g,tr(menu.secured?(menu.allowRedstone?"door_redstone_hint":"door_badge_hint"):"door_unsecured_hint"),14,120,imageWidth-28,MUTED);
            if(!menu.editable)drawFit(g,tr("read_only"),14,149,imageWidth-28,0xD6A97B);
            drawFit(g,status(menu.status),14,197,imageWidth-28,TEXT);
        }
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){onClose();return true;}
        if(codeInput!=null&&codeInput.isFocused())return codeInput.keyPressed(key,scan,modifiers);
        if(limitInput!=null&&limitInput.isFocused())return limitInput.keyPressed(key,scan,modifiers);
        if(nameInput!=null&&nameInput.isFocused())return nameInput.keyPressed(key,scan,modifiers);
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
