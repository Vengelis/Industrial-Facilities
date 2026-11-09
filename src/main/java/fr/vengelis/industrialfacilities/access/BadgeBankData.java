package fr.vengelis.industrialfacilities.access;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Global badge authority is independent from each player's private, removable catalogue. */
public final class BadgeBankData extends SavedData {
    public static final int MAX_BADGES=256;
    public record EntryView(UUID id,ItemStack badge,boolean stored,UUID issuer,boolean foreign,boolean copyAllowed) {
        public EntryView {badge=badge.copy();}
        @Override public ItemStack badge(){return badge.copy();}
    }
    public record Transfer(AccessBadges.Result result,ItemStack stack) {
        public boolean ok(){return result.ok();}
        private static Transfer fail(String key){return new Transfer(failure(key),ItemStack.EMPTY);}
    }
    private record Key(UUID issuer,UUID id) {}
    private static final class Ledger {
        final Key key;
        final long revision;
        ItemStack badge;
        Ledger(Key key,long revision,ItemStack badge){this.key=key;this.revision=revision;this.badge=badge.copy();}
    }
    private static final class Catalog {
        final Key key;
        String localName; // null inherits the issuer's name; an empty string deliberately clears it.
        ItemStack stored=ItemStack.EMPTY;
        Catalog(Key key){this.key=key;}
    }
    private final Map<UUID,Map<UUID,Ledger>> ledger=new HashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,Catalog>> catalogs=new HashMap<>();
    private final Map<UUID,Set<Key>> hidden=new HashMap<>();
    private final Map<UUID,Set<UUID>> revoked=new HashMap<>();
    public static BadgeBankData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BadgeBankData::new,BadgeBankData::load),"industrial_badge_bank");}
    private static AccessBadges.Result failure(String key){return AccessBadges.Result.fail("gui.industrial_facilities.badge_bank."+key);}
    private static AccessBadges.Result success(String key){return AccessBadges.Result.success("gui.industrial_facilities.badge_bank."+key);}
    private Map<UUID,Catalog> catalog(UUID player){return catalogs.getOrDefault(player,new LinkedHashMap<>());}
    private Catalog catalog(UUID player,UUID id){return id==null?null:catalog(player).get(id);}
    private Ledger find(Key key){return key==null?null:ledger.getOrDefault(key.issuer(),Map.of()).get(key.id());}
    private boolean revoked(Key key){return revoked.getOrDefault(key.issuer(),Set.of()).contains(key.id());}
    private boolean hidden(UUID player,Key key){return hidden.getOrDefault(player,Set.of()).contains(key);}
    private static long revision(CompoundTag data){return Math.max(0,data.getLong("Revision"));}
    private static Key key(ItemStack badge){CompoundTag data=AccessBadges.read(badge);return data.hasUUID("Issuer")&&data.hasUUID("BadgeId")?new Key(data.getUUID("Issuer"),data.getUUID("BadgeId")):null;}
    private static boolean sameEdition(ItemStack badge,Ledger entry){return revision(AccessBadges.read(badge))==entry.revision;}
    private void put(Ledger entry){ledger.computeIfAbsent(entry.key.issuer(),k->new HashMap<>()).put(entry.key.id(),entry);setDirty();}
    private void put(UUID player,Catalog entry){catalogs.computeIfAbsent(player,k->new LinkedHashMap<>()).put(entry.key.id(),entry);setDirty();}
    private void remove(UUID player,UUID id){var records=catalogs.get(player);if(records!=null){records.remove(id);if(records.isEmpty())catalogs.remove(player);}}
    private void autoCatalog(Ledger entry){
        UUID owner=entry.key.issuer();
        if(revoked(entry.key)||hidden(owner,entry.key)||catalog(owner).containsKey(entry.key.id())||catalog(owner).size()>=MAX_BADGES)return;
        put(owner,new Catalog(entry.key));
    }
    private boolean copyAllowed(UUID player,Catalog entry){
        Ledger canonical=find(entry.key);
        return canonical!=null&&!revoked(entry.key)&&(entry.key.issuer().equals(player)||AccessBadges.foreignBankAllowed(canonical.badge));
    }
    /** Names are local to this catalogue and its manufactured/returned copies, never access rights. */
    private ItemStack render(Catalog entry){
        Ledger canonical=find(entry.key);
        ItemStack badge=!entry.stored.isEmpty()?entry.stored.copy():canonical==null?ItemStack.EMPTY:canonical.badge.copy();
        if(badge.isEmpty())return badge;
        if(canonical!=null&&!revoked(entry.key)&&sameEdition(badge,canonical))AccessBadges.write(badge,AccessBadges.read(canonical.badge));
        if(entry.localName!=null){if(entry.localName.isEmpty())badge.remove(DataComponents.CUSTOM_NAME);else badge.set(DataComponents.CUSTOM_NAME,Component.literal(entry.localName));}
        return AccessBadges.normalize(badge);
    }
    /** Stable, defensive, authenticated catalogue views; issuer UUIDs never determine the viewer. */
    public List<EntryView> entries(ServerPlayer player){
        UUID viewer=player.getUUID();return catalog(viewer).values().stream().sorted(Comparator.comparing(e->e.key.id().toString()))
            .map(e->new EntryView(e.key.id(),render(e),!e.stored.isEmpty(),e.key.issuer(),!e.key.issuer().equals(viewer),copyAllowed(viewer,e))).toList();
    }
    public ItemStack preview(ServerPlayer player,UUID id){Catalog entry=catalog(player.getUUID(),id);return entry==null?ItemStack.EMPTY:render(entry);}

    /** Only the issuer can edit rights. An issuer's own stored item must be withdrawn first. */
    AccessBadges.Result editable(ServerPlayer player,ItemStack badge){
        CompoundTag data=AccessBadges.read(badge);
        if(data.hasUUID("Issuer")&&!data.getUUID("Issuer").equals(player.getUUID()))return failure("not_issuer");
        Key key=key(badge);if(key!=null){
            if(revoked(key))return failure("revoked");
            Ledger entry=find(key);if(entry!=null&&!sameEdition(badge,entry))return failure("obsolete");
            Catalog own=catalog(player.getUUID(),key.id());if(own!=null&&own.key.equals(key)&&!own.stored.isEmpty())return failure("withdraw_first");
        }
        return AccessBadges.Result.success("");
    }
    /** Synchronize the family first, so editing an older physical copy cannot restore spent uses. */
    AccessBadges.Result prepareEdit(ServerPlayer player,ItemStack badge){
        AccessBadges.Result allowed=editable(player,badge);if(!allowed.ok())return allowed;
        Key key=key(badge);Ledger entry=find(key);Catalog own=key==null?null:catalog(player.getUUID(),key.id());
        if(own!=null&&!own.key.equals(key))return failure("id_conflict");
        if(own==null&&(key==null||!hidden(player.getUUID(),key))&&catalog(player.getUUID()).size()>=MAX_BADGES)return failure("full");
        if(entry!=null)AccessBadges.write(badge,AccessBadges.read(entry.badge));
        return AccessBadges.Result.success("");
    }
    void archiveEdit(ServerPlayer player,ItemStack badge){
        Key key=key(badge);
        if(!AccessBadges.isBadge(badge)||badge.getCount()!=1||key==null||!key.issuer().equals(player.getUUID())||revoked(key))return;
        Ledger existing=find(key);CompoundTag data=AccessBadges.read(badge);long rev=existing==null?revision(data):existing.revision;
        data.putLong("Revision",rev);AccessBadges.write(badge,data);
        Ledger entry=new Ledger(key,rev,AccessBadges.normalize(badge.copy()));put(entry);autoCatalog(entry);
    }
    /** Explicit encoder naming takes precedence over this issuer's former local alias. */
    void encoderNameChanged(ServerPlayer player,ItemStack badge){
        Key key=key(badge);Catalog entry=key==null?null:catalog(player.getUUID(),key.id());
        if(entry!=null&&entry.key.equals(key)){entry.localName=null;setDirty();}
    }
    void clearIdentity(ServerPlayer player,ItemStack badge){
        Key key=key(badge);if(key==null||!key.issuer().equals(player.getUUID()))return;
        revoked.computeIfAbsent(key.issuer(),k->new HashSet<>()).add(key.id());
        remove(player.getUUID(),key.id());hidden.computeIfAbsent(player.getUUID(),k->new HashSet<>()).add(key);
        // Other players retain their physical stored items and may always recover them.
        setDirty();
    }
    ItemStack current(ItemStack badge){
        if(!AccessBadges.isBadge(badge)||badge.getCount()!=1)return ItemStack.EMPTY;
        Key key=key(badge);if(key==null)return badge;
        if(revoked(key))return ItemStack.EMPTY;
        Ledger entry=find(key);if(entry==null)return badge;
        if(!sameEdition(badge,entry))return ItemStack.EMPTY;
        AccessBadges.write(badge,AccessBadges.read(entry.badge));return badge;
    }
    boolean consume(ServerPlayer holder,ItemStack badge){
        if(current(badge).isEmpty()||AccessBadges.remaining(badge)==0)return false;
        Key key=key(badge);if(key==null)return false;
        Ledger entry=find(key);
        if(entry==null){
            CompoundTag data=AccessBadges.read(badge);data.putLong("Revision",revision(data));AccessBadges.write(badge,data);
            entry=new Ledger(key,revision(data),AccessBadges.normalize(badge.copy()));put(entry);
        }
        // Ledger capacity is independent of catalogue capacity: legacy badges keep working,
        // while hidden records remain hidden and all copies still consume one shared budget.
        autoCatalog(entry);
        int remaining=AccessBadges.remaining(badge);
        if(remaining>0){
            CompoundTag data=AccessBadges.read(badge);data.putInt("Remaining",remaining-1);AccessBadges.write(badge,data);
            CompoundTag canonical=AccessBadges.read(entry.badge);canonical.putInt("Remaining",remaining-1);AccessBadges.write(entry.badge,canonical);
            entry.badge=AccessBadges.normalize(entry.badge);setDirty();
        }
        return true;
    }
    private AccessBadges.Result depositCheck(ServerPlayer player,ItemStack badge){
        if(!AccessBadges.isBadge(badge)||badge.getCount()!=1)return failure("insert_owned");
        Key key=key(badge);if(key==null)return failure("insert_owned");
        if(revoked(key))return failure("revoked");
        Ledger entry=find(key);if(entry!=null&&!sameEdition(badge,entry))return failure("obsolete");
        if(!key.issuer().equals(player.getUUID())&&!AccessBadges.foreignBankAllowed(entry==null?badge:entry.badge))return failure("sharing_disabled");
        Catalog existing=catalog(player.getUUID(),key.id());
        if(existing!=null&&!existing.key.equals(key))return failure("id_conflict");
        if(existing!=null&&!existing.stored.isEmpty())return failure("already_stored");
        if(existing==null&&catalog(player.getUUID()).size()>=MAX_BADGES)return failure("full");
        return AccessBadges.Result.success("");
    }
    public boolean canDeposit(ServerPlayer player,ItemStack badge){return depositCheck(player,badge).ok();}
    public AccessBadges.Result deposit(ServerPlayer player,ItemStack badge){
        AccessBadges.Result allowed=depositCheck(player,badge);if(!allowed.ok())return allowed;
        Key key=key(badge);Ledger canonical=find(key);
        if(canonical==null){
            CompoundTag data=AccessBadges.read(badge);data.putLong("Revision",revision(data));AccessBadges.write(badge,data);
            canonical=new Ledger(key,revision(data),AccessBadges.normalize(badge.copy()));put(canonical);
        }else AccessBadges.write(badge,AccessBadges.read(canonical.badge));
        Catalog entry=catalog(player.getUUID(),key.id());
        if(entry==null){entry=new Catalog(key);Component name=badge.get(DataComponents.CUSTOM_NAME);entry.localName=name==null?"":name.getString();}
        entry.stored=AccessBadges.normalize(badge.copy());put(player.getUUID(),entry);
        var suppressed=hidden.get(player.getUUID());if(suppressed!=null){suppressed.remove(key);if(suppressed.isEmpty())hidden.remove(player.getUUID());}
        badge.shrink(1);return success("deposited");
    }
    /** Physical recovery is unconditional, including disabled sharing and revoked identities. */
    public Transfer withdraw(ServerPlayer player,UUID id){
        Catalog entry=catalog(player.getUUID(),id);if(entry==null)return Transfer.fail("not_found");
        if(entry.stored.isEmpty())return Transfer.fail("not_stored");
        ItemStack badge=render(entry);entry.stored=ItemStack.EMPTY;setDirty();return new Transfer(success("withdrawn"),badge);
    }
    public Transfer reissue(ServerPlayer player,UUID id,ItemStack blank){
        Catalog entry=catalog(player.getUUID(),id);if(entry==null)return Transfer.fail("not_found");
        if(!entry.stored.isEmpty())return Transfer.fail("withdraw_first");
        if(revoked(entry.key))return Transfer.fail("revoked");
        if(!copyAllowed(player.getUUID(),entry))return Transfer.fail("sharing_disabled");
        if(!isUnissuedBlank(blank))return Transfer.fail("insert_blank");
        ItemStack badge=render(entry);if(badge.isEmpty())return Transfer.fail("not_found");
        blank.shrink(1);return new Transfer(success("reissued"),badge);
    }
    public AccessBadges.Result renameArchive(ServerPlayer player,UUID id,String name){
        Catalog entry=catalog(player.getUUID(),id);if(entry==null)return failure("not_found");
        String clean=AccessBadges.cleanName(name);if(clean==null)return failure("invalid_name");
        entry.localName=clean;setDirty();return success("archive_renamed");
    }
    /** Removes only this player's record, returning a stored physical copy without revocation. */
    public Transfer removeArchive(ServerPlayer player,UUID id){
        Catalog entry=catalog(player.getUUID(),id);if(entry==null)return Transfer.fail("not_found");
        ItemStack returned=entry.stored.isEmpty()?ItemStack.EMPTY:render(entry);
        remove(player.getUUID(),id);hidden.computeIfAbsent(player.getUUID(),k->new HashSet<>()).add(entry.key);setDirty();
        return new Transfer(success(returned.isEmpty()?"archive_removed":"archive_removed_returned"),returned);
    }
    public static boolean isUnissuedBlank(ItemStack stack){
        if(!stack.is(fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems.ACCESS_BADGE_BLANK.get())||stack.getCount()!=1)return false;
        CompoundTag data=AccessBadges.read(stack);
        return !data.hasUUID("Issuer")&&!data.hasUUID("BadgeId")&&AccessBadges.codes(stack).isEmpty()
            &&data.getInt("MasterLevel")==0&&AccessBadges.remaining(stack)<0;
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
        tag.putInt("Schema",2);ListTag authority=new ListTag();
        for(var issuer:ledger.values())for(Ledger entry:issuer.values()){
            CompoundTag row=new CompoundTag();row.putUUID("Owner",entry.key.issuer());row.putUUID("Id",entry.key.id());row.putLong("Revision",entry.revision);
            row.put("Badge",entry.badge.save(provider));authority.add(row);
        }tag.put("Ledger",authority);
        ListTag records=new ListTag();for(var viewer:catalogs.entrySet())for(Catalog entry:viewer.getValue().values()){
            CompoundTag row=new CompoundTag();row.putUUID("Player",viewer.getKey());row.putUUID("Issuer",entry.key.issuer());row.putUUID("Id",entry.key.id());
            if(entry.localName!=null)row.putString("LocalName",entry.localName);
            if(!entry.stored.isEmpty())row.put("StoredBadge",entry.stored.save(provider));records.add(row);
        }tag.put("Catalogs",records);
        ListTag masks=new ListTag();for(var viewer:hidden.entrySet())for(Key key:viewer.getValue()){
            CompoundTag row=new CompoundTag();row.putUUID("Player",viewer.getKey());row.putUUID("Issuer",key.issuer());row.putUUID("Id",key.id());masks.add(row);
        }tag.put("Hidden",masks);
        ListTag cleared=new ListTag();for(var issuer:revoked.entrySet())for(UUID id:issuer.getValue()){
            CompoundTag row=new CompoundTag();row.putUUID("Owner",issuer.getKey());row.putUUID("Id",id);cleared.add(row);
        }tag.put("Revoked",cleared);tag.remove("Archives");return tag;
    }
    private static ItemStack parseBadge(HolderLookup.Provider provider,CompoundTag row,String field,Key key){
        ItemStack badge=ItemStack.parseOptional(provider,row.getCompound(field));
        return AccessBadges.isBadge(badge)&&badge.getCount()==1&&key.equals(key(badge))?badge:ItemStack.EMPTY;
    }
    public static BadgeBankData load(CompoundTag tag,HolderLookup.Provider provider){
        BadgeBankData result=new BadgeBankData();
        for(Tag raw:tag.getList("Revoked",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)raw;if(row.hasUUID("Owner")&&row.hasUUID("Id"))result.revoked.computeIfAbsent(row.getUUID("Owner"),k->new HashSet<>()).add(row.getUUID("Id"));
        }
        if(tag.getInt("Schema")<2){
            // V5 used one combined archive/counter per issuer. Preserve its full snapshot,
            // stored item, name, revision and every revocation before splitting the records.
            for(Tag raw:tag.getList("Archives",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)raw;if(!row.hasUUID("Owner")||!row.hasUUID("Id"))continue;
                Key key=new Key(row.getUUID("Owner"),row.getUUID("Id"));if(result.revoked(key)||result.catalog(key.issuer()).size()>=MAX_BADGES)continue;
                ItemStack badge=parseBadge(provider,row,"Badge",key);long rev=Math.max(0,row.getLong("Revision"));
                if(badge.isEmpty()||revision(AccessBadges.read(badge))!=rev)continue;
                result.ledger.computeIfAbsent(key.issuer(),k->new HashMap<>()).put(key.id(),new Ledger(key,rev,AccessBadges.normalize(badge)));
                Catalog entry=new Catalog(key);if(row.getBoolean("Stored"))entry.stored=badge.copy();
                result.catalogs.computeIfAbsent(key.issuer(),k->new LinkedHashMap<>()).put(key.id(),entry);
            }
        }else{
            for(Tag raw:tag.getList("Ledger",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)raw;if(!row.hasUUID("Owner")||!row.hasUUID("Id"))continue;
                Key key=new Key(row.getUUID("Owner"),row.getUUID("Id"));ItemStack badge=parseBadge(provider,row,"Badge",key);long rev=Math.max(0,row.getLong("Revision"));
                if(badge.isEmpty()||revision(AccessBadges.read(badge))!=rev)continue;
                result.ledger.computeIfAbsent(key.issuer(),k->new HashMap<>()).put(key.id(),new Ledger(key,rev,AccessBadges.normalize(badge)));
            }
            for(Tag raw:tag.getList("Catalogs",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)raw;if(!row.hasUUID("Player")||!row.hasUUID("Issuer")||!row.hasUUID("Id"))continue;
                UUID viewer=row.getUUID("Player");Key key=new Key(row.getUUID("Issuer"),row.getUUID("Id"));
                if(result.catalog(viewer).size()>=MAX_BADGES||result.catalog(viewer).containsKey(key.id()))continue;
                Catalog entry=new Catalog(key);entry.stored=parseBadge(provider,row,"StoredBadge",key);
                if(result.find(key)==null){
                    if(entry.stored.isEmpty())continue;
                    result.ledger.computeIfAbsent(key.issuer(),k->new HashMap<>()).put(key.id(),new Ledger(key,revision(AccessBadges.read(entry.stored)),AccessBadges.normalize(entry.stored.copy())));
                }
                if(row.contains("LocalName",Tag.TAG_STRING))entry.localName=AccessBadges.cleanName(row.getString("LocalName"));
                result.catalogs.computeIfAbsent(viewer,k->new LinkedHashMap<>()).put(key.id(),entry);
            }
            for(Tag raw:tag.getList("Hidden",Tag.TAG_COMPOUND)){
                CompoundTag row=(CompoundTag)raw;if(row.hasUUID("Player")&&row.hasUUID("Issuer")&&row.hasUUID("Id"))result.hidden.computeIfAbsent(row.getUUID("Player"),k->new HashSet<>()).add(new Key(row.getUUID("Issuer"),row.getUUID("Id")));
            }
        }
        return result;
    }
}
