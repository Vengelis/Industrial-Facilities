package fr.vengelis.industrialfacilities.access;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class AccessTerminalEntity extends BlockEntity {
    public static final int DEFAULT_OPEN_SECONDS = 5, MAX_OPEN_SECONDS = 300;
    private int openSeconds = DEFAULT_OPEN_SECONDS;
    public UUID creator,terminalId,instanceId=UUID.randomUUID();
    public boolean encoder,bank;
    public int accessLevel=1;
    private long screenUntil;
    /** Last server-observed state of the linked doors, synchronized to readers. */
    public boolean accessGranted;
    private boolean clientLockdownDisabled;
    public AccessTerminalEntity(BlockPos pos,BlockState state){super(AccessContent.TYPE,pos,state);encoder=state.getBlock() instanceof AccessTerminalBlock b&&b.encoder;bank=state.getBlock() instanceof AccessTerminalBlock b&&b.bank;}
    public String code(){return terminalId==null?"":terminalId.toString();}
    public boolean isOwner(Player player){return creator!=null&&creator.equals(player.getUUID());}
    public boolean isLockedDown() {
        if (level instanceof ServerLevel server) return TerminalControlData.get(server).locked(instanceId);
        return clientLockdownDisabled;
    }
    public boolean canInteract(Player player) {
        if (!isLockedDown()) return true;
        player.displayClientMessage(Component.translatable(ControlTerminalBlock.UI + "locked"), true);
        return false;
    }
    public static AccessTerminalEntity resolve(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos))return null;BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof AccessTerminalBlock)||!state.getValue(AccessTerminalBlock.ASSEMBLED))return null;
        BlockPos base=state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
        if(!level.hasChunkAt(base)||!level.hasChunkAt(base.above()))return null;
        if(!(level.getBlockEntity(base) instanceof AccessTerminalEntity be))return null;
        return AccessTerminalBlock.validPair(level,base)?be:null;
    }
    public boolean setAccessLevel(Player player,int level){if(!(getLevel() instanceof ServerLevel server)||encoder||bank||!isOwner(player)||level<1||level>5)return false;accessLevel=level;AccessRegistry.get(server.getServer()).put(this);sync();return true;}
    public int openSeconds() { return openSeconds; }
    public boolean setOpenSeconds(Player player, int seconds) {
        if (!(level instanceof ServerLevel) || encoder || bank || !isOwner(player) || isLockedDown()
            || seconds < 1 || seconds > MAX_OPEN_SECONDS) return false;
        openSeconds = seconds;
        sync();
        return true;
    }
    public int linkedCount(){if(!(getLevel() instanceof ServerLevel server)||terminalId==null)return 0;var e=AccessRegistry.get(server.getServer()).find(terminalId);return e==null?0:AccessDoorService.doors(server,e).size();}
    public boolean clearLinks(Player player){if(!(getLevel() instanceof ServerLevel server)||!isOwner(player)||terminalId==null)return false;AccessRegistry.get(server.getServer()).clearLinks(terminalId);screenUntil=0;accessGranted=false;sync();return true;}
    public UUID ensureCode(){if(terminalId==null){terminalId=UUID.randomUUID();sync();}if(getLevel() instanceof ServerLevel server)AccessRegistry.get(server.getServer()).put(this);return terminalId;}
    public void invalidate(){if(getLevel() instanceof ServerLevel server){
        fr.vengelis.industrialfacilities.frequency.FrequencyNetwork.remove(server, instanceId, worldPosition);
        if(terminalId!=null)AccessRegistry.get(server.getServer()).remove(terminalId);
        TerminalControlData.get(server).remove(server,instanceId);
        fr.vengelis.industrialfacilities.supervision.SupervisionData.get(server).removeTerminal(instanceId);
    }terminalId=null;instanceId=UUID.randomUUID();screenUntil=0;accessGranted=false;sync();}
    public long screenUntil() { return screenUntil; }

    protected final void updateScreenState(boolean granted, long until) {
        if (accessGranted == granted && screenUntil == until) return;
        accessGranted = granted;
        screenUntil = until;
        sync();
    }

    public void updateAccessState(){
        if(!(level instanceof ServerLevel))return;
        var screen = AccessDoorService.readerScreen(this);
        updateScreenState(screen.granted(), screen.until());
    }
    public void sync(){
        setChanged();
        syncReaderMaterialState();
        if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    /** Minecraft lighting is scalar; this state exposes the red/green choice to
     * shader packs without making them inspect block-entity NBT. */
    private void syncReaderMaterialState(){
        if(!(level instanceof ServerLevel)||!getBlockState().hasProperty(AccessTerminalBlock.ACCESS_GRANTED))return;
        BlockState state=getBlockState();
        if(state.getValue(AccessTerminalBlock.HALF)!=DoubleBlockHalf.LOWER)return;
        boolean granted=!encoder&&!bank&&state.getValue(AccessTerminalBlock.ASSEMBLED)&&accessGranted;
        for(BlockPos pos:new BlockPos[]{worldPosition,worldPosition.above()}){
            if(!level.hasChunkAt(pos))continue;
            BlockState part=level.getBlockState(pos);
            if(part.is(state.getBlock())&&part.hasProperty(AccessTerminalBlock.ACCESS_GRANTED)&&part.getValue(AccessTerminalBlock.ACCESS_GRANTED)!=granted)
                level.setBlock(pos,part.setValue(AccessTerminalBlock.ACCESS_GRANTED,granted),3);
        }
    }
    @Override public void onLoad(){super.onLoad();syncReaderMaterialState();if(level instanceof ServerLevel server&&terminalId!=null&&getBlockState().getValue(AccessTerminalBlock.ASSEMBLED)&&getBlockState().getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.LOWER)AccessRegistry.get(server.getServer()).put(this);}
    public static void tick(Level level,BlockPos pos,BlockState state,AccessTerminalEntity be){
        if(level instanceof ServerLevel&&state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.LOWER){be.updateAccessState();be.syncReaderMaterialState();}
        if(level instanceof ServerLevel server && state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.LOWER
                && state.getValue(AccessTerminalBlock.ASSEMBLED) && (level.getGameTime()+pos.asLong())%20==0)
            fr.vengelis.industrialfacilities.supervision.SupervisionData.get(server).refresh(server,be);
        if(level instanceof ServerLevel&&(level.getGameTime()+pos.asLong())%20==0){
            if(state.getValue(AccessTerminalBlock.ASSEMBLED)&&level.hasChunkAt(state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below()))AccessTerminalBlock.checkStructure(level,pos,state);
            else if(!state.getValue(AccessTerminalBlock.ASSEMBLED)){AccessTerminalBlock.tryAssemble(level,pos);AccessTerminalBlock.tryAssemble(level,pos.below());}
        }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);if(creator!=null)tag.putUUID("Creator",creator);if(terminalId!=null)tag.putUUID("TerminalId",terminalId);tag.putUUID("InstanceId",instanceId);tag.putInt("AccessLevel",accessLevel);tag.putInt("OpenSeconds",openSeconds);tag.putBoolean("AccessGranted",accessGranted);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);creator=tag.hasUUID("Creator")?tag.getUUID("Creator"):null;terminalId=tag.hasUUID("TerminalId")?tag.getUUID("TerminalId"):null;instanceId=tag.hasUUID("InstanceId")?tag.getUUID("InstanceId"):UUID.randomUUID();accessLevel=Math.clamp(tag.getInt("AccessLevel"),1,5);openSeconds=tag.contains("OpenSeconds")?Math.clamp(tag.getInt("OpenSeconds"),1,MAX_OPEN_SECONDS):DEFAULT_OPEN_SECONDS;screenUntil=tag.getLong("ScreenUntil");accessGranted=tag.getBoolean("AccessGranted");clientLockdownDisabled=tag.getBoolean("LockdownDisabled");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){
        var tag = withLockdownStatus(saveWithoutMetadata(provider));
        if (screenUntil > 0) tag.putLong("ScreenUntil", screenUntil);
        return tag;
    }
    protected final CompoundTag withLockdownStatus(CompoundTag tag){
        tag.putBoolean("LockdownDisabled",isLockedDown());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
