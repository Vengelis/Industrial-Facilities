package fr.vengelis.industrialfacilities.door;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public class IndustrialDoorEntity extends BlockEntity {
    // Client-only audio is installed through a neutral hook; dedicated servers never load client classes.
    public static java.util.function.Consumer<IndustrialDoorEntity> clientSoundTick=door->{};
    public BlockPos owner;
    public BlockPos origin;
    public BlockPos doorOrigin() { return origin!=null ? origin : getBlockPos(); }
    public int size;
    public java.util.UUID doorInstanceId;
    public boolean allowLinkedRedstone;
    public boolean allowCameraAutomation;
    public java.util.UUID automationOwner;
    public long accessOpenUntil;
    public float progress, previousProgress;
    public boolean targetOpen, tearingDown, paused;
    public IndustrialDoorEntity(BlockPos p,BlockState s) { super(IndustrialDoorContent.TYPE,p,s); }
    @Override public void onLoad() {
        super.onLoad();
        if(level instanceof ServerLevel server && size==0 && owner!=null)
            fr.vengelis.industrialfacilities.IndustrialFacilitiesMod.queueServerWork(20,()->{
                if(server.getBlockEntity(worldPosition)==this)cleanupOrphan(server);
            });
    }
    public void cleanupOrphan(ServerLevel server) {
        if(size>0 || owner==null || !server.hasChunkAt(owner))return;
        if(server.getBlockEntity(owner) instanceof IndustrialDoorEntity d && d.size>0) {
            Direction f=d.getBlockState().getValue(IndustrialDoorBlock.FACING),r=f.getCounterClockWise();
            BlockPos base=d.doorOrigin();
            int dx=worldPosition.getX()-base.getX(),dz=worldPosition.getZ()-base.getZ();
            int x=dx*r.getStepX()+dz*r.getStepZ(),y=worldPosition.getY()-base.getY();
            if(dx*f.getStepX()+dz*f.getStepZ()==0 && x>=0 && x<d.size && y>=0 && y<d.size
                && IndustrialDoorManager.edge(x,y,d.size)==(getBlockState().getBlock() instanceof IndustrialDoorBlock))return;
        }
        owner=null;
        if(getBlockState().getBlock() instanceof IndustrialDoorBlock)
            server.setBlock(worldPosition,getBlockState().setValue(IndustrialDoorBlock.ACTIVE,false).setValue(IndustrialDoorBlock.OPEN,false).setValue(IndustrialDoorBlock.RAIL,false),3);
        else server.removeBlock(worldPosition,false);
        sync();
    }
    public void sync() {
        setChanged();
        if(level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider) {
        super.saveAdditional(tag,provider);
        if(doorInstanceId!=null)tag.putUUID("DoorInstanceId",doorInstanceId);
        if(automationOwner!=null)tag.putUUID("AutomationOwner",automationOwner);tag.putBoolean("AllowCameraAutomation",allowCameraAutomation);
        tag.putBoolean("AllowLinkedRedstone",allowLinkedRedstone);tag.putLong("AccessOpenUntil",accessOpenUntil);
        if(owner!=null)tag.putLong("Owner",owner.asLong());
        if(size>0)tag.putLong("Origin",doorOrigin().asLong());
        tag.putInt("Size",size);tag.putFloat("Progress",progress);tag.putBoolean("TargetOpen",targetOpen);tag.putBoolean("Paused",paused);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider) {
        super.loadAdditional(tag,provider);
        doorInstanceId=tag.hasUUID("DoorInstanceId")?tag.getUUID("DoorInstanceId"):null;
        automationOwner=tag.hasUUID("AutomationOwner")?tag.getUUID("AutomationOwner"):null;allowCameraAutomation=tag.getBoolean("AllowCameraAutomation");
        allowLinkedRedstone=tag.getBoolean("AllowLinkedRedstone");accessOpenUntil=tag.getLong("AccessOpenUntil");
        owner=tag.contains("Owner")?BlockPos.of(tag.getLong("Owner")):null;
        origin=tag.contains("Origin")?BlockPos.of(tag.getLong("Origin")):null;
        size=tag.getInt("Size");if(size<5 || size>15)size=0;
        progress=Math.clamp(tag.getFloat("Progress"),0,1);previousProgress=progress;
        targetOpen=tag.getBoolean("TargetOpen");paused=tag.getBoolean("Paused");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider p) { return saveWithoutMetadata(p); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public static void tick(Level l,BlockPos pos,BlockState state,IndustrialDoorEntity be) {
        be.previousProgress=be.progress;
        if(be.size==0){
            if(l instanceof ServerLevel server && (l.getGameTime()+pos.asLong())%20==0)be.cleanupOrphan(server);
            return;
        }
        if(l.isClientSide) {
            if(!be.paused)be.progress=advance(be.progress,be.targetOpen);
            clientSoundTick.accept(be);
            return;
        }
        IndustrialDoorManager.tick((ServerLevel)l,be);
    }
    public static float advance(float p,boolean open) {
        float next=Math.clamp(p+(open ? 1f/40 : -1f/40),0,1);
        return next>.99999f?1:next<.00001f?0:next;
    }
}
