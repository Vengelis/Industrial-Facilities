package fr.vengelis.industrialfacilities.access.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorStyle;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import fr.vengelis.industrialfacilities.frequency.FrequencyContent;
import fr.vengelis.industrialfacilities.frequency.FrequencyEncoding;
import fr.vengelis.industrialfacilities.frequency.FrequencyNames;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AccessMenu extends AbstractContainerMenu {
    public static final int READER=0,ENCODER=1,DOOR=2,BUTTON=3,LOCKDOWN=4;
    public static final int SET_LEVEL=0,CLEAR_LINKS=1,ADD_CODE=2,REMOVE_CODE=3,CLEAR_BADGE=4,MASTER=5,LIMIT=6,REDSTONE=7,RENAME=8,SET_BANK_SHARING=9,CAMERA_AUTOMATION=10;
    public static final int HALF_OPENING=11;
    public static final int IRIS_OPENING=12;
    public static final int BUTTON_SECONDS=13;
    public static final int FORCED_CLOSING=14;
    public static final int OPEN_SECONDS=15;
    public static final int BADGES_TAB = 0, FREQUENCIES_TAB = 1;
    private static final int INVENTORY_START = 1, HOTBAR_START = 28;
    public static final int FREQUENCY_SLOTS = 37;
    public final Inventory inventory;
    public final BlockPos position;
    public final int mode;
    public final String creator;
    public final SimpleContainer input=new SimpleContainer(1);
    public String code="",status="";
    public int accessLevel=1,links;
    public List<TerminalDevices.Link> deviceLinks = List.of();
    public boolean editable,allowRedstone,secured,allowCameraAutomation;
    public boolean meshDoor,halfOpening;
    public int irisOpeningPercent=-1;
    public final DataSlot buttonSeconds=DataSlot.standalone();
    public final DataSlot openSeconds=DataSlot.standalone();
    private final DataSlot forcedClosing=DataSlot.standalone();
    private final DataSlot encoderTab = DataSlot.standalone();
    private SimpleContainer chips;
    private FrequencyContent.CatalogWindow catalogue;
    private int catalogueOffset;
    private long catalogueRevision = -1;
    private final AccessTerminalEntity terminal;
    private final IndustrialDoorEntity door;
    private final UUID instanceId;
    private AccessMessages.State lastState;

    public AccessMenu(int id,Inventory inventory,AccessTerminalEntity terminal){
        super(AccessMenus.TYPE,id);this.inventory=inventory;this.terminal=terminal;door=null;
        position=terminal.getBlockPos();mode=terminalMode(terminal);instanceId=terminal.instanceId;
        creator=terminal.creator==null?"":terminal.creator.toString();code=terminal.code();
        setupSlots();refresh();
    }
    public AccessMenu(int id,Inventory inventory,IndustrialDoorEntity door){
        super(AccessMenus.TYPE,id);this.inventory=inventory;this.door=door;terminal=null;
        position=door.getBlockPos();mode=DOOR;instanceId=door.doorInstanceId;creator="";
        setupSlots();refresh();
    }
    public AccessMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        super(AccessMenus.TYPE,id);this.inventory=inventory;terminal=null;door=null;instanceId=null;
        mode=buffer.readVarInt();if(mode<READER||mode>LOCKDOWN)throw new IllegalArgumentException("Invalid access menu mode");
        position=buffer.readBlockPos();creator=buffer.readUtf(36);code=buffer.readUtf(64);setupSlots();
    }
    public static void writeOpening(FriendlyByteBuf buffer,AccessTerminalEntity terminal){
        buffer.writeVarInt(terminalMode(terminal));buffer.writeBlockPos(terminal.getBlockPos());
        buffer.writeUtf(terminal.creator==null?"":terminal.creator.toString(),36);buffer.writeUtf(terminal.code(),64);
    }
    public static void writeOpening(FriendlyByteBuf buffer,IndustrialDoorEntity door){
        buffer.writeVarInt(DOOR);buffer.writeBlockPos(door.getBlockPos());buffer.writeUtf("",36);buffer.writeUtf("",64);
    }
    public static boolean badge(ItemStack stack){
        return stack.is(IndustrialFacilitiesModItems.ACCESS_BADGE_BLANK.get())||stack.is(IndustrialFacilitiesModItems.ACCESS_BADGE_ENCODED.get())
            ||stack.is(IndustrialFacilitiesModItems.ACCESS_BADGE_MASTER.get())||stack.is(IndustrialFacilitiesModItems.ACCESS_BADGE_LIMITED.get());
    }
    public static boolean renameable(ItemStack stack){return AccessBadges.renameable(stack);}
    private static int terminalMode(AccessTerminalEntity terminal) {
        if (terminal instanceof ControlTerminalEntity control) return control.lockdown() ? LOCKDOWN : BUTTON;
        return terminal.encoder ? ENCODER : READER;
    }
    private void setupSlots() {
        if (mode == BUTTON) addDataSlot(buttonSeconds);
        if (mode == READER) addDataSlot(openSeconds);
        if (mode == DOOR) addDataSlot(forcedClosing);
        if (mode != ENCODER) return;
        addDataSlot(encoderTab);
        chips = new SimpleContainer(FrequencyEncoding.SLOTS);
        addSlot(badgeSlot(30, 228));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 89 + col * 18, 202 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 89 + col * 18, 260));
        for (int index = 0; index < FrequencyEncoding.SLOTS; index++)
            addSlot(chipSlot(index, 0, 0));
    }

    private Slot badgeSlot(int x, int y) {
        return new Slot(input, 0, x, y) {
            @Override public boolean isActive() { return !frequencyTab(); }
            @Override public boolean mayPlace(ItemStack stack) {
                return isActive() && renameable(stack) && !FrequencyEncoding.accepts(0, stack);
            }
            @Override public boolean mayPickup(Player player) { return isActive(); }
            @Override public int getMaxStackSize() { return 1; }
        };
    }

    private Slot chipSlot(int chipIndex, int x, int y) {
        return new Slot(chips, chipIndex, x, y) {
            @Override public boolean isActive() { return frequencyTab(); }
            @Override public boolean mayPlace(ItemStack stack) { return isActive() && FrequencyEncoding.accepts(chipIndex, stack); }
            @Override public boolean mayPickup(Player player) { return isActive(); }
            @Override public int getMaxStackSize() { return chipIndex == 1 ? 1 : super.getMaxStackSize(); }
        };
    }

    public boolean frequencyTab() { return encoderTab.get() == FREQUENCIES_TAB; }

    @Override public boolean clickMenuButton(Player player, int tab) {
        if (mode != ENCODER || (tab != BADGES_TAB && tab != FREQUENCIES_TAB)
                || player.containerMenu != this || !stillValid(player)) return false;
        selectEncoderTab(player, tab);
        broadcastChanges();
        return true;
    }

    private void selectEncoderTab(Player player, int tab) {
        if (tab == FREQUENCIES_TAB && catalogue == null && player instanceof ServerPlayer server)
            FrequencyNames.get(server.serverLevel().getServer()).recoverPrivate(server);
        if (tab == BADGES_TAB && status.startsWith(FrequencyContent.UI)) status = "";
        encoderTab.set(tab);
    }

    void insertHeldBlank(ServerPlayer player, InteractionHand hand) {
        if (mode != ENCODER || player.containerMenu != this || !stillValid(player) || getSlot(FREQUENCY_SLOTS).hasItem()) return;
        ItemStack held = player.getItemInHand(hand);
        if (!FrequencyEncoding.accepts(0, held)) return;
        selectEncoderTab(player, FREQUENCIES_TAB);
        getSlot(FREQUENCY_SLOTS).safeInsert(held);
        player.getInventory().setChanged();
        broadcastChanges();
    }

    public void encodeFrequency(ServerPlayer player, FrequencyContent.Encode packet) {
        if (mode != ENCODER || !frequencyTab() || player.containerMenu != this || !stillValid(player)) return;
        String result = FrequencyEncoding.encode(player, chips, packet);
        status = result.isEmpty() ? "" : FrequencyContent.UI + result;
        broadcastChanges();
    }

    public FrequencyContent.CatalogWindow catalogue() { return catalogue; }

    public void receiveCatalogue(FrequencyContent.CatalogWindow packet) {
        if (mode == ENCODER && packet.menu() == containerId) catalogue = packet;
    }

    public void browseCatalogue(ServerPlayer player, int offset) {
        if (!frequencyTab() || !stillValid(player) || catalogue == null || offset < 0 || offset >= catalogue.total()) return;
        catalogueOffset = offset;
        sendCatalogue(player, FrequencyNames.get(player.serverLevel().getServer()));
    }

    public void editCatalogue(ServerPlayer player, FrequencyContent.EditCatalogue packet) {
        if (!frequencyTab() || !stillValid(player)) return;
        FrequencyNames.get(player.serverLevel().getServer()).editCatalogue(player, packet);
        broadcastChanges();
    }

    private void sendCatalogue(ServerPlayer player, FrequencyNames names) {
        catalogue = names.catalogue(player.getUUID(), containerId, catalogueOffset);
        catalogueOffset = Math.clamp(catalogueOffset, 0, Math.max(0, catalogue.total() - 1));
        catalogueRevision = names.revision();
        PacketDistributor.sendToPlayer(player, catalogue);
    }

    public boolean forcedClosing(){return forcedClosing.get()!=0;}
    /** Client layout only; server slot indices and backing containers remain unchanged. */
    public void layoutEncoderSlots(int badgeX, int invX, int invY, int[] chipX, int chipY) {
        if (!inventory.player.level().isClientSide() || mode != ENCODER) return;
        for (int index = 0; index < slots.size(); index++) {
            Slot old = slots.get(index), updated;
            if (index == 0) updated = badgeSlot(badgeX, invY + 27);
            else if (index < HOTBAR_START) {
                int offset = index - INVENTORY_START;
                updated = new Slot(inventory, old.getContainerSlot(), invX + offset % 9 * 18, invY + offset / 9 * 18);
            } else if (index < FREQUENCY_SLOTS)
                updated = new Slot(inventory, old.getContainerSlot(), invX + (index - HOTBAR_START) * 18, invY + 58);
            else updated = chipSlot(index - FREQUENCY_SLOTS, chipX[index - FREQUENCY_SLOTS], chipY);
            updated.index = old.index;
            slots.set(index, updated);
        }
    }

    @Override public void clicked(int index, int button, ClickType type, Player player) {
        if (mode == ENCODER && !frequencyTab() && index == 0 && type == ClickType.PICKUP
                && FrequencyEncoding.accepts(0, getCarried()) && stillValid(player)) {
            Slot blank = getSlot(FREQUENCY_SLOTS);
            if (blank.hasItem() && (!ItemStack.isSameItemSameComponents(blank.getItem(), getCarried())
                    || blank.getItem().getCount() >= blank.getMaxStackSize(getCarried()))) return;
            selectEncoderTab(player, FREQUENCIES_TAB);
            super.clicked(FREQUENCY_SLOTS, button, type, player);
            return;
        }
        if (index >= 0 && index < slots.size() && !slots.get(index).isActive()) return;
        super.clicked(index, button, type, player);
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.isActive() && super.canTakeItemForPickAll(stack, slot);
    }

    @Override public boolean stillValid(Player player){
        if(player.level().isClientSide())return true;
        if(player!=inventory.player||!player.isAlive()||!player.level().hasChunkAt(position)
            ||player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)>64)return false;
        if(mode==DOOR)return door!=null&&!door.isRemoved()&&door.size>0&&player.level().getBlockEntity(position)==door&&Objects.equals(instanceId,door.doorInstanceId);
        return terminal!=null&&!terminal.isRemoved()&&!terminal.isLockedDown()&&AccessTerminalEntity.resolve(player.level(),position)==terminal&&Objects.equals(instanceId,terminal.instanceId);
    }
    private void refresh(){
        if(terminal!=null){code=terminal.code();accessLevel=terminal.accessLevel;links=terminal.linkedCount();editable=terminal.isOwner(inventory.player);}
        else if(door!=null){
            allowRedstone=door.allowLinkedRedstone;
            allowCameraAutomation=door.allowCameraAutomation;
            forcedClosing.set(door.forcedClosing?1:0);
            editable=AccessDoorService.canConfigure(inventory.player,door);
            secured=AccessDoorService.isSecured(door);
            meshDoor=door.style()==IndustrialDoorStyle.MESH;
            halfOpening=meshDoor&&door.halfOpening;
            irisOpeningPercent=door.style()==IndustrialDoorStyle.IRIS?door.irisOpeningPercent:-1;
        }
        if (mode==BUTTON && terminal instanceof ControlTerminalEntity control) buttonSeconds.set(control.seconds());
        if (mode==READER && terminal != null) openSeconds.set(terminal.openSeconds());
    }
    @Override public void broadcastChanges(){
        if(inventory.player instanceof ServerPlayer)refresh();
        super.broadcastChanges();if(!(inventory.player instanceof ServerPlayer server))return;
        var current=new AccessMessages.State(containerId,code,accessLevel,links,editable,
            allowRedstone,secured,allowCameraAutomation,meshDoor,halfOpening,irisOpeningPercent,status);
        if(!current.equals(lastState)){lastState=current;PacketDistributor.sendToPlayer(server,current);}
        if (frequencyTab() && stillValid(server)) {
            var names = FrequencyNames.get(server.serverLevel().getServer());
            if (catalogue == null || catalogueRevision != names.revision())
                sendCatalogue(server, names);
        }
        if (showsDevices() && editable && stillValid(server)) {
            var devices = TerminalDevices.links(server.serverLevel(), terminal);
            if (!devices.equals(deviceLinks)) {
                deviceLinks = devices;
                PacketDistributor.sendToPlayer(server, new AccessMessages.DeviceList(containerId, devices));
            }
        }
    }
    public boolean showsDevices() {
        return mode == READER || mode == BUTTON || mode == LOCKDOWN;
    }
    void unlinkDevice(ServerPlayer player, AccessMessages.UnlinkDevice action) {
        if (!showsDevices() || terminal == null || !stillValid(player)) return;
        status = TerminalDevices.unlinkOffline(player, terminal, action.device(), action.position());
        broadcastChanges();
    }
    public void receive(AccessMessages.State state){
        if(state.menuId()!=containerId)return;
        code=state.code();accessLevel=Math.clamp(state.level(),1,5);links=Math.max(0,state.links());editable=state.editable();allowRedstone=state.redstone();secured=state.secured();allowCameraAutomation=state.cameraAutomation();status=state.status();
        meshDoor=state.meshDoor();halfOpening=state.halfOpening();
        irisOpeningPercent=state.irisOpeningPercent();
    }
    public void apply(Player player,int action,String enteredCode,int value){
        if(player.level().isClientSide()||player.containerMenu!=this||!stillValid(player)||enteredCode==null||enteredCode.length()>64)return;
        boolean changed=false;
        if(mode==READER){
            if(action==SET_LEVEL&&value>=1&&value<=5)changed=terminal.setAccessLevel(player,value);
            else if(action==CLEAR_LINKS)changed=terminal.clearLinks(player);
            else if(action==OPEN_SECONDS)changed=terminal.setOpenSeconds(player,value);
            else return;
            status=changed?"gui.industrial_facilities.access.saved":"gui.industrial_facilities.access.denied";
        }else if(terminal instanceof ControlTerminalEntity control){
            if(action==BUTTON_SECONDS&&mode==BUTTON)changed=control.setSeconds(player,value);
            else if(action==CLEAR_LINKS)changed=control.clearLinks(player);
            else return;
            status=changed?"gui.industrial_facilities.access.saved":"gui.industrial_facilities.access.denied";
        }else if(mode==DOOR){
            if(action!=IRIS_OPENING&&value!=0&&value!=1)return;
            switch(action){
                case REDSTONE -> changed=AccessDoorService.setAllowRedstone(player,door,value==1);
                case CAMERA_AUTOMATION -> changed=AccessDoorService.setAllowCameraAutomation(player,door,value==1);
                case HALF_OPENING -> changed=AccessDoorService.setHalfOpening(player,door,value==1);
                case IRIS_OPENING -> changed=AccessDoorService.setIrisOpening(player,door,value);
                case FORCED_CLOSING -> changed=AccessDoorService.setForcedClosing(player,door,value==1);
                default -> {return;}
            }
            status=changed?"gui.industrial_facilities.access.saved":"gui.industrial_facilities.access.denied";
        }else{
            if (frequencyTab()) return;
            ItemStack stack=input.getItem(0);
            if(!renameable(stack)||stack.getCount()!=1){status="gui.industrial_facilities.access.insert_card";broadcastChanges();return;}
            if(!badge(stack)&&action!=RENAME){status="gui.industrial_facilities.access.badge_required";broadcastChanges();return;}
            AccessBadges.Result result;
            switch(action){
                case ADD_CODE,REMOVE_CODE -> result=AccessBadges.encodeCode(player,stack,enteredCode.strip(),action==ADD_CODE);
                case CLEAR_BADGE -> result=AccessBadges.clear(player,stack);
                case MASTER -> {if(value<1||value>5)return;result=AccessBadges.encodeMaster(player,stack,value);}
                case LIMIT -> {if(value<0||value>1000000)return;result=AccessBadges.setLimit(player,stack,value);}
                case RENAME -> result=AccessBadges.rename(player,stack,enteredCode);
                case SET_BANK_SHARING -> {if(value!=0&&value!=1)return;result=AccessBadges.setBankSharing(player,stack,value==1);}
                default -> {return;}
            }
            status=result.message();
            if(result.ok()){if(action!=RENAME)input.setItem(0,AccessBadges.normalize(stack));input.setChanged();}
        }
        broadcastChanges();
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && mode == ENCODER) {
            clearContainer(player, input);
            clearContainer(player, chips);
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (mode != ENCODER || index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0 || index >= FREQUENCY_SLOTS) {
            if (!moveItemStackTo(stack, INVENTORY_START, FREQUENCY_SLOTS, true)) return ItemStack.EMPTY;
        } else if (FrequencyEncoding.accepts(0, stack)) {
            selectEncoderTab(player, FREQUENCIES_TAB);
            if (!moveItemStackTo(stack, FREQUENCY_SLOTS, FREQUENCY_SLOTS + 1, false)) return ItemStack.EMPTY;
        } else if (!frequencyTab() && renameable(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (frequencyTab() && FrequencyEncoding.accepts(1, stack)) {
            if (!moveItemStackTo(stack, FREQUENCY_SLOTS + 1, FREQUENCY_SLOTS + 2, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, FREQUENCY_SLOTS, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
