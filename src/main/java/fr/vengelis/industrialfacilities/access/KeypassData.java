package fr.vengelis.industrialfacilities.access;

import fr.vengelis.industrialfacilities.frequency.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-only PIN digests. Menus receive names, coordinates and whether a code exists. */
public final class KeypassData extends SavedData {
    public static final int PIN_LENGTH=6,MAX_READERS=64,MAX_READER_NAME=32;
    private static final SecureRandom RANDOM=new SecureRandom();
    public record Reader(UUID id,BlockPos position,boolean configured,String name) {}
    enum Verification { UNSET, DENIED, OK }
    private record Site(UUID id,BlockPos pos) {}
    private static final class Binding {
        final Site reader,manager;final UUID owner;final String dimension;
        byte[] salt=new byte[0],digest=new byte[0];
        Binding(Site reader,Site manager,UUID owner,String dimension){this.reader=reader;this.manager=manager;this.owner=owner;this.dimension=dimension;}
        boolean configured(){return salt.length==16&&digest.length==32;}
    }
    private final Map<UUID,Binding> bindings=new LinkedHashMap<>();
    // Names belong to the physical reader and survive a change of manager.
    private final Map<UUID,String> names=new HashMap<>();
    public static KeypassData get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(KeypassData::new,KeypassData::load),"industrial_keypass");}
    private static String dimension(KeypassTerminalEntity terminal){return terminal.getLevel().dimension().location().toString();}
    private static Site site(KeypassTerminalEntity terminal){return new Site(terminal.instanceId,terminal.getBlockPos().immutable());}
    public static boolean validPin(String pin){return pin!=null&&pin.length()==PIN_LENGTH&&pin.chars().allMatch(c->c>='0'&&c<='9');}
    public static String cleanReaderName(String name){return name==null||name.length()>MAX_READER_NAME?null:AccessBadges.cleanName(name);}
    private static byte[] digest(byte[] salt,String pin){
        try{var hash=MessageDigest.getInstance("SHA-256");hash.update(salt);return hash.digest(pin.getBytes(StandardCharsets.US_ASCII));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static boolean validDevice(ServerPlayer player,KeypassTerminalEntity terminal){
        return player.isAlive()&&!player.isSpectator()&&player.level()==terminal.getLevel()&&!terminal.isRemoved()
            &&TerminalLinks.loaded(player.serverLevel(),terminal.getBlockPos())&&AccessTerminalEntity.resolve(player.level(),terminal.getBlockPos())==terminal;
    }
    private boolean belongs(Binding b,KeypassTerminalEntity manager){return manager.manager()&&b.manager.equals(site(manager))&&b.owner.equals(manager.creator)&&b.dimension.equals(dimension(manager));}
    public String link(ServerPlayer player,KeypassTerminalEntity manager,KeypassTerminalEntity reader){
        if(!manager.manager()||reader.manager()||!validDevice(player,manager)||!validDevice(player,reader)||!manager.isOwner(player)||!reader.isOwner(player)
            ||player.distanceToSqr(reader.getBlockPos().getCenter())>64||!TerminalLinks.inRange(manager.getBlockPos(),reader.getBlockPos()))return "link_denied";
        var existing=bindings.get(reader.instanceId);if(existing!=null)return belongs(existing,manager)?"already_linked":"other_manager";
        if(bindings.values().stream().filter(b->belongs(b,manager)).count()>=MAX_READERS)return "link_limit";
        reader.ensureCode();bindings.put(reader.instanceId,new Binding(site(reader),site(manager),player.getUUID(),dimension(reader)));setDirty();return "linked";
    }
    private FrequencyEndpoint managerFor(ServerLevel level, UUID reader) {
        var managers = FrequencyNetwork.get(level).senders(level, reader).stream()
            .filter(peer -> peer.type() == FrequencyEndpoint.Type.KEYPASS_MANAGER).toList();
        var binding = bindings.get(reader);
        if (binding != null)
            for (var manager : managers) if (manager.id().equals(binding.manager.id)) return manager;
        return managers.isEmpty() ? null : managers.getFirst();
    }
    public boolean frequencyBinding(ServerLevel level, UUID manager, UUID reader) {
        var selected = managerFor(level, reader);
        return selected != null && selected.id().equals(manager);
    }
    public List<Reader> readers(KeypassTerminalEntity manager){
        if (!(manager.getLevel() instanceof ServerLevel level)) return List.of();
        var network = FrequencyNetwork.get(level);
        var result = new ArrayList<Reader>();
        for (Binding binding : bindings.values()) {
            if (belongs(binding, manager) && network.legacy(manager.instanceId, binding.reader.id))
                result.add(new Reader(binding.reader.id, binding.reader.pos, binding.configured(), readerName(binding.reader.id)));
        }
        for (var peer : network.peers(level, manager.instanceId)) {
            if (peer.type() != FrequencyEndpoint.Type.KEYPASS || !frequencyBinding(level, manager.instanceId, peer.id())) continue;
            Binding binding = bindings.get(peer.id());
            result.add(new Reader(peer.id(), peer.pos(), binding != null && belongs(binding, manager) && binding.configured(), readerName(peer.id())));
        }
        return result;
    }
    public String readerName(KeypassTerminalEntity reader){return reader.manager()?"":readerName(reader.instanceId);}
    public String readerName(UUID reader){return names.getOrDefault(reader,"");}
    private static boolean mayManage(ServerPlayer player,KeypassTerminalEntity manager){
        return manager.manager()&&validDevice(player,manager)&&manager.isOwner(player)&&player.distanceToSqr(manager.getBlockPos().getCenter())<=64;
    }
    public String rename(ServerPlayer player,KeypassTerminalEntity manager,UUID reader,String name){
        if(!mayManage(player,manager))return "link_denied";
        if (readers(manager).stream().noneMatch(row -> row.id().equals(reader))) return "not_linked";
        String clean=cleanReaderName(name);if(clean==null)return "invalid_name";
        if(clean.isEmpty())names.remove(reader);else names.put(reader,clean);
        setDirty();return clean.isEmpty()?"name_cleared":"name_saved";
    }
    public String edit(ServerPlayer player,KeypassTerminalEntity manager,UUID reader,String pin,boolean unlink){
        if(!mayManage(player,manager))return "link_denied";
        var row = readers(manager).stream().filter(value -> value.id().equals(reader)).findFirst().orElse(null);
        if (row == null) return "not_linked";
        if (unlink) {
            if (frequencyBinding(player.serverLevel(), manager.instanceId, reader)) return "frequency_unlink";
            bindings.remove(reader);
            setDirty();
            return "unlinked";
        }
        if (!pin.isEmpty() && !validPin(pin)) return "invalid_pin";
        var b = bindings.get(reader);
        if (b == null || !belongs(b, manager)) {
            b = new Binding(new Site(reader, row.position()), site(manager), manager.creator, dimension(manager));
            bindings.put(reader, b);
        }
        if(pin.isEmpty()){b.salt=b.digest=new byte[0];setDirty();return "revoked";}
        b.salt=new byte[16];RANDOM.nextBytes(b.salt);b.digest=digest(b.salt,pin);setDirty();return "saved";
    }
    Verification verify(KeypassTerminalEntity reader,String pin){
        var b=bindings.get(reader.instanceId);
        if(b==null||reader.manager()||!b.reader.equals(site(reader))||!b.dimension.equals(dimension(reader))||!b.configured())return Verification.UNSET;
        var level = (ServerLevel) reader.getLevel();
        if (FrequencyNetwork.get(level).legacy(b.manager.id, reader.instanceId)) {
            if (!b.owner.equals(reader.creator)) return Verification.UNSET;
        } else if (!frequencyBinding(level, b.manager.id, reader.instanceId)) return Verification.UNSET;
        return MessageDigest.isEqual(b.digest,digest(b.salt,pin))?Verification.OK:Verification.DENIED;
    }
    public void removeDevice(UUID id){boolean named=names.remove(id)!=null;if(bindings.values().removeIf(b->b.reader.id.equals(id)||b.manager.id.equals(id))||named)setDirty();}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        ListTag rows=new ListTag();for(Binding b:bindings.values()){
            CompoundTag row=new CompoundTag();row.putUUID("Reader",b.reader.id);row.putLong("ReaderPos",b.reader.pos.asLong());
            row.putUUID("Manager",b.manager.id);row.putLong("ManagerPos",b.manager.pos.asLong());row.putUUID("Owner",b.owner);row.putString("Dimension",b.dimension);
            if(b.configured()){row.putByteArray("Salt",b.salt);row.putByteArray("Digest",b.digest);}rows.add(row);
        }tag.put("Bindings",rows);
        ListTag named=new ListTag();names.forEach((id,name)->{CompoundTag row=new CompoundTag();row.putUUID("Reader",id);row.putString("Name",name);named.add(row);});
        tag.put("ReaderNames",named);return tag;
    }
    public static KeypassData load(CompoundTag tag,HolderLookup.Provider registries){
        KeypassData data=new KeypassData();Map<UUID,Integer> counts=new HashMap<>();
        for(Tag raw:tag.getList("Bindings",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)raw;if(!row.hasUUID("Reader")||!row.hasUUID("Manager")||!row.hasUUID("Owner"))continue;
            var reader=new Site(row.getUUID("Reader"),BlockPos.of(row.getLong("ReaderPos")));var manager=new Site(row.getUUID("Manager"),BlockPos.of(row.getLong("ManagerPos")));
            if(reader.id.equals(manager.id)||!TerminalLinks.inRange(reader.pos,manager.pos)||data.bindings.containsKey(reader.id)||counts.getOrDefault(manager.id,0)>=MAX_READERS)continue;
            Binding b=new Binding(reader,manager,row.getUUID("Owner"),row.getString("Dimension"));byte[] salt=row.getByteArray("Salt"),hash=row.getByteArray("Digest");
            if(salt.length==16&&hash.length==32){b.salt=salt;b.digest=hash;}
            data.bindings.put(reader.id,b);counts.merge(manager.id,1,Integer::sum);
        }
        for(Tag raw:tag.getList("ReaderNames",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)raw;String name=cleanReaderName(row.getString("Name"));
            if(row.hasUUID("Reader")&&name!=null&&!name.isEmpty())data.names.put(row.getUUID("Reader"),name);
        }
        return data;
    }
}
