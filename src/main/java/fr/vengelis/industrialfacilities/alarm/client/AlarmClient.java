package fr.vengelis.industrialfacilities.alarm.client;

import com.google.gson.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.alarm.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="industrial_facilities",value=Dist.CLIENT)
public final class AlarmClient {
    private static final Map<BlockPos,AlarmLoop> LOOPS=new HashMap<>();
    private static Object world;
    private static long ticks;
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { AlarmSignal.receiver=AlarmClient::receive; }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(AlarmContent.MENU,AlarmScreen::new); }
    @SubscribeEvent public static void packs(AddPackFindersEvent event) {
        if (event.getPackType()!=PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer->{
            AlarmConfig.load();
            try {
                Path root=AlarmConfig.DIRECTORY.resolve("_generated_pack");
                Path assets=root.resolve("assets/industrial_facilities");
                Files.createDirectories(assets.resolve("sounds/custom_alarm"));
                var definitions=new JsonObject();
                for (var e:AlarmConfig.entries()) if (!e.file().isEmpty()) {
                    Path source=AlarmConfig.DIRECTORY.resolve(e.file()).toRealPath();
                    if (!source.startsWith(AlarmConfig.DIRECTORY.toRealPath())) continue;
                    Files.copy(source,assets.resolve("sounds/custom_alarm/"+e.id()+".ogg"),StandardCopyOption.REPLACE_EXISTING);
                    var sound=new JsonObject(); sound.addProperty("name",e.sound().toString()); sound.addProperty("stream",true);
                    var sounds=new JsonArray(); sounds.add(sound);
                    var def=new JsonObject(); def.addProperty("replace",true); def.add("sounds",sounds);
                    definitions.add(e.sound().getPath(),def);
                }
                Files.writeString(assets.resolve("sounds.json"),definitions.toString());
                Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":34,\"description\":\"Industrial Facilities alarm sounds\"}}");
                var pack=Pack.readMetaAndCreate(new PackLocationInfo("industrial_facilities:alarms",Component.literal("Industrial Facilities — Alarms"),PackSource.BUILT_IN,Optional.empty()),
                    new PathPackResources.PathResourcesSupplier(root),PackType.CLIENT_RESOURCES,new PackSelectionConfig(true,Pack.Position.TOP,true));
                if (pack!=null) consumer.accept(pack);
            } catch (Exception error) { IndustrialFacilitiesMod.LOGGER.error("Cannot prepare custom alarm resource pack",error); }
        });
    }
    public static void receive(AlarmSignal signal) {
        var mc=Minecraft.getInstance();
        if (mc.level==null || !mc.level.dimension().location().equals(signal.dimension())) return;
        if (world!=mc.level) clear();
        world=mc.level;
        var old=LOOPS.get(signal.pos());
        if (!signal.active()) { if (old!=null) old.finish(); LOOPS.remove(signal.pos()); return; }
        if (signal.range()<1 || signal.range()>AlarmConfig.MAX_RANGE) return;
        if (old!=null && (!old.getLocation().equals(signal.sound()) || old.isStopped())) { old.finish(); LOOPS.remove(signal.pos()); old=null; }
        if (old==null) {
            // A missing client file must stay silent, without repeatedly asking the sound engine to load it.
            if (mc.getSoundManager().getSoundEvent(signal.sound())==null) return;
            old=new AlarmLoop(signal.pos(),signal.sound(),signal.range());
            LOOPS.put(signal.pos(),old); mc.getSoundManager().play(old);
        }
        old.radius=signal.range(); old.lastHeartbeat=ticks;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if (mc.level!=world) { clear(); world=mc.level; }
        if (mc.isPaused()) return;
        ticks++;
        var it=LOOPS.values().iterator();
        while (it.hasNext()) {
            var loop=it.next();
            if (ticks-loop.lastHeartbeat>15 || loop.isStopped()) { loop.finish(); it.remove(); }
        }
    }
    private static void clear() { LOOPS.values().forEach(AlarmLoop::finish); LOOPS.clear(); }
    public static int activeCount() { return LOOPS.size(); }
    public static long ticks() { return ticks; }
}
