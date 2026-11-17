package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SupervisionMenu extends AbstractContainerMenu {
    public static final int WORLD = 0, PORTABLE = 1, TERMINAL = 2, DISPLAY = 3;
    public static final int SAVE_SECTOR = 0, PAIR = 1;
    public static final int RENAME = 2, POSITION = 3, RESET_LAYOUT = 4;
    public static final int SELECT_SECTOR = 5, REMOVE_SECTOR = 6;
    public static final int RENAME_SECTOR = 7;
    public static final int DISPLAY_PAGE = 8;
    public static final int RENAME_DISPLAY = 9, SELECT_DISPLAY_SECTOR = 10, UNLINK_DISPLAY = 11;
    public final int mode;
    public final boolean edit;
    public String sector;
    public List<String> sectors = List.of();
    public NetworkView view = NetworkView.EMPTY;
    public int displayPage;
    public String displayName = "";
    public String displayStatus = "";
    public List<SupervisionData.LinkedDisplay> linkedDisplays = List.of();
    private final SimpleContainer pairing = new SimpleContainer(1);
    private final Player viewer;
    private final SupervisionConsoleEntity console;
    private final AccessTerminalEntity terminal;
    private final InteractionHand hand;
    private final ItemStack portable;
    private NetworkView sent;
    private String sentSector;
    private List<String> sentSectors = List.of();
    private int sentDisplayPage = -1;
    private String sentDisplayName = "";
    private String sentDisplayStatus = "";
    private List<SupervisionData.LinkedDisplay> sentDisplays = List.of();
    private String portableSector;
    private String previousName = "";
    private String replacementName = "";
    private String renameNotice = "";
    private long lastUpdate = Long.MIN_VALUE;

    public SupervisionMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readByte(), buffer.readBoolean(), buffer.readUtf(24), null, null, null, ItemStack.EMPTY);
    }

    private SupervisionMenu(int id, Inventory inventory, int mode, boolean edit, String sector,
                            SupervisionConsoleEntity console, AccessTerminalEntity terminal, InteractionHand hand, ItemStack portable) {
        super(SupervisionContent.MENU, id);
        this.mode = mode;
        this.edit = edit;
        this.sector = sector;
        this.viewer = inventory.player;
        this.console = console;
        this.terminal = terminal;
        this.hand = hand;
        this.portable = portable;
        if (mode == WORLD && edit) {
            addSlot(new Slot(pairing, 0, 316, 184) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof PortableConsoleItem; }
                @Override public int getMaxStackSize() { return 1; }
            });
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, 9 + row * 9 + col, 91 + col * 18, 209 + row * 18));
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 91 + col * 18, 267));
        }
    }

    private static void open(ServerPlayer player, int mode, boolean edit, String name, SupervisionConsoleEntity console,
                             AccessTerminalEntity terminal, InteractionHand hand, ItemStack portable) {
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, owner) -> new SupervisionMenu(id, inventory, mode, edit, name, console, terminal, hand, portable),
            Component.translatable(NetworkView.UI + (mode == TERMINAL ? "terminal_settings" : mode == DISPLAY ? "display_title" : "title"))),
            buffer -> { buffer.writeByte(mode); buffer.writeBoolean(edit); buffer.writeUtf(name, 24); });
    }
    static void openConsole(ServerPlayer player, SupervisionConsoleEntity console) {
        open(player, WORLD, player.getUUID().equals(console.placedBy), console.sector(), console, null, null, ItemStack.EMPTY);
    }
    static void openDisplay(ServerPlayer player, SupervisionConsoleEntity display) {
        open(player, DISPLAY, player.getUUID().equals(display.placedBy), display.sector(), display, null, null, ItemStack.EMPTY);
    }
    static void openPortable(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        var connection = PortableConsoleItem.resolve(player, stack);
        if (connection != null) open(player, PORTABLE, false, connection.console().sector().name(), null, null, hand, stack);
    }
    static void openTerminal(ServerPlayer player, AccessTerminalEntity terminal, ItemStack stack) {
        String sector = SupervisionData.get(player.serverLevel()).sector(terminal);
        var paired = PortableConsoleItem.resolve(player, stack);
        if (sector.isEmpty() && paired != null) sector = paired.console().sector().name();
        open(player, TERMINAL, true, sector, null, terminal, null, ItemStack.EMPTY);
    }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        if (player != viewer || !player.isAlive() || player.isSpectator()) return false;
        if (mode == PORTABLE) return player instanceof ServerPlayer server && player.getItemInHand(hand) == portable
            && PortableConsoleItem.resolve(server, portable) != null;
        if (mode == TERMINAL) return terminal != null && terminal.isOwner(player) && terminal.getLevel() == player.level()
            && player.distanceToSqr(terminal.getBlockPos().getCenter()) <= 64
            && AccessTerminalEntity.resolve(player.level(), terminal.getBlockPos()) == terminal;
        return console != null && console.getLevel() == player.level() && console.assembled()
            && console.bounds().distanceToSqr(player.position()) <= 64
            && SupervisionConsoleEntity.resolve(player.level(), console.getBlockPos()) == console;
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(viewer instanceof ServerPlayer player) || mode == TERMINAL || !stillValid(player)) return;
        long now = player.serverLevel().getGameTime();
        if (now == lastUpdate || (sent != null && now - lastUpdate < 15)) return;
        lastUpdate = now;
        if (mode == WORLD || mode == DISPLAY) {
            if (mode == DISPLAY) {
                displayPage = console.page();
                displayName = console.displayName();
            } else if (edit) {
                var level = player.serverLevel();
                linkedDisplays = SupervisionData.get(level).linkedDisplays(level, console.reference());
            }
            sector = console.sector();
            sectors = console.sectors();
            view = console.view();
        } else {
            var ref = PortableConsoleItem.resolve(player, portable);
            if (ref == null) return;
            var source = ref.console();
            sectors = source.sectors();
            if (portableSector != null && !sectors.contains(portableSector)) portableSector = null;
            sector = portableSector == null ? source.sector().name() : portableSector;
            view = SupervisionData.get(ref.level()).view(ref.level(), source.showing(sector));
        }
        if (!view.equals(sent) || !sector.equals(sentSector) || !sectors.equals(sentSectors) || displayPage != sentDisplayPage
                || !displayName.equals(sentDisplayName) || !linkedDisplays.equals(sentDisplays) || !displayStatus.equals(sentDisplayStatus)) {
            sent = view;
            sentSector = sector;
            sentSectors = sectors;
            sentDisplayPage = displayPage;
            sentDisplayName = displayName;
            sentDisplays = linkedDisplays;
            sentDisplayStatus = displayStatus;
            var snapshot = view.write(true);
            if (mode == DISPLAY) {
                snapshot.putInt("DisplayPage", displayPage);
                snapshot.putString("DisplayName", displayName);
            } else if (mode == WORLD && edit) {
                var displays = new ListTag();
                linkedDisplays.forEach(display -> displays.add(display.write()));
                snapshot.put("LinkedDisplays", displays);
                snapshot.putString("DisplayStatus", displayStatus);
            }
            PacketDistributor.sendToPlayer(player, new SupervisionPackets.Snapshot(containerId, sector, sectors, renameNotice, snapshot));
            renameNotice = "";
        }
    }

    public void receive(SupervisionPackets.Snapshot packet) {
        if (containerId != packet.menu() || packet.view() == null) return;
        sector = packet.sector();
        if (!packet.renamedFrom().isEmpty()) {
            previousName = packet.renamedFrom();
            replacementName = sector;
        }
        sectors = List.copyOf(packet.sectors());
        if (mode == DISPLAY) {
            displayPage = packet.view().getInt("DisplayPage");
            displayName = packet.view().getString("DisplayName");
        } else if (mode == WORLD && edit) {
            linkedDisplays = packet.view().getList("LinkedDisplays", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast).filter(row -> row.hasUUID("Id")).map(SupervisionData.LinkedDisplay::read).toList();
            displayStatus = packet.view().getString("DisplayStatus");
        }
        view = NetworkView.read(packet.view());
    }

    public boolean consumeSectorRename(String previous) {
        boolean renamed = previousName.equals(previous) && replacementName.equals(sector);
        previousName = replacementName = "";
        return renamed;
    }

    void sectorRenamed(ServerPlayer player, ServerLevel level, SupervisionData.Sector previous, SupervisionData.Sector next) {
        if (!stillValid(player)) return;
        if (mode == TERMINAL) {
            if (terminal.getLevel() == level && previous.owner().equals(terminal.creator) && sector.equals(previous.name())) {
                sector = next.name();
                PacketDistributor.sendToPlayer(player,
                    new SupervisionPackets.Snapshot(containerId, sector, List.of(), previous.name(), NetworkView.EMPTY.write(false)));
            }
            return;
        }
        if (mode == PORTABLE) {
            var connection = PortableConsoleItem.resolve(player, portable);
            if (connection == null || connection.level() != level || !previous.owner().equals(connection.console().sector().owner())) return;
            if (previous.name().equals(portableSector)) portableSector = next.name();
        } else if (mode == DISPLAY) {
            if (!console.displaysSourceIn(level) || !previous.owner().equals(console.placedBy)) return;
            console.update(player.serverLevel());
        } else if (console.getLevel() != level || !previous.owner().equals(console.placedBy)) return;

        if (sector.equals(previous.name())) renameNotice = previous.name();
        refreshView();
    }

    void apply(ServerPlayer player, SupervisionPackets.Action packet) {
        if (!stillValid(player)) return;
        int action = packet.kind();
        String input = packet.name();
        if (mode == DISPLAY) {
            if (!edit || !player.getUUID().equals(console.placedBy)) return;
            if (action == SELECT_SECTOR && console.selectSector(player.serverLevel(), input)) refreshView();
            else if (action == DISPLAY_PAGE && (packet.page() == -1 || packet.page() == 1)) {
                console.turnPage(packet.page());
                refreshView();
            }
            return;
        }
        if (action == SELECT_SECTOR) {
            if (mode == WORLD && console.selectSector(player.serverLevel(), input)) {
                refreshView();
            } else if (mode == PORTABLE) {
                var connection = PortableConsoleItem.resolve(player, portable);
                if (connection != null && connection.console().sectors().contains(input)) {
                    portableSector = input;
                    refreshView();
                }
            }
            return;
        }
        if (!edit || mode == PORTABLE) return;
        if (action == RENAME_DISPLAY || action == SELECT_DISPLAY_SECTOR || action == UNLINK_DISPLAY) {
            if (mode == WORLD && player.getUUID().equals(console.placedBy)) editDisplay(player.serverLevel(), packet);
            return;
        }
        if (action == SAVE_SECTOR) {
            String name = SupervisionData.cleanSector(input);
            if (name == null) {
                player.displayClientMessage(Component.translatable(NetworkView.UI + "invalid_sector"), true);
                return;
            }
            if (mode == TERMINAL) {
                SupervisionData.get(player.serverLevel()).assign(player.serverLevel(), terminal, name);
                player.closeContainer();
            } else {
                if (name.isEmpty()) return;
                console.addSector(player.serverLevel(), name);
                refreshView();
            }
            player.displayClientMessage(Component.translatable(NetworkView.UI + "saved"), true);
        } else if (action == REMOVE_SECTOR && mode == WORLD) {
            console.removeSector(player.serverLevel(), input);
            refreshView();
        } else if (action == RENAME_SECTOR && mode == WORLD) {
            if (console.sector().isEmpty() || !console.sector().equals(packet.sector())) return;
            String name = SupervisionData.cleanSector(input);
            if (name == null || name.isEmpty()) {
                player.displayClientMessage(Component.translatable(NetworkView.UI + "invalid_rename_sector"), true);
                return;
            }
            if (name.equals(console.sector())) return;
            var level = player.serverLevel();
            if (!SupervisionData.get(level).renameSector(level, console.reference().sector(), name)) {
                player.displayClientMessage(Component.translatable(NetworkView.UI + "sector_name_in_use"), true);
                return;
            }
            player.displayClientMessage(Component.translatable(NetworkView.UI + "sector_renamed", name), true);
        } else if (action == PAIR && mode == WORLD && !console.sector().isEmpty()) {
            var stack = pairing.getItem(0);
            if (stack.getItem() instanceof PortableConsoleItem && stack.getCount() == 1) {
                PortableConsoleItem.pair(stack, player.serverLevel(), console.reference());
                pairing.setChanged();
                player.displayClientMessage(Component.translatable(NetworkView.UI + "paired_to", console.sector()), true);
                broadcastChanges();
            }
        } else if (mode == WORLD && console.sector().equals(packet.sector()) && !console.sector().isEmpty()) {
            editGraph(player, packet);
        }
    }

    private void editDisplay(ServerLevel level, SupervisionPackets.Action packet) {
        var data = SupervisionData.get(level);
        var id = packet.node();
        var link = id == null ? null : data.display(id);
        displayStatus = "display_link_missing";
        if (link != null && link.source().equals(console.consoleId)) {
            var target = SupervisionData.loadedDisplay(level, id, link);
            switch (packet.kind()) {
                case RENAME_DISPLAY -> displayStatus = data.renameDisplay(id, packet.name()) ? "display_saved" : "display_invalid_name";
                case SELECT_DISPLAY_SECTOR -> displayStatus = data.selectDisplaySector(id, packet.sector())
                    ? "display_saved" : "display_invalid_sector";
                case UNLINK_DISPLAY -> {
                    if (target != null) displayStatus = "display_back_online";
                    else {
                        data.removeDisplay(id);
                        displayStatus = "display_unlinked";
                    }
                }
            }
            if (target != null && displayStatus.equals("display_saved")) target.update((ServerLevel) target.getLevel());
        }
        refreshView();
    }

    private void editGraph(ServerPlayer player, SupervisionPackets.Action packet) {
        var level = player.serverLevel();
        var data = SupervisionData.get(level);
        var ref = console.reference();
        if (packet.kind() == RESET_LAYOUT) {
            data.resetLayout(ref);
        } else if (packet.kind() == RENAME || packet.kind() == POSITION) {
            var current = data.view(level, ref);
            var node = current.nodes().stream().filter(n -> n.id().equals(packet.node())).findFirst().orElse(null);
            if (node == null) return;
            if (packet.kind() == RENAME) {
                if (!data.rename(ref.sector(), node, packet.name())) return;
            } else {
                int page = packet.page();
                if (page < 0 || page > GraphLayout.pageCount(current) || page > Math.min(current.nodes().size(), 4096)) return;
                data.place(ref, node.id(), GraphLayout.Placement.within(current.canvas(), page, packet.x(), packet.y()));
            }
        } else return;
        console.update(level);
        refreshView();
    }

    private void refreshView() {
        lastUpdate = Long.MIN_VALUE;
        sent = null;
        broadcastChanges();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!edit || mode != WORLD || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        var stack = slot.getItem();
        var before = stack.copy();
        if (stack.isEmpty() || !(index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : moveItemStackTo(stack, 0, 1, false)))
            return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, pairing);
    }
}
