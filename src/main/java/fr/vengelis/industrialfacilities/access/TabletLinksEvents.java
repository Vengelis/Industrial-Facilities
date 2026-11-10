package fr.vengelis.industrialfacilities.access;

import java.util.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.ui.TabletLinksMessages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class TabletLinksEvents {
    private static final Map<MinecraftServer,Map<UUID,ResourceLocation>> ACTIVE=new WeakHashMap<>();
    private static final TerminalLinks.Snapshot EMPTY=new TerminalLinks.Snapshot(List.of(),false);
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();var active=ACTIVE.computeIfAbsent(server,key->new HashMap<>());
        for(var player:server.getPlayerList().getPlayers()){
            ResourceLocation dimension=player.level().dimension().location();
            if(!TerminalLinks.holdingTablet(player)){
                if(active.remove(player.getUUID())!=null)TabletLinksMessages.send(player,EMPTY);
                continue;
            }
            ResourceLocation previous=active.put(player.getUUID(),dimension);
            if(previous==null||!previous.equals(dimension)||server.getTickCount()%10==0)TabletLinksMessages.send(player,TerminalLinks.snapshot(player));
        }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;var active=ACTIVE.get(player.server);
        if(active!=null&&active.remove(player.getUUID())!=null)TabletLinksMessages.send(player,EMPTY);
    }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){var active=ACTIVE.get(player.server);if(active!=null)active.remove(player.getUUID());}}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){ACTIVE.remove(event.getServer());}
    private TabletLinksEvents(){}
}
