package fr.vengelis.industrialfacilities.energybarrier;

import fr.vengelis.industrialfacilities.block.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModBlocks;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded, stateless reconstruction: no global world cache and no forced chunk loads. */
public final class EnergyBarrierManager {
    public static final int MAX_WIDTH=5,MAX_HEIGHT=4;
    public record Structure(BlockPos anchor,Direction along,int width,int height) {
        public BlockPos at(int column,int row){return anchor.relative(along,column).above(row);}
        public Direction.Axis axis(){return along.getAxis();}
        public BlockState field(int column,int row){return IndustrialFacilitiesModBlocks.ENERGY_BARRIER_FIELD.get().defaultBlockState()
            .setValue(EnergyBarrierFieldBlock.AXIS,axis()).setValue(EnergyBarrierFieldBlock.COLUMN,column).setValue(EnergyBarrierFieldBlock.ROW,row);}
    }
    private static boolean base(BlockState s){return s.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_BASE.get());}
    private static boolean pillar(BlockState s){return s.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_PILLAR.get());}
    private static boolean field(BlockState s){return s.is(IndustrialFacilitiesModBlocks.ENERGY_BARRIER_FIELD.get());}
    public static BlockPos owner(BlockPos p,BlockState state){
        Direction along=state.getValue(EnergyBarrierFieldBlock.AXIS)==Direction.Axis.X?Direction.EAST:Direction.SOUTH;
        return p.below(state.getValue(EnergyBarrierFieldBlock.ROW)).relative(along,-state.getValue(EnergyBarrierFieldBlock.COLUMN));
    }
    /** Conservative load check, including one extra column level for rejecting >4-high frames. */
    private static boolean loaded(ServerLevel level,BlockPos p,Direction along){
        for(int x=-6;x<=6;x++)for(int y=0;y<=5;y++)if(!level.hasChunkAt(p.relative(along,x).above(y)))return false;
        return true;
    }
    public static Structure detect(ServerLevel level,BlockPos p,Direction along){
        if(!loaded(level,p,along)||!base(level.getBlockState(p)))return null;
        BlockPos left=p;
        int count=1;
        while(base(level.getBlockState(left.relative(along.getOpposite())))){
            if(++count>MAX_WIDTH)return null;
            left=left.relative(along.getOpposite());
        }
        if(!pillar(level.getBlockState(left.relative(along.getOpposite()))))return null;
        int width=0;
        while(base(level.getBlockState(left.relative(along,width)))){
            if(++width>MAX_WIDTH)return null;
        }
        BlockPos l=left.relative(along.getOpposite()),r=left.relative(along,width);
        if(!pillar(level.getBlockState(r)))return null;
        int a=0,b=0;
        while(a<=MAX_HEIGHT&&pillar(level.getBlockState(l.above(a+1))))a++;
        while(b<=MAX_HEIGHT&&pillar(level.getBlockState(r.above(b+1))))b++;
        if(a<1||a>MAX_HEIGHT||a!=b||left.getY()+a>=level.getMaxBuildHeight())return null;
        return new Structure(left.immutable(),along,width,a);
    }
    public static boolean powered(ServerLevel level,Structure s){
        for(int x=0;x<s.width;x++)if(level.hasNeighborSignal(s.at(x,0)))return true;
        for(int y=0;y<=s.height;y++)if(level.hasNeighborSignal(s.at(-1,y))||level.hasNeighborSignal(s.at(s.width,y)))return true;
        return false;
    }
    public static boolean clear(ServerLevel level,Structure s){
        for(int x=0;x<s.width;x++)for(int y=1;y<=s.height;y++){
            BlockPos p=s.at(x,y);BlockState state=level.getBlockState(p);
            // Never replace fluids, vegetation, blocks, or a neighboring structure's field.
            if(!state.isAir()&&(!field(state)||!owner(p,state).equals(s.anchor)||state.getValue(EnergyBarrierFieldBlock.AXIS)!=s.axis()))return false;
        }
        return true;
    }
    private static void clearOwned(ServerLevel level,BlockPos anchor,Direction along,Structure keep){
        for(int x=0;x<MAX_WIDTH;x++)for(int y=1;y<=MAX_HEIGHT;y++){
            BlockPos p=anchor.relative(along,x).above(y);if(!level.hasChunkAt(p))continue;
            BlockState state=level.getBlockState(p);
            if(field(state)&&owner(p,state).equals(anchor)&&state.getValue(EnergyBarrierFieldBlock.AXIS)==along.getAxis()
                &&(keep==null||x>=keep.width||y>keep.height))level.removeBlock(p,false);
        }
    }
    private static void setActive(ServerLevel level,Structure s,boolean active){
        for(int x=0;x<s.width;x++){
            BlockPos p=s.at(x,0);BlockState before=level.getBlockState(p);
            BlockState after=before.setValue(EnergyBarrierBaseBlock.ACTIVE,active).setValue(EnergyBarrierBaseBlock.AXIS,s.axis());
            after=EnergyBarrierBaseBlock.connected(after,level,p);
            if(before!=after)level.setBlock(p,after,3);
        }
    }
    public static void update(ServerLevel level,BlockPos p){
        boolean found=false;
        for(Direction along:new Direction[]{Direction.EAST,Direction.SOUTH}){
            if(!loaded(level,p,along))continue;
            Structure s=detect(level,p,along);
            if(s==null){clearOwned(level,p,along,null);continue;}
            found=true;
            if(!s.anchor.equals(p))continue;
            boolean active=powered(level,s)&&clear(level,s);
            clearOwned(level,p,along,active?s:null);
            setActive(level,s,active);
            if(active)for(int x=0;x<s.width;x++)for(int y=1;y<=s.height;y++){
                BlockPos cell=s.at(x,y);BlockState desired=s.field(x,y);
                if(level.getBlockState(cell)!=desired)level.setBlock(cell,desired,3);
            }
        }
        if(!found&&base(level.getBlockState(p))&&level.getBlockState(p).getValue(EnergyBarrierBaseBlock.ACTIVE))
            level.setBlock(p,level.getBlockState(p).setValue(EnergyBarrierBaseBlock.ACTIVE,false),3);
    }
    /** Also cleans up orphan fields after the anchor itself was destroyed or after a restart. */
    public static void checkField(ServerLevel level,BlockPos p,BlockState state){
        BlockPos anchor=owner(p,state);
        Direction along=state.getValue(EnergyBarrierFieldBlock.AXIS)==Direction.Axis.X?Direction.EAST:Direction.SOUTH;
        if(!loaded(level,anchor,along))return;
        Structure s=detect(level,anchor,along);
        if(s==null||!s.anchor.equals(anchor)||state.getValue(EnergyBarrierFieldBlock.COLUMN)>=s.width
            ||state.getValue(EnergyBarrierFieldBlock.ROW)>s.height||!powered(level,s)||!clear(level,s))level.removeBlock(p,false);
    }
    private EnergyBarrierManager(){}
}
