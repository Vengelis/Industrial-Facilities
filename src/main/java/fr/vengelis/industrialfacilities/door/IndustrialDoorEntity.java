package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.function.Consumer;

/** État du contrôleur et référence au contrôleur pour chaque bloc de la porte assemblée. */
public class IndustrialDoorEntity extends BlockEntity {
    private static final int ORPHAN_CHECK_INTERVAL = 20;

    // Le client installe ce callback. Le serveur dédié peut charger cette classe sans les classes audio.
    public static Consumer<IndustrialDoorEntity> clientSoundTick = door -> {};

    // owner pointe vers le contrôleur. origin désigne le coin inférieur du cadre.
    public BlockPos owner;
    public BlockPos origin;
    // Width of the assembled frame. The legacy NBT key for it is "Size".
    public int size;
    private int height;
    public UUID doorInstanceId;
    public UUID placedBy;
    public boolean allowLinkedRedstone;
    public boolean allowCameraAutomation;
    public boolean halfOpening;
    public int irisOpeningPercent = 100;
    public UUID automationOwner;
    public long accessOpenUntil;
    public float progress;
    public float previousProgress;
    public boolean targetOpen;
    public boolean tearingDown;
    public boolean paused;

    public IndustrialDoorEntity(BlockPos pos, BlockState state) {
        super(IndustrialDoorContent.TYPE, pos, state);
    }

    public BlockPos doorOrigin() {
        // Les premières sauvegardes prenaient le contrôleur comme coin de référence.
        return origin != null ? origin : getBlockPos();
    }

    public int height() {
        return height == 0 ? size : height;
    }

    public IndustrialDoorPlane plane() {
        return IndustrialDoorBlock.plane(getBlockState());
    }

    void setDimensions(int width, int height) {
        this.size = width;
        this.height = height;
    }

    public boolean lifts() {
        return getBlockState().getBlock() instanceof IndustrialDoorBlock block && block.style.lifts();
    }

    public IndustrialDoorStyle style() {
        return getBlockState().getBlock() instanceof IndustrialDoorBlock block ? block.style : IndustrialDoorStyle.LIGHT;
    }

    public int motionTicks(boolean opening) {
        return DoorMotionConfig.ticks(style(), false, opening);
    }

    public float openLimit() {
        if (style() == IndustrialDoorStyle.IRIS) return irisOpeningPercent / 100f;
        return halfOpening && style() == IndustrialDoorStyle.MESH ? .5f : 1f;
    }

    public boolean isAtOpenLimit() {
        return progress > 0 && progress == openLimit();
    }

