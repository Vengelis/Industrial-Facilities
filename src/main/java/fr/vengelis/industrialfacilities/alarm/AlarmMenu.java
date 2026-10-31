package fr.vengelis.industrialfacilities.alarm;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public class AlarmMenu extends AbstractContainerMenu {
    public record Choice(String id,String name,ResourceLocation sound) {}
    public final BlockPos pos;
    public final List<Choice> choices;
    public final String selected;
    public final int range;
    private final AlarmBlockEntity alarm;
    public AlarmMenu(int id,Inventory inv,AlarmBlockEntity be) {
        super(AlarmContent.MENU,id); alarm=be; pos=be.getBlockPos(); selected=be.soundId(); range=be.range();
        choices=AlarmConfig.entries().stream().map(e -> new Choice(e.id(),e.name(),e.sound())).toList();
    }
    public AlarmMenu(int id,Inventory inv,FriendlyByteBuf buf) {
        super(AlarmContent.MENU,id); alarm=null;
        pos=buf.readBlockPos(); selected=buf.readUtf(48); range=buf.readVarInt();
        int count=buf.readVarInt(); if (count<1 || count>128) throw new IllegalArgumentException("Invalid alarm catalogue size");
        var list=new ArrayList<Choice>();
        for (int i=0;i<count;i++) list.add(new Choice(buf.readUtf(48),buf.readUtf(80),buf.readResourceLocation()));
        choices=List.copyOf(list);
    }
    @Override public boolean stillValid(Player p) {
        return p.isAlive() && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) instanceof AlarmBlockEntity current
            && (alarm==null || current==alarm) && p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
    public void apply(String id,int radius,Player player) {
        if (alarm!=null && stillValid(player) && AlarmConfig.find(id)!=null && radius>=1 && radius<=AlarmConfig.MAX_RANGE) {
            alarm.configure(id,radius); player.closeContainer();
        }
    }
    @Override public ItemStack quickMoveStack(Player p,int index) { return ItemStack.EMPTY; }
}
