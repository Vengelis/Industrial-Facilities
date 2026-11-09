package fr.vengelis.industrialfacilities.access;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class AccessTerminalEntity extends BlockEntity {
    public UUID creator,terminalId,instanceId=UUID.randomUUID();
    public boolean encoder,bank;
    public int accessLevel=1;
    public long grantedUntil;
    /** Last server-observed state of the linked doors, synchronized to readers. */
    public boolean accessGranted;
    public AccessTerminalEntity(BlockPos pos,BlockState state){super(AccessContent.TYPE,pos,state);encoder=state.getBlock() instanceof AccessTerminalBlock b&&b.encoder;bank=state.getBlock() instanceof AccessTerminalBlock b&&b.bank;}
    public String code(){return terminalId==null?"":terminalId.toString();}
    public boolean isOwner(Player player){return creator!=null&&creator.equals(player.getUUID());}
    public static AccessTerminalEntity resolve(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos))return null;BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof AccessTerminalBlock)||!state.getValue(AccessTerminalBlock.ASSEMBLED))return null;
        BlockPos base=state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
        if(!level.hasChunkAt(base)||!level.hasChunkAt(base.above()))return null;
        if(!(level.getBlockEntity(base) instanceof AccessTerminalEntity be))return null;
        return AccessTerminalBlock.validPair(level,base)?be:null;
    }
    public boolean setAccessLevel(Player player,int level){if(!(getLevel() instanceof ServerLevel server)||encoder||bank||!isOwner(player)||level<1||level>5)return false;accessLevel=level;AccessRegistry.get(server.getServer()).put(this);sync();return true;}
    public int linkedCount(){if(!(getLevel() instanceof ServerLevel server)||terminalId==null)return 0;var e=AccessRegistry.get(server.getServer()).find(terminalId);return e==null?0:e.doors.size();}
    public boolean clearLinks(Player player){if(!(getLevel() instanceof ServerLevel server)||!isOwner(player)||terminalId==null)return false;AccessRegistry.get(server.getServer()).clearLinks(terminalId);accessGranted=false;sync();return true;}
    public UUID ensureCode(){if(terminalId==null){terminalId=UUID.randomUUID();sync();}if(getLevel() instanceof ServerLevel server)AccessRegistry.get(server.getServer()).put(this);return terminalId;}
    public void invalidate(){if(terminalId!=null&&getLevel() instanceof ServerLevel server)AccessRegistry.get(server.getServer()).remove(terminalId);terminalId=null;instanceId=UUID.randomUUID();grantedUntil=0;accessGranted=false;sync();}
    public void updateAccessState(){
        if(!(level instanceof ServerLevel))return;
        boolean next=AccessDoorService.readerGranted(this);
        if(accessGranted!=next){accessGranted=next;sync();}
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
        if(level instanceof ServerLevel&&(level.getGameTime()+pos.asLong())%20==0){
            if(state.getValue(AccessTerminalBlock.ASSEMBLED)&&level.hasChunkAt(state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below()))AccessTerminalBlock.checkStructure(level,pos,state);
            else if(!state.getValue(AccessTerminalBlock.ASSEMBLED)){AccessTerminalBlock.tryAssemble(level,pos);AccessTerminalBlock.tryAssemble(level,pos.below());}
        }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);if(creator!=null)tag.putUUID("Creator",creator);if(terminalId!=null)tag.putUUID("TerminalId",terminalId);tag.putUUID("InstanceId",instanceId);tag.putInt("AccessLevel",accessLevel);tag.putLong("GrantedUntil",grantedUntil);tag.putBoolean("AccessGranted",accessGranted);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);creator=tag.hasUUID("Creator")?tag.getUUID("Creator"):null;terminalId=tag.hasUUID("TerminalId")?tag.getUUID("TerminalId"):null;instanceId=tag.hasUUID("InstanceId")?tag.getUUID("InstanceId"):UUID.randomUUID();accessLevel=Math.clamp(tag.getInt("AccessLevel"),1,5);grantedUntil=tag.getLong("GrantedUntil");accessGranted=tag.getBoolean("AccessGranted");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){return saveWithoutMetadata(provider);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
