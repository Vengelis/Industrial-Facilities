package fr.vengelis.industrialfacilities.alarm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
@EventBusSubscriber(modid="industrial_facilities")
public final class AlarmEvents {
    @SubscribeEvent public static void start(ServerAboutToStartEvent event) { AlarmConfig.load(); }
}
