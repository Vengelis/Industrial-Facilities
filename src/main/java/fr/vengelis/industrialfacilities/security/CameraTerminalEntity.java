package fr.vengelis.industrialfacilities.security;
import java.util.*;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;
public class CameraTerminalEntity extends AccessTerminalEntity {
    public record LinkView(UUID id,String kind,BlockPos pos,String name,boolean available) {}
    private record Link(UUID id,String kind,BlockPos pos) {}
    private final List<Link> links=new ArrayList<>();
    private final Map<UUID,Boolean> outputs=new HashMap<>();
    private final Map<UUID,Map<UUID,Integer>> visible=new HashMap<>();
    private final List<CameraProgram.Job> jobs=new ArrayList<>();
    private CompoundTag program=CameraProgram.empty();
    private CameraProgram.Program compiled;
    private boolean running;
    private String status="gui.industrial_facilities.camera.stopped";
    public CameraTerminalEntity(BlockPos pos,BlockState state){super(pos,state);}
    public CompoundTag program(){return program.copy();}
    public boolean isRunning(){return running;}
    public String status(){return status;}
    public List<LinkView> linkViews(){List<LinkView> result=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();for(Link link:links){int index=counts.merge(link.kind,1,Integer::sum);String prefix=switch(link.kind){case "camera"->"CAM";case "door"->"PORTE";case "alarm"->"AL";default->"RED";};result.add(new LinkView(link.id,link.kind,link.pos,prefix+String.format(java.util.Locale.ROOT,"%02d",index),available(link)));}return List.copyOf(result);}
    private boolean canOwn(ServerPlayer player){return isOwner(player)&&player.isAlive()&&player.level()==level&&AccessTerminalEntity.resolve(level,worldPosition)==this&&!isRemoved();}
    private boolean canEdit(ServerPlayer player){return canOwn(player)&&player.distanceToSqr(worldPosition.getCenter())<=64;}
    public AccessBadges.Result setProgram(ServerPlayer player,CompoundTag data){if(!canEdit(player))return fail("not_owner");var result=CameraProgram.validate(data,linkViews());if(!result.ok())return AccessBadges.Result.fail(result.error());pause("stopped");program=result.program().copy();compiled=CameraProgram.compile(program,linkViews());sync();return success("saved");}
    public AccessBadges.Result setRunning(ServerPlayer player,boolean value){if(!canEdit(player))return fail("not_owner");if(!value){pause("stopped");return success("stopped");}try{compiled=CameraProgram.compile(program,linkViews());if(compiled.cameras().isEmpty())return fail("empty_program");for(UUID camera:compiled.cameras())if(links.stream().noneMatch(l->l.id.equals(camera)&&l.kind.equals("camera")&&available(l)))return fail("camera_offline");}catch(IllegalArgumentException e){return fail("invalid_program");}release();visible.clear();running=true;status="gui.industrial_facilities.camera.running";sync();return success("running");}
    private static AccessBadges.Result fail(String key){return AccessBadges.Result.fail("gui.industrial_facilities.camera."+key);}
    private static AccessBadges.Result success(String key){return AccessBadges.Result.success("gui.industrial_facilities.camera."+key);}
    private Link find(UUID id){for(Link link:links)if(link.id.equals(id))return link;return null;}
    private boolean available(Link link){
        if(!(level instanceof ServerLevel server)||!server.hasChunkAt(link.pos)||worldPosition.distSqr(link.pos)>64*64)return false;
        var target=server.getBlockEntity(link.pos);return switch(link.kind){
            case "camera"->target instanceof SecurityCameraEntity c&&link.id.equals(c.instanceId)&&Objects.equals(creator,c.creator)&&c.fieldLoaded();
            case "door"->target instanceof IndustrialDoorEntity d&&d.size>0&&link.id.equals(d.doorInstanceId)&&Objects.equals(creator,d.automationOwner)&&AccessDoorService.canConfigureOwner(creator,d);
            case "alarm"->target instanceof AlarmBlockEntity a&&link.id.equals(a.automationInstanceId)&&Objects.equals(creator,a.automationOwner);
            case "emitter"->target instanceof SignalEmitterEntity e&&link.id.equals(e.instanceId)&&Objects.equals(creator,e.creator);
            default->false;};
    }
    public AccessBadges.Result link(ServerPlayer player,BlockPos pos,boolean remove){
        if(!canOwn(player)||player.distanceToSqr(pos.getCenter())>64||!level.hasChunkAt(pos)||worldPosition.distSqr(pos)>64*64)return fail("link_denied");
        if(remove){
            var door=AccessDoorService.resolveDoor(level,pos);BlockPos remembered=door==null?pos:door.getBlockPos();Link old=null;
            for(Link link:links)if(link.pos.equals(remembered)||link.pos.equals(pos)){old=link;break;}
            if(old==null)return fail("not_linked");pause("stopped");links.remove(old);sync();return success("device_unlinked");
        }
        var target=level.getBlockEntity(pos);UUID id;String kind;BlockPos at=pos;
        if(target instanceof SecurityCameraEntity camera){id=camera.instanceId;kind="camera";if(!Objects.equals(camera.creator,creator))return fail("not_owner");}
        else if(target instanceof SignalEmitterEntity emitter){id=emitter.instanceId;kind="emitter";if(!Objects.equals(emitter.creator,creator))return fail("not_owner");}
        else if(target instanceof AlarmBlockEntity alarm){id=alarm.automationInstanceId;kind="alarm";if(alarm.automationOwner!=null&&!Objects.equals(alarm.automationOwner,creator))return fail("not_owner");}
        else {IndustrialDoorEntity door=AccessDoorService.resolveDoor(level,pos);if(door==null||!AccessDoorService.canConfigure(player,door))return fail("link_denied");id=AccessDoorService.ensureId(door);kind="door";at=door.getBlockPos();if(worldPosition.distSqr(at)>64*64)return fail("link_denied");}
        Link existing=find(id);
        if(existing!=null)return fail("already_linked");long cameras=links.stream().filter(l->l.kind.equals("camera")).count();if(kind.equals("camera")?cameras>=8:links.size()-cameras>=16)return fail("link_limit");
        if(kind.equals("door")&&level.getBlockEntity(at) instanceof IndustrialDoorEntity door){door.automationOwner=creator;door.sync();}
        if(kind.equals("alarm")&&target instanceof AlarmBlockEntity alarm){alarm.automationOwner=creator;alarm.setChanged();}
        pause("stopped");links.add(new Link(id,kind,at.immutable()));sync();return success("linked");
    }
    private void action(UUID id,String action){Link link=find(id);if(link==null||!available(link))return;boolean on=action.endsWith("_on")||action.endsWith("_open");if(link.kind.equals("door")&&level.getBlockEntity(link.pos) instanceof IndustrialDoorEntity door&&!door.allowCameraAutomation){status="gui.industrial_facilities.camera.door_opt_in";return;}status="gui.industrial_facilities.camera.running";outputs.put(id,on);AutomationOutputs.request((ServerLevel)level,link.pos,link.kind,id,instanceId,on);if(!on&&otherSource(link))status="gui.industrial_facilities.camera.other_source";}
    private boolean otherSource(Link link){if(!(level instanceof ServerLevel server))return false;var target=level.getBlockEntity(link.pos);return target instanceof IndustrialDoorEntity door?AccessDoorService.shouldOpen(server,door):target instanceof AlarmBlockEntity alarm?alarm.powered():AutomationOutputs.active(server,link.pos,link.kind,link.id);}
    private boolean enqueue(List<CameraProgram.Job> added){if(jobs.size()+added.size()>CameraProgram.MAX_JOBS){pause("job_limit");return false;}jobs.addAll(added);return true;}
    private void cancelEmpty(UUID camera){jobs.removeIf(job->job.empty()&&job.camera().equals(camera));}
    public void tickServer(){
        if(!(level instanceof ServerLevel server)||!running)return;
        if(AccessTerminalEntity.resolve(level,worldPosition)!=this){pause("stopped");return;}
        if(compiled==null)try{compiled=CameraProgram.compile(program,linkViews());}catch(IllegalArgumentException e){pause("invalid_program");return;}
        if(compiled.cameras().isEmpty()){pause("empty_program");return;}
        for(UUID id:compiled.cameras()){Link link=find(id);if(link==null||!available(link)){pause("camera_offline");return;}}
        long tick=server.getGameTime();boolean scan=tick%5==0;
        if(scan){
            for(UUID id:compiled.cameras()){
                Link link=find(id);SecurityCameraEntity camera=(SecurityCameraEntity)server.getBlockEntity(link.pos);Map<UUID,Integer> seen=visible.computeIfAbsent(id,k->new HashMap<>());boolean occupied=!seen.isEmpty();Set<UUID> now=new HashSet<>();
                var detected=camera.visibleEntities();if(!detected.isEmpty())cancelEmpty(id);
                for(var entity:detected){UUID uuid=entity.getUUID();now.add(uuid);if(!seen.containsKey(uuid)&&!enqueue(compiled.entryJobs(id,entity,camera.lens(),tick)))return;seen.put(uuid,0);}
                var iterator=seen.entrySet().iterator();while(iterator.hasNext()){var entity=iterator.next();if(!now.contains(entity.getKey())){int misses=entity.getValue()+1;if(misses>=2)iterator.remove();else entity.setValue(misses);}}
                // Delay starts only after the last entity has been absent for two scans.
                if(occupied&&seen.isEmpty()&&!enqueue(compiled.emptyJobs(id,tick)))return;
            }
        }else{
            // A return between normal detection scans must cancel a due timer/continuation
            // before it can close a door. This reads at most the eight linked camera fields.
            Set<UUID> pending=new HashSet<>();for(var job:jobs)if(job.empty())pending.add(job.camera());
            for(UUID id:pending){
                SecurityCameraEntity camera=(SecurityCameraEntity)server.getBlockEntity(find(id).pos);var detected=camera.visibleEntities();if(detected.isEmpty())continue;cancelEmpty(id);
                // Positive observations also arm the next empty transition, even when a
                // brief return ends before the next normal scan. Absences still count at 4 Hz.
                Map<UUID,Integer> seen=visible.computeIfAbsent(id,k->new HashMap<>());
                for(var entity:detected){UUID uuid=entity.getUUID();if(!seen.containsKey(uuid)&&!enqueue(compiled.entryJobs(id,entity,camera.lens(),tick)))return;seen.put(uuid,0);}
            }
        }
        var continuation=jobs.iterator();while(continuation.hasNext())if(continuation.next().advance(tick,this::action))continuation.remove();
        if(!scan)return;
        boolean other=false;var iterator=outputs.entrySet().iterator();while(iterator.hasNext()){var output=iterator.next();Link link=find(output.getKey());if(link==null||!available(link)||link.kind.equals("door")&&server.getBlockEntity(link.pos) instanceof IndustrialDoorEntity door&&!door.allowCameraAutomation){if(link!=null)AutomationOutputs.request(server,link.pos,link.kind,link.id,instanceId,false);iterator.remove();continue;}AutomationOutputs.request(server,link.pos,link.kind,link.id,instanceId,output.getValue());if(!output.getValue()&&otherSource(link))other=true;}
        if(other)status="gui.industrial_facilities.camera.other_source";else if(status.equals("gui.industrial_facilities.camera.other_source"))status="gui.industrial_facilities.camera.running";
    }
    private void release(){if(level instanceof ServerLevel server)AutomationOutputs.release(server,instanceId);outputs.clear();jobs.clear();}
    private void pause(String reason){release();visible.clear();running=false;status="gui.industrial_facilities.camera."+reason;sync();}
    @Override public void invalidate(){pause("stopped");links.clear();program=CameraProgram.empty();compiled=null;super.invalidate();}
    @Override public void setRemoved(){release();visible.clear();compiled=null;super.setRemoved();}
    @Override public void onLoad(){super.onLoad();visible.clear();jobs.clear();compiled=null;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){CompoundTag tag=new CompoundTag();if(creator!=null)tag.putUUID("Creator",creator);tag.putUUID("InstanceId",instanceId);tag.putBoolean("CameraRunning",running);return tag;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);tag.put("CameraProgram",program.copy());tag.putBoolean("CameraRunning",running);ListTag list=new ListTag();for(Link link:links){CompoundTag row=new CompoundTag();row.putUUID("Id",link.id);row.putString("Kind",link.kind);row.putLong("Position",link.pos.asLong());list.add(row);}tag.put("CameraLinks",list);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);links.clear();int cameras=0,targets=0;Set<UUID> ids=new HashSet<>();for(Tag raw:tag.getList("CameraLinks",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)raw;String kind=row.getString("Kind");if(!row.hasUUID("Id")||!Set.of("camera","door","alarm","emitter").contains(kind)||!ids.add(row.getUUID("Id")))continue;if(kind.equals("camera")){if(cameras++>=8)continue;}else if(targets++>=16)continue;links.add(new Link(row.getUUID("Id"),kind,BlockPos.of(row.getLong("Position"))));}program=tag.contains("CameraProgram",Tag.TAG_COMPOUND)?tag.getCompound("CameraProgram").copy():CameraProgram.empty();compiled=null;running=tag.getBoolean("CameraRunning");status="gui.industrial_facilities.camera."+(running?"running":"stopped");outputs.clear();visible.clear();jobs.clear();}
}
