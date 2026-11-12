package fr.vengelis.industrialfacilities.security;
import java.util.*;
import fr.vengelis.industrialfacilities.access.*;
import fr.vengelis.industrialfacilities.alarm.AlarmBlockEntity;
import fr.vengelis.industrialfacilities.panel.*;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
public class CameraTerminalEntity extends AccessTerminalEntity {
    public record LinkView(UUID id,String kind,BlockPos pos,String name,boolean available) {}
    private record Link(UUID id,String kind,BlockPos pos) {}
    private final List<Link> links=new ArrayList<>();
    private final Map<UUID,Boolean> outputs=new HashMap<>();
    private static final class Observation {
        int misses;
        CameraProgram.Snapshot lastSeen;
    }
    private final Map<UUID,Map<UUID,Observation>> visible=new HashMap<>();
    private final List<CameraProgram.Job> jobs=new ArrayList<>();
    private final Map<UUID,Long> stageSeen=new HashMap<>();
    private final Map<UUID,UUID> stageResetSeen=new HashMap<>();
    private CompoundTag program=CameraProgram.empty();
    private CameraProgram.Program compiled;
    private boolean running;
    private String status="gui.industrial_facilities.camera.stopped";
    public CameraTerminalEntity(BlockPos pos,BlockState state){super(pos,state);}
    public CompoundTag program(){return program.copy();}
    public boolean isRunning(){return running;}
    public String status(){return status;}
    public List<LinkView> linkViews(){reconcileDoorLinks(true);List<LinkView> result=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();for(Link link:links){int index=counts.merge(link.kind,1,Integer::sum);String prefix=switch(link.kind){case "camera"->"CAM";case "door"->"PORTE";case "alarm"->"AL";case "panel"->"PAN";default->"RED";};result.add(new LinkView(link.id,link.kind,link.pos,prefix+String.format(java.util.Locale.ROOT,"%02d",index),available(link)));}return List.copyOf(result);}
    /** Immediate cleanup of loaded terminals; the registry covers terminals that are offline. */
    public static void doorRemoved(ServerLevel level,BlockPos pos){
        int radius=TerminalLinks.MAX_DISTANCE;List<CameraTerminalEntity> nearby=new ArrayList<>();
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var entity:chunk.getBlockEntities().values())if(entity instanceof CameraTerminalEntity terminal&&!terminal.isRemoved()
                &&TerminalLinks.inRange(terminal.getBlockPos(),pos))nearby.add(terminal);
        }
        for(var terminal:nearby)terminal.discardDoorLinks(link->link.pos.equals(pos));
    }
    private void discardDoorLinks(java.util.function.Predicate<Link> discard){
        Set<UUID> removed=new HashSet<>();links.removeIf(link->{if(!link.kind.equals("door")||!discard.test(link))return false;removed.add(link.id);return true;});
        if(removed.isEmpty())return;
        outputs.keySet().removeAll(removed);
        CompoundTag pruned=CameraProgram.withoutTargets(program,removed);
        if(pruned!=program){program=pruned;compiled=null;pause("door_removed");}else sync();
    }
    private void reconcileDoorLinks(boolean inspectLoaded){
        if(!(level instanceof ServerLevel server)||links.stream().noneMatch(link->link.kind.equals("door")))return;
        var registry=AccessRegistry.get(server.getServer());String dimension=server.dimension().location().toString();
        discardDoorLinks(link->{
            if(registry.obsoleteDoorLink(dimension,link.pos,link.id))return true;
            if(!inspectLoaded||!TerminalLinks.loaded(server,link.pos))return false;
            return !(server.getBlockEntity(link.pos) instanceof IndustrialDoorEntity door)||door.size==0||!link.id.equals(door.doorInstanceId);
        });
    }
    private boolean canOwn(ServerPlayer player){return isOwner(player)&&player.isAlive()&&player.level()==level&&AccessTerminalEntity.resolve(level,worldPosition)==this&&!isRemoved();}
    private boolean canEdit(ServerPlayer player){return canOwn(player)&&player.distanceToSqr(worldPosition.getCenter())<=64;}
    public AccessBadges.Result setProgram(ServerPlayer player,CompoundTag data){if(!canEdit(player))return fail("not_owner");var result=CameraProgram.validate(data,linkViews());if(!result.ok())return AccessBadges.Result.fail(result.error());pause("stopped");program=result.program().copy();compiled=CameraProgram.compile(program,linkViews());sync();return success("saved");}
    public AccessBadges.Result setRunning(ServerPlayer player,boolean value){if(!canEdit(player))return fail("not_owner");if(!value){pause("stopped");return success("stopped");}try{compiled=CameraProgram.compile(program,linkViews());if(compiled.cameras().isEmpty())return fail("empty_program");for(UUID camera:compiled.cameras()){Link link=find(camera);if(link==null||!link.kind.equals("camera")||!available(link))return fail("camera_offline");if(compiled.requiresStages(camera)&&level.getBlockEntity(link.pos) instanceof SecurityCameraEntity c&&!c.stagesEnabled)return fail("stages_disabled");}}catch(IllegalArgumentException e){return fail("invalid_program");}release();visible.clear();primeStages();running=true;status="gui.industrial_facilities.camera.running";sync();return success("running");}
    private void primeStages(){
        stageSeen.clear();stageResetSeen.clear();if(compiled==null)return;
        for(UUID id:compiled.cameras()){Link link=find(id);if(link!=null&&level.getBlockEntity(link.pos) instanceof SecurityCameraEntity camera){stageSeen.put(id,camera.stageRevision);stageResetSeen.put(id,camera.stageResetToken());}}
    }
    private static AccessBadges.Result fail(String key){return AccessBadges.Result.fail("gui.industrial_facilities.camera."+key);}
    private static AccessBadges.Result success(String key){return AccessBadges.Result.success("gui.industrial_facilities.camera."+key);}
    private Link find(UUID id){for(Link link:links)if(link.id.equals(id))return link;return null;}
    private boolean available(Link link){
        if(!(level instanceof ServerLevel server)||!TerminalLinks.loaded(server,link.pos)||!TerminalLinks.inRange(worldPosition,link.pos))return false;
        var target=server.getBlockEntity(link.pos);return switch(link.kind){
            case "camera"->target instanceof SecurityCameraEntity c&&link.id.equals(c.instanceId)&&Objects.equals(creator,c.creator)&&c.fieldLoaded();
            case "door"->target instanceof IndustrialDoorEntity d&&d.size>0&&link.id.equals(d.doorInstanceId)&&Objects.equals(creator,d.automationOwner)&&AccessDoorService.canConfigureOwner(creator,d);
            case "alarm"->target instanceof AlarmBlockEntity a&&link.id.equals(a.automationInstanceId)&&Objects.equals(creator,a.automationOwner);
            case "panel"->target instanceof PanelBlockEntity p&&panelAvailable(p,link.id);
            case "emitter"->target instanceof SignalEmitterEntity e&&link.id.equals(e.instanceId)&&Objects.equals(creator,e.creator);
            default->false;};
    }
    private boolean panelAvailable(PanelBlockEntity panel,UUID id){
        var group=panel.automationGroup(creator,id);
        return group!=null&&group.blocks().stream().allMatch(p->TerminalLinks.inRange(worldPosition,p));
    }
    public AccessBadges.Result link(ServerPlayer player,BlockPos pos,boolean remove){
        if(!canOwn(player)||player.distanceToSqr(pos.getCenter())>64||!(level instanceof ServerLevel server)||!TerminalLinks.loaded(server,pos))return fail("link_denied");
        reconcileDoorLinks(true);
        if(remove){
            var door=AccessDoorService.resolveDoor(level,pos);BlockPos remembered=door==null?pos:door.getBlockPos();Link old=null;
            var panelGroup=level.getBlockEntity(pos) instanceof PanelBlockEntity?PanelStructure.find(level,pos):null;
            if(panelGroup!=null&&panelGroup.valid())for(Link link:links)if(link.kind.equals("panel")&&panelGroup.blocks().contains(link.pos)){remembered=link.pos;break;}
            for(Link link:links)if(link.pos.equals(remembered)||link.pos.equals(pos)){old=link;break;}
            if(old==null)return fail("not_linked");pause("stopped");links.remove(old);sync();return success("device_unlinked");
        }
        var target=level.getBlockEntity(pos);UUID id;String kind;BlockPos at=pos;
        if(target instanceof SecurityCameraEntity camera){id=camera.instanceId;kind="camera";if(!Objects.equals(camera.creator,creator))return fail("not_owner");}
        else if(target instanceof SignalEmitterEntity emitter){id=emitter.instanceId;kind="emitter";if(!Objects.equals(emitter.creator,creator))return fail("not_owner");}
        else if(target instanceof AlarmBlockEntity alarm){id=alarm.automationInstanceId;kind="alarm";if(alarm.automationOwner!=null&&!Objects.equals(alarm.automationOwner,creator))return fail("not_owner");}
        else if(target instanceof PanelBlockEntity){
            var group=PanelStructure.find(level,pos);if(!group.valid())return fail("panel_unavailable");
            for(BlockPos member:group.blocks()){
                if(!TerminalLinks.inRange(worldPosition,member)||!(level.getBlockEntity(member) instanceof PanelBlockEntity panel))return fail("link_denied");
                for(Direction d:PanelStructure.directions(panel.getBlockState()))if(!TerminalLinks.loaded(server,member.relative(d)))return fail("panel_unavailable");
                if(panel.automationOwner!=null&&!Objects.equals(panel.automationOwner,creator))return fail("not_owner");
                for(Link link:links)if(link.kind.equals("panel")&&link.pos.equals(member))return fail("already_linked");
            }
            at=group.origin();id=group.controller(level).automationInstanceId;kind="panel";
        }
        else {IndustrialDoorEntity door=AccessDoorService.resolveDoor(level,pos);if(door==null||!AccessDoorService.canConfigure(player,door))return fail("link_denied");at=door.getBlockPos();if(!TerminalLinks.inRange(worldPosition,at))return fail("link_denied");id=AccessDoorService.ensureId(door);kind="door";}
        if(!TerminalLinks.inRange(worldPosition,at))return fail("link_denied");
        Link existing=find(id);
        if(existing!=null)return fail("already_linked");long cameras=links.stream().filter(l->l.kind.equals("camera")).count();if(kind.equals("camera")?cameras>=8:links.size()-cameras>=16)return fail("link_limit");
        if(kind.equals("door")&&level.getBlockEntity(at) instanceof IndustrialDoorEntity door){door.automationOwner=creator;door.sync();}
        if(kind.equals("alarm")&&target instanceof AlarmBlockEntity alarm){alarm.automationOwner=creator;alarm.setChanged();}
        if(kind.equals("panel"))for(BlockPos member:PanelStructure.find(level,at).blocks())if(level.getBlockEntity(member) instanceof PanelBlockEntity panel){panel.automationOwner=creator;panel.setChanged();}
        pause("stopped");links.add(new Link(id,kind,at.immutable()));sync();return success("linked");
    }
    private void action(UUID id,String action,String text){Link link=find(id);if(link==null||!available(link))return;
        if(link.kind.equals("panel")){if(action.equals("panel_text")&&level.getBlockEntity(link.pos) instanceof PanelBlockEntity panel)panel.setAutomationText(creator,id,text);return;}boolean on=action.endsWith("_on")||action.endsWith("_open");if(link.kind.equals("door")&&level.getBlockEntity(link.pos) instanceof IndustrialDoorEntity door&&!door.allowCameraAutomation){status="gui.industrial_facilities.camera.door_opt_in";return;}status="gui.industrial_facilities.camera.running";outputs.put(id,on);AutomationOutputs.request((ServerLevel)level,link.pos,link.kind,id,instanceId,on);if(!on&&otherSource(link))status="gui.industrial_facilities.camera.other_source";}
    private boolean otherSource(Link link){if(!(level instanceof ServerLevel server))return false;var target=level.getBlockEntity(link.pos);return target instanceof IndustrialDoorEntity door?AccessDoorService.shouldOpen(server,door):target instanceof AlarmBlockEntity alarm?alarm.powered():AutomationOutputs.active(server,link.pos,link.kind,link.id);}
    private boolean enqueue(List<CameraProgram.Job> added){if(jobs.size()+added.size()>CameraProgram.MAX_JOBS){pause("job_limit");return false;}jobs.addAll(added);return true;}
    private void cancelEmpty(UUID camera){jobs.removeIf(job->job.empty()&&job.camera().equals(camera));}
    private boolean observe(UUID cameraId,SecurityCameraEntity camera,Map<UUID,Observation> seen,Entity entity,long tick){
        UUID uuid=entity.getUUID();
        Observation observation=seen.get(uuid);
        if(observation==null){
            if(!enqueue(compiled.entryJobs(cameraId,entity,camera.lens(),tick)))return false;
            observation=new Observation();seen.put(uuid,observation);
        }
        observation.misses=0;
        observation.lastSeen=compiled.observeExit(cameraId,entity,camera.lens());
        return true;
    }
    public void tickServer(){
        if(!(level instanceof ServerLevel server))return;
        if((server.getGameTime()+worldPosition.asLong())%20==0)reconcileDoorLinks(true);
        if(!running)return;
        if(AccessTerminalEntity.resolve(level,worldPosition)!=this){pause("stopped");return;}
        if(compiled==null)try{compiled=CameraProgram.compile(program,linkViews());primeStages();}catch(IllegalArgumentException e){pause("invalid_program");return;}
        if(compiled.cameras().isEmpty()){pause("empty_program");return;}
        for(UUID id:compiled.cameras()){Link link=find(id);if(link==null||!available(link)){pause("camera_offline");return;}if(compiled.requiresStages(id)&&server.getBlockEntity(link.pos) instanceof SecurityCameraEntity c){if(!c.stagesEnabled){pause("stages_disabled");return;}if(!Objects.equals(stageResetSeen.get(id),c.stageResetToken())){pause("camera_reconfigured");return;}}}
        long tick=server.getGameTime();boolean scan=tick%5==0;
        Map<UUID,List<Entity>> detectedThisTick=new HashMap<>();
        for(UUID id:compiled.cameras()){
            SecurityCameraEntity camera=(SecurityCameraEntity)server.getBlockEntity(find(id).pos);
            long revision=stageSeen.getOrDefault(id,camera.stageRevision);
            if(camera.stagesEnabled)for(var transition:camera.stageTransitionsAfter(revision))if(!enqueue(compiled.stageJobs(id,transition.stage(),tick)))return;
            stageSeen.put(id,camera.stageRevision);
        }
        if(scan){
            for(UUID id:compiled.cameras()){
                Link link=find(id);SecurityCameraEntity camera=(SecurityCameraEntity)server.getBlockEntity(link.pos);Map<UUID,Observation> seen=visible.computeIfAbsent(id,k->new HashMap<>());boolean occupied=!seen.isEmpty();Set<UUID> now=new HashSet<>();
                var detected=camera.visibleEntities();detectedThisTick.put(id,detected);if(!detected.isEmpty())cancelEmpty(id);
                for(var entity:detected){now.add(entity.getUUID());if(!observe(id,camera,seen,entity,tick))return;}
                var iterator=seen.entrySet().iterator();
                while(iterator.hasNext()){
                    var entity=iterator.next();
                    if(now.contains(entity.getKey()))continue;
                    var observation=entity.getValue();
                    if(++observation.misses>=2){
                        iterator.remove();
                        if(!enqueue(compiled.exitJobs(id,observation.lastSeen,tick)))return;
                    }
                }
                // Delay starts only after the last entity has been absent for two scans.
                if(occupied&&seen.isEmpty()&&!enqueue(compiled.emptyJobs(id,tick)))return;
            }
        }else{
            // A return between normal detection scans must cancel a due timer/continuation
            // before it can close a door. This reads at most the eight linked camera fields.
            Set<UUID> pending=new HashSet<>();for(var job:jobs)if(job.empty())pending.add(job.camera());
            for(UUID id:pending){
                SecurityCameraEntity camera=(SecurityCameraEntity)server.getBlockEntity(find(id).pos);var detected=camera.visibleEntities();detectedThisTick.put(id,detected);if(detected.isEmpty())continue;cancelEmpty(id);
                // Positive observations also arm the next empty transition, even when a
                // brief return ends before the next normal scan. Absences still count at 4 Hz.
                Map<UUID,Observation> seen=visible.computeIfAbsent(id,k->new HashMap<>());
                for(var entity:detected)if(!observe(id,camera,seen,entity,tick))return;
            }
        }
        // A condition reached after a wait uses the current field, even between normal scans.
        java.util.function.Function<UUID,List<Entity>> zone=id->detectedThisTick.computeIfAbsent(id,key->{
            Link link=find(key);
            return link!=null&&available(link)&&server.getBlockEntity(link.pos) instanceof SecurityCameraEntity camera
                ?camera.visibleEntities():null;
        });
        var continuation=jobs.iterator();while(continuation.hasNext())if(continuation.next().advance(tick,this::action,zone))continuation.remove();
        if(!scan)return;
        boolean other=false;var iterator=outputs.entrySet().iterator();while(iterator.hasNext()){var output=iterator.next();Link link=find(output.getKey());if(link==null||!available(link)||link.kind.equals("door")&&server.getBlockEntity(link.pos) instanceof IndustrialDoorEntity door&&!door.allowCameraAutomation){if(link!=null)AutomationOutputs.request(server,link.pos,link.kind,link.id,instanceId,false);iterator.remove();continue;}AutomationOutputs.request(server,link.pos,link.kind,link.id,instanceId,output.getValue());if(!output.getValue()&&otherSource(link))other=true;}
        if(other)status="gui.industrial_facilities.camera.other_source";else if(status.equals("gui.industrial_facilities.camera.other_source"))status="gui.industrial_facilities.camera.running";
    }
    private void release(){if(level instanceof ServerLevel server)AutomationOutputs.release(server,instanceId);outputs.clear();jobs.clear();stageSeen.clear();stageResetSeen.clear();}
    private void pause(String reason){release();visible.clear();running=false;status="gui.industrial_facilities.camera."+reason;sync();}
    @Override public void invalidate(){pause("stopped");links.clear();program=CameraProgram.empty();compiled=null;super.invalidate();}
    @Override public void setRemoved(){release();visible.clear();compiled=null;super.setRemoved();}
    @Override public void onLoad(){super.onLoad();visible.clear();jobs.clear();stageSeen.clear();stageResetSeen.clear();compiled=null;reconcileDoorLinks(false);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider){CompoundTag tag=new CompoundTag();if(creator!=null)tag.putUUID("Creator",creator);tag.putUUID("InstanceId",instanceId);tag.putBoolean("CameraRunning",running);return tag;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider provider){super.saveAdditional(tag,provider);tag.put("CameraProgram",program.copy());tag.putBoolean("CameraRunning",running);ListTag list=new ListTag();for(Link link:links){CompoundTag row=new CompoundTag();row.putUUID("Id",link.id);row.putString("Kind",link.kind);row.putLong("Position",link.pos.asLong());list.add(row);}tag.put("CameraLinks",list);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider provider){super.loadAdditional(tag,provider);links.clear();int cameras=0,targets=0;Set<UUID> ids=new HashSet<>();for(Tag raw:tag.getList("CameraLinks",Tag.TAG_COMPOUND)){CompoundTag row=(CompoundTag)raw;String kind=row.getString("Kind");if(!row.hasUUID("Id")||!Set.of("camera","door","alarm","emitter","panel").contains(kind)||!ids.add(row.getUUID("Id")))continue;if(kind.equals("camera")){if(cameras++>=8)continue;}else if(targets++>=16)continue;links.add(new Link(row.getUUID("Id"),kind,BlockPos.of(row.getLong("Position"))));}program=tag.contains("CameraProgram",Tag.TAG_COMPOUND)?tag.getCompound("CameraProgram").copy():CameraProgram.empty();compiled=null;running=tag.getBoolean("CameraRunning");status="gui.industrial_facilities.camera."+(running?"running":"stopped");outputs.clear();visible.clear();jobs.clear();stageSeen.clear();stageResetSeen.clear();}
}
