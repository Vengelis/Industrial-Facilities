package fr.vengelis.industrialfacilities.access.client;

import java.util.ArrayList;
import java.util.List;
import fr.vengelis.industrialfacilities.access.AccessBadges;
import fr.vengelis.industrialfacilities.access.ui.*;
import fr.vengelis.industrialfacilities.frequency.FrequencyChip;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.frequency.FrequencyEncoding;
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
    private static final int ENCODER_MARGIN = 14, CHIP_Y = 47, FREQUENCY_BUTTON_HEIGHT = 17;
    private boolean compact, compactControls,confirmClearLinks,confirmClearBadge;
    private int inventoryX, inventoryY, frequencyFormX, frequencyStep;
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
    private List<AbstractWidget> settings = List.of(), badgeWidgets = List.of(), frequencyWidgets = List.of();
    private Button badgesTab, frequenciesTab, encodePublic, encodePrivate, duplicateChip, renameChip, encodeSelection, chipMode;
    private FrequencyChip.Mode selectedChipMode = FrequencyChip.Mode.BOTH;
    private ItemStack observedChip = ItemStack.EMPTY;
    private FrequencyCataloguePanel cataloguePanel;
    private EditBox frequencyNumber, frequencyName;
    private String frequencyDraft = "", frequencyNameDraft = "";
    private boolean showingFrequencies;
    private final List<Button> readerLevels=new ArrayList<>(),masterLevels=new ArrayList<>(),ownerButtons=new ArrayList<>(),encodingButtons=new ArrayList<>();
    public AccessScreen(AccessMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=340;imageHeight=218;}
    private static Component tr(String key,Object... args){return Component.translatable("gui.industrial_facilities.access."+key,args);}
    private static Component status(String text){return text.startsWith("gui.industrial_facilities.")?Component.translatable(text):Component.literal(text);}
    private Button button(int x,int y,int width,int height,Component text,Button.OnPress action){return addRenderableWidget(TerminalGui.button(leftPos+x,topPos+y,width,height,text,action));}
    private void send(int action,String code,int value){PacketDistributor.sendToServer(new AccessMessages.Action(menu.containerId,action,code,value));}
    @Override protected void init(){
        compactControls = width < 352 || height < 296;
        imageWidth = compactControls ? 300 : 340;
        imageHeight = 218;
        if (menu.mode == AccessMenu.READER) imageHeight = Math.clamp(height - 12, 218, 246);
        if (menu.mode == AccessMenu.ENCODER) {
            imageWidth = Math.clamp(width - 16, 300, 448);
            imageHeight = Math.clamp(height - 12, 224, 330);
            inventoryX = (imageWidth - 160) / 2;
            inventoryY = imageHeight - 78;
            compact = inventoryY < 202;
            frequencyFormX = Math.max(148, imageWidth / 2 - 4);
            frequencyStep = Math.clamp((inventoryY - (compact ? 52 : 63)) / 6, 16, 29);
            int[] chipX = new int[FrequencyEncoding.SLOTS];
            int formWidth = imageWidth - ENCODER_MARGIN - frequencyFormX;
            for (int i = 0; i < chipX.length; i++)
                chipX[i] = frequencyFormX + formWidth * (2 * i + 1) / (2 * chipX.length) - 8;
            menu.layoutEncoderSlots((inventoryX - 4) / 2, inventoryX, inventoryY, chipX, CHIP_Y);
        }
        deviceList = null;
        super.init();ownerButtons.clear();readerLevels.clear();masterLevels.clear();encodingButtons.clear();
        if(menu.mode==AccessMenu.ENCODER)initEncoderTabs();
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
            initDuration(87, AccessMenu.BUTTON_SECONDS);
        }
        initLinkControls();
    }
    private void initDuration(int y, int action) {
        lastDuration = -1;
        duration = field(14,y,76,20,menu.mode == AccessMenu.READER ? tr("open_seconds") : controlText("seconds"));
        duration.setMaxLength(3);
        duration.setFilter(value -> value.matches("[0-9]{0,3}"));
        duration.setResponder(value -> refreshDuration());
        applyDuration = button(98,y,92,TerminalGui.ACTION_HEIGHT,tr("apply_iris_opening"),
            b -> send(action,"",Integer.parseInt(duration.getValue())));
        duration.setTooltip(Tooltip.create(controlText("range")));
    }
    private void refreshDuration() {
        if (duration == null || applyDuration == null) return;
        int seconds = menu.mode == AccessMenu.READER ? menu.openSeconds.get() : menu.buttonSeconds.get();
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
        for(int i=1;i<=5;i++){int level=i;Button b=button(14+(i-1)*48,Math.min(132,imageHeight-108),44,20,Component.literal(Integer.toString(i)),v->send(AccessMenu.SET_LEVEL,"",level));readerLevels.add(b);ownerButtons.add(b);}
        initDuration(imageHeight-72, AccessMenu.OPEN_SECONDS);
        initLinkControls();
        clearLinks.setTooltip(Tooltip.create(tr("clear_links_hint")));
    }
    private void initLinkControls() {
        int w = (imageWidth - 114) / 2;
        int y = menu.mode == AccessMenu.READER ? imageHeight - 44 : 162;
        devicesButton = button(14, y, w, TerminalGui.ACTION_HEIGHT, tr("devices"), ignored -> showDevices(true));
        clearLinks=button(18+w,y,w,TerminalGui.ACTION_HEIGHT,tr("clear_links"),b->{
            if(!confirmClearLinks){confirmClearLinks=true;b.setMessage(tr("confirm"));}
            else{send(AccessMenu.CLEAR_LINKS,"",0);confirmClearLinks=false;b.setMessage(tr("clear_links"));}
        });ownerButtons.add(clearLinks);
        button(imageWidth-88,y,74,20,tr("close"),b->onClose());
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
    private void initEncoderTabs() {
        initEncoder();
        badgeWidgets = children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        initFrequencyEncoder();
        frequencyWidgets = children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .skip(badgeWidgets.size()).toList();
        int catalogueWidth = frequencyFormX - 10 - ENCODER_MARGIN;
        int catalogueFooterGap = Math.clamp(18 + (imageHeight - 224) / 4, 18, 32);
        cataloguePanel = new FrequencyCataloguePanel(font, menu, leftPos + ENCODER_MARGIN, topPos, catalogueWidth,
            topPos + inventoryY - catalogueFooterGap, this::addRenderableWidget, input -> {
            if (getFocused() != null) getFocused().setFocused(false);
            setFocused(input);
            input.setFocused(true);
        }, cataloguePanel);
        badgesTab = button(imageWidth - 152, 4, 63, 18, tr("tab_badges"),
            b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AccessMenu.BADGES_TAB));
        frequenciesTab = button(imageWidth - 85, 4, 73, 18, tr("tab_frequencies"),
            b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AccessMenu.FREQUENCIES_TAB));
    }

    private static Component frequencyText(String key) {
        return Component.translatable(FrequencyContent.UI + key);
    }

    private void initFrequencyEncoder() {
        int formWidth = imageWidth - ENCODER_MARGIN - frequencyFormX;
        int actionWidth = Math.min(formWidth, compactControls ? 132 : 172);
        int actionX = frequencyFormX + (formWidth - actionWidth) / 2;
        int numberWidth = 49;
        int gap = 5;
        int fieldY = frequencyFieldY();
        int fieldHeight = Math.min(17, frequencyStep - 2);
        frequencyNumber = field(actionX, fieldY, numberWidth, fieldHeight, frequencyText("number"));
        frequencyNumber.setMaxLength(6);
        frequencyNumber.setFilter(value -> value.matches("[0-9]{0,6}"));
        if (frequencyStep < 24) frequencyNumber.setHint(frequencyText("number_hint"));
        frequencyNumber.setValue(frequencyDraft);
        frequencyNumber.setResponder(value -> frequencyDraft = value);
        frequencyNumber.setTooltip(Tooltip.create(frequencyText("number")));

        frequencyName = field(actionX + numberWidth + gap, fieldY, actionWidth - numberWidth - gap, fieldHeight, frequencyText("name"));
        frequencyName.setMaxLength(FrequencyEncoding.NAME_LENGTH);
        if (frequencyStep < 24) frequencyName.setHint(frequencyText("name_short"));
        frequencyName.setValue(frequencyNameDraft);
        frequencyName.setResponder(value -> frequencyNameDraft = value);

        int buttonWidth = (actionWidth - 4) / 2;
        int right = actionX + actionWidth - buttonWidth;
        int actionY = fieldY + frequencyStep;
        int copyY = actionY + frequencyActionStep();
        encodePublic = button(actionX, actionY, buttonWidth, FREQUENCY_BUTTON_HEIGHT,
            frequencyText("public"), b -> sendFrequency(FrequencyEncoding.PUBLIC));
        encodePrivate = button(right, actionY, buttonWidth, FREQUENCY_BUTTON_HEIGHT,
            frequencyText("private"), b -> sendFrequency(FrequencyEncoding.PRIVATE));
        duplicateChip = button(actionX, copyY, buttonWidth, FREQUENCY_BUTTON_HEIGHT,
            frequencyText("duplicate"), b -> sendFrequency(FrequencyEncoding.DUPLICATE));
        renameChip = button(right, copyY, buttonWidth, FREQUENCY_BUTTON_HEIGHT,
            frequencyText("apply_chip"), b -> sendFrequency(FrequencyEncoding.RENAME));
        renameChip.setTooltip(Tooltip.create(frequencyText("apply_chip_hint")));
        frequencyName.setTooltip(Tooltip.create(frequencyText("name")));
        encodeSelection = button(actionX, copyY + frequencyActionStep(), actionWidth - 57, FREQUENCY_BUTTON_HEIGHT, frequencyText("catalogue_encode_short"), b -> {
            var selection = cataloguePanel.selection();
            if (selection == null) return;
            int action = selection.privateFrequency() ? FrequencyEncoding.FROM_PRIVATE_CATALOGUE : FrequencyEncoding.FROM_PUBLIC_CATALOGUE;
            PacketDistributor.sendToServer(new FrequencyContent.Encode(menu.containerId, action, selection.number(), "", selectedChipMode.ordinal()));
        });
        encodeSelection.setTooltip(Tooltip.create(frequencyText("catalogue_encode")));
        chipMode = button(actionX + actionWidth - 53, copyY + frequencyActionStep(), 53, FREQUENCY_BUTTON_HEIGHT,
            Component.empty(), b -> { selectedChipMode = selectedChipMode.next(); refreshChipMode(); });
        refreshChipMode();
    }

    private void refreshChipMode() {
        chipMode.setMessage(Component.literal(selectedChipMode.shortName));
        chipMode.setTooltip(Tooltip.create(Component.empty().append(selectedChipMode.label()).append("\n")
            .append(selectedChipMode.hint()).append("\n").append(frequencyText("encoder_mode_hint"))));
    }

    private int frequencyFieldY() { return 70 + (frequencyStep - 18) * 2; }

    private int frequencyActionStep() { return Math.max(frequencyStep, FREQUENCY_BUTTON_HEIGHT + 1); }

    private int frequencyStatusY() { return encodeSelection.getY() - topPos + FREQUENCY_BUTTON_HEIGHT + 2; }

    private int enteredFrequency() {
        return frequencyDraft.isEmpty() ? 0 : Integer.parseInt(frequencyDraft);
    }

    private void sendFrequency(int action) {
        PacketDistributor.sendToServer(new FrequencyContent.Encode(menu.containerId, action, enteredFrequency(), frequencyNameDraft, selectedChipMode.ordinal()));
    }

    private void refreshEncoderTabs() {
        boolean selected = menu.frequencyTab();
        if (showingFrequencies != selected && getFocused() != null) {
            getFocused().setFocused(false);
            setFocused(null);
        }
        showingFrequencies = selected;
        for (var widget : badgeWidgets) widget.visible = !selected;
        for (var widget : frequencyWidgets) widget.visible = selected;
        cataloguePanel.refresh(selected);
        badgesTab.active = selected;
        frequenciesTab.active = !selected;
        boolean blank = FrequencyEncoding.accepts(0, menu.getSlot(AccessMenu.FREQUENCY_SLOTS).getItem());
        var source = menu.getSlot(AccessMenu.FREQUENCY_SLOTS + 1).getItem();
        boolean hasSource = FrequencyChip.read(source) != null;
        if (!ItemStack.isSameItemSameComponents(source, observedChip)) {
            observedChip = source.copy();
            if (hasSource) {
                selectedChipMode = FrequencyChip.mode(source);
                frequencyName.setValue(FrequencyContent.displayName(source, FrequencyChip.read(source)));
            }
        }
        refreshChipMode();
        encodePublic.active = encodePrivate.active = blank && enteredFrequency() > 0;
        duplicateChip.active = blank && hasSource;
        renameChip.active = hasSource;
        encodeSelection.active = blank && cataloguePanel.selection() != null;
    }

    private void initEncoder(){
        bankSharing=button(12,inventoryY+11,50,15,Component.empty(),
            b->send(AccessMenu.SET_BANK_SHARING,"",AccessBadges.foreignBankAllowed(menu.input.getItem(0))?0:1));
        int codeY=compact?33:43;
        int codeWidth = compactControls ? 224 : 264;
        codeInput=field(12,codeY,codeWidth,18,tr("terminal_code"));codeInput.setMaxLength(64);codeInput.setHint(tr("terminal_code"));codeInput.setValue(codeDraft);codeInput.setResponder(v->codeDraft=v);
        button(16+codeWidth,codeY,48,18,tr("paste"),b->codeInput.setValue(minecraft.keyboardHandler.getClipboard().strip()));
        int actionY=compact?55:67;
        int actionWidth = compactControls ? 89 : 102;
        encodingButtons.add(button(12,actionY,actionWidth,20,tr("add_code"),b->send(AccessMenu.ADD_CODE,codeInput.getValue(),0)));
        encodingButtons.add(button((imageWidth-actionWidth)/2,actionY,actionWidth,20,tr("remove_code"),b->send(AccessMenu.REMOVE_CODE,codeInput.getValue(),0)));
        clearBadge=button(imageWidth-12-actionWidth,actionY,actionWidth,20,tr("clear_badge"),b->{
            if(!confirmClearBadge){confirmClearBadge=true;b.setMessage(tr("confirm"));}
            else{send(AccessMenu.CLEAR_BADGE,"",0);confirmClearBadge=false;b.setMessage(tr("clear_badge"));}
        });
        encodingButtons.add(clearBadge);
        int levelY=compact?77:97,start=compact?82:96,pitch=compactControls?21:23;
        for(int i=1;i<=5;i++){int level=i;masterLevels.add(button(start+(i-1)*pitch,levelY,compactControls?19:21,compactControls?16:18,Component.literal(Integer.toString(i)),b->{selectedLevel=level;refresh();}));}
        encodingButtons.add(button(imageWidth-100,levelY,88,compactControls?16:18,tr("make_master"),b->send(AccessMenu.MASTER,"",selectedLevel)));
        int limitY=compact?95:125;
        int valueWidth = compactControls ? 100 : 112;
        limitInput=field(start,limitY,valueWidth,18,tr("limit"));limitInput.setMaxLength(7);limitInput.setFilter(s->s.matches("[0-9]{0,7}"));limitInput.setValue(limitDraft);limitInput.setResponder(v->limitDraft=v);
        encodingButtons.add(button(imageWidth-100,limitY,88,18,tr("set_limit"),b->{
            try{int count=Integer.parseInt(limitInput.getValue());if(count>=0&&count<=1000000){send(AccessMenu.LIMIT,"",count);return;}}catch(NumberFormatException ignored){}
            menu.status="gui.industrial_facilities.access.invalid_limit";
        }));
        int nameY=compact?116:151;
        nameInput=field(start,nameY,valueWidth,18,tr("badge_name"));nameInput.setMaxLength(64);nameInput.setHint(tr("badge_name_hint"));
        nameInput.setValue(nameDraft);nameInput.setResponder(v->{nameDraft=v;if(!updatingName)nameDirty=!v.equals(lastCustomName);});
        renameBadge=button(imageWidth-100,nameY,88,18,tr("rename"),b->{
            send(AccessMenu.RENAME,nameInput.getValue(),0);
            if(nameInput.getValue().strip().equals(lastCustomName))nameDirty=false;
        });
        nextCode=button(12,inventoryY+54,50,18,tr("next_code"),b->{
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
        if (menu.mode == AccessMenu.ENCODER) refreshEncoderTabs();
        if(nameInput!=null&&menu.mode==AccessMenu.ENCODER){
            boolean canEncode=AccessMenu.badge(menu.input.getItem(0));
            var badgeData=AccessBadges.read(menu.input.getItem(0));
            bankSharing.active=canEncode&&(!badgeData.hasUUID("Issuer")||badgeData.getUUID("Issuer").equals(menu.inventory.player.getUUID()));
            bankSharing.setMessage(tr(AccessBadges.foreignBankAllowed(menu.input.getItem(0))?"bank_shared_short":"bank_private_short"));
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
            if (showingFrequencies)
                g.fill(x + frequencyFormX - 6, y + 33, x + frequencyFormX - 5, y + inventoryY - 3, 0xFF48545C);
            for (var slot : menu.slots)
                if (slot.isActive()) TerminalGui.slot(g, x + slot.x, y + slot.y);
        }
    }
    private void drawFit(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),width),x,y,color,false);}
    private void wrapped(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawWordWrap(font,text,x,y,width,color);}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        drawFit(g,title,14,9,menu.mode==AccessMenu.ENCODER?imageWidth-176:imageWidth-28,0xFFFFFF);
        if (showingDevices) {
            deviceList.render(g, font, menu.status);
            return;
        }
        if(menu.mode==AccessMenu.READER){
            g.drawString(font,tr(menu.code.isEmpty()?"code_unassigned":"code"),14,34,MUTED,false);
            if (imageHeight >= 240) drawFit(g,tr("owner",menu.editable?tr("you").getString():menu.creator),14,101,imageWidth-28,MUTED);
            g.drawString(font,tr("level",menu.accessLevel),14,Math.min(132,imageHeight-108)-14,ACCENT,false);
            g.drawString(font,tr("open_seconds"),14,imageHeight-85,TEXT,false);
            g.drawString(font,tr("linked",menu.links),142,77,MUTED,false);
            drawFit(g,status(menu.status.isEmpty()?(menu.editable?"gui.industrial_facilities.access.reader_hint":"gui.industrial_facilities.access.read_only"):menu.status),14,imageHeight-18,imageWidth-28,TEXT);
        }else if(menu.mode==AccessMenu.ENCODER){
            if (showingFrequencies) {
                cataloguePanel.render(g, ENCODER_MARGIN);
                String[] captions = {"blank_short", "template_short", "output_short"};
                for (int i = 0; i < captions.length; i++)
                    g.drawCenteredString(font, frequencyText(captions[i]), menu.getSlot(AccessMenu.FREQUENCY_SLOTS + i).x + 8, 34, MUTED);
                if (frequencyStep >= 24) {
                    g.drawString(font, frequencyText("number_hint"), frequencyNumber.getX() - leftPos, frequencyFieldY() - 11, MUTED, false);
                    Component nameLabel = frequencyText("name");
                    if (font.width(nameLabel) > frequencyName.getWidth()) nameLabel = frequencyText("name_short");
                    g.drawString(font, nameLabel, frequencyName.getX() - leftPos, frequencyFieldY() - 11, MUTED, false);
                }
                int statusY = frequencyStatusY();
                int formWidth = imageWidth - frequencyFormX - ENCODER_MARGIN;
                if (menu.status.startsWith(FrequencyContent.UI))
                    drawFit(g, status(menu.status), frequencyFormX, statusY, formWidth, TEXT);
                else {
                    drawFit(g, selectedChipMode.label(), frequencyFormX, statusY, formWidth, ACCENT);
                    if (inventoryY - statusY >= 37)
                        wrapped(g, frequencyText("batch_help"), frequencyFormX, statusY + 12, formWidth, MUTED);
                }
                if (!compact) g.drawString(font, playerInventoryTitle, inventoryX, inventoryY - 11, MUTED, false);
                return;
            }
            int levelY=compact?82:102,limitY=compact?100:130,nameY=compact?121:156;
            drawFit(g,tr("master"),12,levelY,compact?66:80,MUTED);drawFit(g,tr("uses"),12,limitY,compact?66:80,MUTED);
            drawFit(g,tr("badge_name"),12,nameY,compact?66:80,MUTED);
            String hint=!menu.input.isEmpty()&&!AccessMenu.badge(menu.input.getItem(0))?"gui.industrial_facilities.access.link_card_name_hint":"gui.industrial_facilities.access.encoder_hint";
            int statusY=compact?137:177;drawFit(g,status(menu.status.isEmpty()?hint:menu.status),12,statusY,imageWidth-24,TEXT);
            g.drawString(font,tr("card_slot"),14,inventoryY + 1,ACCENT,false);
            if(!compact)g.drawString(font,playerInventoryTitle,inventoryX,inventoryY-11,MUTED,false);
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
        if (showingFrequencies && cataloguePanel.keyPressed(key)) return true;
        if(key==256){if(showingDevices)showDevices(false);else onClose();return true;}
        if (TerminalGui.handleInventoryKey(getFocused(),key,scan,modifiers)) return true;
        if(codeInput!=null&&codeInput.isFocused())return codeInput.keyPressed(key,scan,modifiers);
        if(limitInput!=null&&limitInput.isFocused())return limitInput.keyPressed(key,scan,modifiers);
        if(nameInput!=null&&nameInput.isFocused())return nameInput.keyPressed(key,scan,modifiers);
        if(duration!=null&&duration.isFocused())return duration.keyPressed(key,scan,modifiers);
        return super.keyPressed(key,scan,modifiers);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return (showingFrequencies && cataloguePanel.mouseScrolled(x, y, vertical))
            || super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return (showingFrequencies && cataloguePanel.mouseClicked(x, y, button)) || super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return (showingFrequencies && cataloguePanel.mouseDragged(y, button))
            || super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return (showingFrequencies && cataloguePanel.mouseReleased(button)) || super.mouseReleased(x, y, button);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
        int sy=menu.mode==AccessMenu.ENCODER?(compact?137:177):197;
        if (showingFrequencies) {
            int chipX = leftPos + menu.getSlot(AccessMenu.FREQUENCY_SLOTS).x + 8;
            if (Math.abs(mouseX - chipX) < 24 && mouseY >= topPos + 33 && mouseY < topPos + 44)
                g.renderTooltip(font, font.split(frequencyText("batch_help"), 210), mouseX, mouseY);
            int statusY = topPos + frequencyStatusY();
            if (menu.status.startsWith(FrequencyContent.UI) && mouseX >= leftPos + frequencyFormX
                    && mouseX < leftPos + imageWidth - ENCODER_MARGIN && mouseY >= statusY && mouseY < statusY + 10)
                g.renderTooltip(font, font.split(status(menu.status), 210), mouseX, mouseY);
            return;
        }
        if(!menu.status.isEmpty()&&mouseX>=leftPos+12&&mouseX<leftPos+imageWidth-12&&mouseY>=topPos+sy&&mouseY<topPos+sy+10)
            g.renderTooltip(font,font.split(status(menu.status),imageWidth-24),mouseX,mouseY);
        if(menu.mode==AccessMenu.ENCODER&&limitInput!=null&&limitInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("zero_unlimited"),mouseX,mouseY);
        if(nameInput!=null&&nameInput.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("name_hint"),mouseX,mouseY);
        if(bankSharing!=null&&bankSharing.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("bank_sharing_hint"),240),mouseX,mouseY);
        if(cameraAutomation!=null&&cameraAutomation.isMouseOver(mouseX,mouseY))g.renderTooltip(font,font.split(tr("camera_automation_hint"),240),mouseX,mouseY);
        if(nextCode!=null&&nextCode.isMouseOver(mouseX,mouseY))g.renderTooltip(font,tr("next_code_hint"),mouseX,mouseY);
    }
}
