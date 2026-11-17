package fr.vengelis.industrialfacilities.supervision;

import fr.vengelis.industrialfacilities.access.TerminalLinks;
import fr.vengelis.industrialfacilities.block.SupervisionDisplayBlock;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SupervisionConsoleEntity extends BlockEntity {
    UUID placedBy, consoleId;
    BlockPos root;
    String sector = "";
    private List<String> sectors = List.of();
    int page;
    private NetworkView view = NetworkView.EMPTY;
    private ResourceLocation displayDimension;
    private String displayName = "";
    private record Footprint(int left, int bottom, GraphLayout.Canvas canvas) {
        private static final Footprint DEFAULT = new Footprint(-1, 0, GraphLayout.Canvas.DEFAULT);
        private static final Footprint DISPLAY = new Footprint(-1, 0, new GraphLayout.Canvas(3, 3, true));
        private Footprint {
            left = Math.clamp(left, 1 - canvas.columns(), 0);
            bottom = Math.clamp(bottom, 1 - canvas.rows(), 0);
        }
        int right() { return left + canvas.columns() - 1; }
        int top() { return bottom + canvas.rows() - 1; }
    }
    private Footprint footprint;

    public SupervisionConsoleEntity(BlockPos pos, BlockState state) {
        super(SupervisionContent.TYPE, pos, state);
        footprint = initialFootprint();
    }
    private Footprint initialFootprint() { return isDisplay() ? Footprint.DISPLAY : Footprint.DEFAULT; }
    public boolean assembled() { return consoleId != null && worldPosition.equals(root); }
    public boolean isDisplay() { return getBlockState().getBlock() instanceof SupervisionDisplayBlock; }
    public String sector() { return sector; }
    public List<String> sectors() { return sectors; }
    public int page() { return page; }
    public NetworkView view() { return view; }
    public GraphLayout.Canvas canvas() { return footprint.canvas(); }
    public int firstColumn() { return footprint.left(); }
    public int lastColumn() { return footprint.right(); }
    public int bottomRow() { return footprint.bottom(); }
    public AABB bounds() {
        var right = getBlockState().getValue(SupervisionConsoleBlock.FACING).getCounterClockWise();
        return AABB.encapsulatingFullBlocks(worldPosition.above(footprint.bottom()).relative(right, footprint.left()),
            worldPosition.above(footprint.top()).relative(right, footprint.right()));
    }

    static SupervisionConsoleEntity resolve(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SupervisionConsoleEntity part && part.root != null
                && level.hasChunkAt(part.root) && level.getBlockEntity(part.root) instanceof SupervisionConsoleEntity console
                && console.assembled() && part.getBlockState().is(console.getBlockState().getBlock())) return console;
        return null;
    }

    static SupervisionConsoleEntity assemble(ServerLevel level, BlockPos placed, UUID owner, Direction face) {
        Direction right = face.getCounterClockWise();
        Block block = level.getBlockState(placed).getBlock();
        for (var side : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
            BlockPos nearby = placed.relative(side);
            if (!TerminalLinks.loaded(level, nearby)) continue;
            var console = resolve(level, nearby);
            if (console != null && owner.equals(console.placedBy) && console.getBlockState().is(block)
                    && console.getBlockState().getValue(SupervisionConsoleBlock.FACING) == face) {
                console.expand(level, face);
                if (resolve(level, placed) == console) return console;
            }
        }
        for (int row = 0; row < GraphLayout.MIN_SIZE; row++) for (int column = -1; column <= 1; column++) {
            BlockPos origin = placed.below(row).relative(right, -column);
            var initial = block instanceof SupervisionDisplayBlock ? Footprint.DISPLAY : Footprint.DEFAULT;
            if (!complete(level, origin, owner, face, block, initial, true)) continue;
            var console = (SupervisionConsoleEntity) level.getBlockEntity(origin);
            console.consoleId = UUID.randomUUID();
            console.applyFootprint(level, face, console.largestFootprint(level, face));
            return console;
        }
        return null;
    }

    private void expand(ServerLevel level, Direction face) {
        var larger = largestFootprint(level, face);
        if (!larger.equals(footprint)) applyFootprint(level, face, larger);
    }

    private Footprint largestFootprint(ServerLevel level, Direction face) {
        var current = footprint;
        while (true) {
            var next = growOneSide(level, face, current);
            if (next.equals(current)) return current;
            current = next;
        }
    }

    private Footprint growOneSide(ServerLevel level, Direction face, Footprint current) {
        var canvas = current.canvas();
        Block block = getBlockState().getBlock();
        if (canvas.columns() < GraphLayout.MAX_SIZE) {
            var wider = new GraphLayout.Canvas(canvas.columns() + 1, canvas.rows(), canvas.fullHeight());
            var left = new Footprint(current.left() - 1, current.bottom(), wider);
            if (complete(level, worldPosition, placedBy, face, block, left, true)) return left;
            var right = new Footprint(current.left(), current.bottom(), wider);
            if (complete(level, worldPosition, placedBy, face, block, right, true)) return right;
        }
        if (canvas.rows() < GraphLayout.MAX_SIZE) {
            var taller = new GraphLayout.Canvas(canvas.columns(), canvas.rows() + 1, canvas.fullHeight());
            var top = new Footprint(current.left(), current.bottom(), taller);
            if (complete(level, worldPosition, placedBy, face, block, top, true)) return top;
            var bottom = new Footprint(current.left(), current.bottom() - 1, taller);
            if (complete(level, worldPosition, placedBy, face, block, bottom, true)) return bottom;
        }
        return current;
    }

    private void applyFootprint(ServerLevel level, Direction face, Footprint size) {
        footprint = size;
        Direction right = face.getCounterClockWise();
        int center = size.left() + size.canvas().columns() / 2;
        for (int y = size.bottom(); y <= size.top(); y++) {
            int row = y == size.bottom() ? 1 : y == size.top() ? 3 : y == size.bottom() + 1 ? 2 : 4;
            for (int x = size.left(); x <= size.right(); x++) {
                int column = x == size.left() ? 0 : x == size.right() ? 2 : row == 1 && x != center && !isDisplay() ? 3 : 1;
                BlockPos pos = worldPosition.above(y).relative(right, x);
                var part = (SupervisionConsoleEntity) level.getBlockEntity(pos);
                part.root = worldPosition.immutable();
                level.setBlock(pos, part.getBlockState().setValue(SupervisionConsoleBlock.ROW, row)
                    .setValue(SupervisionConsoleBlock.COLUMN, column), 3);
                part.sync();
            }
        }
        update(level);
    }

    private static boolean complete(ServerLevel level, BlockPos origin, UUID owner, Direction face, Block block, Footprint size,
                                    boolean allowUnassembled) {
        Direction right = face.getCounterClockWise();
        for (int row = size.bottom(); row <= size.top(); row++) for (int col = size.left(); col <= size.right(); col++) {
            BlockPos pos = origin.above(row).relative(right, col);
            if (!TerminalLinks.loaded(level, pos) || !(level.getBlockEntity(pos) instanceof SupervisionConsoleEntity part)
                    || !owner.equals(part.placedBy) || !part.getBlockState().is(block)
                    || part.getBlockState().getValue(SupervisionConsoleBlock.FACING) != face
                    || (!origin.equals(part.root) && !(allowUnassembled && part.root == null))) return false;
        }
        return true;
    }

    void dismantle(ServerLevel level, BlockPos removed) {
        if (isDisplay()) {
            var source = displayLevel(level);
            if (source != null) SupervisionData.get(source).removeDisplay(consoleId);
        } else SupervisionData.get(level).removeConsole(consoleId);
        var face = getBlockState().getValue(SupervisionConsoleBlock.FACING);
        var right = face.getCounterClockWise();
        var size = footprint;
        for (int row = size.bottom(); row <= size.top(); row++) for (int col = size.left(); col <= size.right(); col++) {
            BlockPos pos = worldPosition.above(row).relative(right, col);
            if (pos.equals(removed) || !TerminalLinks.loaded(level, pos)) continue;
            if (level.getBlockEntity(pos) instanceof SupervisionConsoleEntity part && worldPosition.equals(part.root)) {
                part.unassemble(level);
            }
        }
    }

    private void unassemble(ServerLevel level) {
        root = null;
        consoleId = null;
        sector = "";
        sectors = List.of();
        page = 0;
        view = NetworkView.EMPTY;
        footprint = initialFootprint();
        displayDimension = null;
        displayName = "";
        level.setBlock(worldPosition, getBlockState().setValue(SupervisionConsoleBlock.ROW, 0)
            .setValue(SupervisionConsoleBlock.COLUMN, 1), 3);
        sync();
    }

    void tick(ServerLevel level) {
        if (root == null || (level.getGameTime() + worldPosition.asLong()) % 20 != 0) return;
        if (!assembled()) {
            if (TerminalLinks.loaded(level, root) && resolve(level, worldPosition) == null) {
                unassemble(level);
            }
            return;
        }
        for (int c = footprint.left(); c <= footprint.right(); c++)
            if (!TerminalLinks.loaded(level, worldPosition.relative(getBlockState().getValue(SupervisionConsoleBlock.FACING).getCounterClockWise(), c))) return;
        if (placedBy == null || !complete(level, worldPosition, placedBy,
                getBlockState().getValue(SupervisionConsoleBlock.FACING), getBlockState().getBlock(), footprint, false)) {
            dismantle(level, null);
            return;
        }
        update(level);
    }

    SupervisionData.Console reference() {
        return new SupervisionData.Console(consoleId, worldPosition, new SupervisionData.Sector(placedBy, sector), sectors, canvas());
    }
    void update(ServerLevel level) {
        if (isDisplay()) {
            updateDisplay(level);
            return;
        }
        var data = SupervisionData.get(level);
        var saved = data.console(consoleId);
        boolean changed = false;
        if (saved == null || !saved.pos().equals(worldPosition) || !saved.sector().owner().equals(placedBy)) {
            saved = reference();
            data.register(saved);
        } else {
            if (!sectors.equals(saved.sectors()) || !sector.equals(saved.sector().name())) {
                sectors = saved.sectors();
                sector = saved.sector().name();
                changed = true;
            }
            if (!canvas().equals(saved.canvas())) {
                saved = reference();
                data.register(saved);
                changed = true;
            }
        }
        var next = data.view(level, saved);
        if (changed || !next.equals(view)) {
            view = next;
            sync();
        }
    }

    private ServerLevel displayLevel(ServerLevel local) {
        return displayDimension == null ? null : local.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, displayDimension));
    }

    void bindDisplay(ServerLevel level, PortableConsoleItem.Connection connection) {
        var oldLevel = displayLevel(level);
        var old = oldLevel == null ? null : SupervisionData.get(oldLevel).display(consoleId);
        if (oldLevel != null && oldLevel != connection.level()) SupervisionData.get(oldLevel).removeDisplay(consoleId);
        if (old == null || !old.source().equals(connection.console().id())) page = 0;
        displayDimension = connection.level().dimension().location();
        SupervisionData.get(connection.level()).bindDisplay(consoleId, connection.console(),
            old == null ? "" : old.name(), location(level));
        updateDisplay(level);
        sync();
    }

    private void updateDisplay(ServerLevel level) {
        var sourceLevel = displayLevel(level);
        displayName = "";
        String nextSector = "";
        List<String> nextSectors = List.of();
        NetworkView next = NetworkView.EMPTY.withCanvas(canvas());
        boolean lostLink = false;
        if (sourceLevel != null) {
            var data = SupervisionData.get(sourceLevel);
            var link = data.display(consoleId);
            if (link == null) {
                displayDimension = null;
                lostLink = true;
            } else {
                data.locateDisplay(consoleId, location(level));
                var source = PortableConsoleItem.resolveConsole(sourceLevel, placedBy, link.source());
                if (source != null) {
                    displayName = link.name();
                    nextSectors = source.sectors();
                    nextSector = nextSectors.contains(link.sector()) ? link.sector() : source.sector().name();
                    data.selectDisplaySector(consoleId, nextSector);
                    next = data.view(sourceLevel, source.showing(nextSector)).resized(canvas());
                }
            }
        }
        if (lostLink || !sector.equals(nextSector) || !sectors.equals(nextSectors) || !next.equals(view)) {
            if (!sector.equals(nextSector)) page = 0;
            sector = nextSector;
            sectors = nextSectors;
            view = next;
            sync();
        }
    }

    boolean displaysSourceIn(ServerLevel level) {
        return level.dimension().location().equals(displayDimension);
    }

    String displayName() { return displayName; }

    private SupervisionData.DisplayLocation location(ServerLevel level) {
        return new SupervisionData.DisplayLocation(level.dimension().location(), worldPosition);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (isDisplay() && assembled() && level instanceof ServerLevel local) {
            var source = displayLevel(local);
            if (source != null) SupervisionData.get(source).locateDisplay(consoleId, location(local));
        }
    }

    void addSector(ServerLevel level, String name) {
        if (name.isEmpty()) return;
        if (!sectors.contains(name)) {
            var names = new ArrayList<>(sectors);
            names.add(name);
            sectors = List.copyOf(names);
        }
        changeSector(level, name);
    }

    void removeSector(ServerLevel level, String name) {
        int index = sectors.indexOf(name);
        if (index < 0) return;
        var names = new ArrayList<>(sectors);
        names.remove(index);
        sectors = List.copyOf(names);
        changeSector(level, name.equals(sector) ? (sectors.isEmpty() ? "" : sectors.get(index % sectors.size())) : sector);
    }

    boolean selectSector(ServerLevel level, String name) {
        if (isDisplay()) {
            var source = displayLevel(level);
            if (source == null || !SupervisionData.get(source).selectDisplaySector(consoleId, name)) return false;
            updateDisplay(level);
            return true;
        }
        if (!sectors.contains(name)) return false;
        if (!sector.equals(name)) changeSector(level, name);
        return true;
    }

    void turnSector(ServerLevel level, int delta) {
        if (sectors.size() < 2) return;
        int index = sectors.indexOf(sector);
        changeSector(level, sectors.get(Math.floorMod(index + Integer.signum(delta), sectors.size())));
    }

    private void changeSector(ServerLevel level, String name) {
        if (!sector.equals(name)) page = 0;
        sector = name;
        SupervisionData.get(level).register(reference());
        update(level);
        sync();
    }
    void turnPage(int delta) {
        page = Math.floorMod(page + Integer.signum(delta), GraphLayout.pageCount(view));
        sync();
    }
    void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (placedBy != null) tag.putUUID("Owner", placedBy);
        if (consoleId != null) {
            tag.putUUID("Console", consoleId);
            canvas().write(tag);
            tag.putInt("LeftColumn", footprint.left());
            tag.putInt("BottomRow", footprint.bottom());
            if (displayDimension != null) tag.putString("DisplayDimension", displayDimension.toString());
        }
        if (root != null) tag.putLong("Root", root.asLong());
        tag.putString("Sector", sector);
        if (!sectors.isEmpty()) SupervisionData.writeSectorNames(tag, sectors);
        tag.putInt("Page", page);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        placedBy = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        consoleId = tag.hasUUID("Console") ? tag.getUUID("Console") : null;
        root = tag.contains("Root") ? BlockPos.of(tag.getLong("Root")) : null;
        var savedSize = GraphLayout.Canvas.read(tag);
        footprint = consoleId == null ? initialFootprint() : new Footprint(
            tag.contains("LeftColumn") ? tag.getInt("LeftColumn") : -1, tag.getInt("BottomRow"),
            new GraphLayout.Canvas(savedSize.columns(), savedSize.rows(), isDisplay()));
        String sourceDimension = tag.getString("DisplayDimension");
        displayDimension = isDisplay() && !sourceDimension.isEmpty() ? ResourceLocation.tryParse(sourceDimension) : null;
        sector = tag.getString("Sector");
        sectors = SupervisionData.readSectorNames(tag, sector);
        if (sector.isEmpty() && !sectors.isEmpty()) sector = sectors.getFirst();
        page = tag.getInt("Page");
        view = NetworkView.read(tag.getCompound("View"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        var tag = saveWithoutMetadata(provider);
        if (assembled()) tag.put("View", view.write(false));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
