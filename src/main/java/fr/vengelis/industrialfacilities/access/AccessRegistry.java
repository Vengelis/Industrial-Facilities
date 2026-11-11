package fr.vengelis.industrialfacilities.access;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Authoritative world-wide directory; looking up a code never loads its chunk. */
public final class AccessRegistry extends SavedData {
    public record DoorLink(String dimension, BlockPos position, UUID instance) {}
    public static final int MAX_GROUP_READERS=64,MAX_DOORS=64;
    public enum GroupResult { LINKED, ALREADY_LINKED, INCOMPATIBLE, TOO_MANY_READERS, TOO_FAR, DIFFERENT_DOORS }
    private static final class ReaderGroup {
        final Set<Entry> members=new LinkedHashSet<>();
        final List<DoorLink> doors=new ArrayList<>();
        final List<DoorLink> view=Collections.unmodifiableList(doors);
    }
    public static final class Entry {
        public final UUID id,owner;
        public final String dimension;
        public final BlockPos position;
        public int level;
        private ReaderGroup group=new ReaderGroup();
        Entry(UUID id,UUID owner,String dimension,BlockPos position,int level){this.id=id;this.owner=owner;this.dimension=dimension;this.position=position.immutable();this.level=level;group.members.add(this);}
        public List<DoorLink> doors(){return group.view;}
        public int readerCount(){return group.members.size();}
    }
    private final Map<UUID,Entry> terminals=new HashMap<>();
    private final Map<UUID,Set<UUID>> doorReaders=new HashMap<>();
    private record DoorSite(String dimension,BlockPos position) {}
    /** A null generation means the controller was removed. An absent site is a legacy door. */
    private final Map<DoorSite,UUID> doorGenerations=new HashMap<>();
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
    public void remove(UUID id){Entry e=terminals.remove(id);if(e!=null){e.group.members.remove(e);reindex();setDirty();}}
    /** Disconnect only this reader; shared doors belong to the remaining group. */
    public void clearLinks(UUID id){Entry e=terminals.get(id);if(e!=null){e.group.members.remove(e);e.group=new ReaderGroup();e.group.members.add(e);reindex();setDirty();}}
    public boolean allReadersInRange(Entry reader,DoorLink door){return reader.group.members.stream().allMatch(e->inRange(e,door));}
    private static boolean inRange(Entry reader,DoorLink door){return reader.dimension.equals(door.dimension())&&TerminalLinks.inRange(reader.position,door.position());}
    private static boolean nearby(Set<Entry> members){for(Entry a:members)for(Entry b:members)if(!TerminalLinks.inRange(a.position,b.position))return false;return true;}
    private static void assignGroup(Set<Entry> members,List<DoorLink> doors){ReaderGroup group=new ReaderGroup();group.members.addAll(members);group.doors.addAll(doors);for(Entry e:members)e.group=group;}
    public GroupResult groupReaders(UUID first,UUID second){
        Entry a=terminals.get(first),b=terminals.get(second);
        if(a==null||b==null||!a.owner.equals(b.owner)||!a.dimension.equals(b.dimension))return GroupResult.INCOMPATIBLE;
        if(a.group==b.group)return GroupResult.ALREADY_LINKED;
        Set<Entry> members=new LinkedHashSet<>(a.group.members);members.addAll(b.group.members);
        if(members.size()>MAX_GROUP_READERS)return GroupResult.TOO_MANY_READERS;
        if(!a.doors().isEmpty()&&!b.doors().isEmpty()&&!new HashSet<>(a.doors()).equals(new HashSet<>(b.doors())))return GroupResult.DIFFERENT_DOORS;
        List<DoorLink> doors=a.doors().isEmpty()?b.doors():a.doors();
        if(!nearby(members)||members.stream().anyMatch(e->doors.stream().anyMatch(d->!inRange(e,d))))return GroupResult.TOO_FAR;
        assignGroup(members,doors);reindex();setDirty();return GroupResult.LINKED;
    }
    public boolean link(UUID reader,DoorLink link){Entry e=terminals.get(reader);if(e==null||e.doors().contains(link)||e.doors().size()>=MAX_DOORS)return false;e.group.doors.add(link);reindex();setDirty();return true;}
    public boolean obsoleteDoorLink(String dimension,BlockPos pos,UUID instance){
        DoorSite site=new DoorSite(dimension,pos);return doorGenerations.containsKey(site)&&!Objects.equals(doorGenerations.get(site),instance);
    }
    public void registerDoor(String dimension,BlockPos pos,UUID instance){
        doorGenerations.put(new DoorSite(dimension,pos.immutable()),instance);
        removeDoorLinks(d->d.dimension().equals(dimension)&&d.position().equals(pos)&&!d.instance().equals(instance));setDirty();
    }
    public Set<Entry> invalidateDoor(String dimension,BlockPos pos,UUID instance){
        doorGenerations.put(new DoorSite(dimension,pos.immutable()),null);
        Set<Entry> affected=removeDoorLinks(d->d.instance().equals(instance)||d.dimension().equals(dimension)&&d.position().equals(pos));
        setDirty();return affected;
    }
    private Set<Entry> removeDoorLinks(java.util.function.Predicate<DoorLink> remove){
        Set<Entry> affected=new HashSet<>();for(ReaderGroup group:groups())if(group.doors.removeIf(remove))affected.addAll(group.members);
        if(!affected.isEmpty())reindex();return affected;
    }
    private Set<ReaderGroup> groups(){Set<ReaderGroup> result=new HashSet<>();for(Entry e:terminals.values())result.add(e.group);return result;}
    private void reindex(){doorReaders.clear();for(Entry e:terminals.values())for(DoorLink d:e.doors())doorReaders.computeIfAbsent(d.instance(),k->new HashSet<>()).add(e.id);}
    private static ListTag writeDoors(List<DoorLink> doors){
        ListTag list=new ListTag();for(DoorLink d:doors){CompoundTag q=new CompoundTag();q.putString("Dimension",d.dimension());q.putLong("Position",d.position().asLong());q.putUUID("Instance",d.instance());list.add(q);}return list;
    }
    private static List<DoorLink> readDoors(CompoundTag tag){
        Set<DoorLink> doors=new LinkedHashSet<>();for(Tag raw:tag.getList("Doors",Tag.TAG_COMPOUND)){CompoundTag q=(CompoundTag)raw;if(doors.size()>=MAX_DOORS)break;if(q.hasUUID("Instance"))doors.add(new DoorLink(q.getString("Dimension"),BlockPos.of(q.getLong("Position")),q.getUUID("Instance")));}return List.copyOf(doors);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
        ListTag list=new ListTag();for(Entry e:terminals.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",e.id);t.putUUID("Owner",e.owner);t.putString("Dimension",e.dimension);t.putLong("Position",e.position.asLong());t.putInt("Level",e.level);
            if(e.readerCount()==1)t.put("Doors",writeDoors(e.doors()));list.add(t);
        }tag.put("Terminals",list);
        ListTag grouped=new ListTag();for(ReaderGroup group:groups())if(group.members.size()>1){
            CompoundTag g=new CompoundTag();ListTag members=new ListTag();for(Entry e:group.members){CompoundTag member=new CompoundTag();member.putUUID("Id",e.id);members.add(member);}
            g.put("Readers",members);g.put("Doors",writeDoors(group.doors));grouped.add(g);
        }tag.put("ReaderGroups",grouped);
        ListTag generations=new ListTag();doorGenerations.forEach((site,id)->{
            CompoundTag row=new CompoundTag();row.putString("Dimension",site.dimension());row.putLong("Position",site.position().asLong());
            if(id!=null)row.putUUID("Instance",id);generations.add(row);
        });tag.put("DoorGenerations",generations);return tag;
    }
    public static AccessRegistry load(CompoundTag tag,HolderLookup.Provider provider){
        AccessRegistry data=new AccessRegistry();for(Tag raw:tag.getList("Terminals",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)raw;if(!t.hasUUID("Id")||!t.hasUUID("Owner"))continue;
            Entry e=new Entry(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Dimension"),BlockPos.of(t.getLong("Position")),Math.clamp(t.getInt("Level"),1,5));
            e.group.doors.addAll(readDoors(t));
            data.terminals.put(e.id,e);
        }
        for(Tag raw:tag.getList("ReaderGroups",Tag.TAG_COMPOUND)){
            CompoundTag g=(CompoundTag)raw;ListTag rows=g.getList("Readers",Tag.TAG_COMPOUND);
            if(rows.size()<2||rows.size()>MAX_GROUP_READERS)continue;
            Set<Entry> members=new LinkedHashSet<>();
            for(Tag row:rows){CompoundTag r=(CompoundTag)row;Entry e=r.hasUUID("Id")?data.find(r.getUUID("Id")):null;if(e!=null&&e.readerCount()==1&&e.doors().isEmpty())members.add(e);}
            if(members.size()!=rows.size()||!nearby(members))continue;
            Entry first=members.iterator().next();List<DoorLink> doors=readDoors(g);
            if(members.stream().anyMatch(e->!e.owner.equals(first.owner)||!e.dimension.equals(first.dimension)||doors.stream().anyMatch(d->!inRange(e,d))))continue;
            assignGroup(members,doors);
        }
        for(Tag raw:tag.getList("DoorGenerations",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)raw;data.doorGenerations.put(new DoorSite(row.getString("Dimension"),BlockPos.of(row.getLong("Position"))),row.hasUUID("Instance")?row.getUUID("Instance"):null);
        }
        data.reindex();return data;
    }
}
