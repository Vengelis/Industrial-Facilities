package fr.vengelis.industrialfacilities.access.client;

import java.util.*;
import fr.vengelis.industrialfacilities.access.KeypassData;
import fr.vengelis.industrialfacilities.access.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KeypassScreen extends AbstractContainerScreen<KeypassMenu> {
    private static final int PAGE_SIZE=4,INK=0xE2E5EB,ACCENT=0xFFD089;
    private final List<Button> rows=new ArrayList<>();
    private EditBox input,nameInput;
    private Button previous,next,save,revoke,unlink,submit,detach,rename;
    private UUID selected;
    private String loadedName="";
    private int page;
    private boolean confirmUnlink,confirmDetach;
    public KeypassScreen(KeypassMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=282;imageHeight=208;}
    private static Component tr(String key,Object... args){return Component.translatable(KeypassMenus.KEY+key,args);}
    private Button button(int x,int y,int w,int h,Component label,Button.OnPress callback){return addRenderableWidget(Button.builder(label,callback).bounds(leftPos+x,topPos+y,w,h).build());}
    @Override protected void init(){
        String draft=nameInput==null?loadedName:nameInput.getValue();
        super.init();rows.clear();
        if(menu.manager){
            for(int i=0;i<PAGE_SIZE;i++){int row=i;rows.add(button(12,31+i*19,258,18,Component.empty(),b->{int index=page*PAGE_SIZE+row;if(index<menu.readers.size())selectReader(menu.readers.get(index));}));}
            previous=button(12,109,24,18,Component.literal("‹"),b->{page--;refresh();});next=button(246,109,24,18,Component.literal("›"),b->{page++;refresh();});
            nameInput=new EditBox(font,leftPos+12,topPos+130,176,18,tr("reader_name"));nameInput.setMaxLength(KeypassData.MAX_READER_NAME);
            nameInput.setHint(tr("reader_name"));nameInput.setTooltip(Tooltip.create(tr("reader_name_hint")));nameInput.setValue(draft);
            nameInput.setResponder(s->refresh());addRenderableWidget(nameInput);
            rename=button(192,130,78,18,tr("rename"),b->renameSelected());
            field(12,153,90);save=button(106,153,56,18,tr("save"),b->send(KeypassMenu.SAVE,input.getValue()));
            revoke=button(166,153,51,18,tr("revoke"),b->send(KeypassMenu.REVOKE,""));
            unlink=button(218,153,52,18,tr("unlink"),b->{if(confirmUnlink)send(KeypassMenu.UNLINK,"");else confirmUnlink=true;refresh();});
        }else{
            field(72,29,138);
            for(int i=1;i<=9;i++){String digit=Integer.toString(i);button(72+(i-1)%3*47,54+(i-1)/3*24,44,21,Component.literal(digit),b->digit(digit));}
            button(72,126,44,21,tr("backspace"),b->{String s=input.getValue();if(!s.isEmpty())input.setValue(s.substring(0,s.length()-1));});
            button(119,126,44,21,Component.literal("0"),b->digit("0"));
            submit=button(166,126,44,21,tr("ok"),b->send(KeypassMenu.SUBMIT,input.getValue()));
            if(menu.owner)detach=button(72,154,138,18,tr("detach"),b->{if(confirmDetach)send(KeypassMenu.DETACH,"");else confirmDetach=true;refresh();});
        }
        refresh();
    }
    private void selectReader(KeypassData.Reader reader){
        selected=reader.id();loadedName=reader.name();confirmUnlink=false;input.setValue("");nameInput.setValue(loadedName);refresh();
    }
    private void renameSelected(){
        String clean=KeypassData.cleanReaderName(nameInput.getValue());if(clean==null||!rename.active)return;
        nameInput.setValue(clean);send(KeypassMenu.RENAME,clean);
    }
    private void field(int x,int y,int width){
        input=new EditBox(font,leftPos+x,topPos+y,width,18,tr("pin"));input.setMaxLength(KeypassData.PIN_LENGTH);
        input.setFilter(s->s.matches("[0-9]*"));input.setHint(tr("pin"));
        input.setFormatter((s,offset)->Component.literal("•".repeat(s.length())).getVisualOrderText());
        input.setResponder(s->refresh());addRenderableWidget(input);setInitialFocus(input);
    }
    private void digit(String digit){if(input.getValue().length()<KeypassData.PIN_LENGTH)input.setValue(input.getValue()+digit);}
    private void send(int kind,String value){
        PacketDistributor.sendToServer(new KeypassMessages.Action(menu.containerId,kind,selected==null?KeypassMessages.NONE:selected,value));
        if(kind!=KeypassMenu.RENAME)input.setValue("");confirmUnlink=false;confirmDetach=false;refresh();
    }
    private int pages(){return Math.max(1,(menu.readers.size()+PAGE_SIZE-1)/PAGE_SIZE);}
    private void refresh(){
        if(input==null)return;
        boolean valid=KeypassData.validPin(input.getValue());
        if(menu.manager){
            if(previous==null||save==null||unlink==null||nameInput==null||rename==null)return;
            page=Math.clamp(page,0,pages()-1);previous.active=page>0;next.active=page+1<pages();
            var current=menu.readers.stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);
            if(current==null){selected=null;confirmUnlink=false;}
            String serverName=current==null?"":current.name();
            if(!loadedName.equals(serverName)){loadedName=serverName;nameInput.setValue(serverName);}
            for(int i=0;i<rows.size();i++){
                var row=rows.get(i);int index=page*PAGE_SIZE+i;row.visible=index<menu.readers.size();
                if(row.visible){
                    var r=menu.readers.get(index);String mark=r.id().equals(selected)?"› ":"";var status=tr(r.configured()?"ready":"unset");
                    var details=tr("row","",r.position().toShortString(),status);
                    row.setMessage(r.name().isEmpty()?tr("row",mark,r.position().toShortString(),status):tr("named_row",mark,r.name(),status));
                    row.setTooltip(r.name().isEmpty()?null:Tooltip.create(Component.literal(r.name()+"\n").append(details)));
                }
            }
            String draft=KeypassData.cleanReaderName(nameInput.getValue());nameInput.setEditable(current!=null);rename.active=current!=null&&draft!=null&&!draft.equals(serverName);
            input.setEditable(current!=null);save.active=current!=null&&valid;revoke.active=current!=null&&current.configured();unlink.active=current!=null;
            unlink.setMessage(tr(confirmUnlink?"confirm":"unlink"));
        }else{
            if(submit!=null)submit.active=valid;
            if(detach!=null){detach.active=menu.doors>0;detach.setMessage(tr(confirmDetach?"confirm":"detach"));}
        }
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(nameInput!=null&&nameInput.isFocused()&&key!=256){if(key==257||key==335){renameSelected();return true;}return nameInput.keyPressed(key,scan,modifiers)||key!=258;}
        if((key==257||key==335)&&input!=null&&KeypassData.validPin(input.getValue())&&(!menu.manager||selected!=null)){send(menu.manager?KeypassMenu.SAVE:KeypassMenu.SUBMIT,input.getValue());return true;}
        if(input!=null&&input.isFocused()&&key!=256)return input.keyPressed(key,scan,modifiers)||key!=258;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF202129);
        g.renderOutline(leftPos,topPos,imageWidth,imageHeight,0xFF636675);
        g.fill(leftPos+1,topPos+1,leftPos+imageWidth-1,topPos+23,menu.manager?0xFF244A4B:0xFF5B4129);
        g.fill(leftPos+8,topPos+179,leftPos+imageWidth-8,topPos+200,0xFF15161B);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,12,8,INK,false);
        if(menu.manager){
            g.drawCenteredString(font,tr("page",page+1,pages()),imageWidth/2,114,INK);
            if(menu.readers.isEmpty())g.drawCenteredString(font,tr("no_readers"),imageWidth/2,66,INK);
        }else{
            g.drawString(font,tr("doors",menu.doors),12,161,INK,false);
        }
        Component message=tr(menu.status.isEmpty()?(menu.manager?"manager_hint":"reader_hint"):menu.status);
        g.drawString(font,font.plainSubstrByWidth(message.getString(),imageWidth-26),13,186,ACCENT,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
}
