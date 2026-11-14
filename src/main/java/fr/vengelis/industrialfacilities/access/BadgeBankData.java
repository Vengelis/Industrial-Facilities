package fr.vengelis.industrialfacilities.access;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Le registre serveur porte les droits et le compteur partagés entre toutes les copies d'un badge.
 * Chaque joueur dispose aussi de son propre catalogue pour ranger, nommer et reproduire ses badges.
 */
public final class BadgeBankData extends SavedData {
    public static final int MAX_BADGES = 256;
    private static final int SCHEMA_VERSION = 2;
    private static final String MESSAGE_PREFIX = "gui.industrial_facilities.badge_bank.";

    public record EntryView(UUID id, ItemStack badge, boolean stored, UUID issuer, boolean foreign, boolean copyAllowed) {
        public EntryView {
            badge = badge.copy();
        }

        @Override
        public ItemStack badge() {
            return badge.copy();
        }
    }

    public record Transfer(AccessBadges.Result result, ItemStack stack) {
        public boolean ok() {
            return result.ok();
        }

        private static Transfer fail(String key) {
            return new Transfer(failure(key), ItemStack.EMPTY);
        }
    }

    private record Key(UUID issuer, UUID id) {}

    private static final class Ledger {
        final Key key;
        final long revision;
        ItemStack badge;

        Ledger(Key key, long revision, ItemStack badge) {
            this.key = key;
            this.revision = revision;
            this.badge = badge.copy();
        }
    }

    private static final class Catalog {
        final Key key;
        // null reprend le nom de l'émetteur. Une chaîne vide retire le nom.
        String localName;
        ItemStack stored = ItemStack.EMPTY;

        Catalog(Key key) {
            this.key = key;
        }
    }

    private final Map<UUID, Map<UUID, Ledger>> ledger = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, Catalog>> catalogs = new HashMap<>();
    // Les entrées masquées restent valides. Les identités révoquées sont inutilisables.
    private final Map<UUID, Set<Key>> hidden = new HashMap<>();
    private final Map<UUID, Set<UUID>> revoked = new HashMap<>();

