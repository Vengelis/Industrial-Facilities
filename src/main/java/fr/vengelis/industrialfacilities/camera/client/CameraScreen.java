package fr.vengelis.industrialfacilities.camera.client;

import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import fr.vengelis.industrialfacilities.camera.ui.*;
import fr.vengelis.industrialfacilities.security.SecurityCameraEntity;
import fr.vengelis.industrialfacilities.security.CameraProgram;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Entirely native Minecraft widgets and GuiGraphics; no embedded browser or command text. */
public class CameraScreen extends AbstractContainerScreen<CameraMenu> {
    private static final int TEXT=0xE7E6ED,MUTED=0xA8A6B5,PURPLE=0xC3A0ED;
    private static final int ROW=20;
    private static final UUID NONE=new UUID(0,0);
    private record Palette(String token,String label,int color,boolean heading) {}
    private record Row(String path,String type,String label,int depth,String insertion) {}
    public record EditorRow(String path,String type,String label,int x,int y,int width,int height) {}
    public record PaletteRow(String token,String label,int x,int y,int width,int height,boolean enabled) {}
    private static final List<Palette> PALETTE=List.of(
        new Palette("","section_events",0,true),new Palette("event","event",0xFF69527F,false),
        new Palette("event_empty","event_empty",0xFF69527F,false),
        new Palette("","section_logic",0,true),new Palette("if","if",0xFF69527F,false),
        new Palette("and","and",0xFF385B6D,false),new Palette("or","or",0xFF385B6D,false),new Palette("not","not",0xFF385B6D,false),
        new Palette("","section_get",0,true),new Palette("get:type","get_type",0xFF385B6D,false),new Palette("get:category","get_category",0xFF385B6D,false),
        new Palette("get:name","get_name",0xFF385B6D,false),new Palette("get:uuid","get_uuid",0xFF385B6D,false),new Palette("get:team","get_team",0xFF385B6D,false),
        new Palette("get:distance","get_distance",0xFF385B6D,false),new Palette("get:health","get_health",0xFF385B6D,false),
        new Palette("","section_actions",0,true),new Palette("door_open","door_open",0xFF4D4B5C,false),new Palette("door_close","door_close",0xFF4D4B5C,false),
        new Palette("alarm_on","alarm_on",0xFF4D4B5C,false),new Palette("alarm_off","alarm_off",0xFF4D4B5C,false),
        new Palette("emitter_on","emitter_on",0xFF4D4B5C,false),new Palette("emitter_off","emitter_off",0xFF4D4B5C,false),
        new Palette("wait","wait",0xFF4D4B5C,false));
    private CompoundTag draft=CameraMenu.emptyProgram(),submitted;
    private final Deque<CompoundTag> undoHistory=new ArrayDeque<>();
    private final List<Row> rows=new ArrayList<>();
    private String selected="",insertPath="Events",localStatus="";
    private int paletteScroll,canvasScroll,seenRevision=-1,pendingSaveRequest,nextRequest=1,actionTicks,observedActionSerial=-1;
    private boolean loaded,dirty,waitingSave,invalidNumber;
    private int paneX,paneY,paletteWidth,canvasX,canvasWidth,paneHeight;
    private Button save,test,run,devices,preview,undo,up,down,remove,durationUnitButton,sweepToggleButton;
    private EditBox valueInput,durationInput,rangeInput,yawInput,pitchInput,sweepAngleInput,sweepPeriodInput;
    private String rangeDraft="16",yawDraft="0",pitchDraft="0",sweepAngleDraft="30",sweepPeriodDraft="8";
    private boolean sweepDraft=true;
    private boolean cameraLoaded,configDirty,showField,updatingConfig;
    private int lastCameraResponse=-1;
    private String dragToken,dragPath;
    private boolean dragging;
    private double pressX,pressY;
    private List<CameraMessages.Link> popup=List.of();
    private String popupMode="",popupPath="";
    private int popupScroll,popupSelected,popupX,popupY,popupWidth,popupHeight;

