package fr.vengelis.industrialfacilities.security;
import java.util.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
/** Short renewable leases prevent an unloaded/crashed source from latching outputs forever. */
public final class AutomationOutputs {
    private record Target(String dimension,BlockPos position,String kind,UUID instance) {}
    private static final Map<MinecraftServer,Map<Target,Map<UUID,Long>>> SERVERS=new WeakHashMap<>();
    private static Target target(ServerLevel level,BlockPos pos,String kind,UUID id){return new Target(level.dimension().location().toString(),pos.immutable(),kind,id);}
    public static boolean active(ServerLevel level,BlockPos pos,String kind,UUID id){
        if(id==null)return false;var world=SERVERS.get(level.getServer());if(world==null)return false;Target target=target(level,pos,kind,id);var requests=world.get(target);if(requests==null)return false;
        requests.values().removeIf(until->until<=level.getGameTime());if(requests.isEmpty()){world.remove(target);return false;}return true;
    }
    public static void request(ServerLevel level,BlockPos pos,String kind,UUID id,UUID source,boolean on){
        Target target=target(level,pos,kind,id);var world=SERVERS.computeIfAbsent(level.getServer(),k->new HashMap<>());
        if(on)world.computeIfAbsent(target,k->new HashMap<>()).put(source,level.getGameTime()+15);
        else {var requests=world.get(target);if(requests!=null){requests.remove(source);if(requests.isEmpty())world.remove(target);}}
        refresh(level,target);
    }
    public static void release(ServerLevel level,UUID source){
        var world=SERVERS.get(level.getServer());if(world==null)return;List<Target> changed=new ArrayList<>();
        var iterator=world.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(entry.getValue().remove(source)!=null)changed.add(entry.getKey());if(entry.getValue().isEmpty())iterator.remove();}
        for(Target target:changed)if(target.dimension.equals(level.dimension().location().toString()))refresh(level,target);
    }
    public static void forgetDoor(ServerLevel level,BlockPos position){
        var world=SERVERS.get(level.getServer());if(world==null)return;String dimension=level.dimension().location().toString();
        world.keySet().removeIf(t->t.kind.equals("door")&&t.dimension.equals(dimension)&&t.position.equals(position));
    }
    private static void refresh(ServerLevel level,Target target){
        if(!level.hasChunkAt(target.position))return;var be=level.getBlockEntity(target.position);
        if(be instanceof AlarmBlockEntity alarm)alarm.updatePower();else if(be instanceof SignalEmitterEntity emitter)emitter.updatePower();
    }
    private AutomationOutputs(){}
}
