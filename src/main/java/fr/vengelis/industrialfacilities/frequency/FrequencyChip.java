package fr.vengelis.industrialfacilities.frequency;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record FrequencyChip(int number, UUID issuer) {
    public static final int MAX_NUMBER = 999999;

    /** Direction belongs to a physical chip, never to the frequency's identity. */
    public enum Mode {
        BOTH(true, true, "both", "TX/RX"), SEND(true, false, "send", "TX"), RECEIVE(false, true, "receive", "RX");
        private final boolean sends, receives;
        public final String key, shortName;
        Mode(boolean sends, boolean receives, String key, String shortName) {
            this.sends = sends; this.receives = receives; this.key = key; this.shortName = shortName;
        }
        public boolean sends() { return sends; }
        public boolean receives() { return receives; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
        public static boolean valid(int id) { return id >= 0 && id < values().length; }
        public static Mode fromId(int id) { return valid(id) ? values()[id] : BOTH; }
        public net.minecraft.network.chat.Component label() {
            return net.minecraft.network.chat.Component.translatable(FrequencyContent.UI + "mode_" + key);
        }
        public net.minecraft.network.chat.Component hint() {
            return net.minecraft.network.chat.Component.translatable(FrequencyContent.UI + "mode_" + key + "_hint");
        }
    }

    public static Mode mode(ItemStack stack) {
        return Mode.fromId(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("FrequencyMode"));
    }

    public static ItemStack withMode(ItemStack stack, Mode mode) {
        ItemStack result = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> {
            // Missing mode is the historical bidirectional behavior and preserves old stackability.
            if (mode == Mode.BOTH) tag.remove("FrequencyMode");
            else tag.putInt("FrequencyMode", mode.ordinal());
        });
        return result;
    }

    public static FrequencyChip read(ItemStack stack) {
        if (!stack.is(FrequencyContent.CHIP.get())) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int number = tag.getInt("Frequency");
        if (number < 1 || number > MAX_NUMBER) return null;
        return new FrequencyChip(number, tag.hasUUID("Issuer") ? tag.getUUID("Issuer") : null);
    }

    static String savedName(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("FrequencyName");
    }

    ItemStack encode(ItemStack blank) { return encode(blank, ""); }

    ItemStack encode(ItemStack blank, String name) {
        ItemStack result = new ItemStack(blank.getItem());
        CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> {
            tag.putInt("Frequency", number);
            if (issuer != null) tag.putUUID("Issuer", issuer);
        });
        return withSavedName(result, name);
    }

    ItemStack withSavedName(ItemStack stack, String name) {
        ItemStack result = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> {
            if (name.isEmpty()) tag.remove("FrequencyName");
            else tag.putString("FrequencyName", name);
        });
        return result;
    }

    public boolean permits(UUID owner) {
        return issuer == null || issuer.equals(owner);
    }
}