    public CameraScreen(CameraMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    private static Component tr(String key,Object... args){return Component.translatable("gui.industrial_facilities.camera."+key,args);}
    private static Component text(String value){return value.startsWith("gui.industrial_facilities.")?Component.translatable(value):Component.literal(value);}
    public CompoundTag draftProgram(){return draft.copy();}
    public int[] canvasBounds(){return new int[]{canvasX,paneY,canvasWidth,paneHeight};}
    public int[] paletteBounds(){return new int[]{paneX,paneY,paletteWidth,paneHeight};}
    public String selectedPath(){return selected;}
    public List<PaletteRow> paletteEntries(){
        List<PaletteRow> result=new ArrayList<>();for(int i=0;i<PALETTE.size();i++){Palette item=PALETTE.get(i);if(!item.heading)result.add(new PaletteRow(item.token,tr(item.label).getString(),paneX+3,paneY+i*ROW-paletteScroll,paletteWidth-8,ROW-2,paletteEnabled(item.token)));}return List.copyOf(result);
    }
    public PaletteRow paletteRow(String token){return paletteEntries().stream().filter(row->row.token().equals(token)).findFirst().orElse(null);}
    public List<EditorRow> rowsSnapshot(){
        List<EditorRow> result=new ArrayList<>();for(int i=0;i<rows.size();i++){Row row=rows.get(i);int x=canvasX+4+Math.min(6,row.depth)*11;result.add(new EditorRow(row.path,row.type,row.label,x,paneY+i*ROW-canvasScroll,Math.max(22,canvasX+canvasWidth-x-6),ROW-2));}return List.copyOf(result);
    }
    private Button button(int x,int y,int w,int h,Component label,Button.OnPress action){return addRenderableWidget(Button.builder(label,action).bounds(leftPos+x,topPos+y,w,h).build());}
    private Component fittedLabel(String fullKey,String shortKey,int width){Component full=tr(fullKey);return font.width(full)>width-8?tr(shortKey):full;}
    private Button commandButton(int x,int width,String fullKey,String shortKey,Button.OnPress action){Button control=button(x,26,width,20,fittedLabel(fullKey,shortKey,width),action);control.setTooltip(Tooltip.create(tr(fullKey)));return control;}
    private int send(int action,int value,CompoundTag program){int request=nextRequest++;PacketDistributor.sendToServer(new CameraMessages.Action(menu.containerId,action,value,1,0,0,program,request));return request;}
    @Override protected void init(){
        imageWidth=Math.min(980,width-12);imageHeight=Math.min(menu.mode==CameraMenu.PROGRAM?620:320,height-12);super.init();
        paneX=leftPos+8;paneY=topPos+52;paletteWidth=Math.max(78,(imageWidth-20)/4);canvasX=paneX+paletteWidth+6;canvasWidth=imageWidth-16-paletteWidth-6;paneHeight=Math.max(40,imageHeight-142);
        sync();rebuildRows();rebuildControls();
    }
    private void sync(){
        if(menu.actionSerial!=observedActionSerial){observedActionSerial=menu.actionSerial;actionTicks=menu.status.isEmpty()?0:100;}
        if(menu.mode==CameraMenu.CAMERA){
            if(!cameraLoaded||(!configDirty&&lastCameraResponse!=menu.responseVersion)){
                rangeDraft=Integer.toString(menu.range);yawDraft=Float.toString(menu.yaw);pitchDraft=Float.toString(menu.pitch);sweepDraft=menu.sweepEnabled;sweepAngleDraft=Float.toString(menu.sweepAngle);sweepPeriodDraft=formatTicks(menu.sweepPeriodTicks,"seconds");cameraLoaded=true;
                if(rangeInput!=null){updatingConfig=true;rangeInput.setValue(rangeDraft);yawInput.setValue(yawDraft);pitchInput.setValue(pitchDraft);sweepAngleInput.setValue(sweepAngleDraft);sweepPeriodInput.setValue(sweepPeriodDraft);updatingConfig=false;}
                if(sweepToggleButton!=null)sweepToggleButton.setMessage(tr(sweepDraft?"sweep_on":"sweep_off"));
            }
            lastCameraResponse=menu.responseVersion;return;
        }
        if(waitingSave&&menu.lastRequestId==pendingSaveRequest){
            waitingSave=false;
            if(menu.program.equals(submitted)){draft=menu.program.copy();dirty=false;seenRevision=menu.revision;localStatus="";rebuildRows();rebuildControls();}
        }
        if(!loaded||(!dirty&&!waitingSave&&seenRevision!=menu.revision)){
            draft=menu.program.copy();seenRevision=menu.revision;loaded=true;selected="";insertPath="Events";undoHistory.clear();rebuildRows();rebuildControls();
        }
    }
    @Override protected void containerTick(){super.containerTick();sync();if(actionTicks>0)actionTicks--;updateControls();if(showField&&menu.mode==CameraMenu.CAMERA)updateField();}
    private void rebuildControls(){
        if(getFocused()!=null)getFocused().setFocused(false);
        setFocused(null);
        clearWidgets();valueInput=null;durationInput=null;durationUnitButton=null;rangeInput=null;yawInput=null;pitchInput=null;sweepAngleInput=null;sweepPeriodInput=null;sweepToggleButton=null;
        if(menu.mode==CameraMenu.CAMERA){cameraControls();return;}
        int w=Math.min(94,(imageWidth-40)/6),x=10;
        save=commandButton(x,w,"save","save_short",b->saveDraft());x+=w+4;
        run=commandButton(x,w,menu.running?"pause":"run",menu.running?"pause":"run_short",b->{if(!waitingSave&&(menu.running||!dirty&&!invalidNumber))send(CameraMenu.RUNNING,menu.running?0:1,CameraMenu.emptyProgram());});x+=w+4;
        test=commandButton(x,w,"test","test_short",b->{if(!invalidNumber){localStatus="";send(CameraMenu.TEST,0,draft);}});x+=w+4;
        devices=commandButton(x,w,"devices","devices_short",b->openPopup("devices",""));x+=w+4;
        preview=commandButton(x,w,"preview","preview_short",b->{if(CameraUiClient.fieldPreview!=null){CameraUiClient.fieldPreview=null;localStatus="gui.industrial_facilities.camera.preview_off";}else openPopup("preview","camera");});x+=w+4;
        undo=commandButton(x,w,"undo","undo",b->undo());
        int y=imageHeight-82;
        up=button(imageWidth-108,y,22,18,Component.literal("↑"),b->reorder(-1));
        down=button(imageWidth-82,y,22,18,Component.literal("↓"),b->reorder(1));
        remove=button(imageWidth-56,y,46,18,tr("delete"),b->deleteSelected());
        Tag tag=at(selected);
        if(tag instanceof CompoundTag node){
            if(selected.startsWith("Events/")&&node.hasUUID("Camera")){
                boolean empty=node.getString("Trigger").equals("empty");int targetWidth=empty?(imageWidth-28)/2:imageWidth-20;
                button(10,y+23,targetWidth,18,tr("target_camera",linkName(node.getUUID("Camera"))),b->openPopup("target","camera"));
                if(empty)durationControls(node,14+targetWidth,y+23,imageWidth-targetWidth-24,false);
            }else if(node.getString("Op").equals("wait")){
                durationControls(node,10,y+23,imageWidth-20,true);
            }else if(node.getString("Op").equals("action")){
                String action=node.getString("Action"),kind=actionKind(action);
                button(10,y+23,Math.max(100,(imageWidth-28)/2),18,tr("target",linkName(node.getUUID("Target"))),b->openPopup("target",kind));
                button(14+(imageWidth-28)/2,y+23,(imageWidth-28)/2,18,tr(action),b->{remember();node.putString("Action",opposite(action));changed();});
            }else if(node.getString("Op").equals("if")){
                int bw=(imageWidth-28)/3;
                button(10,y+23,bw,18,tr("condition"),b->{selected+="/Condition";rebuildControls();});
                button(14+bw,y+23,bw,18,tr("then"),b->{insertPath=selected+"/Then";localStatus="gui.industrial_facilities.camera.insertion_selected";});
                button(18+bw*2,y+23,bw,18,tr("else"),b->{insertPath=selected+"/Else";localStatus="gui.industrial_facilities.camera.insertion_selected";});
            }else if(node.getString("Op").equals("compare")){
                String getter=node.getString("Getter");
                button(10,y+23,104,18,tr("get_"+getter),b->cycleGetter(node));
                button(118,y+23,52,18,tr("test_"+node.getString("Test")),b->cycleTest(node));
                if(getter.equals("category"))button(174,y+23,imageWidth-184,18,tr("category_"+node.getString("Text")),b->cycleCategory(node));
                else{
                    valueInput=new EditBox(font,leftPos+174,topPos+y+23,imageWidth-184,18,tr("comparison_value"));valueInput.setMaxLength(getter.equals("distance")||getter.equals("health")?12:64);
                    boolean numeric=numeric(getter);valueInput.setValue(numeric?Double.toString(node.getDouble("Number")):node.getString("Text"));
                    if(numeric)valueInput.setFilter(s->s.matches("[+-]?[0-9]*([.,][0-9]*)?"));
                    valueInput.setResponder(value->{
                        if(!menu.editable||waitingSave)return;
                        if(numeric){try{double parsed=Double.parseDouble(value.replace(',','.'));if(!Double.isFinite(parsed)){invalidNumber=true;return;}if(parsed!=node.getDouble("Number")){remember();node.putDouble("Number",parsed);dirty=!draft.equals(menu.program);rebuildRows();}invalidNumber=false;}catch(NumberFormatException e){invalidNumber=true;}}
                        else if(!value.equals(node.getString("Text"))){remember();node.putString("Text",value);dirty=!draft.equals(menu.program);rebuildRows();}
                        localStatus="";updateControls();
                    });addRenderableWidget(valueInput);
                }
            }else if(List.of("and","or","not").contains(node.getString("Op"))){
                if(node.getString("Op").equals("not"))button(10,y+23,imageWidth-20,18,tr("operand"),b->{selected+="/Value";rebuildControls();});
                else{int bw=(imageWidth-24)/2;button(10,y+23,bw,18,tr("operand_left"),b->{selected+="/Left";rebuildControls();});button(14+bw,y+23,bw,18,tr("operand_right"),b->{selected+="/Right";rebuildControls();});}
            }
        }
        updateControls();
    }
    private void updateControls(){
        if(menu.mode!=CameraMenu.PROGRAM)return;
        if(save==null)return;
        save.active=menu.editable&&dirty&&!waitingSave&&!invalidNumber;test.active=menu.editable&&!waitingSave&&!invalidNumber;
        run.active=menu.editable&&!waitingSave&&(menu.running||!dirty&&!invalidNumber&&!draft.getList("Events",Tag.TAG_COMPOUND).isEmpty());run.setMessage(fittedLabel(menu.running?"pause":"run",menu.running?"pause":"run_short",run.getWidth()));run.setTooltip(Tooltip.create(tr(menu.running?"pause":"run")));
        devices.active=!menu.links.isEmpty();preview.active=CameraUiClient.fieldPreview!=null||menu.links.stream().anyMatch(l->l.kind().equals("camera"));undo.active=!undoHistory.isEmpty()&&!waitingSave;
        ListRef ref=listRef(selected);up.active=menu.editable&&!waitingSave&&ref!=null&&ref.index>0;down.active=menu.editable&&!waitingSave&&ref!=null&&ref.index+1<ref.list.size();remove.active=menu.editable&&!waitingSave&&!selected.isEmpty()&&at(selected)!=null;
        for(var child:children())if(child instanceof Button button&&button.getY()>=topPos+imageHeight-59)button.active=menu.editable&&!waitingSave;
        if(valueInput!=null)valueInput.setEditable(menu.editable&&!waitingSave);
        if(durationInput!=null)durationInput.setEditable(menu.editable&&!waitingSave);
        if(durationUnitButton!=null)durationUnitButton.active=menu.editable&&!waitingSave&&!invalidNumber;
    }
    private void saveDraft(){if(!menu.editable||invalidNumber||waitingSave)return;normalizeEntryTriggers(draft);waitingSave=true;submitted=draft.copy();localStatus="";pendingSaveRequest=send(CameraMenu.SAVE,0,draft);updateControls();}
    private static void normalizeEntryTriggers(CompoundTag program){for(Tag tag:program.getList("Events",Tag.TAG_COMPOUND))if(tag instanceof CompoundTag event&&event.getString("Trigger").equals("entry"))event.remove("Trigger");}
    private Tag at(String path){
        if(path==null||path.isEmpty())return null;Tag tag=draft;
        try{for(String part:path.split("/")){if(tag instanceof CompoundTag compound)tag=compound.get(part);else if(tag instanceof ListTag list)tag=list.get(Integer.parseInt(part));else return null;}return tag;}
        catch(IndexOutOfBoundsException|NumberFormatException ignored){return null;}
    }
    private void replace(String path,CompoundTag value){
        int end=path.lastIndexOf('/');if(end<0)return;Tag parent=at(path.substring(0,end));String key=path.substring(end+1);
        if(parent instanceof CompoundTag compound)compound.put(key,value);else if(parent instanceof ListTag list)list.set(Integer.parseInt(key),value);
    }
    private record ListRef(ListTag list,int index,String path) {}
    private ListRef listRef(String path){
        int split=path.lastIndexOf('/');if(split<0)return null;Tag parent=at(path.substring(0,split));
        try{int index=Integer.parseInt(path.substring(split+1));return parent instanceof ListTag list&&index>=0&&index<list.size()?new ListRef(list,index,path.substring(0,split)):null;}catch(NumberFormatException ignored){return null;}
    }
    private static CompoundTag expression(String getter){
        CompoundTag node=new CompoundTag();node.putString("Op","compare");node.putString("Getter",getter);node.putString("Test",numeric(getter)?"le":"eq");
        if(numeric(getter))node.putDouble("Number",getter.equals("distance")?4:10);else node.putString("Text",getter.equals("category")?"player":getter.equals("type")?"minecraft:player":"");return node;
    }
    private static boolean numeric(String getter){return getter.equals("distance")||getter.equals("health");}
    private static String timeUnit(CompoundTag node){return node.getString("Unit").equals("minutes")?"minutes":"seconds";}
    private static String formatTicks(int ticks,String unit){return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(unit.equals("minutes")?1200:20),6,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private String durationLabel(CompoundTag node){return formatTicks(node.getInt("Ticks"),timeUnit(node))+" "+tr(timeUnit(node).equals("minutes")?"unit_minutes_short":"unit_seconds_short").getString();}
    private void durationControls(CompoundTag node,int x,int y,int width,boolean wait){
        String unit=timeUnit(node);int valueWidth=Math.max(42,(width-4)/2);
        durationInput=new EditBox(font,leftPos+x,topPos+y,valueWidth,18,tr(wait?"wait_duration":"empty_delay"));durationInput.setMaxLength(12);durationInput.setValue(formatTicks(node.getInt("Ticks"),unit));durationInput.setFilter(s->s.matches("[+]?[0-9]*([.,][0-9]*)?"));
        durationInput.setResponder(value->{
            if(!menu.editable||waitingSave)return;
            try{
                BigDecimal amount=new BigDecimal(value.replace(',','.'));BigDecimal ticks=amount.multiply(BigDecimal.valueOf(unit.equals("minutes")?1200:20)).setScale(0,RoundingMode.HALF_UP);
                if(amount.signum()<0||ticks.compareTo(BigDecimal.valueOf(wait?1:0))<0||ticks.compareTo(BigDecimal.valueOf(CameraProgram.MAX_DURATION_TICKS))>0)throw new ArithmeticException();
                int count=ticks.intValueExact();
                if(!node.contains("Ticks")||node.getInt("Ticks")!=count||!node.getString("Unit").equals(unit)){remember();node.putInt("Ticks",count);node.putString("Unit",unit);dirty=!draft.equals(menu.program);rebuildRows();}
                invalidNumber=false;localStatus="";
            }catch(NumberFormatException|ArithmeticException error){invalidNumber=true;localStatus="gui.industrial_facilities.camera.invalid_duration_input";}
            updateControls();
        });addRenderableWidget(durationInput);
        durationUnitButton=button(x+valueWidth+4,y,width-valueWidth-4,18,tr(unit.equals("minutes")?"unit_minutes":"unit_seconds"),b->{
            if(!menu.editable||waitingSave||invalidNumber)return;remember();node.putInt("Ticks",node.getInt("Ticks"));node.putString("Unit",unit.equals("seconds")?"minutes":"seconds");changed();
        });durationUnitButton.setTooltip(Tooltip.create(tr(wait?"wait_duration":"empty_delay")));
    }
    private String actionKind(String action){return action.startsWith("door_")?"door":action.startsWith("alarm_")?"alarm":"emitter";}
    private String opposite(String action){return switch(action){case "door_open"->"door_close";case "door_close"->"door_open";case "alarm_on"->"alarm_off";case "alarm_off"->"alarm_on";case "emitter_on"->"emitter_off";default->"emitter_on";};}
    private UUID firstLink(String kind){return menu.links.stream().filter(l->l.kind().equals(kind)).map(CameraMessages.Link::id).findFirst().orElse(null);}
    private String linkName(UUID id){return menu.links.stream().filter(l->l.id().equals(id)).map(CameraMessages.Link::name).findFirst().orElse(tr("unlinked").getString());}
    private static boolean eventToken(String token){return "event".equals(token)||"event_empty".equals(token);}
    private static boolean conditionToken(String token){return token.startsWith("get:")||List.of("and","or","not").contains(token);}
    private static boolean entityToken(String token){return token.equals("if")||conditionToken(token);}
    private boolean emptyEvent(String path){
        if(path==null||!path.startsWith("Events/"))return false;String[] parts=path.split("/");
        return parts.length>=2&&at("Events/"+parts[1]) instanceof CompoundTag event&&event.getString("Trigger").equals("empty");
    }
    private boolean entityUnavailable(String token){
        if(token.equals("if"))return emptyEvent(insertPath)||emptyEvent(selected);
        return conditionToken(token)&&emptyEvent(selected.isEmpty()?insertPath:selected);
    }
    private boolean paletteEnabled(String token){
        if(!menu.editable||waitingSave)return false;
        if(eventToken(token))return firstLink("camera")!=null;
        if(entityUnavailable(token))return false;
        if(token.contains("_"))return firstLink(actionKind(token))!=null;
        return true;
    }
    private int semanticCount(Tag tag){
        if(tag instanceof CompoundTag c){int count=c.contains("Op")||c.hasUUID("Camera")?1:0;for(String key:c.getAllKeys())count+=semanticCount(c.get(key));return count;}
        if(tag instanceof ListTag list){int count=0;for(Tag child:list)count+=semanticCount(child);return count;}return 0;
    }
    private int semanticDepth(Tag tag){
        if(tag instanceof CompoundTag c){int max=0;for(String key:c.getAllKeys())max=Math.max(max,semanticDepth(c.get(key)));return max+(c.contains("Op")||c.hasUUID("Camera")?1:0);}
        if(tag instanceof ListTag list){int max=0;for(Tag child:list)max=Math.max(max,semanticDepth(child));return max;}return 0;
    }
    private void remember(){undoHistory.addLast(draft.copy());while(undoHistory.size()>20)undoHistory.removeFirst();}
    private void changed(){
        if(semanticCount(draft)>64||semanticDepth(draft)>6){if(!undoHistory.isEmpty())draft=undoHistory.removeLast();localStatus="gui.industrial_facilities.camera.editor_limit";}
        else{dirty=!draft.equals(menu.program);localStatus="";}popup=List.of();invalidNumber=false;rebuildRows();rebuildControls();
    }
    private void undo(){if(waitingSave||undoHistory.isEmpty())return;draft=undoHistory.removeLast();dirty=!draft.equals(menu.program);selected="";insertPath="Events";localStatus="";invalidNumber=false;rebuildRows();rebuildControls();}
    /** Called by the same click/drag paths as the visible palette; useful for deterministic UI checks. */
    public void editorAdd(String token){
        if(PALETTE.stream().noneMatch(item->!item.heading&&item.token.equals(token)))return;
        if(entityUnavailable(token)){localStatus="gui.industrial_facilities.camera.empty_actions_only";return;}
        if(!paletteEnabled(token))return;
        if(conditionToken(token)){
            String path=selected;Tag current=at(path);
            if(current instanceof CompoundTag n&&n.getString("Op").equals("if")){path+="/Condition";current=at(path);}
            if(!(current instanceof CompoundTag old)||!List.of("compare","and","or","not").contains(old.getString("Op"))){localStatus="gui.industrial_facilities.camera.select_condition";return;}
            remember();CompoundTag replacement;
            if(token.startsWith("get:"))replacement=expression(token.substring(4));
            else{replacement=new CompoundTag();replacement.putString("Op",token);replacement.put(token.equals("not")?"Value":"Left",old.copy());if(!token.equals("not"))replacement.put("Right",expression("category"));}
            replace(path,replacement);selected=path+(token.equals("and")||token.equals("or")?"/Right":"");changed();return;
        }
        CompoundTag node=new CompoundTag();
        if(eventToken(token)){
            ListTag events=draft.getList("Events",Tag.TAG_COMPOUND);if(events.size()>=CameraProgram.MAX_EVENTS){localStatus="gui.industrial_facilities.camera.editor_limit";return;}
            node.putUUID("Camera",firstLink("camera"));if(token.equals("event_empty"))node.putString("Trigger","empty");node.put("Body",new ListTag());remember();events.add(node);selected="Events/"+(events.size()-1);insertPath=selected+"/Body";changed();return;
        }
        if(!(at(insertPath) instanceof ListTag destination)||insertPath.equals("Events")){localStatus="gui.industrial_facilities.camera.select_branch";return;}
        if(token.equals("if")){node.putString("Op","if");node.put("Condition",expression("category"));node.put("Then",new ListTag());node.put("Else",new ListTag());}
        else if(token.equals("wait")){node.putString("Op","wait");node.putInt("Ticks",20);node.putString("Unit","seconds");}
        else{UUID target=firstLink(actionKind(token));if(target==null)return;node.putString("Op","action");node.putString("Action",token);node.putUUID("Target",target);}
        remember();destination.add(node);selected=insertPath+"/"+(destination.size()-1);if(token.equals("if"))insertPath=selected+"/Then";changed();
    }
    private void deleteSelected(){
        if(!menu.editable||waitingSave)return;ListRef ref=listRef(selected);
        if(ref!=null){remember();ref.list.remove(ref.index);selected="";insertPath=ref.path;changed();}
        else if(at(selected) instanceof CompoundTag node&&List.of("compare","and","or","not").contains(node.getString("Op"))){remember();replace(selected,expression("category"));changed();}
    }
    private void reorder(int delta){
        if(!menu.editable||waitingSave)return;
        ListRef ref=listRef(selected);if(ref==null||ref.index+delta<0||ref.index+delta>=ref.list.size())return;
        String previous=selected,next=ref.path+"/"+(ref.index+delta);
        remember();Tag node=ref.list.remove(ref.index);ref.list.add(ref.index+delta,node);
        // List indices move with both exchanged subtrees, including their active insertion slots.
        insertPath=swapPrefix(insertPath,previous,next);selected=next;changed();revealSelected();
    }
    private static String swapPrefix(String path,String first,String second){
        if(path.equals(first)||path.startsWith(first+"/"))return second+path.substring(first.length());
        if(path.equals(second)||path.startsWith(second+"/"))return first+path.substring(second.length());
        return path;
    }
    private void cycleGetter(CompoundTag node){
        if(!menu.editable||waitingSave)return;
        List<String> choices=List.of("type","category","name","uuid","team","distance","health");String getter=choices.get((choices.indexOf(node.getString("Getter"))+1)%choices.size());
        remember();replace(selected,expression(getter));changed();
    }
    private void cycleTest(CompoundTag node){
        if(!menu.editable||waitingSave)return;
        List<String> choices=numeric(node.getString("Getter"))?List.of("eq","ne","lt","le","gt","ge"):List.of("eq","ne");
        remember();node.putString("Test",choices.get((choices.indexOf(node.getString("Test"))+1)%choices.size()));changed();
    }
    private void cycleCategory(CompoundTag node){if(!menu.editable||waitingSave)return;List<String> values=List.of("player","animal","hostile","other");remember();node.putString("Text",values.get((values.indexOf(node.getString("Text"))+1)%values.size()));changed();}
    private void rebuildRows(){
        rows.clear();ListTag events=draft.getList("Events",Tag.TAG_COMPOUND);
        for(int i=0;i<events.size();i++){String path="Events/"+i;CompoundTag event=events.getCompound(i);boolean empty=event.getString("Trigger").equals("empty");String label=empty?tr("event_empty_delay_target",linkName(event.hasUUID("Camera")?event.getUUID("Camera"):NONE),durationLabel(event)).getString():tr("event_target",linkName(event.hasUUID("Camera")?event.getUUID("Camera"):NONE)).getString();rows.add(new Row(path,"event",label,0,path+"/Body"));statements(event.getList("Body",Tag.TAG_COMPOUND),path+"/Body",1);rows.add(new Row(path+"/Body","insert",tr(empty?"add_action_here":"add_here").getString(),1,path+"/Body"));}
        canvasScroll=Math.clamp(canvasScroll,0,Math.max(0,rows.size()*ROW-paneHeight));paletteScroll=Math.clamp(paletteScroll,0,Math.max(0,PALETTE.size()*ROW-paneHeight));
    }
    private void statements(ListTag list,String base,int depth){
        for(int i=0;i<list.size();i++){
            CompoundTag node=list.getCompound(i);String path=base+"/"+i;
            if(node.getString("Op").equals("if")){
                rows.add(new Row(path,"if",tr("if").getString(),depth,path+"/Then"));condition(node.getCompound("Condition"),path+"/Condition",depth+1);
                rows.add(new Row(path+"/Then","insert",tr("then_add").getString(),depth+1,path+"/Then"));statements(node.getList("Then",Tag.TAG_COMPOUND),path+"/Then",depth+1);
                rows.add(new Row(path+"/Else","insert",tr("else_add").getString(),depth+1,path+"/Else"));statements(node.getList("Else",Tag.TAG_COMPOUND),path+"/Else",depth+1);
                rows.add(new Row(path,"end",tr("end_if").getString(),depth,""));
            }else if(node.getString("Op").equals("wait"))rows.add(new Row(path,"wait",tr("wait_for",durationLabel(node)).getString(),depth,base));
            else rows.add(new Row(path,"action",tr(node.getString("Action")).getString()+" ["+linkName(node.hasUUID("Target")?node.getUUID("Target"):NONE)+"]",depth,base));
        }
    }
    private void condition(CompoundTag node,String path,int depth){
        String op=node.getString("Op"),label;
        if(op.equals("compare")){
            String getter=node.getString("Getter");String value=numeric(getter)?Double.toString(node.getDouble("Number")):getter.equals("category")?tr("category_"+node.getString("Text")).getString():node.getString("Text");
            label="GET "+tr("get_"+getter).getString()+" "+tr("test_"+node.getString("Test")).getString()+" ["+value+"]";
        }else label=tr(op).getString();
        rows.add(new Row(path,"expr",label,depth,""));
        if(op.equals("and")||op.equals("or")){condition(node.getCompound("Left"),path+"/Left",depth+1);condition(node.getCompound("Right"),path+"/Right",depth+1);}
        else if(op.equals("not"))condition(node.getCompound("Value"),path+"/Value",depth+1);
    }
    private void revealSelected(){for(int i=0;i<rows.size();i++)if(rows.get(i).path.equals(selected)){if(i*ROW<canvasScroll)canvasScroll=i*ROW;else if((i+1)*ROW>canvasScroll+paneHeight)canvasScroll=(i+1)*ROW-paneHeight;break;}}
    private Row rowAt(double x,double y){
        if(x<canvasX||x>=canvasX+canvasWidth||y<paneY||y>=paneY+paneHeight)return null;int index=(int)(y-paneY+canvasScroll)/ROW;return index>=0&&index<rows.size()?rows.get(index):null;
    }
    private void select(Row row){
        if(row==null)return;selected=row.type.equals("insert")?"":row.path;if(!row.insertion.isEmpty())insertPath=row.insertion;
        localStatus=row.type.equals("insert")?"gui.industrial_facilities.camera.insertion_selected":"";invalidNumber=false;rebuildControls();
    }
    private void drop(String token,String path,Row row){
        if(row==null||!menu.editable||waitingSave)return;
        if(token!=null){
            if(entityToken(token)&&emptyEvent(row.insertion.isEmpty()?row.path:row.insertion)){localStatus="gui.industrial_facilities.camera.empty_actions_only";return;}
            if(conditionToken(token)){
                if(!row.type.equals("expr")&&!row.type.equals("if")){localStatus="gui.industrial_facilities.camera.select_condition";return;}selected=row.path;
            }else if(!row.insertion.isEmpty())insertPath=row.insertion;
            else if(!eventToken(token)){localStatus="gui.industrial_facilities.camera.select_branch";return;}
            editorAdd(token);return;
        }
        if(path==null||row.insertion.isEmpty()||row.insertion.startsWith(path+"/"))return;
        if(emptyEvent(row.insertion)&&at(path) instanceof CompoundTag source&&source.getString("Op").equals("if")){localStatus="gui.industrial_facilities.camera.empty_actions_only";return;}
        ListRef ref=listRef(path);Tag target=at(row.insertion);if(ref==null||!(target instanceof ListTag destination)||path.matches("Events/[0-9]+"))return;
        remember();Tag node=ref.list.remove(ref.index);destination.add(node);selected="";insertPath="Events";changed();
    }
    private void openPopup(String mode,String kind){
        popup=menu.links.stream().filter(l->kind.isEmpty()||l.kind().equals(kind)).toList();popupMode=mode;popupPath=selected;popupScroll=0;popupSelected=0;
        if(popup.isEmpty()){localStatus="gui.industrial_facilities.camera.no_devices";return;}
        popupWidth=Math.min(270,imageWidth-20);popupHeight=Math.min(popup.size()*ROW,Math.max(ROW,imageHeight-110));popupX=leftPos+imageWidth-popupWidth-10;popupY=topPos+52;
    }
    private void chooseLink(CameraMessages.Link link){
        if(popupMode.equals("target")&&!waitingSave&&menu.editable&&at(popupPath) instanceof CompoundTag node){remember();node.putUUID(node.hasUUID("Camera")?"Camera":"Target",link.id());selected=popupPath;changed();}
        else if(popupMode.equals("preview")||popupMode.equals("devices")&&link.kind().equals("camera"))previewLink(link);
        else localStatus=link.name()+" · "+tr(link.available()?"online":"offline").getString();
        popup=List.of();
    }
    private void previewLink(CameraMessages.Link link){
        if(minecraft.level!=null&&minecraft.level.hasChunkAt(link.position())&&minecraft.level.getBlockEntity(link.position()) instanceof SecurityCameraEntity camera){CameraUiClient.fieldPreview=new CameraUiClient.FieldPreview(link.position(),camera.range,camera.yaw,camera.pitch,camera.sweepEnabled,camera.sweepAngle,camera.sweepPeriodTicks);localStatus=tr("preview_for",link.name()).getString();}
        else localStatus="gui.industrial_facilities.camera.offline";
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&!popup.isEmpty()){
            if(x>=popupX&&x<popupX+popupWidth&&y>=popupY&&y<popupY+popupHeight){int index=(int)(y-popupY+popupScroll)/ROW;if(index<popup.size())chooseLink(popup.get(index));return true;}popup=List.of();return true;
        }
        if(menu.mode==CameraMenu.PROGRAM&&button==0){
            if(x>=paneX&&x<paneX+paletteWidth&&y>=paneY&&y<paneY+paneHeight){int index=(int)(y-paneY+paletteScroll)/ROW;if(index<PALETTE.size()){Palette item=PALETTE.get(index);if(!item.heading&&paletteEnabled(item.token)){dragToken=item.token;dragPath=null;pressX=x;pressY=y;dragging=false;}else if(!item.heading&&entityUnavailable(item.token))localStatus="gui.industrial_facilities.camera.empty_actions_only";}return true;}
            Row row=rowAt(x,y);if(row!=null){select(row);dragToken=null;dragPath=row.type.equals("event")||row.type.equals("if")||row.type.equals("action")||row.type.equals("wait")?row.path:null;pressX=x;pressY=y;dragging=false;return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&(dragToken!=null||dragPath!=null)){if(Math.hypot(x-pressX,y-pressY)>4)dragging=true;return true;}return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==0&&(dragToken!=null||dragPath!=null)){String token=dragToken,path=dragPath;boolean moved=dragging;dragToken=null;dragPath=null;dragging=false;if(moved){Row row=rowAt(x,y);if(row==null&&eventToken(token)&&x>=canvasX&&x<canvasX+canvasWidth&&y>=paneY&&y<paneY+paneHeight)editorAdd(token);else drop(token,path,row);}else if(token!=null)editorAdd(token);return true;}return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(!popup.isEmpty()){popupScroll=Math.clamp(popupScroll-(int)(vertical*ROW),0,Math.max(0,popup.size()*ROW-popupHeight));return true;}
        if(menu.mode==CameraMenu.PROGRAM&&y>=paneY&&y<paneY+paneHeight){
            if(x<canvasX)paletteScroll=Math.clamp(paletteScroll-(int)(vertical*ROW*2),0,Math.max(0,PALETTE.size()*ROW-paneHeight));
            else canvasScroll=Math.clamp(canvasScroll-(int)(vertical*ROW*2),0,Math.max(0,rows.size()*ROW-paneHeight));return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!popup.isEmpty()){
            if(key==256){popup=List.of();return true;}if(key==264||key==265){popupSelected=Math.clamp(popupSelected+(key==264?1:-1),0,popup.size()-1);popupScroll=Math.clamp(popupSelected*ROW,0,Math.max(0,popup.size()*ROW-popupHeight));return true;}if(key==257){chooseLink(popup.get(popupSelected));return true;}
        }
        if(key==256){onClose();return true;}
        for(EditBox input:new EditBox[]{valueInput,durationInput,rangeInput,yawInput,pitchInput,sweepAngleInput,sweepPeriodInput})if(input!=null&&input.isFocused())return input.keyPressed(key,scan,modifiers);
        if(menu.mode==CameraMenu.PROGRAM){
            if(key==261){deleteSelected();return true;}if(key==90&&hasControlDown()){undo();return true;}
            if(key==266||key==267){canvasScroll=Math.clamp(canvasScroll+(key==267?paneHeight:-paneHeight),0,Math.max(0,rows.size()*ROW-paneHeight));return true;}
            if((key==264||key==265)&&!rows.isEmpty()){int current=0;for(int i=0;i<rows.size();i++)if(rows.get(i).path.equals(selected)){current=i;break;}select(rows.get(Math.clamp(current+(key==264?1:-1),0,rows.size()-1)));revealSelected();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private void fit(GuiGraphics g,Component value,int x,int y,int w,int color){g.drawString(font,font.plainSubstrByWidth(value.getString(),Math.max(0,w)),x,y,color,false);}
    private static void outline(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x-1,y-1,x+imageWidth+1,y+imageHeight+1,0xFF777582);g.fill(x,y,x+imageWidth,y+imageHeight,0xFF23232B);g.fill(x,y,x+imageWidth,y+22,0xFF58436F);
        if(menu.mode==CameraMenu.CAMERA){renderCamera(g,partial);return;}
        g.fill(paneX,paneY,paneX+paletteWidth,paneY+paneHeight,0xFF303039);g.fill(canvasX,paneY,canvasX+canvasWidth,paneY+paneHeight,0xFF17181D);
        g.enableScissor(paneX,paneY,paneX+paletteWidth,paneY+paneHeight);
        for(int i=0;i<PALETTE.size();i++){Palette item=PALETTE.get(i);int ry=paneY+i*ROW-paletteScroll;if(ry+ROW<paneY||ry>paneY+paneHeight)continue;
            if(item.heading)fit(g,tr(item.label),paneX+4,ry+6,paletteWidth-8,PURPLE);
            else{g.fill(paneX+3,ry+2,paneX+paletteWidth-5,ry+ROW-1,paletteEnabled(item.token)?item.color:0xFF3B3B42);g.fill(paneX+12,ry,paneX+28,ry+3,0xFF303039);fit(g,tr(item.label),paneX+7,ry+6,paletteWidth-15,paletteEnabled(item.token)?TEXT:MUTED);}
        }g.disableScissor();
        g.enableScissor(canvasX,paneY,canvasX+canvasWidth,paneY+paneHeight);
        for(int gx=canvasX+8;gx<canvasX+canvasWidth;gx+=18)for(int gy=paneY+8-(canvasScroll%18);gy<paneY+paneHeight;gy+=18)g.fill(gx,gy,gx+1,gy+1,0xFF383740);
        for(int i=0;i<rows.size();i++)if(rows.get(i).type.equals("if")){
            Row start=rows.get(i);for(int j=i+1;j<rows.size();j++)if(rows.get(j).type.equals("end")&&rows.get(j).path.equals(start.path)){
                int barX=canvasX+4+Math.min(start.depth,6)*11;g.fill(barX,paneY+i*ROW-canvasScroll+3,barX+3,paneY+(j+1)*ROW-canvasScroll-1,0xFF654C7F);break;
            }
        }
        if(rows.isEmpty())fit(g,tr(menu.links.stream().anyMatch(l->l.kind().equals("camera"))?"empty_program":"link_camera_first"),canvasX+10,paneY+16,canvasWidth-20,MUTED);
        for(int i=0;i<rows.size();i++){
            Row row=rows.get(i);int ry=paneY+i*ROW-canvasScroll,rx=canvasX+4+Math.min(row.depth,6)*11,rw=Math.max(20,canvasX+canvasWidth-rx-7);if(ry+ROW<paneY||ry>paneY+paneHeight)continue;
            int color=row.type.equals("event")||row.type.equals("if")||row.type.equals("end")?0xFF654C7F:row.type.equals("expr")?0xFF345363:row.type.equals("insert")?0xFF25242F:0xFF494753;
            g.fill(rx,ry+1,rx+rw,ry+ROW-1,color);
            if(row.type.equals("if")||row.type.equals("event")){g.fill(rx+10,ry,rx+26,ry+4,0xFF17181D);g.fill(rx,ry+3,rx+3,ry+ROW,color);}
            if(row.type.equals("expr")){g.fill(rx,ry+1,rx+4,ry+4,0xFF17181D);g.fill(rx+rw-4,ry+ROW-4,rx+rw,ry+ROW-1,0xFF17181D);}
            if(row.type.equals("insert")&&row.insertion.equals(insertPath))outline(g,rx,ry+1,rw,ROW-2,0xFFAA87D0);
            if(row.path.equals(selected)&&!row.type.equals("end"))outline(g,rx,ry,rw,ROW,0xFFE0BEFF);
            fit(g,Component.literal(row.label),rx+6,ry+6,rw-12,row.type.equals("insert")?PURPLE:TEXT);
        }g.disableScissor();
        scrollbar(g,paneX+paletteWidth-3,paneY,paneHeight,PALETTE.size()*ROW,paletteScroll);scrollbar(g,canvasX+canvasWidth-3,paneY,paneHeight,rows.size()*ROW,canvasScroll);
        g.fill(leftPos+8,topPos+imageHeight-87,leftPos+imageWidth-8,topPos+imageHeight-27,0xFF2C2B34);
    }
    private static void scrollbar(GuiGraphics g,int x,int y,int height,int full,int scroll){if(full<=height)return;int thumb=Math.max(12,height*height/full),offset=(height-thumb)*scroll/Math.max(1,full-height);g.fill(x,y+offset,x+2,y+offset+thumb,0xFF8C809F);}
    private String runtimeWarning(){String value=menu.runtimeStatus;return value.isEmpty()||value.endsWith(".running")||value.endsWith(".stopped")||value.endsWith(".paused")?"":value;}
    private String visibleStatus(){
        if(invalidNumber)return localStatus.equals("gui.industrial_facilities.camera.invalid_duration_input")?localStatus:"gui.industrial_facilities.camera.invalid_number";
        if(localStatus.equals("gui.industrial_facilities.camera.empty_actions_only"))return localStatus;
        String warning=runtimeWarning();
        if(!warning.isEmpty())return text(warning).getString()+(actionTicks>0&&!menu.status.isEmpty()?" · "+text(menu.status).getString():"");
        if(waitingSave)return "gui.industrial_facilities.camera.saving";
        if(!localStatus.isEmpty())return localStatus;
        if(actionTicks>0&&!menu.status.isEmpty())return menu.status;
        if(dirty)return "gui.industrial_facilities.camera.unsaved";
        return menu.runtimeStatus.isEmpty()?"gui.industrial_facilities.camera.editor_hint":menu.runtimeStatus;
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        fit(g,title,10,7,imageWidth-130,0xFFFFFF);
        if(menu.mode==CameraMenu.CAMERA){cameraLabels(g);return;}
        fit(g,text(runtimeWarning().isEmpty()?tr(menu.running?"running_label":"paused_label").getString():runtimeWarning()),imageWidth-115,7,105,PURPLE);
        String info=selected.isEmpty()?tr("selection_hint").getString():rows.stream().filter(r->r.path.equals(selected)).map(Row::label).findFirst().orElse(tr("condition").getString());
        fit(g,Component.literal(info),12,imageHeight-77,imageWidth-130,TEXT);
        String status=visibleStatus();
        fit(g,text(status),10,imageHeight-17,imageWidth-20,invalidNumber?0xEDB795:MUTED);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);
        if(menu.mode==CameraMenu.PROGRAM){
            Row row=rowAt(mx,my);if(row!=null&&!dragging)g.renderTooltip(font,Component.literal(row.label),mx,my);
            if(mx>=paneX&&mx<paneX+paletteWidth&&my>=paneY&&my<paneY+paneHeight){int index=(my-paneY+paletteScroll)/ROW;if(index<PALETTE.size()&&!PALETTE.get(index).heading){Palette item=PALETTE.get(index);g.renderTooltip(font,entityUnavailable(item.token)?font.split(tr("empty_actions_only"),Math.min(300,imageWidth-20)):List.of(tr(item.label).getVisualOrderText()),mx,my);}}
            if(dragging){String label=dragToken!=null?PALETTE.stream().filter(p->p.token.equals(dragToken)).map(p->tr(p.label).getString()).findFirst().orElse(""):tr("move").getString();g.fill(mx+8,my+8,mx+18+font.width(label),my+27,0xEE58436F);g.drawString(font,label,mx+13,my+14,TEXT,false);}
        }
        if(!popup.isEmpty())renderPopup(g,mx,my);
        if(durationInput!=null&&durationInput.isMouseOver(mx,my))g.renderTooltip(font,font.split(tr(at(selected) instanceof CompoundTag node&&node.hasUUID("Camera")?"empty_delay":"wait_duration"),Math.min(300,imageWidth-20)),mx,my);
        if(mx>=leftPos+8&&mx<leftPos+imageWidth-8&&my>=topPos+imageHeight-23&&my<topPos+imageHeight-5){String status=menu.mode==CameraMenu.PROGRAM?visibleStatus():localStatus.isEmpty()?menu.status:localStatus;if(!status.isEmpty())g.renderTooltip(font,font.split(text(status),Math.min(360,imageWidth-20)),mx,my);}
    }
    private void renderPopup(GuiGraphics g,int mx,int my){
        g.fill(popupX-1,popupY-1,popupX+popupWidth+1,popupY+popupHeight+1,0xFFE0C0FF);g.fill(popupX,popupY,popupX+popupWidth,popupY+popupHeight,0xFF292632);
        g.enableScissor(popupX,popupY,popupX+popupWidth,popupY+popupHeight);
        for(int i=0;i<popup.size();i++){var link=popup.get(i);int ry=popupY+i*ROW-popupScroll;if(ry+ROW<popupY||ry>popupY+popupHeight)continue;
            if(i==popupSelected||mx>=popupX&&mx<popupX+popupWidth&&my>=ry&&my<ry+ROW)g.fill(popupX+1,ry,popupX+popupWidth-1,ry+ROW,0xFF51425F);
            fit(g,Component.literal(link.name()+" · "+tr(link.available()?"online":"offline").getString()),popupX+6,ry+6,popupWidth-12,link.available()?TEXT:MUTED);
        }g.disableScissor();
    }
    private void cameraControls(){
        int y=48,w=Math.max(48,(imageWidth-40)/3);
        rangeInput=configField(10,y,w,rangeDraft,tr("range"),s->rangeDraft=s);
        yawInput=configField(20+w,y,w,yawDraft,tr("yaw"),s->yawDraft=s);
        pitchInput=configField(30+w*2,y,w,pitchDraft,tr("pitch"),s->pitchDraft=s);
        sweepToggleButton=button(10,88,w,18,tr(sweepDraft?"sweep_on":"sweep_off"),b->{sweepDraft=!sweepDraft;configDirty=true;sweepToggleButton.setMessage(tr(sweepDraft?"sweep_on":"sweep_off"));if(showField)updateField();});
        sweepToggleButton.setTooltip(Tooltip.create(tr("sweep_hint")));
        sweepAngleInput=configField(20+w,88,w,sweepAngleDraft,tr("sweep_angle"),s->sweepAngleDraft=s);
        sweepPeriodInput=configField(30+w*2,88,w,sweepPeriodDraft,tr("sweep_period"),s->sweepPeriodDraft=s);
        button(10,imageHeight-52,(imageWidth-24)/2,20,tr("apply"),b->applyCamera());
        button(14+(imageWidth-24)/2,imageHeight-52,(imageWidth-24)/2,20,tr("field_preview"),b->{showField=!showField;if(!showField)CameraUiClient.fieldPreview=null;else updateField();});
    }
    private EditBox configField(int x,int y,int width,String value,Component label,java.util.function.Consumer<String> setter){
        EditBox input=new EditBox(font,leftPos+x,topPos+y,width,18,label);input.setMaxLength(8);input.setValue(value);input.setFilter(s->s.matches("[+-]?[0-9]*([.,][0-9]*)?"));
        input.setResponder(s->{setter.accept(s);if(!updatingConfig)configDirty=true;if(showField)updateField();});addRenderableWidget(input);return input;
    }
    private float[] parsedCamera(){
        try{int range=Integer.parseInt(rangeDraft);float yaw=Float.parseFloat(yawDraft.replace(',','.')),pitch=Float.parseFloat(pitchDraft.replace(',','.')),angle=Float.parseFloat(sweepAngleDraft.replace(',','.'));double period=Double.parseDouble(sweepPeriodDraft.replace(',','.'));
            return range>=1&&range<=16&&Float.isFinite(yaw)&&Float.isFinite(pitch)&&yaw>=-180&&yaw<=180&&pitch>=-80&&pitch<=80&&Float.isFinite(angle)&&angle>=0&&angle<=90&&Double.isFinite(period)&&period>=2&&period<=120?new float[]{range,yaw,pitch,angle,(int)Math.round(period*20)}:null;
        }catch(NumberFormatException e){return null;}
    }
    private void applyCamera(){float[] values=parsedCamera();if(values==null){localStatus="gui.industrial_facilities.camera.invalid_settings";return;}localStatus="";PacketDistributor.sendToServer(new CameraMessages.Action(menu.containerId,CameraMenu.CONFIGURE,0,(int)values[0],values[1],values[2],CameraMenu.emptyProgram(),nextRequest++,sweepDraft,values[3],(int)values[4]));configDirty=false;}
    private void updateField(){float[] values=parsedCamera();if(values!=null)CameraUiClient.fieldPreview=new CameraUiClient.FieldPreview(menu.position,(int)values[0],values[1],values[2],sweepDraft,values[3],(int)values[4]);}
    private static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int color){int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,err=dx+dy;while(true){g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int twice=2*err;if(twice>=dy){err+=dy;x0+=sx;}if(twice<=dx){err+=dx;y0+=sy;}}}
    private void renderCamera(GuiGraphics g,float partial){
        int px=leftPos+10,py=topPos+126,pw=imageWidth-20,ph=Math.max(8,imageHeight-209);g.fill(px,py,px+pw,py+ph,0xFF17181D);
        float[] values=parsedCamera();if(values==null)return;double heading=Math.toRadians(CameraUiClient.previewYaw(new CameraUiClient.FieldPreview(menu.position,(int)values[0],values[1],values[2],sweepDraft,values[3],(int)values[4]),partial)),scale=Math.min(pw,ph)*.43/16;int cx=px+pw/2,cy=py+ph/2;
        for(int range=4;range<=16;range+=4){int rr=(int)(range*scale);g.hLine(cx-rr,cx+rr,cy,0xFF3D364A);g.vLine(cx,cy-rr,cy+rr,0xFF3D364A);}
        if(sweepDraft)for(int side:new int[]{-1,1}){double edge=Math.toRadians(values[1]+side*(values[3]+45));line(g,cx,cy,cx+(int)(Math.sin(edge)*values[0]*scale),cy-(int)(Math.cos(edge)*values[0]*scale),0xFF665276);}
        int ax=cx+(int)(Math.sin(heading-Math.PI/4)*values[0]*scale),ay=cy-(int)(Math.cos(heading-Math.PI/4)*values[0]*scale);
        int bx=cx+(int)(Math.sin(heading+Math.PI/4)*values[0]*scale),by=cy-(int)(Math.cos(heading+Math.PI/4)*values[0]*scale);
        line(g,cx,cy,ax,ay,0xFFC29AEC);line(g,cx,cy,bx,by,0xFFC29AEC);line(g,ax,ay,bx,by,0xFF82639D);g.fill(cx-2,cy-2,cx+3,cy+3,0xFFD9B9FF);
    }
    private void cameraLabels(GuiGraphics g){
        int w=Math.max(48,(imageWidth-40)/3);fit(g,tr("range"),10,33,w,MUTED);fit(g,tr("yaw"),20+w,33,w,MUTED);fit(g,tr("pitch"),30+w*2,33,w,MUTED);
        fit(g,tr("sweep"),10,74,w,MUTED);fit(g,tr("sweep_angle"),20+w,74,w,MUTED);fit(g,tr("sweep_period"),30+w*2,74,w,MUTED);
        fit(g,tr("fov"),14,114,imageWidth-28,PURPLE);fit(g,tr("occlusion"),10,imageHeight-74,imageWidth-20,MUTED);
        fit(g,text(localStatus.isEmpty()?menu.status:localStatus),10,imageHeight-17,imageWidth-20,MUTED);
    }
    @Override public void removed(){CameraUiClient.fieldPreview=null;super.removed();}
}
