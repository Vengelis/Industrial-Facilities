package fr.vengelis.industrialfacilities.access;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative world-wide directory; looking up a code never loads its chunk. */
public final class AccessRegistry extends SavedData {
    public record DoorLink(String dimension, BlockPos position, UUID instance) {}
    public static final class Entry {
        public final UUID id,owner;
        public final String dimension;
        public final BlockPos position;
        public int level;
        public final List<DoorLink> doors=new ArrayList<>();
        Entry(UUID id,UUID owner,String dimension,BlockPos position,int level){this.id=id;this.owner=owner;this.dimension=dimension;this.position=position.immutable();this.level=level;}
    }
    private final Map<UUID,Entry> terminals=new HashMap<>();
    private final Map<UUID,Set<UUID>> doorReaders=new HashMap<>();
    public static AccessRegistry get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AccessRegistry::new,AccessRegistry::load),"industrial_access");}
    public Entry find(UUID id){return terminals.get(id);}
    public Collection<Entry> entries(){return Collections.unmodifiableCollection(terminals.values());}
    public Collection<Entry> readers(UUID door){return doorReaders.getOrDefault(door,Set.of()).stream().map(terminals::get).filter(Objects::nonNull).toList();}
    public void put(AccessTerminalEntity terminal){
        if(terminal.terminalId==null||terminal.creator==null)return;
        Entry existing=terminals.get(terminal.terminalId);
        if(existing==null)terminals.put(terminal.terminalId,new Entry(terminal.terminalId,terminal.creator,terminal.getLevel().dimension().location().toString(),terminal.getBlockPos(),terminal.accessLevel));
        else if(existing.owner.equals(terminal.creator))existing.level=terminal.accessLevel;
        setDirty();
    }
    public void remove(UUID id){Entry e=terminals.remove(id);if(e!=null){reindex();setDirty();}}
    public void clearLinks(UUID id){Entry e=terminals.get(id);if(e!=null){e.doors.clear();reindex();setDirty();}}
    public boolean link(UUID reader,DoorLink link){Entry e=terminals.get(reader);if(e==null||e.doors.contains(link))return false;if(e.doors.size()>=64)return false;e.doors.add(link);reindex();setDirty();return true;}
    public void invalidateDoor(UUID instance){boolean changed=false;for(Entry e:terminals.values())changed|=e.doors.removeIf(d->d.instance().equals(instance));if(changed){reindex();setDirty();}}
    private void reindex(){doorReaders.clear();for(Entry e:terminals.values())for(DoorLink d:e.doors)doorReaders.computeIfAbsent(d.instance(),k->new HashSet<>()).add(e.id);}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
        ListTag list=new ListTag();for(Entry e:terminals.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",e.id);t.putUUID("Owner",e.owner);t.putString("Dimension",e.dimension);t.putLong("Position",e.position.asLong());t.putInt("Level",e.level);
            ListTag doors=new ListTag();for(DoorLink d:e.doors){CompoundTag q=new CompoundTag();q.putString("Dimension",d.dimension());q.putLong("Position",d.position().asLong());q.putUUID("Instance",d.instance());doors.add(q);}t.put("Doors",doors);list.add(t);
        }tag.put("Terminals",list);return tag;
    }
    public static AccessRegistry load(CompoundTag tag,HolderLookup.Provider provider){
        AccessRegistry data=new AccessRegistry();for(Tag raw:tag.getList("Terminals",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw;if(!t.hasUUID("Id")||!t.hasUUID("Owner"))continue;
            Entry e=new Entry(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Dimension"),BlockPos.of(t.getLong("Position")),Math.clamp(t.getInt("Level"),1,5));
            for(Tag qraw:t.getList("Doors",Tag.TAG_COMPOUND)){CompoundTag q=(CompoundTag)qraw;if(q.hasUUID("Instance")&&e.doors.size()<64)e.doors.add(new DoorLink(q.getString("Dimension"),BlockPos.of(q.getLong("Position")),q.getUUID("Instance")));}
            data.terminals.put(e.id,e);
        }data.reindex();return data;
    }
}
