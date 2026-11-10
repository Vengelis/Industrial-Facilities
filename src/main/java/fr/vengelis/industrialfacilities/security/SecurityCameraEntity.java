package fr.vengelis.industrialfacilities.security;
import java.util.*;
import fr.vengelis.industrialfacilities.access.AccessBadges;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModSounds;
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
    public static final int MAX_STAGE_TICKS=72000;
    public boolean stagesEnabled=false,haloEnabled=true;
    public int alertTicks=60,resetTicks=40,stage=1;
    public long stageRevision;
    private static final int PATROL=0,TRACK=1,HOLD=2,RETURN=3;
    private int headMode=PATROL;
    private UUID trackedEntity;
    public long trackingRevision;
    private float headYaw,headPitch;
    private long lastHeadSync=Long.MIN_VALUE;
    private float audioYaw=Float.NaN,audioPitch=Float.NaN;
    private long nextMotorSound;
    private boolean headInitialized,headSyncDirty;
    private float sentYaw=Float.NaN,sentPitch=Float.NaN;
    private boolean clientHeadReceived;
    private float clientFromYaw,clientFromPitch,clientToYaw,clientToPitch;
    private long clientBlendStart;
    public boolean trackingActive(){return stagesEnabled&&headMode==TRACK&&trackedEntity!=null;}
    public UUID trackedEntityId(){return trackedEntity;}
    private record Aim(float yaw,float pitch) {}
    private UUID stageResetToken=UUID.randomUUID();
    /** Server-local observation generation; configuration resets and reloaded instances differ. */
    public UUID stageResetToken(){return stageResetToken;}
    public record StageTransition(long revision,int stage) {}
    private final Deque<StageTransition> stageTransitions=new ArrayDeque<>();
    private long occupiedSince=-1,quietSince=-1,lastStageTick=Long.MIN_VALUE;
    private int occupiedTicks,quietTicks;
    public int occupiedTicks(){return occupiedTicks;}
    public int quietTicks(){return quietTicks;}
    /** Only real detection transitions are replayable; configuration and startup are not events. */
    public List<StageTransition> stageTransitionsAfter(long revision){return stageTransitions.stream().filter(t->t.revision()>revision).toList();}
    private AABB boundsCache;
    private int boundsRange;
    private float boundsYaw,boundsPitch,boundsAngle;
    private boolean boundsSweep,boundsStages;
    private Direction boundsFacing;
    public SecurityCameraEntity(BlockPos pos,BlockState state){super(SecurityContent.CAMERA_TYPE,pos,state);}
    public AccessBadges.Result configure(ServerPlayer player,int range,float yaw,float pitch){
        return configure(player,range,yaw,pitch,false,sweepAngle,sweepPeriodTicks);
    }
    public AccessBadges.Result configure(ServerPlayer player,int range,float yaw,float pitch,boolean enabled,float angle,int periodTicks){
        return configure(player,range,yaw,pitch,enabled,angle,periodTicks,stagesEnabled,alertTicks,resetTicks,haloEnabled);
    }
    public AccessBadges.Result configure(ServerPlayer player,int range,float yaw,float pitch,boolean enabled,float angle,int periodTicks,boolean stagesEnabled,int alertTicks,int resetTicks,boolean haloEnabled){
        if(!canConfigure(player)||range<1||range>16||!Float.isFinite(yaw)||!Float.isFinite(pitch)||yaw< -180||yaw>180||pitch< -80||pitch>80
            ||!Float.isFinite(angle)||angle<0||angle>90||periodTicks<MIN_SWEEP_TICKS||periodTicks>MAX_SWEEP_TICKS||alertTicks<1||alertTicks>MAX_STAGE_TICKS||resetTicks<0||resetTicks>MAX_STAGE_TICKS)
            return AccessBadges.Result.fail("gui.industrial_facilities.camera.configure_denied");
        boolean opticalChanged=this.range!=range||this.yaw!=yaw||this.pitch!=pitch||sweepEnabled!=enabled||sweepAngle!=angle||sweepPeriodTicks!=periodTicks;
        boolean stagesChanged=this.stagesEnabled!=stagesEnabled||this.alertTicks!=alertTicks||this.resetTicks!=resetTicks;
        this.range=range;this.yaw=yaw;this.pitch=pitch;sweepEnabled=enabled;sweepAngle=angle;sweepPeriodTicks=periodTicks;
        if(opticalChanged)sweepEpoch=level.getGameTime();
        this.stagesEnabled=stagesEnabled;this.alertTicks=alertTicks;this.resetTicks=resetTicks;this.haloEnabled=haloEnabled;
        // Visual-only changes must not disarm an acquired alert or move the sweeping head.
        if(opticalChanged||stagesChanged){if(trackedEntity!=null)cameraSound(IndustrialFacilitiesModSounds.CAMERA_TRACKING_RELEASE.get(),.18f,1);resetStageRuntime();}sync();return AccessBadges.Result.success("gui.industrial_facilities.camera.saved");
    }
    private void clearStreaks(){occupiedSince=-1;quietSince=-1;occupiedTicks=0;quietTicks=0;}
    private void resetStageRuntime(){clearStreaks();lastStageTick=Long.MIN_VALUE;stage=1;stageTransitions.clear();stageRevision++;stageResetToken=UUID.randomUUID();resetHead();}
    private void resetHead(){headInitialized=false;trackedEntity=null;headMode=PATROL;trackingRevision++;headSyncDirty=true;lastHeadSync=Long.MIN_VALUE;sentYaw=sentPitch=Float.NaN;audioYaw=audioPitch=Float.NaN;nextMotorSound=0;}
    private void headMode(int mode,UUID target){if(headMode!=mode||!Objects.equals(trackedEntity,target)){
        if(trackedEntity==null&&target!=null)cameraSound(IndustrialFacilitiesModSounds.CAMERA_TRACKING_ACQUIRE.get(),.22f,1);
        else if(trackedEntity!=null&&target==null)cameraSound(IndustrialFacilitiesModSounds.CAMERA_TRACKING_RELEASE.get(),.18f,1);
        headMode=mode;trackedEntity=target;trackingRevision++;headSyncDirty=true;
    }}
    private void cameraSound(SoundEvent sound,float volume,float pitch){if(level instanceof ServerLevel server)server.playSound(null,worldPosition,sound,SoundSource.BLOCKS,volume,pitch);}
    private void motorAudio(long now,boolean enabled){
        float currentYaw=effectiveYaw(0),currentPitch=effectivePitch(0);
        boolean moving=Float.isFinite(audioYaw)&&(Math.abs(currentYaw-audioYaw)>.025f||Math.abs(currentPitch-audioPitch)>.025f);
        audioYaw=currentYaw;audioPitch=currentPitch;
        if(enabled&&headMode==TRACK&&moving&&now>=nextMotorSound){cameraSound(IndustrialFacilitiesModSounds.CAMERA_ROTATE.get(),.065f,1.08f);nextMotorSound=now+5;}
    }
    private void initializeHead(){if(!headInitialized){headYaw=yaw+sweepOffset(0);headPitch=pitch;headInitialized=true;}}
    private void beginReturn(){initializeHead();headMode(RETURN,null);}
    private static float approach(float value,float desired,float step){return value+Math.clamp(desired-value,-step,step);}
    private static float blend(float start,float target,double fraction){return (float)(start+(target-start)*fraction);}
    private static float angleNear(float value,float reference){float delta=(value-reference)%360;if(delta>=180)delta-=360;if(delta< -180)delta+=360;return reference+delta;}
    private void patrolHead(long now){
        if(headMode==RETURN){float goal=yaw+sweepOffset(0);headYaw=approach(headYaw,goal,8);headPitch=approach(headPitch,pitch,6);if(Math.abs(headYaw-goal)<.001&&Math.abs(headPitch-pitch)<.001)headMode(PATROL,null);}
        if(headMode==PATROL){headYaw=yaw+sweepOffset(0);headPitch=pitch;}
    }
    private void syncHead(long now){if(headSyncDirty||((headMode==TRACK||headMode==RETURN)&&now-lastHeadSync>=2&&(Math.abs(headYaw-sentYaw)>.001||Math.abs(headPitch-sentPitch)>.001)))sync();}
    private void transition(int next){
        if(stage==next)return;stage=next;stageRevision++;if(next==1)beginReturn();
        stageTransitions.addLast(new StageTransition(stageRevision,next));while(stageTransitions.size()>128)stageTransitions.removeFirst();
        sync();
    }
    /** Shared occupancy timers still read only the actual, freshly rotated optical cone. */
    public void tickServer(){
        if(!(level instanceof ServerLevel server)||isRemoved())return;
        long now=server.getGameTime();if(lastStageTick==now)return;
        if(lastStageTick!=Long.MIN_VALUE&&now!=lastStageTick+1)clearStreaks();lastStageTick=now;
        initializeHead();
        if(!stagesEnabled){clearStreaks();headMode(PATROL,null);patrolHead(now);if(stage!=1){stage=1;stageTransitions.clear();stageRevision++;sync();}syncHead(now);motorAudio(now,true);return;}
        // Unknown chunks freeze orientation and both timers. No target lookup or entity query is allowed.
        if(!fieldLoaded()){clearStreaks();headMode(HOLD,null);syncHead(now);motorAudio(now,false);return;}
        if(stage==1){if(headMode!=PATROL&&headMode!=RETURN)beginReturn();patrolHead(now);}
        Entity target=null;
        if(stage>=2&&trackedEntity!=null){Entity old=server.getEntity(trackedEntity);if(validTracked(server,old))target=old;}
        if(target==null&&stage>=2){
            var candidates=visibleEntities();
            target=candidates.stream().filter(e->validTracked(server,e)).min(Comparator.comparingDouble((Entity e)->e.position().distanceToSqr(worldPosition.getCenter())).thenComparing(Entity::getUUID)).orElse(null);
        }
        if(target!=null){
            Aim aim=aim(target.getBoundingBox().getCenter());headMode(TRACK,target.getUUID());
            headYaw=approach(headYaw,aim.yaw,8);headPitch=approach(headPitch,aim.pitch,6);
        }else if(stage>=2)headMode(HOLD,null);
        // Retention permits aiming toward a valid target, but never invents occupancy outside the current cone.
        if(!visibleEntities().isEmpty()){
            quietSince=-1;quietTicks=0;if(occupiedSince<0)occupiedSince=now;
            occupiedTicks=(int)Math.min(MAX_STAGE_TICKS,now-occupiedSince);
            if(stage==1)transition(2);else if(stage==2&&occupiedTicks>=alertTicks)transition(3);
        }else{
            occupiedSince=-1;occupiedTicks=0;if(quietSince<0)quietSince=now;
            quietTicks=(int)Math.min(MAX_STAGE_TICKS,now-quietSince);
            if(stage!=1&&quietTicks>=resetTicks)transition(1);
        }
        syncHead(now);
        motorAudio(now,true);
    }
    private Aim aim(Vec3 point){
        Vec3 delta=point.subtract(worldPosition.getCenter());Direction facing=getBlockState().getValue(SecurityCameraBlock.FACING);
        double desired=delta.x*delta.x+delta.z*delta.z<1.0E-10?headYaw:Math.toDegrees(Math.atan2(facing.getStepX()*delta.z-facing.getStepZ()*delta.x,facing.getStepX()*delta.x+facing.getStepZ()*delta.z));
        double relative=(desired-yaw)%360;if(relative>=180)relative-=360;if(relative< -180)relative+=360;
        float aimYaw=(float)(yaw+Math.clamp(relative,-sweepAngle,sweepAngle));
        float aimPitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        return new Aim(aimYaw,Math.clamp(aimPitch,Math.max(-80,pitch-30),Math.min(80,pitch+30)));
    }
    private boolean validTracked(ServerLevel server,Entity entity){
        if(entity==null||entity.level()!=server||!entity.isAlive()||entity.isRemoved()||entity.isSpectator()||concealedByInvisibility(entity))return false;
        BlockPos pos=entity.blockPosition();if(!TerminalLinks.loaded(server,pos)||!server.areEntitiesLoaded(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4))||!server.isPositionEntityTicking(pos))return false;
        Aim aim=aim(entity.getBoundingBox().getCenter());Vec3 forward=directionAt(aim.yaw,aim.pitch),start=worldPosition.getCenter().add(forward.scale(LENS_DISTANCE));
        return clearAt(server,entity,entity.getBoundingBox().getCenter(),start,forward)||clearAt(server,entity,entity.getEyePosition(),start,forward);
    }
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)resetStageRuntime();}
    public boolean canConfigure(ServerPlayer player){return creator!=null&&creator.equals(player.getUUID())&&player.isAlive()&&level==player.level()&&player.distanceToSqr(worldPosition.getCenter())<=64&&!isRemoved();}
    public static float sweepOffsetAt(double elapsedTicks,boolean enabled,float angle,int periodTicks){
        if(!enabled||!Float.isFinite(angle)||angle<=0||periodTicks<MIN_SWEEP_TICKS)return 0;
        return (float)(Math.sin(elapsedTicks*2*Math.PI/periodTicks)*Math.min(90,angle));
    }
    public float sweepOffset(float partial){return sweepOffsetAt((level==null?0:level.getGameTime())+(double)partial-sweepEpoch,sweepEnabled,sweepAngle,sweepPeriodTicks);}
    private double clientBlend(float partial){return Math.clamp(((level==null?0:level.getGameTime())+(double)partial-clientBlendStart)/2,0,1);}
    public float effectiveYaw(float partial){
        float patrol=yaw+sweepOffset(partial);
        if(level!=null&&level.isClientSide&&clientHeadReceived)return blend(clientFromYaw,!stagesEnabled||headMode==PATROL?patrol:clientToYaw,clientBlend(partial));
        return !stagesEnabled||headMode==PATROL?patrol:headInitialized?headYaw:patrol;
    }
    public float effectivePitch(float partial){
        if(level!=null&&level.isClientSide&&clientHeadReceived)return blend(clientFromPitch,!stagesEnabled||headMode==PATROL?pitch:clientToPitch,clientBlend(partial));
        return !stagesEnabled||headMode==PATROL?pitch:headInitialized?headPitch:pitch;
    }
    public Vec3 direction(){return direction(0);}
    public Vec3 direction(float partial){return directionAt(effectiveYaw(partial),effectivePitch(partial));}
    private Vec3 directionAt(float aim){return directionAt(aim,pitch);}
    private Vec3 directionAt(float aim,float aimPitch){
        Direction facing=getBlockState().getValue(SecurityCameraBlock.FACING);double a=Math.toRadians(aim),p=Math.toRadians(aimPitch),x=facing.getStepX(),z=facing.getStepZ();
        return new Vec3((x*Math.cos(a)-z*Math.sin(a))*Math.cos(p),-Math.sin(p),(x*Math.sin(a)+z*Math.cos(a))*Math.cos(p)).normalize();
    }
    public Vec3 lens(){return lens(0);}
    public Vec3 lens(float partial){return Vec3.atCenterOf(worldPosition).add(direction(partial).scale(LENS_DISTANCE));}
    /** Conservative broad phase for the whole configured sweep; visibility still uses the instantaneous head direction. */
    public AABB fieldBounds(){
        Direction facing=getBlockState().getValue(SecurityCameraBlock.FACING);
        if(boundsCache!=null&&boundsRange==range&&boundsYaw==yaw&&boundsPitch==pitch&&boundsAngle==sweepAngle&&boundsSweep==sweepEnabled&&boundsFacing==facing&&boundsStages==stagesEnabled)return boundsCache;
        boundsCache=computeBounds();boundsRange=range;boundsYaw=yaw;boundsPitch=pitch;boundsAngle=sweepAngle;boundsSweep=sweepEnabled;boundsFacing=facing;boundsStages=stagesEnabled;return boundsCache;
    }
    private AABB computeBounds(){
        // Fixed conservative envelope of every allowed yaw/pitch cone. It never follows a target outward.
        if(stagesEnabled){double radius=(range+LENS_DISTANCE)*Math.sqrt(7.0/3);return new AABB(worldPosition.getCenter(),worldPosition.getCenter()).inflate(radius+.01);}
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
        return inCone(point,lens(),direction());
    }
    private boolean inCone(Vec3 point,Vec3 start,Vec3 forward){
        Vec3 delta=point.subtract(start);double depth=delta.dot(forward);if(depth<=0||depth>range)return false;
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize();
        return Math.abs(delta.dot(right))<=depth&&Math.abs(delta.dot(up))<=depth*Math.tan(Math.toRadians(30));
    }
    public boolean sees(Entity entity){
        if(!(level instanceof ServerLevel server)||!entity.isAlive()||entity.isSpectator()||concealedByInvisibility(entity))return false;
        Vec3 center=entity.getBoundingBox().getCenter(),eyes=entity.getEyePosition();return clear(server,entity,center)||clear(server,entity,eyes);
    }
    /** Potion concealment is broken by visible equipment, not by items stored in inventory. */
    private static boolean concealedByInvisibility(Entity entity){
        if(!(entity instanceof LivingEntity living)||!living.hasEffect(MobEffects.INVISIBILITY))return false;
        for(var stack:living.getAllSlots())if(!stack.isEmpty())return false;
        return true;
    }
    private boolean clear(ServerLevel server,Entity entity,Vec3 point){
        return clearAt(server,entity,point,lens(),direction());
    }
    private boolean clearAt(ServerLevel server,Entity entity,Vec3 point,Vec3 start,Vec3 forward){
        if(!inCone(point,start,forward))return false;Vec3 delta=point.subtract(start);
        // Reject every unloaded chunk along the ray, rather than letting the ray query load it.
        int steps=(int)Math.ceil(delta.length()*2);for(int i=0;i<=steps;i++)if(!server.hasChunkAt(BlockPos.containing(start.add(delta.scale(steps==0?0:(double)i/steps)))))return false;
        return server.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity){
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter getter,BlockPos pos){
                if(pos.equals(worldPosition))return Shapes.empty();
                // Aperture collision consults its controller, potentially outside the observed
                // field. Unknown controller data is opaque and must never force a chunk load.
                if(state.getBlock() instanceof fr.vengelis.industrialfacilities.block.IndustrialDoorPartBlock
                    &&getter.getBlockEntity(pos) instanceof fr.vengelis.industrialfacilities.door.IndustrialDoorEntity part
                    &&part.owner!=null&&!TerminalLinks.loaded(server,part.owner))return Shapes.block();
                return super.getBlockShape(state,getter,pos);
            }
        }).getType()==HitResult.Type.MISS;
    }
    public List<Entity> visibleEntities(){if(!(level instanceof ServerLevel server))return List.of();return server.getEntities((Entity)null,fieldBounds(),this::sees);}
    public void sync(){setChanged();if(level!=null){lastHeadSync=level.getGameTime();sentYaw=effectiveYaw(0);sentPitch=effectivePitch(0);headSyncDirty=false;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);if(creator!=null)tag.putUUID("Creator",creator);tag.putUUID("InstanceId",instanceId);tag.putInt("Range",range);tag.putFloat("Yaw",yaw);tag.putFloat("Pitch",pitch);tag.putBoolean("SweepEnabled",sweepEnabled);tag.putFloat("SweepAngle",sweepAngle);tag.putInt("SweepPeriod",sweepPeriodTicks);tag.putLong("SweepEpoch",sweepEpoch);tag.putBoolean("StagesEnabled",stagesEnabled);tag.putInt("AlertTicks",alertTicks);tag.putInt("ResetTicks",resetTicks);tag.putBoolean("HaloEnabled",haloEnabled);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){
        float oldYaw=effectiveYaw(0),oldPitch=effectivePitch(0);boolean hadHead=clientHeadReceived;
        super.loadAdditional(tag,provider);creator=tag.hasUUID("Creator")?tag.getUUID("Creator"):null;instanceId=tag.hasUUID("InstanceId")?tag.getUUID("InstanceId"):UUID.randomUUID();range=tag.contains("Range")?Math.clamp(tag.getInt("Range"),1,16):16;yaw=Float.isFinite(tag.getFloat("Yaw"))?Math.clamp(tag.getFloat("Yaw"),-180,180):0;pitch=Float.isFinite(tag.getFloat("Pitch"))?Math.clamp(tag.getFloat("Pitch"),-80,80):0;
        sweepEnabled=!tag.contains("SweepEnabled")||tag.getBoolean("SweepEnabled");sweepAngle=!tag.contains("SweepAngle")?30:Float.isFinite(tag.getFloat("SweepAngle"))?Math.clamp(tag.getFloat("SweepAngle"),0,90):30;
        sweepPeriodTicks=tag.contains("SweepPeriod")?Math.clamp(tag.getInt("SweepPeriod"),MIN_SWEEP_TICKS,MAX_SWEEP_TICKS):160;sweepEpoch=tag.getLong("SweepEpoch");
        stagesEnabled=tag.getBoolean("StagesEnabled");haloEnabled=!tag.contains("HaloEnabled")||tag.getBoolean("HaloEnabled");
        alertTicks=tag.contains("AlertTicks")?Math.clamp(tag.getInt("AlertTicks"),1,MAX_STAGE_TICKS):60;
        resetTicks=tag.contains("ResetTicks")?Math.clamp(tag.getInt("ResetTicks"),0,MAX_STAGE_TICKS):40;
        // Initial client chunk NBT is decoded before a level is attached. Disk saves omit
        // these live fields; server onLoad still resets observations and never restores alerts.
        resetStageRuntime();if(tag.contains("Stage")){stage=Math.clamp(tag.getInt("Stage"),1,3);stageRevision=tag.getLong("StageRevision");}
        if(tag.contains("TrackingYaw")&&Float.isFinite(tag.getFloat("TrackingYaw"))&&Float.isFinite(tag.getFloat("TrackingPitch"))){
            headMode=Math.clamp(tag.getInt("TrackingMode"),PATROL,RETURN);headYaw=Math.clamp(tag.getFloat("TrackingYaw"),yaw-sweepAngle,yaw+sweepAngle);headPitch=Math.clamp(tag.getFloat("TrackingPitch"),Math.max(-80,pitch-30),Math.min(80,pitch+30));headInitialized=true;
            trackedEntity=tag.hasUUID("TrackingTarget")?tag.getUUID("TrackingTarget"):null;trackingRevision=tag.getLong("TrackingRevision");
            clientFromYaw=hadHead?angleNear(oldYaw,headYaw):headYaw;clientFromPitch=hadHead?oldPitch:headPitch;clientToYaw=headYaw;clientToPitch=headPitch;clientBlendStart=level==null?0:level.getGameTime();clientHeadReceived=true;
        }else clientHeadReceived=false;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){CompoundTag tag=saveWithoutMetadata(provider);tag.putInt("Stage",stage);tag.putLong("StageRevision",stageRevision);tag.putFloat("TrackingYaw",effectiveYaw(0));tag.putFloat("TrackingPitch",effectivePitch(0));tag.putInt("TrackingMode",headMode);if(trackedEntity!=null)tag.putUUID("TrackingTarget",trackedEntity);tag.putLong("TrackingRevision",trackingRevision);tag.putLong("TrackingTick",level==null?0:level.getGameTime());return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
