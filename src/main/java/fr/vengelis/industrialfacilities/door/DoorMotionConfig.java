package fr.vengelis.industrialfacilities.door;

import java.util.EnumMap;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DoorMotionConfig {
    public static final int DEFAULT_TICKS = 40;
    public static final int MAX_TICKS = 1200;
    private record Timing(int fallback, ModConfigSpec.IntValue open, ModConfigSpec.IntValue close) {}

    private static final EnumMap<IndustrialDoorStyle, Timing> MULTIBLOCK = new EnumMap<>(IndustrialDoorStyle.class);
    private static final EnumMap<IndustrialDoorStyle, Timing> CLASSIC = new EnumMap<>(IndustrialDoorStyle.class);
    public static final String FILE = "industrial_facilities-doors.toml";
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        define(builder, "multiblock", MULTIBLOCK, false);
        define(builder, "classic", CLASSIC, true);
        SPEC = builder.build();
    }

    private static void define(ModConfigSpec.Builder builder, String name, EnumMap<IndustrialDoorStyle, Timing> settings, boolean classic) {
        builder.comment("20 ticks = 1 second. Values describe a full opening or closing.").push(name);
        for (var style : IndustrialDoorStyle.values()) {
            if (classic && !style.hasClassic()) continue;
            int min = classic && !style.lifts() ? 0 : 1;
            int fallback = min == 0 ? 0 : DEFAULT_TICKS;
            builder.push(style.configName());
            var open = builder.comment(min == 0 ? "Delay before pivoting open; 0 = immediate." : "Time to travel from fully closed to fully open.")
                .defineInRange("open_ticks", fallback, min, MAX_TICKS);
            var close = builder.comment(min == 0 ? "Delay before pivoting closed; 0 = immediate." : "Time to travel from fully open to fully closed.")
                .defineInRange("close_ticks", fallback, min, MAX_TICKS);
            settings.put(style, new Timing(fallback, open, close));
            builder.pop();
        }
        builder.pop();
    }

    public static int ticks(IndustrialDoorStyle style, boolean classic, boolean opening) {
        var timing = (classic ? CLASSIC : MULTIBLOCK).get(style);
        return SPEC.isLoaded() ? (opening ? timing.open() : timing.close()).get() : timing.fallback();
    }

    private DoorMotionConfig() {}
}
