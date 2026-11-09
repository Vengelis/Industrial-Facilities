package fr.vengelis.industrialfacilities.security;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public class SignalEmitterEntity extends BlockEntity {
    public UUID creator,instanceId=UUID.randomUUID();
    public SignalEmitterEntity(BlockPos pos,BlockState state){super(SecurityContent.EMITTER_TYPE,pos,state);}
    public void updatePower(){if(level instanceof ServerLevel server){boolean on=AutomationOutputs.active(server,worldPosition,"emitter",instanceId);BlockState state=getBlockState();if(state.getValue(SignalEmitterBlock.POWERED)!=on){level.setBlock(worldPosition,state.setValue(SignalEmitterBlock.POWERED,on),3);level.updateNeighborsAt(worldPosition,state.getBlock());level.updateNeighborsAt(worldPosition.relative(state.getValue(SignalEmitterBlock.FACING)),state.getBlock());}}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);if(creator!=null)tag.putUUID("Creator",creator);tag.putUUID("InstanceId",instanceId);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);creator=tag.hasUUID("Creator")?tag.getUUID("Creator"):null;instanceId=tag.hasUUID("InstanceId")?tag.getUUID("InstanceId"):UUID.randomUUID();}
}
