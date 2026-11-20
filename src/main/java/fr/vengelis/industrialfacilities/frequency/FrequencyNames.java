package fr.vengelis.industrialfacilities.frequency;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class FrequencyNames extends SavedData {
    static final int MAX_LENGTH = 32;

    record Entry(FrequencyChip frequency, String name) {}
    private record Label(String name, UUID editor) {}

    enum Result { SAVED, DENIED, INVALID }
    private static final Comparator<FrequencyChip> FREQUENCY_ORDER = Comparator.comparingInt(FrequencyChip::number)
        .thenComparing(chip -> chip.issuer() != null);
    private final Map<FrequencyChip, Label> labels = new HashMap<>();
    private final Map<UUID, NavigableSet<FrequencyChip>> catalogues = new HashMap<>();
    private final Map<UUID, Set<FrequencyChip>> hiddenFromCatalogue = new HashMap<>();
    private long revision;

    public static FrequencyNames get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(FrequencyNames::new, FrequencyNames::load), "industrial_frequency_names");
    }

    public long revision() { return revision; }

    private NavigableSet<FrequencyChip> owned(UUID player) {
        return catalogues.computeIfAbsent(player, key -> new TreeSet<>(FREQUENCY_ORDER));
    }

    private boolean add(UUID player, FrequencyChip frequency) {
        return frequency.permits(player) && !hiddenFromCatalogue.getOrDefault(player, Set.of()).contains(frequency)
            && owned(player).add(frequency);
    }

    void remember(UUID player, FrequencyChip frequency, boolean manualEncoding) {
        if (!frequency.permits(player)) return;
        var hidden = hiddenFromCatalogue.get(player);
        boolean restored = manualEncoding && hidden != null && hidden.remove(frequency);
        if (restored && hidden.isEmpty()) hiddenFromCatalogue.remove(player);
        if (add(player, frequency) || restored) changed();
    }

    private void hide(UUID player, FrequencyChip frequency) {
        hiddenFromCatalogue.computeIfAbsent(player, id -> new HashSet<>()).add(frequency);
    }

    public void editCatalogue(ServerPlayer player, FrequencyContent.EditCatalogue packet) {
        if (packet.number() < 1 || packet.number() > FrequencyChip.MAX_NUMBER) return;
        UUID owner = player.getUUID();
        var frequency = new FrequencyChip(packet.number(), packet.privateFrequency() ? owner : null);
        String message;
        if (!knows(owner, frequency)) message = "catalogue_unknown";
        else if (packet.remove()) {
            var entries = owned(owner);
            entries.remove(frequency);
            if (entries.isEmpty()) catalogues.remove(owner);
            hide(owner, frequency);
            changed();
            message = "catalogue_removed";
        } else {
            message = switch (change(player.serverLevel().getServer(), frequency, owner, packet.name())) {
                case SAVED -> name(frequency).isEmpty() ? "name_removed" : "name_saved";
                case DENIED -> "name_denied";
                case INVALID -> "name_invalid";
            };
        }
        player.displayClientMessage(Component.translatable(FrequencyContent.UI + message), true);
    }

    private void changed() {
        revision++;
        setDirty();
    }

    boolean knows(UUID player, FrequencyChip frequency) {
        var catalogue = catalogues.get(player);
        return catalogue != null && frequency.permits(player) && catalogue.contains(frequency);
    }

    public void recoverPrivate(ServerPlayer player) {
        UUID owner = player.getUUID();
        boolean recovered = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var chip = FrequencyChip.read(player.getInventory().getItem(i));
            if (chip != null && owner.equals(chip.issuer())) recovered |= add(owner, chip);
        }
        for (var level : player.serverLevel().getServer().getAllLevels())
            for (var chip : FrequencyNetwork.get(level).privateFrequencies(owner)) recovered |= add(owner, chip);
        if (recovered) changed();
    }

    public FrequencyContent.CatalogWindow catalogue(UUID player, int menu, int offset) {
        var entries = catalogues.get(player);
        int count = entries == null ? 0 : entries.size();
        int size = FrequencyContent.CatalogWindow.MAX_ENTRIES;
        int first = Math.clamp(offset - size / 4, 0, Math.max(0, count - size));
        var result = count == 0 ? List.<FrequencyContent.CatalogEntry>of()
            : entries.stream().skip(first).limit(size)
                .map(chip -> new FrequencyContent.CatalogEntry(chip.number(), chip.issuer() != null, name(chip))).toList();
        return new FrequencyContent.CatalogWindow(menu, first, count, result);
    }

    String name(FrequencyChip frequency) {
        var label = labels.get(frequency);
        return label == null ? "" : label.name();
    }

    static String clean(String value) {
        String name = value.strip();
        if (name.length() > MAX_LENGTH || name.codePoints().anyMatch(ch -> Character.isISOControl(ch) || ch == 0x00a7))
            return null;
        return name;
    }

    Result change(MinecraftServer server, FrequencyChip frequency, UUID player, String input) {
        String name = clean(input);
        if (name == null) return Result.INVALID;
        if (!frequency.permits(player)) return Result.DENIED;
        var previous = labels.get(frequency);
        if (name.equals(name(frequency))) return Result.SAVED;
        if (previous != null && !previous.editor().equals(player)) return Result.DENIED;
        if (name.isEmpty()) labels.remove(frequency);
        else {
            labels.put(frequency, new Label(name, player));
            add(player, frequency);
        }
        changed();
        var update = new FrequencyContent.Names(false, List.of(new Entry(frequency, name)));
        for (ServerPlayer receiver : server.getPlayerList().getPlayers())
            if (frequency.permits(receiver.getUUID())) PacketDistributor.sendToPlayer(receiver, update);
        return Result.SAVED;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var registry = get(player.serverLevel().getServer());
        PacketDistributor.sendToPlayer(player, new FrequencyContent.Names(true, List.of()));
        var batch = new ArrayList<Entry>();
        for (var entry : registry.labels.entrySet()) {
            if (!entry.getKey().permits(player.getUUID())) continue;
            batch.add(new Entry(entry.getKey(), entry.getValue().name()));
            if (batch.size() == FrequencyContent.Names.MAX_ENTRIES) {
                PacketDistributor.sendToPlayer(player, new FrequencyContent.Names(false, List.copyOf(batch)));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) PacketDistributor.sendToPlayer(player, new FrequencyContent.Names(false, List.copyOf(batch)));
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        var entries = new ListTag();
        for (var entry : labels.entrySet()) {
            var row = new CompoundTag();
            row.putInt("Number", entry.getKey().number());
            if (entry.getKey().issuer() != null) row.putUUID("Issuer", entry.getKey().issuer());
            row.putString("Name", entry.getValue().name());
            row.putUUID("Editor", entry.getValue().editor());
            entries.add(row);
        }
        tag.put("Labels", entries);
        tag.put("Catalogues", writeCatalogues(catalogues));
        tag.put("HiddenCatalogues", writeCatalogues(hiddenFromCatalogue));
        return tag;
    }

    private static ListTag writeCatalogues(Map<UUID, ? extends Set<FrequencyChip>> catalogues) {
        var owners = new ListTag();
        for (var entry : catalogues.entrySet()) {
            var row = new CompoundTag();
            row.putUUID("Owner", entry.getKey());
            row.putIntArray("Public", entry.getValue().stream().filter(chip -> chip.issuer() == null)
                .mapToInt(FrequencyChip::number).toArray());
            row.putIntArray("Private", entry.getValue().stream().filter(chip -> chip.issuer() != null)
                .mapToInt(FrequencyChip::number).toArray());
            owners.add(row);
        }
        return owners;
    }

    static FrequencyNames load(CompoundTag tag, HolderLookup.Provider lookup) {
        var result = new FrequencyNames();
        readCatalogues(tag.getList("HiddenCatalogues", Tag.TAG_COMPOUND), result::hide);
        for (Tag value : tag.getList("Labels", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            int number = row.getInt("Number");
            String name = clean(row.getString("Name"));
            if (number < 1 || number > FrequencyChip.MAX_NUMBER || !row.hasUUID("Editor") || name == null || name.isEmpty()) continue;
            var key = new FrequencyChip(number, row.hasUUID("Issuer") ? row.getUUID("Issuer") : null);
            UUID editor = row.getUUID("Editor");
            if (key.permits(editor)) {
                result.labels.put(key, new Label(name, editor));
                result.add(editor, key);
            }
        }
        readCatalogues(tag.getList("Catalogues", Tag.TAG_COMPOUND), result::add);
        return result;
    }

    private static void readCatalogues(ListTag owners, BiConsumer<UUID, FrequencyChip> register) {
        for (Tag value : owners) {
            var row = (CompoundTag) value;
            if (!row.hasUUID("Owner")) continue;
            UUID owner = row.getUUID("Owner");
            readNumbers(register, owner, row.getIntArray("Public"), null);
            readNumbers(register, owner, row.getIntArray("Private"), owner);
        }
    }

    private static void readNumbers(BiConsumer<UUID, FrequencyChip> register, UUID owner, int[] numbers, UUID issuer) {
        for (int number : numbers)
            if (number >= 1 && number <= FrequencyChip.MAX_NUMBER) register.accept(owner, new FrequencyChip(number, issuer));
    }
}

