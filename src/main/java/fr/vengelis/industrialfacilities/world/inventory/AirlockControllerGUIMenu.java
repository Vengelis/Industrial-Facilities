package fr.vengelis.industrialfacilities.world.inventory;

import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModMenus;
import fr.vengelis.industrialfacilities.network.MenuStateUpdateMessage;
import fr.vengelis.industrialfacilities.procedures.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AirlockControllerGUIMenu extends AbstractContainerMenu implements IndustrialFacilitiesModMenus.MenuAccessor {
    public final Level world;
    public final Player entity;
    public final int x,y,z;
    private final BlockPos controllerPos;
    private final AirlockControllerBlockEntity controller;
    private int updateTicks;
    private boolean firstSnapshot=true;
    private final Map<String,Object> sentState=new HashMap<>();
    public final Map<String,Object> menuState=new HashMap<>() {
        @Override public Object put(String key,Object value) {
            // Generic MCreator state packets must never set server-owned status/toggles.
            if (!world.isClientSide()) {
                if (entity.containerMenu!=AirlockControllerGUIMenu.this || !stillValid(entity)) return null;
                if ("0:text_input_restricted_access".equals(key)) {
                    if (!(value instanceof String text) || text.length()>16) return null;
                    return super.put(key,text);
                }
                if ("2:slider_detect_range".equals(key)) {
                    if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) return null;
                    Object old=super.put(key,(double)Math.round(Math.max(1,Math.min(10,n.doubleValue()))));
                    AirlockSetDetectionRangeProcedure.execute(world,x,y,z,entity);
                    refreshState();
                    return old;
                }
                return null;
            }
            if (!containsKey(key) && size()>=16) return null;
            return super.put(key,value);
        }
    };

    public AirlockControllerGUIMenu(int id,Inventory inv,FriendlyByteBuf extraData) {
        super(IndustrialFacilitiesModMenus.AIRLOCK_CONTROLLER_GUI.get(),id);
        entity=inv.player;
        world=entity.level();
        controllerPos=extraData!=null ? extraData.readBlockPos() : BlockPos.ZERO;
        x=controllerPos.getX(); y=controllerPos.getY(); z=controllerPos.getZ();
        controller=world.getBlockEntity(controllerPos) instanceof AirlockControllerBlockEntity be ? be : null;
        AirlockInitializeGUIProcedure.execute(world,x,y,z);
        // The first snapshot is sent in broadcastChanges, after this is the active menu.
    }

    public boolean matches(BlockPos pos) { return controllerPos.equals(pos); }
    public String getPlayerInput() {
        Object value=menuState.get("0:text_input_restricted_access");
        return value instanceof String text ? text : "";
    }
    @Override public boolean stillValid(Player player) {
        return player.level()==world && player.isAlive() && world.hasChunkAt(controllerPos)
            && controller!=null && !controller.isRemoved() && world.getBlockEntity(controllerPos)==controller
            && world.getBlockState(controllerPos).is(IndustrialFacilitiesModBlocks.AIRLOCK_CONTROLLER.get())
            && player.distanceToSqr(x+0.5,y+0.5,z+0.5)<=64;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (firstSnapshot || ++updateTicks>=10) { updateTicks=0; refreshState(); }
    }
    public void refreshState() {
        if (!(entity instanceof ServerPlayer player) || player.containerMenu!=this || !stillValid(player)) return;
        var data=controller.getPersistentData();
        publish(player,1,"player_detection",!data.contains("IFPlayerDetection") || data.getBoolean("IFPlayerDetection"));
        publish(player,1,"redstone",data.getBoolean("IFRedstoneEnabled"));
        publish(player,1,"restricted",data.getBoolean("IFWhitelistEnabled"));
        publish(player,1,"inverted",data.getBoolean("IFInvertSelection"));
        publish(player,1,"closed",data.getBoolean("IFAirlockClosed"));
        publish(player,2,"slider_detect_range",(double)Math.max(1,Math.min(10,data.getInt("IFDetectionRange"))));
        publish(player,0,"players_text",AirlockGetPlayerTextProcedure.execute(world,x,y,z));
        publish(player,0,"status_text",AirlockGetStatusTextProcedure.execute(world,x,y,z));
        publish(player,1,"ready",true);
        firstSnapshot=false;
    }
    private void publish(ServerPlayer player,int type,String name,Object value) {
        String key=type+":"+name;
        if (firstSnapshot || !Objects.equals(sentState.get(key),value)) {
            sentState.put(key,value);
            PacketDistributor.sendToPlayer(player,new MenuStateUpdateMessage(type,name,value));
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public Map<Integer,Slot> getSlots() { return Collections.emptyMap(); }
    @Override public Map<String,Object> getMenuState() { return menuState; }
}

