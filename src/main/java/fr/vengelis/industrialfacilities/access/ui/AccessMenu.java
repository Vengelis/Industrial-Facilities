package fr.vengelis.industrialfacilities.access.ui;

import java.util.Objects;
import java.util.UUID;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AccessMenu extends AbstractContainerMenu {
    public static final int READER=0,ENCODER=1,DOOR=2;
    public static final int SET_LEVEL=0,CLEAR_LINKS=1,ADD_CODE=2,REMOVE_CODE=3,CLEAR_BADGE=4,MASTER=5,LIMIT=6,REDSTONE=7,RENAME=8,SET_BANK_SHARING=9;
    public final Inventory inventory;
    public final BlockPos position;
    public final int mode;
    public final String creator;
    public final SimpleContainer input=new SimpleContainer(1);
    public String code="",status="";
    public int accessLevel=1,links;
    public boolean editable,allowRedstone,secured;
    private final AccessTerminalEntity terminal;
    private final IndustrialDoorEntity door;
    private final UUID instanceId;
    private AccessMessages.State lastState;

    public AccessMenu(int id,Inventory inventory,AccessTerminalEntity terminal){
        super(AccessMenus.TYPE,id);this.inventory=inventory;this.terminal=terminal;door=null;
        position=terminal.getBlockPos();mode=terminal.encoder?ENCODER:READER;instanceId=terminal.instanceId;
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
        mode=buffer.readVarInt();if(mode<READER||mode>DOOR)throw new IllegalArgumentException("Invalid access menu mode");
        position=buffer.readBlockPos();creator=buffer.readUtf(36);code=buffer.readUtf(64);setupSlots();
    }
    public static void writeOpening(FriendlyByteBuf buffer,AccessTerminalEntity terminal){
        buffer.writeVarInt(terminal.encoder?ENCODER:READER);buffer.writeBlockPos(terminal.getBlockPos());
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
    private void setupSlots(){
        if(mode!=ENCODER)return;
        addSlot(new Slot(input,0,30,228){@Override public boolean mayPlace(ItemStack stack){return renameable(stack);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,89+col*18,202+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,89+col*18,260));
    }
    /** Client layout only; server slot indices and backing containers remain unchanged. */
    public void layoutSlots(boolean compact){
        if(!inventory.player.level().isClientSide()||mode!=ENCODER)return;
        int invX=compact?69:89,invY=compact?150:202;
        for(int index=0;index<slots.size();index++){
            var old=slots.get(index);int x,y;
            if(index==0){x=compact?24:30;y=invY+26;}
            else if(index<=27){x=invX+(index-1)%9*18;y=invY+(index-1)/9*18;}
            else{x=invX+(index-28)*18;y=invY+58;}
            Slot updated=index==0?new Slot(input,0,x,y){@Override public boolean mayPlace(ItemStack stack){return renameable(stack);}@Override public int getMaxStackSize(){return 1;}}:new Slot(inventory,old.getContainerSlot(),x,y);
            updated.index=old.index;slots.set(index,updated);
        }
    }
    @Override public boolean stillValid(Player player){
        if(player.level().isClientSide())return true;
        if(player!=inventory.player||!player.isAlive()||!player.level().hasChunkAt(position)
            ||player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)>64)return false;
        if(mode==DOOR)return door!=null&&!door.isRemoved()&&door.size>0&&player.level().getBlockEntity(position)==door&&Objects.equals(instanceId,door.doorInstanceId);
        return terminal!=null&&!terminal.isRemoved()&&AccessTerminalEntity.resolve(player.level(),position)==terminal&&Objects.equals(instanceId,terminal.instanceId);
    }
    private void refresh(){
        if(terminal!=null){code=terminal.code();accessLevel=terminal.accessLevel;links=terminal.linkedCount();editable=terminal.isOwner(inventory.player);}
        else if(door!=null){allowRedstone=door.allowLinkedRedstone;editable=AccessDoorService.canConfigure(inventory.player,door);secured=AccessDoorService.isSecured(door);}
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(inventory.player instanceof ServerPlayer server))return;
        refresh();var current=new AccessMessages.State(containerId,code,accessLevel,links,editable,allowRedstone,secured,status);
        if(!current.equals(lastState)){lastState=current;PacketDistributor.sendToPlayer(server,current);}
    }
    public void receive(AccessMessages.State state){
        if(state.menuId()!=containerId)return;
        code=state.code();accessLevel=Math.clamp(state.level(),1,5);links=Math.max(0,state.links());editable=state.editable();allowRedstone=state.redstone();secured=state.secured();status=state.status();
    }
    public void apply(Player player,int action,String enteredCode,int value){
        if(player.level().isClientSide()||player.containerMenu!=this||!stillValid(player)||enteredCode==null||enteredCode.length()>64)return;
        boolean changed=false;
        if(mode==READER){
            if(action==SET_LEVEL&&value>=1&&value<=5)changed=terminal.setAccessLevel(player,value);
            else if(action==CLEAR_LINKS)changed=terminal.clearLinks(player);
            else return;
            status=changed?"gui.industrial_facilities.access.saved":"gui.industrial_facilities.access.denied";
        }else if(mode==DOOR){
            if(action!=REDSTONE||(value!=0&&value!=1))return;
            changed=AccessDoorService.setAllowRedstone(player,door,value==1);
            status=changed?"gui.industrial_facilities.access.saved":"gui.industrial_facilities.access.denied";
        }else{
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
    @Override public void removed(Player player){
        super.removed(player);if(!player.level().isClientSide()&&mode==ENCODER)clearContainer(player,input);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(mode!=ENCODER||index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(renameable(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<28){if(!moveItemStackTo(stack,28,37,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,1,28,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
