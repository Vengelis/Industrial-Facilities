package fr.vengelis.industrialfacilities.frequency;

import fr.vengelis.industrialfacilities.frequency.FrequencyContent.Encode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public final class FrequencyEncoding {
    public static final int PUBLIC = 0, PRIVATE = 1, DUPLICATE = 2, RENAME = 3;
    public static final int FROM_PUBLIC_CATALOGUE = 4, FROM_PRIVATE_CATALOGUE = 5;
    public static final int SET_MODE = 6;
    public static final int NAME_LENGTH = FrequencyNames.MAX_LENGTH;
    public static final int SLOTS = 3;

    public static boolean accepts(int slot, ItemStack stack) {
        if (!stack.is(FrequencyContent.CHIP.get())) return false;
        FrequencyChip chip = FrequencyChip.read(stack);
        return slot == 0 ? chip == null : slot == 1 && chip != null;
    }

    public static String encode(ServerPlayer player, SimpleContainer chips, Encode packet) {
        boolean rename = packet.operation() == RENAME;
        boolean duplicate = packet.operation() == DUPLICATE;
        boolean setMode = packet.operation() == SET_MODE;
        if (packet.mode() != -1 && !FrequencyChip.Mode.valid(packet.mode())) return "mode_invalid";
        boolean fromCatalogue = packet.operation() == FROM_PUBLIC_CATALOGUE || packet.operation() == FROM_PRIVATE_CATALOGUE;
        var server = player.serverLevel().getServer();
        var names = FrequencyNames.get(server);
        ItemStack blank = chips.getItem(0);
        if (!rename && !setMode && !accepts(0, blank)) return "";

        FrequencyChip frequency;
        if (rename || duplicate || setMode) {
            frequency = FrequencyChip.read(chips.getItem(1));
            if (frequency == null || !frequency.permits(player.getUUID())) return rename ? "rename_source" : "copy_denied";
        } else {
            if (!fromCatalogue && packet.operation() != PUBLIC && packet.operation() != PRIVATE) return "";
            if (packet.frequency() < 1 || packet.frequency() > FrequencyChip.MAX_NUMBER) return "";
            boolean isPrivate = packet.operation() == PRIVATE || packet.operation() == FROM_PRIVATE_CATALOGUE;
            frequency = new FrequencyChip(packet.frequency(), isPrivate ? player.getUUID() : null);
            if (fromCatalogue && !names.knows(player.getUUID(), frequency)) return "catalogue_unknown";
        }
        var mode = packet.mode() == -1 ? duplicate || setMode ? FrequencyChip.mode(chips.getItem(1)) : FrequencyChip.Mode.BOTH
            : FrequencyChip.Mode.fromId(packet.mode());
        if (setMode) {
            chips.setItem(1, FrequencyChip.withMode(chips.getItem(1), mode));
            return "mode_saved";
        }
        boolean updateName = rename || !duplicate && !fromCatalogue && !packet.name().isBlank();
        String name = updateName ? FrequencyNames.clean(packet.name()) : names.name(frequency);
        if (name == null) return "name_invalid";

        ItemStack encoded = rename ? ItemStack.EMPTY : FrequencyChip.withMode(frequency.encode(blank, name), mode);
        ItemStack output = chips.getItem(2);
        int count = 0;
        if (!rename) {
            if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(encoded, output)) return "batch_output_mismatch";
            count = Math.min(blank.getCount(), encoded.getMaxStackSize() - output.getCount());
            if (count <= 0) return "batch_output_full";
        }

        if (updateName && !name.equals(names.name(frequency))) {
            var result = names.change(server, frequency, player.getUUID(), name);
            if (result != FrequencyNames.Result.SAVED)
                return result == FrequencyNames.Result.DENIED ? "name_denied" : "name_invalid";
        }

        if (rename) {
            var renamed = frequency.withSavedName(chips.getItem(1), name);
            chips.setItem(1, packet.mode() == -1 ? renamed : FrequencyChip.withMode(renamed, mode));
            return packet.mode() == -1 ? name.isEmpty() ? "name_removed" : "name_saved" : "chip_saved";
        }
        chips.removeItem(0, count);
        chips.setItem(2, encoded.copyWithCount(output.getCount() + count));
        if (!fromCatalogue && (!duplicate || player.getUUID().equals(frequency.issuer())))
            names.remember(player.getUUID(), frequency, !duplicate);
        return "";
    }

    private FrequencyEncoding() {}
}
