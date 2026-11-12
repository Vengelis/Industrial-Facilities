package fr.vengelis.industrialfacilities.access;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class KeypassTerminalEntity extends AccessTerminalEntity {
    private int failures;
    private long nextAttempt,lockedUntil;
    public KeypassTerminalEntity(BlockPos pos,BlockState state){super(pos,state);}
    public boolean manager(){return encoder;}
    String checkPin(ServerLevel server,String pin){
        long tick=server.getGameTime();
        if(tick<lockedUntil)return "locked";
        if(tick<nextAttempt)return "slow_down";
        if(!KeypassData.validPin(pin))return "invalid_pin";
        nextAttempt=tick+10;
        var result=KeypassData.get(server).verify(this,pin);
        if(result==KeypassData.Verification.UNSET)return "not_configured";
        if(result==KeypassData.Verification.DENIED){
            ++failures;setChanged();
            if(failures>=5){failures=0;lockedUntil=tick+600;return "locked";}
            return "denied";
        }
        if(failures!=0){failures=0;setChanged();}return "";
    }
    @Override public void invalidate(){
        if(level instanceof ServerLevel server)KeypassData.get(server).removeDevice(instanceId);
        failures=0;nextAttempt=lockedUntil=0;
        super.invalidate();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("PinFailures",failures);tag.putLong("PinLockedUntil",lockedUntil);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);failures=Math.clamp(tag.getInt("PinFailures"),0,4);lockedUntil=Math.max(0,tag.getLong("PinLockedUntil"));nextAttempt=0;
    }
}
