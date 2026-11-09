package fr.vengelis.industrialfacilities.security;
import java.util.*;
import fr.vengelis.industrialfacilities.access.AccessBadges;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.BlockGetter;
public class SecurityCameraEntity extends BlockEntity {
    public static final double MODEL_SCALE=.85,LENS_DISTANCE=.46*MODEL_SCALE;
    public static final int MIN_SWEEP_TICKS=40,MAX_SWEEP_TICKS=2400;
    public UUID creator,instanceId=UUID.randomUUID();
    public int range=16;
    public float yaw,pitch;
    public boolean sweepEnabled=true;
    public float sweepAngle=30;
    public int sweepPeriodTicks=160;
    public long sweepEpoch;
    private AABB boundsCache;
    private int boundsRange;
    private float boundsYaw,boundsPitch,boundsAngle;
    private boolean boundsSweep;
    private Direction boundsFacing;
    public SecurityCameraEntity(BlockPos pos,BlockState state){super(SecurityContent.CAMERA_TYPE,pos,state);}
    public AccessBadges.Result configure(ServerPlayer player,int range,float yaw,float pitch){
        return configure(player,range,yaw,pitch,false,sweepAngle,sweepPeriodTicks);
    }
    public AccessBadges.Result configure(ServerPlayer player,int range,float yaw,float pitch,boolean enabled,float angle,int periodTicks){
        if(!canConfigure(player)||range<1||range>16||!Float.isFinite(yaw)||!Float.isFinite(pitch)||yaw< -180||yaw>180||pitch< -80||pitch>80
            ||!Float.isFinite(angle)||angle<0||angle>90||periodTicks<MIN_SWEEP_TICKS||periodTicks>MAX_SWEEP_TICKS)
            return AccessBadges.Result.fail("gui.industrial_facilities.camera.configure_denied");
        this.range=range;this.yaw=yaw;this.pitch=pitch;sweepEnabled=enabled;sweepAngle=angle;sweepPeriodTicks=periodTicks;sweepEpoch=level.getGameTime();
        sync();return AccessBadges.Result.success("gui.industrial_facilities.camera.saved");
    }
    public boolean canConfigure(ServerPlayer player){return creator!=null&&creator.equals(player.getUUID())&&player.isAlive()&&level==player.level()&&player.distanceToSqr(worldPosition.getCenter())<=64&&!isRemoved();}
    public static float sweepOffsetAt(double elapsedTicks,boolean enabled,float angle,int periodTicks){
        if(!enabled||!Float.isFinite(angle)||angle<=0||periodTicks<MIN_SWEEP_TICKS)return 0;
        return (float)(Math.sin(elapsedTicks*2*Math.PI/periodTicks)*Math.min(90,angle));
    }
    public float sweepOffset(float partial){return sweepOffsetAt((level==null?0:level.getGameTime())+(double)partial-sweepEpoch,sweepEnabled,sweepAngle,sweepPeriodTicks);}
    public float effectiveYaw(float partial){return yaw+sweepOffset(partial);}
    public Vec3 direction(){return direction(0);}
    public Vec3 direction(float partial){return directionAt(effectiveYaw(partial));}
    private Vec3 directionAt(float aim){
        Direction facing=getBlockState().getValue(SecurityCameraBlock.FACING);double a=Math.toRadians(aim),p=Math.toRadians(pitch),x=facing.getStepX(),z=facing.getStepZ();
        return new Vec3((x*Math.cos(a)-z*Math.sin(a))*Math.cos(p),-Math.sin(p),(x*Math.sin(a)+z*Math.cos(a))*Math.cos(p)).normalize();
    }
    public Vec3 lens(){return Vec3.atCenterOf(worldPosition).add(direction().scale(LENS_DISTANCE));}
    /** Conservative broad phase for the whole configured sweep; visibility still uses the instantaneous head direction. */
    public AABB fieldBounds(){
        Direction facing=getBlockState().getValue(SecurityCameraBlock.FACING);
        if(boundsCache!=null&&boundsRange==range&&boundsYaw==yaw&&boundsPitch==pitch&&boundsAngle==sweepAngle&&boundsSweep==sweepEnabled&&boundsFacing==facing)return boundsCache;
        boundsCache=computeBounds();boundsRange=range;boundsYaw=yaw;boundsPitch=pitch;boundsAngle=sweepAngle;boundsSweep=sweepEnabled;boundsFacing=facing;return boundsCache;
    }
    private AABB computeBounds(){
        if(!sweepEnabled||sweepAngle<=0)return boundsAt(yaw);
        double lo=Math.toRadians(yaw-sweepAngle),hi=Math.toRadians(yaw+sweepAngle),p=Math.toRadians(pitch);
        Direction f=getBlockState().getValue(SecurityCameraBlock.FACING);double x=f.getStepX(),z=f.getStepZ();
        Set<Double> angles=new TreeSet<>();angles.add(lo);angles.add(hi);
        // Each rotating frustum vertex has x=A*cos(t)+B*sin(t). Include its
        // analytic extrema as well as the endpoints, rather than sampling an arc.
        for(int h:new int[]{-1,1})for(int v:new int[]{-1,1}){
            double a=(LENS_DISTANCE+range)*Math.cos(p)+v*range*Math.tan(Math.toRadians(30))*Math.sin(p),b=h*range;
            addExtrema(angles,lo,hi,a*x-b*z,-a*z-b*x);addExtrema(angles,lo,hi,a*z+b*x,a*x-b*z);
        }
        addExtrema(angles,lo,hi,x,-z);addExtrema(angles,lo,hi,z,x);
        AABB box=null;
        for(double angle:angles){AABB next=boundsAt((float)Math.toDegrees(angle));box=box==null?next:box.minmax(next);}
        return box;
    }
    private static void addExtrema(Set<Double> angles,double lo,double hi,double a,double b){
        double phi=Math.atan2(b,a);for(int k=-3;k<=3;k++){double angle=phi+k*Math.PI;if(angle>=lo&&angle<=hi)angles.add(angle);}
    }
    private AABB boundsAt(float angle){
        Vec3 forward=directionAt(angle),start=Vec3.atCenterOf(worldPosition).add(forward.scale(LENS_DISTANCE)),right=forward.cross(new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize();
        double minX=start.x,minY=start.y,minZ=start.z,maxX=start.x,maxY=start.y,maxZ=start.z;
        for(int horizontal:new int[]{-1,1})for(int vertical:new int[]{-1,1}){Vec3 point=start.add(forward.scale(range)).add(right.scale(horizontal*range)).add(up.scale(vertical*range*Math.tan(Math.toRadians(30))));minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);}
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(.01);
    }
    public boolean fieldLoaded(){
        if(!(level instanceof ServerLevel server))return false;AABB box=fieldBounds();int minX=((int)Math.floor(box.minX))>>4,maxX=((int)Math.floor(box.maxX))>>4,minZ=((int)Math.floor(box.minZ))>>4,maxZ=((int)Math.floor(box.maxZ))>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            BlockPos sample=new BlockPos(x*16,worldPosition.getY(),z*16);
            // Full block data alone does not imply that asynchronous entity data is loaded
            // or visible. Unknown/hidden chunks must never be interpreted as an empty zone.
            if(server.getChunkSource().getChunkNow(x,z)==null||!server.areEntitiesLoaded(ChunkPos.asLong(x,z))||!server.isPositionEntityTicking(sample))return false;
        }return true;
    }
    public boolean inField(Vec3 point){
        Vec3 delta=point.subtract(lens()),forward=direction();double depth=delta.dot(forward);if(depth<=0||depth>range)return false;
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize();
        return Math.abs(delta.dot(right))<=depth&&Math.abs(delta.dot(up))<=depth*Math.tan(Math.toRadians(30));
    }
    public boolean sees(Entity entity){
        if(!(level instanceof ServerLevel server)||!entity.isAlive()||entity.isSpectator())return false;
        Vec3 center=entity.getBoundingBox().getCenter(),eyes=entity.getEyePosition();return clear(server,entity,center)||clear(server,entity,eyes);
    }
    private boolean clear(ServerLevel server,Entity entity,Vec3 point){
        if(!inField(point))return false;Vec3 start=lens(),delta=point.subtract(start);
        // Reject every unloaded chunk along the ray, rather than letting the ray query load it.
        int steps=(int)Math.ceil(delta.length()*2);for(int i=0;i<=steps;i++)if(!server.hasChunkAt(BlockPos.containing(start.add(delta.scale(steps==0?0:(double)i/steps)))))return false;
        return server.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity){
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter getter,BlockPos pos){return pos.equals(worldPosition)?Shapes.empty():super.getBlockShape(state,getter,pos);}
        }).getType()==HitResult.Type.MISS;
    }
    public List<Entity> visibleEntities(){if(!(level instanceof ServerLevel server))return List.of();return server.getEntities((Entity)null,fieldBounds(),this::sees);}
    public void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);if(creator!=null)tag.putUUID("Creator",creator);tag.putUUID("InstanceId",instanceId);tag.putInt("Range",range);tag.putFloat("Yaw",yaw);tag.putFloat("Pitch",pitch);tag.putBoolean("SweepEnabled",sweepEnabled);tag.putFloat("SweepAngle",sweepAngle);tag.putInt("SweepPeriod",sweepPeriodTicks);tag.putLong("SweepEpoch",sweepEpoch);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);creator=tag.hasUUID("Creator")?tag.getUUID("Creator"):null;instanceId=tag.hasUUID("InstanceId")?tag.getUUID("InstanceId"):UUID.randomUUID();range=tag.contains("Range")?Math.clamp(tag.getInt("Range"),1,16):16;yaw=Float.isFinite(tag.getFloat("Yaw"))?Math.clamp(tag.getFloat("Yaw"),-180,180):0;pitch=Float.isFinite(tag.getFloat("Pitch"))?Math.clamp(tag.getFloat("Pitch"),-80,80):0;
        sweepEnabled=!tag.contains("SweepEnabled")||tag.getBoolean("SweepEnabled");sweepAngle=!tag.contains("SweepAngle")?30:Float.isFinite(tag.getFloat("SweepAngle"))?Math.clamp(tag.getFloat("SweepAngle"),0,90):30;
        sweepPeriodTicks=tag.contains("SweepPeriod")?Math.clamp(tag.getInt("SweepPeriod"),MIN_SWEEP_TICKS,MAX_SWEEP_TICKS):160;sweepEpoch=tag.getLong("SweepEpoch");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){return saveWithoutMetadata(provider);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
