package fr.vengelis.industrialfacilities.access.ui;

import java.util.List;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KeypassMenu extends AbstractContainerMenu {
    public static final int SUBMIT=0,SAVE=1,REVOKE=2,UNLINK=3,DETACH=4,RENAME=5;
    public final boolean manager,owner;
    private final Inventory inventory;
    private final KeypassTerminalEntity terminal;
    private final java.util.UUID instance;
    public List<KeypassData.Reader> readers=List.of();
    public String status="";
    public int doors,revision;
    private KeypassMessages.State last;
    public KeypassMenu(int id,Inventory inventory,KeypassTerminalEntity terminal){
        super(KeypassMenus.TYPE,id);this.inventory=inventory;this.terminal=terminal;instance=terminal.instanceId;manager=terminal.manager();owner=terminal.isOwner(inventory.player);
    }
    public KeypassMenu(int id,Inventory inventory,FriendlyByteBuf b){
        super(KeypassMenus.TYPE,id);this.inventory=inventory;terminal=null;instance=null;manager=b.readBoolean();owner=b.readBoolean();
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){
        if(player.level().isClientSide)return true;
        return terminal!=null&&player==inventory.player&&player instanceof ServerPlayer server&&KeypassData.validDevice(server,terminal)
            &&instance.equals(terminal.instanceId)&&player.distanceToSqr(terminal.getBlockPos().getCenter())<=64&&(!manager||terminal.isOwner(player));
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(inventory.player instanceof ServerPlayer player)||!stillValid(player))return;
        var state=new KeypassMessages.State(containerId,revision,terminal.linkedCount(),status,manager?KeypassData.get(player.serverLevel()).readers(terminal):List.of());
        if(!state.equals(last)){last=state;PacketDistributor.sendToPlayer(player,state);}
    }
    public void receive(KeypassMessages.State state){if(state.menuId()==containerId){readers=state.readers();status=state.status();doors=state.doors();revision=state.revision();}}
    public void apply(ServerPlayer player,KeypassMessages.Action action){
        if(player.containerMenu!=this||action.menuId()!=containerId||!stillValid(player))return;
        int kind=action.kind();
        if(manager){
            if(kind!=SAVE&&kind!=REVOKE&&kind!=UNLINK&&kind!=RENAME)return;
            var data=KeypassData.get(player.serverLevel());
            if(kind==RENAME)status=data.rename(player,terminal,action.reader(),action.value());
            else if(kind==SAVE&&!KeypassData.validPin(action.value()))status="invalid_pin";
            else status=data.edit(player,terminal,action.reader(),kind==SAVE?action.value():"",kind==UNLINK);
        }else if(kind==SUBMIT){
            status=AccessDoorService.activateKeypass(player,terminal,action.value());
            boolean ok=status.equals("granted");
            if(!status.equals("slow_down")&&!status.equals("locked"))player.serverLevel().playSound(null,terminal.getBlockPos().getX()+.5,terminal.getBlockPos().getY()+1.3,terminal.getBlockPos().getZ()+.5,
                ok?IndustrialFacilitiesModSounds.TERMINAL_ACCESS_GRANTED.get():IndustrialFacilitiesModSounds.TERMINAL_ACCESS_DENIED.get(),SoundSource.BLOCKS,.6f,1f);
            if(ok){player.closeContainer();return;}
        }else if(kind==DETACH&&terminal.isOwner(player)){status=terminal.clearLinks(player)?"detached":"not_linked";}
        else return;
        revision++;broadcastChanges();
    }
}
