
package fr.vengelis.industrialfacilities.airlock;

import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class AirlockPlayerAccessManager {

    private static final String KEY = "IFAllowedPlayers";

    private AirlockPlayerAccessManager() {}

    public static String addPlayer(
        ServerLevel level, BlockPos pos, String input
    ) {
        if (!(level.getBlockEntity(pos)
                instanceof AirlockControllerBlockEntity be)) {
            return "Airlock controller not found.";
        }

        String name = input.trim();

        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            return "Invalid Minecraft player name.";
        }

        ServerPlayer target = level.getServer()
            .getPlayerList().getPlayerByName(name);

        if (target == null) {
            return "Player must be online to be added.";
        }

        CompoundTag data = be.getPersistentData();
        ListTag players = data.getList(KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);

            if (entry.hasUUID("UUID") &&
                entry.getUUID("UUID").equals(target.getUUID())) {
                return "Player is already registered.";
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putUUID("UUID", target.getUUID());
        entry.putString("Name", target.getGameProfile().getName());

        players.add(entry);
        data.put(KEY, players);

        sync(level, pos, be);

        return "Player added: " + target.getName().getString();
    }

    public static String removePlayer(
        ServerLevel level, BlockPos pos, String input
    ) {
        if (!(level.getBlockEntity(pos)
                instanceof AirlockControllerBlockEntity be)) {
            return "Airlock controller not found.";
        }

        CompoundTag data = be.getPersistentData();
        ListTag players = data.getList(KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);

            if (entry.getString("Name").equalsIgnoreCase(input.trim())) {
                players.remove(i);
                data.put(KEY, players);
                sync(level, pos, be);
                return "Player removed.";
            }
        }

        return "Player not found.";
    }

    public static boolean isAllowed(
        CompoundTag data, Player player
    ) {
        if (!data.getBoolean("IFWhitelistEnabled")) {
            return true;
        }

        boolean listed = false;
        ListTag players = data.getList(KEY, Tag.TAG_COMPOUND);
        UUID uuid = player.getUUID();

        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);

            if (entry.hasUUID("UUID") &&
                entry.getUUID("UUID").equals(uuid)) {
                listed = true;
                break;
            }
        }

        return data.getBoolean("IFInvertSelection")
            ? !listed
            : listed;
    }

    public static String getPlayersText(
        Level level, BlockPos pos
    ) {
        if (!(level.getBlockEntity(pos)
                instanceof AirlockControllerBlockEntity be)) {
            return "No controller";
        }

        ListTag players = be.getPersistentData()
            .getList(KEY, Tag.TAG_COMPOUND);

        if (players.isEmpty()) {
            return "No registered players";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < players.size(); i++) {
            if (i > 0) result.append(", ");
            result.append(players.getCompound(i).getString("Name"));
        }

        return result.toString();
    }

    private static void sync(
        ServerLevel level,
        BlockPos pos,
        AirlockControllerBlockEntity be
    ) {
        be.setChanged();
        var state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