    public float targetProgress() {
        return targetOpen ? openLimit() : 0f;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && size == 0 && owner != null) {
            IndustrialFacilitiesMod.queueServerWork(ORPHAN_CHECK_INTERVAL, () -> {
                if (server.getBlockEntity(worldPosition) == this) {
                    cleanupOrphan(server);
                }
            });
        }
    }

    public void cleanupOrphan(ServerLevel server) {
        if (size > 0 || owner == null || !server.hasChunkAt(owner)) {
            return;
        }
        if (server.getBlockEntity(owner) instanceof IndustrialDoorEntity controller
                && controller.size > 0 && fitsFrame(controller)) {
            return;
        }

        // Rendre les blocs de cadre réutilisables et retirer les pièces générées dans le passage.
        owner = null;
        if (getBlockState().getBlock() instanceof IndustrialDoorBlock) {
            server.setBlock(worldPosition, getBlockState()
                .setValue(IndustrialDoorBlock.ACTIVE, false)
                .setValue(IndustrialDoorBlock.OPEN, false)
                .setValue(IndustrialDoorBlock.RAIL, false), 3);
        } else {
            server.removeBlock(worldPosition, false);
        }
        sync();
    }

    private boolean fitsFrame(IndustrialDoorEntity controller) {
        IndustrialDoorPlane plane = controller.plane();
        BlockPos base = controller.doorOrigin();

        // Projeter le décalage sur le plan du cadre, puis vérifier contour et intérieur.
        int depth = IndustrialDoorPlane.offset(base, worldPosition, plane.facing());
        int column = plane.column(base, worldPosition);
        int row = plane.row(base, worldPosition);
        boolean isFrameBlock = getBlockState().getBlock() instanceof IndustrialDoorBlock;
        return depth == 0 && column >= 0 && column < controller.size && row >= 0 && row < controller.height()
            && IndustrialDoorManager.edge(column, row, controller.size, controller.height()) == isFrameBlock;
    }

    public void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (doorInstanceId != null) {
            tag.putUUID("DoorInstanceId", doorInstanceId);
        }
        if (placedBy != null) {
            tag.putUUID("PlacedBy", placedBy);
        }
        if (automationOwner != null) {
            tag.putUUID("AutomationOwner", automationOwner);
        }
        tag.putBoolean("AllowCameraAutomation", allowCameraAutomation);
        tag.putBoolean("AllowLinkedRedstone", allowLinkedRedstone);
        tag.putBoolean("HalfOpening", halfOpening);
        if (style() == IndustrialDoorStyle.IRIS) tag.putInt("IrisOpeningPercent", irisOpeningPercent);
        tag.putLong("AccessOpenUntil", accessOpenUntil);

        if (owner != null) {
            tag.putLong("Owner", owner.asLong());
        }
        if (size > 0) {
            tag.putLong("Origin", doorOrigin().asLong());
            tag.putInt("Height", height());
        }
        tag.putInt("Size", size);
        tag.putFloat("Progress", progress);
        tag.putBoolean("TargetOpen", targetOpen);
        tag.putBoolean("Paused", paused);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        doorInstanceId = tag.hasUUID("DoorInstanceId") ? tag.getUUID("DoorInstanceId") : null;
        placedBy = tag.hasUUID("PlacedBy") ? tag.getUUID("PlacedBy") : null;
        automationOwner = tag.hasUUID("AutomationOwner") ? tag.getUUID("AutomationOwner") : null;
        allowCameraAutomation = tag.getBoolean("AllowCameraAutomation");
        allowLinkedRedstone = tag.getBoolean("AllowLinkedRedstone");
        halfOpening = tag.getBoolean("HalfOpening");
        irisOpeningPercent = tag.contains("IrisOpeningPercent") ? Math.clamp(tag.getInt("IrisOpeningPercent"), 0, 100) : 100;
        accessOpenUntil = tag.getLong("AccessOpenUntil");

        owner = tag.contains("Owner") ? BlockPos.of(tag.getLong("Owner")) : null;
        origin = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : null;
        int width = tag.getInt("Size");
        int savedHeight = tag.contains("Height") ? tag.getInt("Height") : width;
        if (IndustrialDoorManager.isSavedFrameSizeValid(style(), width, savedHeight)) {
            setDimensions(width, savedHeight);
        } else {
            setDimensions(0, 0);
        }
        progress = Math.clamp(tag.getFloat("Progress"), 0, 1);
        previousProgress = progress;
        targetOpen = tag.getBoolean("TargetOpen");
        paused = tag.getBoolean("Paused");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IndustrialDoorEntity door) {
        door.previousProgress = door.progress;
        if (door.size == 0) {
            // Décaler les vérifications entre les blocs pour éviter de traiter tout le cadre au même tick.
            if (level instanceof ServerLevel server && (level.getGameTime() + pos.asLong()) % ORPHAN_CHECK_INTERVAL == 0) {
                door.cleanupOrphan(server);
            }
            return;
        }
        if (level.isClientSide) {
            clientSoundTick.accept(door);
            if (!door.paused) {
                float target = door.targetProgress();
                door.progress = advance(door.progress, target, door.motionTicks(target > door.progress));
            }
            return;
        }
        IndustrialDoorManager.tick((ServerLevel) level, door);
    }

    public static float advance(float progress, boolean open) {
        return advance(progress, open, DoorMotionConfig.DEFAULT_TICKS);
    }

    public static float advance(float progress, boolean open, int ticks) {
        return advance(progress, open ? 1f : 0f, ticks);
    }

    public static float advance(float progress, float target, int ticks) {
        float remaining = target - progress;
        if (ticks == 0 || Math.abs(remaining) <= 1f / ticks + .0001f) return target;
        return progress + Math.copySign(1f / ticks, remaining);
    }
}
