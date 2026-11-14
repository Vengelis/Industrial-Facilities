package fr.vengelis.industrialfacilities.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Registre des lecteurs et des portes du serveur, consultable sans charger leurs chunks. */
public final class AccessRegistry extends SavedData {
    public static final int MAX_GROUP_READERS = 64;
    public static final int MAX_DOORS = 64;

    public record DoorLink(String dimension, BlockPos position, UUID instance) {}

    public enum GroupResult {
        LINKED, ALREADY_LINKED, INCOMPATIBLE, TOO_MANY_READERS, TOO_FAR, DIFFERENT_DOORS
    }

    private static final class ReaderGroup {
        final Set<Entry> members = new LinkedHashSet<>();
        final List<DoorLink> doors = new ArrayList<>();
        final List<DoorLink> view = Collections.unmodifiableList(doors);
    }

    public static final class Entry {
        public final UUID id;
        public final UUID owner;
        public final String dimension;
        public final BlockPos position;
        public int level;
        public boolean keypass;
        private ReaderGroup group = new ReaderGroup();

        Entry(UUID id, UUID owner, String dimension, BlockPos position, int level) {
            this.id = id;
            this.owner = owner;
            this.dimension = dimension;
            this.position = position.immutable();
            this.level = level;
            group.members.add(this);
        }

        public List<DoorLink> doors() {
            return group.view;
        }

        public int readerCount() {
            return group.members.size();
        }
    }

    private record DoorSite(String dimension, BlockPos position) {}

    private final Map<UUID, Entry> terminals = new HashMap<>();
    // Index inverse reconstruit après chaque changement de liaisons.
    private final Map<UUID, Set<UUID>> doorReaders = new HashMap<>();
    // Une valeur null indique une démolition. Une position absente correspond à une porte ancienne.
    private final Map<DoorSite, UUID> doorGenerations = new HashMap<>();

