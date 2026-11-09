package fr.vengelis.industrialfacilities.access.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import fr.vengelis.industrialfacilities.access.BadgeBankData;
import fr.vengelis.industrialfacilities.access.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Six archive rows, with all mutations performed by the currently open server menu. */
public class BadgeBankScreen extends AbstractContainerScreen<BadgeBankMenu> {
    private static final int TEXT=0xDDE3E6,MUTED=0x97A6AE,ACCENT=0xE0B368;
    private boolean compact,confirmReissue,confirmRemove,nameDirty,updatingName,awaitingName;
    private UUID observedSelection;
    private UUID observedNameSelection;
    private int codeCursor;
    private int nameReplyVersion;
    private String nameDraft="",lastArchiveName="";
    private String searchDraft="",lastSentSearch="";
    private int searchDebounce;
    private boolean searchInitialized;
    private ItemStack observedInput=ItemStack.EMPTY;
    private Button deposit,withdraw,reissue,previous,next,copyCode,rename,remove,clearSearch;
    private EditBox nameInput,searchInput;
    private final List<Button> rows=new ArrayList<>();
    public BadgeBankScreen(BadgeBankMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=400;imageHeight=304;}
    private static Component tr(String key,Object... args){return Component.translatable("gui.industrial_facilities.bank."+key,args);}
    private static Component status(String value){return value.startsWith("gui.industrial_facilities.")?Component.translatable(value):Component.literal(value);}
    private Button button(int x,int y,int w,int h,Component label,Button.OnPress action){return addRenderableWidget(Button.builder(label,action).bounds(leftPos+x,topPos+y,w,h).build());}
    private void send(int action,String id,int value){send(action,id,value,"");}
    private void send(int action,String id,int value,String text){
        if(action!=BadgeBankMenu.SEARCH&&searchPending())return;
        PacketDistributor.sendToServer(new BadgeBankMessages.Action(menu.containerId,action,id,value,text));
    }
    private String selectedId(){return menu.selectedId==null?"":menu.selectedId.toString();}
    private void cancelConfirmation(){confirmReissue=false;confirmRemove=false;}
    private boolean searchPending(){String query=BadgeBankMenu.cleanQuery(searchDraft);return !query.equals(menu.searchQuery)||!query.equals(lastSentSearch);}
    private void searchChanged(String value){
        String clean=BadgeBankMenu.cleanQuery(value);
        if(!clean.equals(value)){searchInput.setValue(clean);return;}
        searchDraft=clean;searchDebounce=4;cancelConfirmation();refresh();
    }
    @Override protected void init(){
        boolean restoreSearchFocus=searchInput!=null&&searchInput.isFocused();
        int searchCursor=searchInput==null?0:searchInput.getCursorPosition();
        if(!searchInitialized){searchDraft=menu.searchQuery;lastSentSearch=searchDraft;searchInitialized=true;}
        compact=width<412||height<316;imageWidth=compact?320:400;imageHeight=compact?232:304;
        super.init();menu.layoutSlots(compact);rows.clear();
        searchInput=new EditBox(font,leftPos+112,topPos+4,imageWidth-226,16,tr("search"));
        searchInput.setMaxLength(64);searchInput.setTextColor(TEXT);searchInput.setHint(tr("search_hint"));searchInput.setValue(searchDraft);
        searchInput.setResponder(this::searchChanged);addRenderableWidget(searchInput);
        clearSearch=button(imageWidth-110,4,16,16,Component.literal("X"),b->searchInput.setValue(""));
        previous=button(imageWidth-91,4,18,16,Component.literal("<"),b->{cancelConfirmation();send(BadgeBankMenu.PAGE,"",menu.page-1);});
        next=button(imageWidth-27,4,18,16,Component.literal(">"),b->{cancelConfirmation();send(BadgeBankMenu.PAGE,"",menu.page+1);});
        int rowY=compact?29:34,rowH=compact?13:18,rowW=imageWidth/2-16;
        for(int i=0;i<BadgeBankMessages.PAGE_SIZE;i++){
            int index=i;
            rows.add(button(10,rowY+i*rowH,rowW,rowH,Component.empty(),b->{
                if(index<menu.entries.size()){cancelConfirmation();send(BadgeBankMenu.SELECT,menu.entries.get(index).id().toString(),0);}
            }));
        }
        int detailX=imageWidth/2+4,copyY=compact?101:131;
        copyCode=button(detailX,copyY,imageWidth-detailX-10,compact?12:14,tr("copy_code"),b->{
            if(searchPending()||menu.selectedCodes.isEmpty())return;
            codeCursor=Math.floorMod(codeCursor,menu.selectedCodes.size());
            minecraft.keyboardHandler.setClipboard(menu.selectedCodes.get(codeCursor));codeCursor=(codeCursor+1)%menu.selectedCodes.size();
        });
        int actionY=compact?114:154,actionH=compact?18:20,actionW=(imageWidth-28)/3;
        deposit=button(10,actionY,actionW,actionH,tr("deposit"),b->{
            if(confirmReissue||confirmRemove){cancelConfirmation();refresh();return;}
            send(BadgeBankMenu.DEPOSIT,"",0);
        });
        withdraw=button(14+actionW,actionY,actionW,actionH,tr("withdraw"),b->send(BadgeBankMenu.WITHDRAW,selectedId(),0));
        reissue=button(18+actionW*2,actionY,actionW,actionH,tr("reissue"),b->{
            if(searchPending())return;
            if(!confirmReissue){confirmReissue=true;confirmRemove=false;refresh();}
            else{send(BadgeBankMenu.REISSUE,selectedId(),1);cancelConfirmation();refresh();}
        });
        int nameX=compact?10:12,nameY=compact?150:218,nameW=compact?126:198,nameH=compact?16:18;
        nameInput=new EditBox(font,leftPos+nameX,topPos+nameY,nameW,nameH,tr("archive_name"));
        nameInput.setMaxLength(64);nameInput.setTextColor(TEXT);nameInput.setHint(tr("archive_name_hint"));nameInput.setValue(nameDraft);
        nameInput.setResponder(value->{nameDraft=value;if(!updatingName)nameDirty=!value.equals(lastArchiveName);});addRenderableWidget(nameInput);
        int manageY=compact?170:240,manageH=compact?16:18,manageW=compact?62:96;
        rename=button(nameX,manageY,manageW,manageH,tr("rename"),b->{
            if(searchPending())return;
            cancelConfirmation();awaitingName=true;nameReplyVersion=menu.responseVersion;
            send(BadgeBankMenu.RENAME,selectedId(),0,nameInput.getValue());
        });
        remove=button(nameX+manageW+4,manageY,manageW,manageH,tr("remove"),b->{
            if(searchPending())return;
            if(!confirmRemove){confirmRemove=true;confirmReissue=false;refresh();}
            else{send(BadgeBankMenu.REMOVE,selectedId(),1);cancelConfirmation();refresh();}
        });
        refresh();
        if(restoreSearchFocus){setInitialFocus(searchInput);searchInput.setCursorPosition(Math.min(searchCursor,searchDraft.length()));}
    }
    private static boolean blank(ItemStack badge){
        return BadgeBankData.isUnissuedBlank(badge);
    }
    private Component name(BadgeBankMessages.Entry entry){return entry.name().isEmpty()?tr(entry.masterLevel()>0?"unnamed_master":"unnamed_badge"):Component.literal(entry.name());}
    private Component kind(BadgeBankMessages.Entry entry){return tr(entry.masterLevel()>0?"type_master":entry.codeCount()>0?"type_codes":"type_unencoded");}
    @Override protected void containerTick(){
        super.containerTick();
        if(searchDebounce>0&&--searchDebounce==0&&!searchDraft.equals(lastSentSearch)){
            lastSentSearch=BadgeBankMenu.cleanQuery(searchDraft);send(BadgeBankMenu.SEARCH,"",0,lastSentSearch);
        }
        refresh();
    }
    private void refresh(){
        boolean pending=searchPending();if(pending)cancelConfirmation();
        ItemStack input=menu.input.getItem(0);
        if(!Objects.equals(observedSelection,menu.selectedId)||!ItemStack.isSameItemSameComponents(input,observedInput)){
            cancelConfirmation();codeCursor=0;observedSelection=menu.selectedId;observedInput=input.copy();
        }
        for(int i=0;i<rows.size();i++){
            Button row=rows.get(i);row.visible=i<menu.entries.size();row.active=row.visible&&!pending;
            if(row.visible){var entry=menu.entries.get(i);String prefix=entry.id().equals(menu.selectedId)?"> ":"  ";
                row.setMessage(Component.literal(prefix+font.plainSubstrByWidth(name(entry).getString(),row.getWidth()-16)));}
        }
        previous.active=menu.page>0&&!pending;next.active=menu.page+1<menu.pages&&!pending;
        clearSearch.active=!searchDraft.isEmpty();
        var selected=menu.selected();
        if(confirmReissue&&(selected==null||selected.stored()||!selected.copyAllowed()||!blank(input)))cancelConfirmation();
        if(confirmRemove&&selected==null)cancelConfirmation();
        boolean confirming=confirmReissue||confirmRemove;
        deposit.active=(confirming||menu.canDeposit)&&!pending;deposit.setMessage(tr(confirming?"cancel":"deposit"));
        withdraw.active=selected!=null&&selected.stored()&&!confirming&&!pending;
        reissue.active=selected!=null&&!selected.stored()&&selected.copyAllowed()&&blank(input)&&!confirmRemove&&!pending;
        reissue.setMessage(tr(confirmReissue?"confirm_copy":"reissue"));
        remove.active=selected!=null&&!confirmReissue&&!pending;remove.setMessage(tr(confirmRemove?"confirm_remove":"remove"));
        rename.active=selected!=null&&!confirming&&!pending;nameInput.setEditable(selected!=null&&!confirming&&!pending);
        refreshName(selected);
        copyCode.active=!menu.selectedCodes.isEmpty()&&!pending;
        copyCode.setMessage(menu.selectedCodes.size()>1?tr("copy_code_index",Math.floorMod(codeCursor,menu.selectedCodes.size())+1,menu.selectedCodes.size()):tr("copy_code"));
    }
    private void refreshName(BadgeBankMessages.Entry selected){
        String current=selected==null?"":selected.name();
        boolean identityChanged=!Objects.equals(observedNameSelection,menu.selectedId);
        boolean acknowledged=awaitingName&&menu.responseVersion>nameReplyVersion
            &&menu.status.equals("gui.industrial_facilities.badge_bank.archive_renamed");
        if(identityChanged||!current.equals(lastArchiveName)||acknowledged||!nameDirty){
            nameDraft=current;
            if(!nameInput.getValue().equals(current)){updatingName=true;nameInput.setValue(current);updatingName=false;}
            nameDirty=false;
            if(identityChanged||acknowledged)awaitingName=false;
        }
        observedNameSelection=menu.selectedId;lastArchiveName=current;
    }
    private static void slot(GuiGraphics g,int x,int y){g.fill(x-1,y-1,x+17,y+17,0xFF59636A);g.fill(x,y,x+16,y+16,0xFF11171B);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos,invX=compact?146:226,invY=compact?150:218;
        g.fill(x-1,y-1,x+imageWidth+1,y+imageHeight+1,0xFF6D777E);g.fill(x,y,x+imageWidth,y+imageHeight,0xFF1B2026);
        g.fill(x,y,x+imageWidth,y+24,0xFF6B512D);g.fill(x+6,y+26,x+imageWidth-6,y+imageHeight-6,0xFF242B32);
        int split=x+imageWidth/2;g.fill(split-2,y+28,split-1,y+(compact?111:146),0xFF46545D);
        slot(g,x+(compact?28:186),y+(compact?207:266));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+invX+col*18,y+invY+row*18);
        for(int col=0;col<9;col++)slot(g,x+invX+col*18,y+invY+58);
    }
    private void fit(GuiGraphics g,Component label,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(label.getString(),w),x,y,color,false);}
    private Component shownStatus(){
        if(searchPending())return tr("searching");
        if(confirmRemove)return tr(compact?"confirm_remove_short":"confirm_remove_hint");
        if(!confirmReissue&&menu.status.isEmpty()&&!searchDraft.isBlank())return tr("search_results",menu.matched,menu.total);
        return confirmReissue?tr("confirm_copy_hint"):menu.status.isEmpty()?tr(menu.total==0?"empty_hint":"hint"):status(menu.status);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        fit(g,title,10,8,92,0xFFFFFF);
        fit(g,tr("page",menu.page+1,menu.pages),imageWidth-70,8,40,TEXT);
        if(menu.entries.isEmpty())fit(g,tr(searchPending()?"searching":menu.total>0&&menu.matched==0?"search_no_results":"empty"),12,compact?46:62,imageWidth/2-24,MUTED);
        var selected=menu.selected();int dx=imageWidth/2+4,dw=imageWidth-dx-10,dy=compact?29:34,pitch=compact?12:15;
        if(selected!=null){
            fit(g,name(selected),dx,dy,dw,ACCENT);fit(g,kind(selected),dx,dy+pitch,dw,TEXT);
            fit(g,tr("level",selected.masterLevel()>0?Integer.toString(selected.masterLevel()):"—"),dx,dy+pitch*2,dw,MUTED);
            fit(g,tr("codes",selected.codeCount()),dx,dy+pitch*3,dw,MUTED);
            fit(g,selected.remaining()<0?tr("unlimited"):tr("remaining",selected.remaining()),dx,dy+pitch*4,dw,selected.remaining()==0?0xE58E81:MUTED);
            fit(g,tr(selected.stored()?"stored":"in_circulation"),dx,dy+pitch*5,dw,selected.stored()?ACCENT:MUTED);
        }else fit(g,tr("select"),dx,dy,dw,MUTED);
        int statusY=compact?138:181;
        if(compact)fit(g,shownStatus(),10,statusY,imageWidth-20,confirmReissue||confirmRemove?0xF2C07E:TEXT);
        else g.drawWordWrap(font,shownStatus(),10,statusY,imageWidth-20,confirmReissue||confirmRemove?0xF2C07E:TEXT);
        int invY=compact?150:218,invX=compact?146:226;
        fit(g,tr("input"),compact?10:12,compact?191:269,compact?126:166,ACCENT);
        if(!compact)fit(g,playerInventoryTitle,invX,invY-11,162,MUTED);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){onClose();return true;}
        if(searchInput!=null&&searchInput.isFocused())return searchInput.keyPressed(key,scan,modifiers);
        if(nameInput!=null&&nameInput.isFocused())return nameInput.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
        int statusY=topPos+(compact?138:181);
        if(mouseX>=leftPos+10&&mouseX<leftPos+imageWidth-10&&mouseY>=statusY&&mouseY<statusY+(compact?10:20))
            g.renderTooltip(font,font.split(shownStatus(),imageWidth-24),mouseX,mouseY);
        if(reissue!=null&&reissue.isMouseOver(mouseX,mouseY)&&!confirmReissue){
            var selected=menu.selected();g.renderTooltip(font,font.split(tr(selected!=null&&!selected.copyAllowed()?"copy_disabled":"copy_hint"),240),mouseX,mouseY);
        }
        if(remove!=null&&remove.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr(confirmRemove?"confirm_remove_hint":"remove_hint"),240),mouseX,mouseY);
        if(nameInput!=null&&nameInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("archive_name_help"),240),mouseX,mouseY);
        if(searchInput!=null&&searchInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("search_help"),240),mouseX,mouseY);
        if(clearSearch!=null&&clearSearch.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("search_clear"),mouseX,mouseY);
        if(copyCode!=null&&copyCode.active&&copyCode.isMouseOver(mouseX,mouseY)){
            int index=Math.floorMod(codeCursor,menu.selectedCodes.size());g.renderTooltip(font,Component.literal(menu.selectedCodes.get(index)),mouseX,mouseY);
        }
        for(int i=0;i<rows.size()&&i<menu.entries.size();i++)if(rows.get(i).isMouseOver(mouseX,mouseY)){
            var entry=menu.entries.get(i);g.renderTooltip(font,List.of(name(entry).getVisualOrderText(),tr(entry.foreign()?"foreign":"own").getVisualOrderText(),
                tr(entry.stored()?"stored":"in_circulation").getVisualOrderText(),tr(entry.copyAllowed()?"copy_enabled":"copy_disabled").getVisualOrderText()),mouseX,mouseY);break;
        }
    }
}
