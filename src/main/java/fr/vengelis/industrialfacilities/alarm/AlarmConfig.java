package fr.vengelis.industrialfacilities.alarm;

import com.google.gson.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Shared server catalogue. Audio files remain local resources on each client. */
public final class AlarmConfig {
    public record Entry(String id, String name, String file, ResourceLocation sound) {}
    public static final int MAX_RANGE = 128;
    public static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("industrial_facilities/alarms");
    private static volatile List<Entry> entries = List.of();
    private static int defaultRange = 32;
    public static synchronized void load() {
        try {
            Files.createDirectories(DIRECTORY);
            for (String name : List.of("alarms.json", "siren.ogg", "evacuation.ogg", "beacon.ogg", "LISEZ-MOI.txt")) {
                Path target = DIRECTORY.resolve(name);
                if (!Files.exists(target)) try (var input = AlarmConfig.class.getResourceAsStream("/alarm_defaults/" + name)) {
                    if (input != null) Files.copy(input, target);
                }
            }
            Path config = DIRECTORY.resolve("alarms.json");
            if (Files.size(config) > 262144) throw new IOException("alarms.json exceeds 256 KiB");
            JsonObject root;
            try (var reader = Files.newBufferedReader(config)) { root = JsonParser.parseReader(reader).getAsJsonObject(); }
            var result = new ArrayList<Entry>();
            var ids = new HashSet<String>();
            var definitions = root.getAsJsonArray("sounds");
            for (var value : definitions) {
                if (result.size() == 128) break;
                try {
                    var item = value.getAsJsonObject();
                    String id = item.get("id").getAsString();
                    String name = item.get("name").getAsString();
                    if (!id.matches("[a-z0-9_]{1,48}") || name.isBlank() || name.length() > 80 || ids.contains(id))
                        throw new IllegalArgumentException("invalid or duplicate id/name");
                    String file = item.has("file") ? item.get("file").getAsString() : "";
                    ResourceLocation sound;
                    if (!file.isEmpty()) {
                        // Resolve real paths too: symlinks must not escape the alarm directory.
                        if (!file.matches("[a-z0-9_./-]+\\.ogg")) throw new IOException("expected lowercase .ogg path");
                        Path path = DIRECTORY.resolve(file).normalize();
                        if (!path.toRealPath().startsWith(DIRECTORY.toRealPath()) || !Files.isRegularFile(path)
                            || Files.size(path) > 16 * 1024 * 1024) throw new IOException("invalid, missing or oversized audio file");
                        sound = AlarmContent.id("custom_alarm/" + id);
                    } else sound = ResourceLocation.parse(item.get("sound").getAsString());
                    result.add(new Entry(id, name, file, sound));
                    ids.add(id);
                } catch (Exception error) { IndustrialFacilitiesMod.LOGGER.warn("Ignoring alarm entry {}: {}", value, error.getMessage()); }
            }
            if (result.isEmpty()) throw new IOException("no valid alarm sounds");
            defaultRange = Math.clamp(root.has("default_range") ? root.get("default_range").getAsInt() : 32, 1, MAX_RANGE);
            entries = List.copyOf(result);
        } catch (Exception error) {
            IndustrialFacilitiesMod.LOGGER.error("Cannot load alarm catalogue; keeping last valid catalogue", error);
            if (entries.isEmpty()) entries = List.of(new Entry("fallback", "Minecraft bell", "", ResourceLocation.parse("minecraft:block.bell.use")));
        }
    }
    public static List<Entry> entries() { if (entries.isEmpty()) load(); return entries; }
    public static Entry find(String id) { return entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }
    public static Entry resolve(String id) { var e = find(id); return e != null ? e : entries().getFirst(); }
    public static int defaultRange() { entries(); return defaultRange; }
    private AlarmConfig() {}
}
