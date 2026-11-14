package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final float ANIMATION_STEP = 1f / 40;

    // Le client installe ce callback. Le serveur dédié peut charger cette classe sans les classes audio.
    public static Consumer<IndustrialDoorEntity> clientSoundTick = door -> {};

    // owner pointe vers le contrôleur. origin désigne le coin inférieur du cadre.
    public BlockPos owner;
    public BlockPos origin;
    public int size;
    public UUID doorInstanceId;
    public UUID placedBy;
    public boolean allowLinkedRedstone;
    public boolean allowCameraAutomation;
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
        Direction facing = controller.getBlockState().getValue(IndustrialDoorBlock.FACING);
        Direction across = facing.getCounterClockWise();
        BlockPos base = controller.doorOrigin();
        int dx = worldPosition.getX() - base.getX();
        int dz = worldPosition.getZ() - base.getZ();

        // Projeter le décalage sur le plan du cadre, puis vérifier contour et intérieur.
        int depth = dx * facing.getStepX() + dz * facing.getStepZ();
        int column = dx * across.getStepX() + dz * across.getStepZ();
        int row = worldPosition.getY() - base.getY();
        boolean isFrameBlock = getBlockState().getBlock() instanceof IndustrialDoorBlock;
        return depth == 0 && column >= 0 && column < controller.size && row >= 0 && row < controller.size
            && IndustrialDoorManager.edge(column, row, controller.size) == isFrameBlock;
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
        tag.putLong("AccessOpenUntil", accessOpenUntil);

        if (owner != null) {
            tag.putLong("Owner", owner.asLong());
        }
        if (size > 0) {
            tag.putLong("Origin", doorOrigin().asLong());
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
        accessOpenUntil = tag.getLong("AccessOpenUntil");

        owner = tag.contains("Owner") ? BlockPos.of(tag.getLong("Owner")) : null;
        origin = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : null;
        size = tag.getInt("Size");
        if (size < IndustrialDoorManager.MIN_FRAME_SIZE || size > IndustrialDoorManager.MAX_FRAME_SIZE) {
            size = 0;
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
            if (!door.paused) {
                door.progress = advance(door.progress, door.targetOpen);
            }
            clientSoundTick.accept(door);
            return;
        }
        IndustrialDoorManager.tick((ServerLevel) level, door);
    }

    public static float advance(float progress, boolean open) {
        float next = Math.clamp(progress + (open ? ANIMATION_STEP : -ANIMATION_STEP), 0, 1);
        // Ramener les valeurs proches des extrémités à 0 ou 1 malgré les arrondis du flottant.
        if (next > .99999f) {
            return 1;
        }
        return next < .00001f ? 0 : next;
    }
}