    public static BadgeBankData get(MinecraftServer server) {
        // Un stockage dans l'Overworld permet aux lecteurs de toutes les dimensions de partager le compteur.
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(BadgeBankData::new, BadgeBankData::load), "industrial_badge_bank");
    }

    private static AccessBadges.Result failure(String key) {
        return AccessBadges.Result.fail(MESSAGE_PREFIX + key);
    }

    private static AccessBadges.Result success(String key) {
        return AccessBadges.Result.success(MESSAGE_PREFIX + key);
    }

    private Map<UUID, Catalog> catalog(UUID player) {
        return catalogs.getOrDefault(player, new LinkedHashMap<>());
    }

    private Catalog catalog(UUID player, UUID id) {
        return id == null ? null : catalog(player).get(id);
    }

    private Ledger find(Key key) {
        return key == null ? null : ledger.getOrDefault(key.issuer(), Map.of()).get(key.id());
    }

    private boolean revoked(Key key) {
        return revoked.getOrDefault(key.issuer(), Set.of()).contains(key.id());
    }

    private boolean hidden(UUID player, Key key) {
        return hidden.getOrDefault(player, Set.of()).contains(key);
    }

    private static long revision(CompoundTag data) {
        return Math.max(0, data.getLong("Revision"));
    }

    private static Key key(ItemStack badge) {
        CompoundTag data = AccessBadges.read(badge);
        if (!data.hasUUID("Issuer") || !data.hasUUID("BadgeId")) {
            return null;
        }
        return new Key(data.getUUID("Issuer"), data.getUUID("BadgeId"));
    }

    private static boolean sameEdition(ItemStack badge, Ledger entry) {
        return revision(AccessBadges.read(badge)) == entry.revision;
    }

    private void put(Ledger entry) {
        ledger.computeIfAbsent(entry.key.issuer(), issuer -> new HashMap<>()).put(entry.key.id(), entry);
        setDirty();
    }

    private void put(UUID player, Catalog entry) {
        catalogs.computeIfAbsent(player, viewer -> new LinkedHashMap<>()).put(entry.key.id(), entry);
        setDirty();
    }

    private void remove(UUID player, UUID id) {
        var records = catalogs.get(player);
        if (records == null) {
            return;
        }
        records.remove(id);
        if (records.isEmpty()) {
            catalogs.remove(player);
        }
    }

    private void autoCatalog(Ledger entry) {
        UUID issuer = entry.key.issuer();
        var records = catalog(issuer);
        if (revoked(entry.key) || hidden(issuer, entry.key)
            || records.containsKey(entry.key.id()) || records.size() >= MAX_BADGES) {
            return;
        }
        put(issuer, new Catalog(entry.key));
    }

    private boolean copyAllowed(UUID player, Catalog entry) {
        Ledger canonical = find(entry.key);
        return canonical != null && !revoked(entry.key)
            && (entry.key.issuer().equals(player) || AccessBadges.foreignBankAllowed(canonical.badge));
    }

    /** Prépare une copie destinée à l'affichage ou à la restitution, avec l'alias de ce catalogue. */
    private ItemStack render(Catalog entry) {
        Ledger canonical = find(entry.key);
        ItemStack badge = ItemStack.EMPTY;
        if (!entry.stored.isEmpty()) {
            badge = entry.stored.copy();
        } else if (canonical != null) {
            badge = canonical.badge.copy();
        }
        if (badge.isEmpty()) {
            return badge;
        }
        if (canonical != null && !revoked(entry.key) && sameEdition(badge, canonical)) {
            AccessBadges.write(badge, AccessBadges.read(canonical.badge));
        }
        if (entry.localName != null) {
            if (entry.localName.isEmpty()) {
                badge.remove(DataComponents.CUSTOM_NAME);
            } else {
                badge.set(DataComponents.CUSTOM_NAME, Component.literal(entry.localName));
            }
        }
        return AccessBadges.normalize(badge);
    }

    public List<EntryView> entries(ServerPlayer player) {
        UUID viewer = player.getUUID();
        return catalog(viewer).values().stream()
            .sorted(Comparator.comparing(entry -> entry.key.id().toString()))
            .map(entry -> new EntryView(entry.key.id(), render(entry), !entry.stored.isEmpty(),
                entry.key.issuer(), !entry.key.issuer().equals(viewer), copyAllowed(viewer, entry)))
            .toList();
    }

    public ItemStack preview(ServerPlayer player, UUID id) {
        Catalog entry = catalog(player.getUUID(), id);
        return entry == null ? ItemStack.EMPTY : render(entry);
    }

    /** L'émetteur doit retirer son badge stocké avant de modifier les droits. */
    AccessBadges.Result editable(ServerPlayer player, ItemStack badge) {
        UUID playerId = player.getUUID();
        CompoundTag data = AccessBadges.read(badge);
        if (data.hasUUID("Issuer") && !data.getUUID("Issuer").equals(playerId)) {
            return failure("not_issuer");
        }
        Key key = key(badge);
        if (key != null) {
            if (revoked(key)) {
                return failure("revoked");
            }
            Ledger entry = find(key);
            if (entry != null && !sameEdition(badge, entry)) {
                return failure("obsolete");
            }
            Catalog own = catalog(playerId, key.id());
            if (own != null && own.key.equals(key) && !own.stored.isEmpty()) {
                return failure("withdraw_first");
            }
        }
        return AccessBadges.Result.success("");
    }

    /** Recharge le compteur partagé avant une édition pour préserver les utilisations déjà dépensées. */
    AccessBadges.Result prepareEdit(ServerPlayer player, ItemStack badge) {
        AccessBadges.Result allowed = editable(player, badge);
        if (!allowed.ok()) {
            return allowed;
        }

        UUID playerId = player.getUUID();
        Key key = key(badge);
        Ledger entry = find(key);
        Catalog own = key == null ? null : catalog(playerId, key.id());
        if (own != null && !own.key.equals(key)) {
            return failure("id_conflict");
        }
        if (own == null && (key == null || !hidden(playerId, key)) && catalog(playerId).size() >= MAX_BADGES) {
            return failure("full");
        }
        if (entry != null) {
            AccessBadges.write(badge, AccessBadges.read(entry.badge));
        }
        return AccessBadges.Result.success("");
    }

    void archiveEdit(ServerPlayer player, ItemStack badge) {
        Key key = key(badge);
        if (!AccessBadges.isBadge(badge) || badge.getCount() != 1
            || key == null || !key.issuer().equals(player.getUUID()) || revoked(key)) {
            return;
        }
        Ledger existing = find(key);
        CompoundTag data = AccessBadges.read(badge);
        long edition = existing == null ? revision(data) : existing.revision;
        data.putLong("Revision", edition);
        AccessBadges.write(badge, data);

        Ledger entry = new Ledger(key, edition, AccessBadges.normalize(badge.copy()));
        put(entry);
        autoCatalog(entry);
    }

    /** Un nom saisi à l'encodeur remplace l'alias de l'émetteur dans son catalogue. */
    void encoderNameChanged(ServerPlayer player, ItemStack badge) {
        Key key = key(badge);
        Catalog entry = key == null ? null : catalog(player.getUUID(), key.id());
        if (entry != null && entry.key.equals(key)) {
            entry.localName = null;
            setDirty();
        }
    }

    void clearIdentity(ServerPlayer player, ItemStack badge) {
        UUID playerId = player.getUUID();
        Key key = key(badge);
        if (key == null || !key.issuer().equals(playerId)) {
            return;
        }
        revoked.computeIfAbsent(key.issuer(), issuer -> new HashSet<>()).add(key.id());
        remove(playerId, key.id());
        hidden.computeIfAbsent(playerId, viewer -> new HashSet<>()).add(key);
        // Les autres joueurs gardent la possibilité de retirer leur badge physique.
        setDirty();
    }

    ItemStack current(ItemStack badge) {
        if (!AccessBadges.isBadge(badge) || badge.getCount() != 1) {
            return ItemStack.EMPTY;
        }
        Key key = key(badge);
        if (key == null) {
            return badge;
        }
        if (revoked(key)) {
            return ItemStack.EMPTY;
        }
        Ledger entry = find(key);
        if (entry == null) {
            return badge;
        }
        if (!sameEdition(badge, entry)) {
            return ItemStack.EMPTY;
        }
        AccessBadges.write(badge, AccessBadges.read(entry.badge));
        return badge;
    }

    boolean consume(ServerPlayer holder, ItemStack badge) {
        if (current(badge).isEmpty() || AccessBadges.remaining(badge) == 0) {
            return false;
        }
        Key key = key(badge);
        if (key == null) {
            return false;
        }
        Ledger entry = find(key);
        if (entry == null) {
            entry = registerLegacyBadge(key, badge);
        }

        // Le registre accepte les anciens badges même lorsque le catalogue est plein.
        autoCatalog(entry);
        int remaining = AccessBadges.remaining(badge);
        if (remaining > 0) {
            // La copie présentée et le registre reflètent désormais le même solde.
            CompoundTag data = AccessBadges.read(badge);
            data.putInt("Remaining", remaining - 1);
            AccessBadges.write(badge, data);

            CompoundTag canonical = AccessBadges.read(entry.badge);
            canonical.putInt("Remaining", remaining - 1);
            AccessBadges.write(entry.badge, canonical);
            entry.badge = AccessBadges.normalize(entry.badge);
            setDirty();
        }
        return true;
    }

    private Ledger registerLegacyBadge(Key key, ItemStack badge) {
        CompoundTag data = AccessBadges.read(badge);
        long edition = revision(data);
        data.putLong("Revision", edition);
        AccessBadges.write(badge, data);
        Ledger entry = new Ledger(key, edition, AccessBadges.normalize(badge.copy()));
        put(entry);
        return entry;
    }

    private AccessBadges.Result depositCheck(ServerPlayer player, ItemStack badge) {
        if (!AccessBadges.isBadge(badge) || badge.getCount() != 1) {
            return failure("insert_owned");
        }
        Key key = key(badge);
        if (key == null) {
            return failure("insert_owned");
        }
        if (revoked(key)) {
            return failure("revoked");
        }
        Ledger entry = find(key);
        if (entry != null && !sameEdition(badge, entry)) {
            return failure("obsolete");
        }

        UUID playerId = player.getUUID();
        ItemStack currentBadge = entry == null ? badge : entry.badge;
        if (!key.issuer().equals(playerId) && !AccessBadges.foreignBankAllowed(currentBadge)) {
            return failure("sharing_disabled");
        }
        Catalog existing = catalog(playerId, key.id());
        if (existing != null && !existing.key.equals(key)) {
            return failure("id_conflict");
        }
        if (existing != null && !existing.stored.isEmpty()) {
            return failure("already_stored");
        }
        if (existing == null && catalog(playerId).size() >= MAX_BADGES) {
            return failure("full");
        }
        return AccessBadges.Result.success("");
    }

    public boolean canDeposit(ServerPlayer player, ItemStack badge) {
        return depositCheck(player, badge).ok();
    }

    public AccessBadges.Result deposit(ServerPlayer player, ItemStack badge) {
        AccessBadges.Result allowed = depositCheck(player, badge);
        if (!allowed.ok()) {
            return allowed;
        }
        Key key = key(badge);
        Ledger canonical = find(key);
        if (canonical == null) {
            registerLegacyBadge(key, badge);
        } else {
            AccessBadges.write(badge, AccessBadges.read(canonical.badge));
        }

        UUID playerId = player.getUUID();
        Catalog entry = catalog(playerId, key.id());
        if (entry == null) {
            entry = new Catalog(key);
            Component name = badge.get(DataComponents.CUSTOM_NAME);
            entry.localName = name == null ? "" : name.getString();
        }
        entry.stored = AccessBadges.normalize(badge.copy());
        put(playerId, entry);

        // Déposer une copie réaffiche le badge s'il avait été masqué.
        var suppressed = hidden.get(playerId);
        if (suppressed != null) {
            suppressed.remove(key);
            if (suppressed.isEmpty()) {
                hidden.remove(playerId);
            }
        }

        badge.shrink(1);
        return success("deposited");
    }

    /** Le badge physique reste récupérable après révocation ou désactivation du partage. */
    public Transfer withdraw(ServerPlayer player, UUID id) {
        Catalog entry = catalog(player.getUUID(), id);
        if (entry == null) {
            return Transfer.fail("not_found");
        }
        if (entry.stored.isEmpty()) {
            return Transfer.fail("not_stored");
        }
        ItemStack badge = render(entry);
        entry.stored = ItemStack.EMPTY;
        setDirty();
        return new Transfer(success("withdrawn"), badge);
    }

    public Transfer reissue(ServerPlayer player, UUID id, ItemStack blank) {
        Catalog entry = catalog(player.getUUID(), id);
        if (entry == null) {
            return Transfer.fail("not_found");
        }
        if (!entry.stored.isEmpty()) {
            return Transfer.fail("withdraw_first");
        }
        if (revoked(entry.key)) {
            return Transfer.fail("revoked");
        }
        if (!copyAllowed(player.getUUID(), entry)) {
            return Transfer.fail("sharing_disabled");
        }
        if (!isUnissuedBlank(blank)) {
            return Transfer.fail("insert_blank");
        }
        ItemStack badge = render(entry);
        if (badge.isEmpty()) {
            return Transfer.fail("not_found");
        }
        blank.shrink(1);
        return new Transfer(success("reissued"), badge);
    }

    public AccessBadges.Result renameArchive(ServerPlayer player, UUID id, String name) {
        Catalog entry = catalog(player.getUUID(), id);
        if (entry == null) {
            return failure("not_found");
        }
        String cleanName = AccessBadges.cleanName(name);
        if (cleanName == null) {
            return failure("invalid_name");
        }
        entry.localName = cleanName;
        setDirty();
        return success("archive_renamed");
    }

    /** Restitue l'éventuel objet stocké et masque l'archive dans le catalogue de ce joueur. */
    public Transfer removeArchive(ServerPlayer player, UUID id) {
        UUID playerId = player.getUUID();
        Catalog entry = catalog(playerId, id);
        if (entry == null) {
            return Transfer.fail("not_found");
        }
        ItemStack returned = entry.stored.isEmpty() ? ItemStack.EMPTY : render(entry);
        remove(playerId, id);
        hidden.computeIfAbsent(playerId, viewer -> new HashSet<>()).add(entry.key);
        setDirty();
        return new Transfer(success(returned.isEmpty() ? "archive_removed" : "archive_removed_returned"), returned);
    }

    public static boolean isUnissuedBlank(ItemStack stack) {
        if (!stack.is(IndustrialFacilitiesModItems.ACCESS_BADGE_BLANK.get()) || stack.getCount() != 1) {
            return false;
        }
        CompoundTag data = AccessBadges.read(stack);
        return !data.hasUUID("Issuer") && !data.hasUUID("BadgeId") && AccessBadges.codes(stack).isEmpty()
            && data.getInt("MasterLevel") == 0 && AccessBadges.remaining(stack) < 0;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Schema", SCHEMA_VERSION);
        tag.put("Ledger", saveLedger(provider));
        tag.put("Catalogs", saveCatalogs(provider));
        tag.put("Hidden", saveHiddenEntries());
        tag.put("Revoked", saveRevocations());
        tag.remove("Archives");
        return tag;
    }

    private ListTag saveLedger(HolderLookup.Provider provider) {
        ListTag rows = new ListTag();
        for (var issuerEntries : ledger.values()) {
            for (Ledger entry : issuerEntries.values()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Owner", entry.key.issuer());
                row.putUUID("Id", entry.key.id());
                row.putLong("Revision", entry.revision);
                row.put("Badge", entry.badge.save(provider));
                rows.add(row);
            }
        }
        return rows;
    }

    private ListTag saveCatalogs(HolderLookup.Provider provider) {
        ListTag rows = new ListTag();
        for (var viewer : catalogs.entrySet()) {
            for (Catalog entry : viewer.getValue().values()) {
                CompoundTag row = catalogRow(viewer.getKey(), entry.key);
                if (entry.localName != null) {
                    row.putString("LocalName", entry.localName);
                }
                if (!entry.stored.isEmpty()) {
                    row.put("StoredBadge", entry.stored.save(provider));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private ListTag saveHiddenEntries() {
        ListTag rows = new ListTag();
        for (var viewer : hidden.entrySet()) {
            for (Key key : viewer.getValue()) {
                rows.add(catalogRow(viewer.getKey(), key));
            }
        }
        return rows;
    }

    private static CompoundTag catalogRow(UUID viewer, Key key) {
        CompoundTag row = new CompoundTag();
        row.putUUID("Player", viewer);
        row.putUUID("Issuer", key.issuer());
        row.putUUID("Id", key.id());
        return row;
    }

    private ListTag saveRevocations() {
        ListTag rows = new ListTag();
        for (var issuer : revoked.entrySet()) {
            for (UUID id : issuer.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("Owner", issuer.getKey());
                row.putUUID("Id", id);
                rows.add(row);
            }
        }
        return rows;
    }

    private static ItemStack parseBadge(HolderLookup.Provider provider, CompoundTag row, String field, Key key) {
        ItemStack badge = ItemStack.parseOptional(provider, row.getCompound(field));
        return AccessBadges.isBadge(badge) && badge.getCount() == 1 && key.equals(key(badge))
            ? badge : ItemStack.EMPTY;
    }

    public static BadgeBankData load(CompoundTag tag, HolderLookup.Provider provider) {
        BadgeBankData result = new BadgeBankData();
        result.loadRevocations(tag.getList("Revoked", Tag.TAG_COMPOUND));
        if (tag.getInt("Schema") < SCHEMA_VERSION) {
            result.loadLegacyArchives(tag.getList("Archives", Tag.TAG_COMPOUND), provider);
        } else {
            result.loadLedger(tag.getList("Ledger", Tag.TAG_COMPOUND), provider);
            result.loadCatalogs(tag.getList("Catalogs", Tag.TAG_COMPOUND), provider);
            result.loadHiddenEntries(tag.getList("Hidden", Tag.TAG_COMPOUND));
        }
        return result;
    }

    private void loadRevocations(ListTag rows) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (row.hasUUID("Owner") && row.hasUUID("Id")) {
                revoked.computeIfAbsent(row.getUUID("Owner"), issuer -> new HashSet<>()).add(row.getUUID("Id"));
            }
        }
    }

    private void loadLegacyArchives(ListTag rows, HolderLookup.Provider provider) {
        // L'ancien format stockait les droits et l'objet déposé dans la même entrée.
        // Lors de la migration, les révocations sont déjà chargées pour filtrer ces archives.
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.hasUUID("Owner") || !row.hasUUID("Id")) {
                continue;
            }
            Key key = new Key(row.getUUID("Owner"), row.getUUID("Id"));
            if (revoked(key) || catalog(key.issuer()).size() >= MAX_BADGES) {
                continue;
            }

            ItemStack badge = parseBadge(provider, row, "Badge", key);
            long edition = revision(row);
            if (badge.isEmpty() || revision(AccessBadges.read(badge)) != edition) {
                continue;
            }
            ledger.computeIfAbsent(key.issuer(), issuer -> new HashMap<>())
                .put(key.id(), new Ledger(key, edition, AccessBadges.normalize(badge)));

            Catalog entry = new Catalog(key);
            if (row.getBoolean("Stored")) {
                entry.stored = badge.copy();
            }
            catalogs.computeIfAbsent(key.issuer(), viewer -> new LinkedHashMap<>()).put(key.id(), entry);
        }
    }

    private void loadLedger(ListTag rows, HolderLookup.Provider provider) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.hasUUID("Owner") || !row.hasUUID("Id")) {
                continue;
            }
            Key key = new Key(row.getUUID("Owner"), row.getUUID("Id"));
            ItemStack badge = parseBadge(provider, row, "Badge", key);
            long edition = revision(row);
            if (badge.isEmpty() || revision(AccessBadges.read(badge)) != edition) {
                continue;
            }
            ledger.computeIfAbsent(key.issuer(), issuer -> new HashMap<>())
                .put(key.id(), new Ledger(key, edition, AccessBadges.normalize(badge)));
        }
    }

    private void loadCatalogs(ListTag rows, HolderLookup.Provider provider) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.hasUUID("Player") || !row.hasUUID("Issuer") || !row.hasUUID("Id")) {
                continue;
            }
            UUID viewer = row.getUUID("Player");
            Key key = new Key(row.getUUID("Issuer"), row.getUUID("Id"));
            var records = catalog(viewer);
            if (records.size() >= MAX_BADGES || records.containsKey(key.id())) {
                continue;
            }

            Catalog entry = new Catalog(key);
            entry.stored = parseBadge(provider, row, "StoredBadge", key);
            if (find(key) == null) {
                // Un exemplaire physique peut restaurer une entrée manquante du registre.
                if (entry.stored.isEmpty()) {
                    continue;
                }
                long edition = revision(AccessBadges.read(entry.stored));
                ledger.computeIfAbsent(key.issuer(), issuer -> new HashMap<>())
                    .put(key.id(), new Ledger(key, edition, AccessBadges.normalize(entry.stored.copy())));
            }
            if (row.contains("LocalName", Tag.TAG_STRING)) {
                entry.localName = AccessBadges.cleanName(row.getString("LocalName"));
            }
            catalogs.computeIfAbsent(viewer, player -> new LinkedHashMap<>()).put(key.id(), entry);
        }
    }

    private void loadHiddenEntries(ListTag rows) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (row.hasUUID("Player") && row.hasUUID("Issuer") && row.hasUUID("Id")) {
                Key key = new Key(row.getUUID("Issuer"), row.getUUID("Id"));
                hidden.computeIfAbsent(row.getUUID("Player"), viewer -> new HashSet<>()).add(key);
            }
        }
    }
}