    public static AccessRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(AccessRegistry::new, AccessRegistry::load), "industrial_access");
    }

    public Entry find(UUID id) {
        return terminals.get(id);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(terminals.values());
    }

    public Collection<Entry> readers(UUID door) {
        return doorReaders.getOrDefault(door, Set.of()).stream()
            .map(terminals::get)
            .filter(Objects::nonNull)
            .toList();
    }

    public void put(AccessTerminalEntity terminal) {
        if (terminal.terminalId == null || terminal.creator == null) {
            return;
        }
        Entry existing = terminals.get(terminal.terminalId);
        if (existing == null) {
            existing = new Entry(terminal.terminalId, terminal.creator,
                terminal.getLevel().dimension().location().toString(), terminal.getBlockPos(), terminal.accessLevel);
            terminals.put(terminal.terminalId, existing);
        }
        if (existing.owner.equals(terminal.creator)) {
            existing.level = terminal.accessLevel;
            existing.keypass = terminal instanceof KeypassTerminalEntity;
        }
        setDirty();
    }

    public void remove(UUID id) {
        Entry reader = terminals.remove(id);
        if (reader != null) {
            reader.group.members.remove(reader);
            reindex();
            setDirty();
        }
    }

    /** Un lecteur quitte le groupe avec une liste vide. Les autres lecteurs gardent leurs portes. */
    public void clearLinks(UUID id) {
        Entry reader = terminals.get(id);
        if (reader != null) {
            reader.group.members.remove(reader);
            reader.group = new ReaderGroup();
            reader.group.members.add(reader);
            reindex();
            setDirty();
        }
    }

    public boolean allReadersInRange(Entry reader, DoorLink door) {
        return reader.group.members.stream().allMatch(member -> inRange(member, door));
    }

    private static boolean inRange(Entry reader, DoorLink door) {
        return reader.dimension.equals(door.dimension()) && TerminalLinks.inRange(reader.position, door.position());
    }

    private static boolean nearby(Set<Entry> readers) {
        for (Entry first : readers) {
            for (Entry second : readers) {
                if (!TerminalLinks.inRange(first.position, second.position)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void assignGroup(Set<Entry> members, List<DoorLink> doors) {
        ReaderGroup group = new ReaderGroup();
        group.members.addAll(members);
        group.doors.addAll(doors);
        for (Entry reader : members) {
            reader.group = group;
        }
    }

    public GroupResult groupReaders(UUID first, UUID second) {
        Entry firstReader = terminals.get(first);
        Entry secondReader = terminals.get(second);
        if (firstReader == null || secondReader == null
            || !firstReader.owner.equals(secondReader.owner) || !firstReader.dimension.equals(secondReader.dimension)) {
            return GroupResult.INCOMPATIBLE;
        }
        if (firstReader.group == secondReader.group) {
            return GroupResult.ALREADY_LINKED;
        }

        Set<Entry> members = new LinkedHashSet<>(firstReader.group.members);
        members.addAll(secondReader.group.members);
        if (members.size() > MAX_GROUP_READERS) {
            return GroupResult.TOO_MANY_READERS;
        }

        List<DoorLink> firstDoors = firstReader.doors();
        List<DoorLink> secondDoors = secondReader.doors();
        // Un groupe sans porte hérite de la liste de l'autre. Deux listes existantes doivent coïncider.
        if (!firstDoors.isEmpty() && !secondDoors.isEmpty()
            && !new HashSet<>(firstDoors).equals(new HashSet<>(secondDoors))) {
            return GroupResult.DIFFERENT_DOORS;
        }
        List<DoorLink> sharedDoors = firstDoors.isEmpty() ? secondDoors : firstDoors;
        if (!nearby(members) || members.stream().anyMatch(reader -> !allDoorsInRange(reader, sharedDoors))) {
            return GroupResult.TOO_FAR;
        }

        assignGroup(members, sharedDoors);
        reindex();
        setDirty();
        return GroupResult.LINKED;
    }

    private static boolean allDoorsInRange(Entry reader, List<DoorLink> doors) {
        return doors.stream().allMatch(door -> inRange(reader, door));
    }

    public boolean link(UUID reader, DoorLink link) {
        Entry entry = terminals.get(reader);
        if (entry == null || entry.doors().contains(link) || entry.doors().size() >= MAX_DOORS) {
            return false;
        }
        entry.group.doors.add(link);
        reindex();
        setDirty();
        return true;
    }

    public boolean obsoleteDoorLink(String dimension, BlockPos pos, UUID instance) {
        DoorSite site = new DoorSite(dimension, pos);
        return doorGenerations.containsKey(site) && !Objects.equals(doorGenerations.get(site), instance);
    }

    public void registerDoor(String dimension, BlockPos pos, UUID instance) {
        doorGenerations.put(new DoorSite(dimension, pos.immutable()), instance);
        removeDoorLinks(door -> door.dimension().equals(dimension) && door.position().equals(pos)
            && !door.instance().equals(instance));
        setDirty();
    }

    public Set<Entry> invalidateDoor(String dimension, BlockPos pos, UUID instance) {
        doorGenerations.put(new DoorSite(dimension, pos.immutable()), null);
        Set<Entry> affected = removeDoorLinks(door -> door.instance().equals(instance)
            || (door.dimension().equals(dimension) && door.position().equals(pos)));
        setDirty();
        return affected;
    }

    private Set<Entry> removeDoorLinks(Predicate<DoorLink> remove) {
        Set<Entry> affected = new HashSet<>();
        for (ReaderGroup group : groups()) {
            if (group.doors.removeIf(remove)) {
                affected.addAll(group.members);
            }
        }
        if (!affected.isEmpty()) {
            reindex();
        }
        return affected;
    }

    private Set<ReaderGroup> groups() {
        Set<ReaderGroup> result = new HashSet<>();
        for (Entry reader : terminals.values()) {
            result.add(reader.group);
        }
        return result;
    }

    private void reindex() {
        doorReaders.clear();
        for (Entry reader : terminals.values()) {
            for (DoorLink door : reader.doors()) {
                doorReaders.computeIfAbsent(door.instance(), id -> new HashSet<>()).add(reader.id);
            }
        }
    }

    private static ListTag writeDoors(List<DoorLink> doors) {
        ListTag rows = new ListTag();
        for (DoorLink door : doors) {
            CompoundTag row = new CompoundTag();
            row.putString("Dimension", door.dimension());
            row.putLong("Position", door.position().asLong());
            row.putUUID("Instance", door.instance());
            rows.add(row);
        }
        return rows;
    }

    private static List<DoorLink> readDoors(CompoundTag tag) {
        Set<DoorLink> doors = new LinkedHashSet<>();
        for (Tag raw : tag.getList("Doors", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            if (doors.size() >= MAX_DOORS) {
                break;
            }
            if (row.hasUUID("Instance")) {
                doors.add(new DoorLink(row.getString("Dimension"), BlockPos.of(row.getLong("Position")),
                    row.getUUID("Instance")));
            }
        }
        return List.copyOf(doors);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("Terminals", saveTerminals());
        tag.put("ReaderGroups", saveReaderGroups());
        tag.put("DoorGenerations", saveDoorGenerations());
        return tag;
    }

    private ListTag saveTerminals() {
        ListTag rows = new ListTag();
        for (Entry reader : terminals.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", reader.id);
            row.putUUID("Owner", reader.owner);
            row.putString("Dimension", reader.dimension);
            row.putLong("Position", reader.position.asLong());
            row.putInt("Level", reader.level);
            if (reader.keypass) {
                row.putBoolean("Keypass", true);
            }
            // Garde le format historique des lecteurs autonomes. Un groupe sauvegarde ses portes une seule fois.
            if (reader.readerCount() == 1) {
                row.put("Doors", writeDoors(reader.doors()));
            }
            rows.add(row);
        }
        return rows;
    }

    private ListTag saveReaderGroups() {
        ListTag rows = new ListTag();
        for (ReaderGroup group : groups()) {
            if (group.members.size() <= 1) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            ListTag members = new ListTag();
            for (Entry reader : group.members) {
                CompoundTag member = new CompoundTag();
                member.putUUID("Id", reader.id);
                members.add(member);
            }
            row.put("Readers", members);
            row.put("Doors", writeDoors(group.doors));
            rows.add(row);
        }
        return rows;
    }

    private ListTag saveDoorGenerations() {
        ListTag rows = new ListTag();
        doorGenerations.forEach((site, instance) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Dimension", site.dimension());
            row.putLong("Position", site.position().asLong());
            if (instance != null) {
                row.putUUID("Instance", instance);
            }
            rows.add(row);
        });
        return rows;
    }

    public static AccessRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        AccessRegistry data = new AccessRegistry();
        data.loadTerminals(tag.getList("Terminals", Tag.TAG_COMPOUND));
        data.loadReaderGroups(tag.getList("ReaderGroups", Tag.TAG_COMPOUND));
        data.loadDoorGenerations(tag.getList("DoorGenerations", Tag.TAG_COMPOUND));
        data.reindex();
        return data;
    }

    private void loadTerminals(ListTag rows) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (!row.hasUUID("Id") || !row.hasUUID("Owner")) {
                continue;
            }
            Entry reader = new Entry(row.getUUID("Id"), row.getUUID("Owner"), row.getString("Dimension"),
                BlockPos.of(row.getLong("Position")), Math.clamp(row.getInt("Level"), 1, 5));
            reader.keypass = row.getBoolean("Keypass");
            reader.group.doors.addAll(readDoors(row));
            terminals.put(reader.id, reader);
        }
    }

    private void loadReaderGroups(ListTag groups) {
        for (Tag raw : groups) {
            CompoundTag group = (CompoundTag) raw;
            ListTag readerRows = group.getList("Readers", Tag.TAG_COMPOUND);
            if (readerRows.size() < 2 || readerRows.size() > MAX_GROUP_READERS) {
                continue;
            }

            // Un groupe sauvegardé doit retrouver chacun de ses lecteurs sans écraser de liaisons individuelles.
            Set<Entry> members = new LinkedHashSet<>();
            for (Tag tag : readerRows) {
                CompoundTag row = (CompoundTag) tag;
                Entry reader = row.hasUUID("Id") ? find(row.getUUID("Id")) : null;
                if (reader != null && reader.readerCount() == 1 && reader.doors().isEmpty()) {
                    members.add(reader);
                }
            }
            if (members.size() != readerRows.size() || !nearby(members)) {
                continue;
            }

            Entry first = members.iterator().next();
            List<DoorLink> doors = readDoors(group);
            if (members.stream().anyMatch(reader -> !reader.owner.equals(first.owner)
                || !reader.dimension.equals(first.dimension) || !allDoorsInRange(reader, doors))) {
                continue;
            }
            assignGroup(members, doors);
        }
    }

    private void loadDoorGenerations(ListTag rows) {
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            DoorSite site = new DoorSite(row.getString("Dimension"), BlockPos.of(row.getLong("Position")));
            UUID instance = row.hasUUID("Instance") ? row.getUUID("Instance") : null;
            doorGenerations.put(site, instance);
        }
    }
}
