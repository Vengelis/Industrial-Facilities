package fr.vengelis.industrialfacilities.access.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.text.Normalizer;
import fr.vengelis.industrialfacilities.access.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A single physical input slot and a private, server-owned archive for the current player. */
public class BadgeBankMenu extends AbstractContainerMenu {
    public static final int PAGE=0,SELECT=1,DEPOSIT=2,WITHDRAW=3,REISSUE=4,RENAME=5,REMOVE=6,SEARCH=7;
    private static final Pattern MARKS=Pattern.compile("\\p{M}+");
    public final Inventory inventory;
    public final BlockPos position;
    public final SimpleContainer input=new SimpleContainer(1);
    public List<BadgeBankMessages.Entry> entries=List.of();
    public List<String> selectedCodes=List.of();
    public UUID selectedId;
    public int page,pages=1,total,matched;
    public String searchQuery="";
    public boolean canDeposit;
    public int responseVersion;
    public String status="";
    private final AccessTerminalEntity terminal;
    private final UUID instanceId;
    private BadgeBankMessages.State lastState;

    public BadgeBankMenu(int id,Inventory inventory,AccessTerminalEntity terminal){
        super(AccessMenus.BANK_TYPE,id);this.inventory=inventory;this.terminal=terminal;
        position=terminal.getBlockPos();instanceId=terminal.instanceId;setupSlots();refresh();
    }
    public BadgeBankMenu(int id,Inventory inventory,FriendlyByteBuf buffer){
        super(AccessMenus.BANK_TYPE,id);this.inventory=inventory;terminal=null;instanceId=null;
        position=buffer.readBlockPos();setupSlots();
    }
    public static void writeOpening(FriendlyByteBuf buffer,AccessTerminalEntity terminal){buffer.writeBlockPos(terminal.getBlockPos());}
    private Slot inputSlot(int x,int y){return new Slot(input,0,x,y){
        @Override public boolean mayPlace(ItemStack stack){return AccessBadges.isBadge(stack);}
        @Override public int getMaxStackSize(){return 1;}
    };}
    private void setupSlots(){
        addSlot(inputSlot(186,266));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,9+row*9+col,226+col*18,218+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,226+col*18,276));
    }
    /** Layout changes are local; slot IDs and backing containers never change. */
    public void layoutSlots(boolean compact){
        if(!inventory.player.level().isClientSide())return;
        int invX=compact?146:226,invY=compact?150:218;
        for(int i=0;i<slots.size();i++){
            Slot old=slots.get(i),updated;
            if(i==0)updated=inputSlot(compact?28:186,compact?207:266);
            else if(i<=27)updated=new Slot(inventory,old.getContainerSlot(),invX+(i-1)%9*18,invY+(i-1)/9*18);
            else updated=new Slot(inventory,old.getContainerSlot(),invX+(i-28)*18,invY+58);
            updated.index=old.index;slots.set(i,updated);
        }
    }
    @Override public boolean stillValid(Player player){
        if(player.level().isClientSide())return true;
        return player==inventory.player&&player.isAlive()&&player.level().hasChunkAt(position)
            &&player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)<=64
            &&terminal!=null&&terminal.bank&&!terminal.isRemoved()&&terminal.getLevel()==player.level()
            &&AccessTerminalEntity.resolve(player.level(),position)==terminal&&Objects.equals(instanceId,terminal.instanceId);
    }
    private static String normalized(String text){return MARKS.matcher(Normalizer.normalize(text,Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);}
    public static String cleanQuery(String text){
        String plain=net.minecraft.ChatFormatting.stripFormatting(text);StringBuilder clean=new StringBuilder();
        plain.codePoints().filter(c->!Character.isISOControl(c)&&Character.getType(c)!=Character.FORMAT
            &&Character.getType(c)!=Character.SURROGATE&&c!=0x00a7).forEach(clean::appendCodePoint);
        // Preserve ordinary spaces for the client's exact query acknowledgement.
        return clean.toString();
    }
    private List<BadgeBankData.EntryView> filtered(List<BadgeBankData.EntryView> archive){
        String query=normalized(searchQuery).strip();if(query.isEmpty())return archive;
        return archive.stream().filter(entry->{
            ItemStack badge=entry.badge();var custom=badge.get(DataComponents.CUSTOM_NAME);String name=custom==null?"":custom.getString();
            if(!name.isEmpty())return normalized(name).contains(query);
            boolean master=AccessBadges.read(badge).getInt("MasterLevel")>0;
            return normalized(master?"Passe-partout sans nom":"Badge sans nom").contains(query)
                ||normalized(master?"Unnamed master badge":"Unnamed badge").contains(query);
        }).toList();
    }
    private boolean visible(UUID id){return entries.stream().anyMatch(entry->entry.id().equals(id));}
    private void refresh(){
        if(!(inventory.player instanceof ServerPlayer player))return;
        var bank=BadgeBankData.get(player.server);var all=bank.entries(player);var archive=filtered(all);canDeposit=bank.canDeposit(player,input.getItem(0));
        total=all.size();matched=archive.size();pages=Math.max(1,(matched+BadgeBankMessages.PAGE_SIZE-1)/BadgeBankMessages.PAGE_SIZE);page=Math.clamp(page,0,pages-1);
        int start=page*BadgeBankMessages.PAGE_SIZE,end=Math.min(start+BadgeBankMessages.PAGE_SIZE,matched);
        boolean selectedVisible=false;for(int i=start;i<end;i++)if(archive.get(i).id().equals(selectedId)){selectedVisible=true;break;}
        if(!selectedVisible)selectedId=start<end?archive.get(start).id():null;
        List<BadgeBankMessages.Entry> visible=new ArrayList<>();selectedCodes=List.of();
        for(int i=start;i<end;i++){
            var entry=archive.get(i);ItemStack badge=entry.badge();
            if(entry.id().equals(selectedId))selectedCodes=AccessBadges.codes(badge).stream().map(UUID::toString).toList();
            var data=AccessBadges.read(badge);var custom=badge.get(DataComponents.CUSTOM_NAME);String name=custom==null?"":custom.getString();
            if(name.length()>64)name=name.substring(0,64);
            visible.add(new BadgeBankMessages.Entry(entry.id(),name,Math.clamp(data.getInt("MasterLevel"),0,5),
                AccessBadges.codes(badge).size(),AccessBadges.remaining(badge),entry.stored(),entry.issuer(),entry.foreign(),entry.copyAllowed()));
        }
        entries=List.copyOf(visible);
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(inventory.player instanceof ServerPlayer player))return;
        refresh();var current=new BadgeBankMessages.State(containerId,page,pages,total,selectedId,entries,selectedCodes,canDeposit,status,searchQuery,matched);
        if(!current.equals(lastState)){lastState=current;PacketDistributor.sendToPlayer(player,current);}
    }
    public void receive(BadgeBankMessages.State state){
        if(state.menuId()!=containerId)return;
        page=Math.max(0,state.page());pages=Math.max(1,state.pages());total=Math.max(0,state.total());
        searchQuery=state.searchQuery();matched=Math.clamp(state.matched(),0,total);
        selectedId=state.selectedId();entries=state.entries();selectedCodes=state.codes();canDeposit=state.canDeposit();status=state.status();responseVersion++;
    }
    public BadgeBankMessages.Entry selected(){for(var entry:entries)if(entry.id().equals(selectedId))return entry;return null;}
    private static UUID parse(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException e){return null;}}
    private static boolean hasSpace(Inventory inventory,ItemStack stack){
        if(stack.isEmpty())return false;
        int remaining=stack.getCount();
        for(ItemStack slot:inventory.items){
            if(slot.isEmpty())return true;
            if(ItemStack.isSameItemSameComponents(slot,stack))remaining-=Math.max(0,Math.min(slot.getMaxStackSize(),inventory.getMaxStackSize())-slot.getCount());
            if(remaining<=0)return true;
        }
        return false;
    }
    private void deliver(ServerPlayer player,ItemStack stack){
        if(stack.isEmpty())return;
        player.getInventory().add(stack);
        // A last-resort item entity preserves the badge if another hook changes capacity during insertion.
        if(!stack.isEmpty())player.drop(stack,false);
        player.getInventory().setChanged();
    }
    public void apply(Player player,int action,String selectedUUID,int value){
        apply(player,action,selectedUUID,value,"");
    }
    public void apply(Player player,int action,String selectedUUID,int value,String text){
        if(!(player instanceof ServerPlayer server)||player.containerMenu!=this||!stillValid(player)
            ||selectedUUID==null||selectedUUID.length()>36||text==null||text.length()>64)return;
        var bank=BadgeBankData.get(server.server);refresh();
        if(action==SEARCH){
            String query=cleanQuery(text);if(!query.equals(searchQuery)){searchQuery=query;page=0;selectedId=null;}status="";
        }else if(action==PAGE){
            if(value<0||value>=pages)return;page=value;selectedId=null;status="";
        }else if(action==SELECT){
            UUID id=parse(selectedUUID);if(id==null)return;
            var archive=filtered(bank.entries(server));int index=-1;
            for(int i=0;i<archive.size();i++)if(archive.get(i).id().equals(id)){index=i;break;}
            if(index<0)return;
            selectedId=id;page=index/BadgeBankMessages.PAGE_SIZE;status="";
        }else if(action==DEPOSIT){
            var result=bank.deposit(server,input.getItem(0));status=result.message();if(result.ok())input.setChanged();
        }else if(action==RENAME||action==REMOVE){
            if(action==REMOVE&&value!=1)return;
            UUID id=parse(selectedUUID);if(id==null||!id.equals(selectedId)||!visible(id))return;
            var entry=filtered(bank.entries(server)).stream().filter(candidate->candidate.id().equals(id)).findFirst().orElse(null);
            if(entry==null)return;
            if(action==RENAME)status=bank.renameArchive(server,id,text).message();
            else{
                if(entry.stored()&&!hasSpace(inventory,bank.preview(server,id))){status="gui.industrial_facilities.bank.inventory_full";lastState=null;broadcastChanges();return;}
                var transfer=bank.removeArchive(server,id);status=transfer.result().message();
                if(transfer.ok())deliver(server,transfer.stack());
            }
        }else if(action==WITHDRAW||action==REISSUE){
            if(action==REISSUE&&value!=1)return;
            UUID id=parse(selectedUUID);if(id==null||!id.equals(selectedId)||!visible(id)||filtered(bank.entries(server)).stream().noneMatch(entry->entry.id().equals(id)))return;
            ItemStack preview=bank.preview(server,id);
            if(!hasSpace(inventory,preview)){status="gui.industrial_facilities.bank.inventory_full";broadcastChanges();return;}
            var transfer=action==WITHDRAW?bank.withdraw(server,id):bank.reissue(server,id,input.getItem(0));
            status=transfer.result().message();
            if(transfer.ok()){deliver(server,transfer.stack());input.setChanged();}
        }else return;
        // Explicit action replies also acknowledge names that sanitize to the existing value.
        lastState=null;
        broadcastChanges();
    }
    @Override public void removed(Player player){super.removed(player);if(!player.level().isClientSide())clearContainer(player,input);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(AccessBadges.isBadge(stack)){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(index<28){if(!moveItemStackTo(stack,28,37,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,1,28,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
