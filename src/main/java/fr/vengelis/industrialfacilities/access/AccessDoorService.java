package fr.vengelis.industrialfacilities.access;

import java.util.*;
import fr.vengelis.industrialfacilities.door.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AccessDoorService {
    public static IndustrialDoorEntity resolveDoor(Level level,BlockPos pos){
        if(!loaded(level,pos)||!(level.getBlockEntity(pos) instanceof IndustrialDoorEntity part))return null;
        if(part.size>0)return part;
        return part.owner!=null&&loaded(level,part.owner)&&level.getBlockEntity(part.owner) instanceof IndustrialDoorEntity root&&root.size>0?root:null;
    }
    private static boolean loaded(Level level,BlockPos pos){return level instanceof ServerLevel server?TerminalLinks.loaded(server,pos):level.hasChunkAt(pos);}
    public static UUID ensureId(IndustrialDoorEntity door){if(door.doorInstanceId==null){door.doorInstanceId=UUID.randomUUID();door.sync();}return door.doorInstanceId;}
    public static boolean isSecured(IndustrialDoorEntity door){return door.getLevel() instanceof ServerLevel level&&door.doorInstanceId!=null&&!AccessRegistry.get(level.getServer()).readers(door.doorInstanceId).isEmpty();}
    public static boolean canConfigure(Player player,IndustrialDoorEntity door){
        if(!(door.getLevel() instanceof ServerLevel level)||player.level()!=level||door.size==0)return false;
        return canConfigureOwner(player.getUUID(),door);
    }
    public static boolean canConfigureOwner(UUID owner,IndustrialDoorEntity door){
        if(owner==null||!(door.getLevel() instanceof ServerLevel level)||door.size==0||door.automationOwner!=null&&!door.automationOwner.equals(owner))return false;
        var readers=door.doorInstanceId==null?List.<AccessRegistry.Entry>of():AccessRegistry.get(level.getServer()).readers(door.doorInstanceId);
        return readers.stream().allMatch(e->e.owner.equals(owner));
    }
    public static boolean setAllowRedstone(Player player,IndustrialDoorEntity door,boolean allow){if(!player.isAlive()||player.distanceToSqr(door.getBlockPos().getCenter())>64||!canConfigure(player,door))return false;door.allowLinkedRedstone=allow;door.sync();return true;}
    public static boolean setAllowCameraAutomation(Player player,IndustrialDoorEntity door,boolean allow){if(!player.isAlive()||player.distanceToSqr(door.getBlockPos().getCenter())>64||!canConfigure(player,door))return false;door.allowCameraAutomation=allow;if(allow&&door.automationOwner==null)door.automationOwner=player.getUUID();door.sync();return true;}
    public static boolean shouldOpen(ServerLevel level,IndustrialDoorEntity door){return level.getGameTime()<door.accessOpenUntil||((!isSecured(door)||door.allowLinkedRedstone)&&level.hasNeighborSignal(door.getBlockPos()))||(door.allowCameraAutomation&&fr.vengelis.industrialfacilities.security.AutomationOutputs.active(level,door.getBlockPos(),"door",door.doorInstanceId));}
    /** Keep the open display through closing/obstruction, not just the badge lease. */
    public static boolean readerGranted(AccessTerminalEntity terminal){
        if(!(terminal.getLevel() instanceof ServerLevel source)||terminal.encoder||terminal.bank||terminal.terminalId==null
            ||AccessTerminalEntity.resolve(source,terminal.getBlockPos())!=terminal)return false;
        var entry=AccessRegistry.get(source.getServer()).find(terminal.terminalId);
        if(TerminalLinks.loadedReader(source,entry)!=terminal||entry.doors.isEmpty())return false;
        boolean unknown=false;
        for(var link:entry.doors){
            if(!TerminalLinks.admissible(source,terminal.getBlockPos(),link))continue;
            if(!TerminalLinks.loaded(source,link.position())){unknown=true;continue;}
            var door=TerminalLinks.loadedDoor(source,link);if(door==null)continue;
            if(door.accessOpenUntil>source.getGameTime()||door.targetOpen||door.progress>0)return true;
        }
        // An unloaded door is not evidence that it closed. Retain the last
        // observed state until its chunk returns, without forcing it to load.
        return unknown&&terminal.accessGranted;
    }
    public static void dismantle(ServerLevel level,IndustrialDoorEntity door){if(door.doorInstanceId!=null)AccessRegistry.get(level.getServer()).invalidateDoor(door.doorInstanceId);door.doorInstanceId=null;door.accessOpenUntil=0;door.allowLinkedRedstone=false;door.allowCameraAutomation=false;door.automationOwner=null;}
    public static AccessBadges.Result link(Player player,UUID source,IndustrialDoorEntity door){
        if(!(player instanceof ServerPlayer serverPlayer)||!(door.getLevel() instanceof ServerLevel level))return AccessBadges.Result.fail("Liaison impossible.");
        AccessRegistry registry=AccessRegistry.get(level.getServer());AccessRegistry.Entry reader=registry.find(source);
        if(reader==null||!reader.owner.equals(player.getUUID()))return AccessBadges.Result.fail("Vous n'êtes pas le créateur de ce terminal.");
        if(!player.isAlive()||player.level()!=level||TerminalLinks.loadedReader(level,reader)==null)return AccessBadges.Result.fail("Le terminal lecteur doit être assemblé et chargé dans cette dimension.");
        if(!TerminalLinks.loaded(level,door.getBlockPos())||resolveDoor(level,door.getBlockPos())!=door)return AccessBadges.Result.fail("La porte doit être assemblée et chargée.");
        if(!TerminalLinks.inRange(reader.position,door.getBlockPos()))return AccessBadges.Result.fail("La porte doit se trouver à 64 blocs maximum du terminal lecteur.");
        if(!canConfigure(player,door))return AccessBadges.Result.fail("Cette porte appartient déjà à un autre réseau d'accès.");
        UUID id=ensureId(door);
        var link=new AccessRegistry.DoorLink(level.dimension().location().toString(),door.getBlockPos(),id);
        if(reader.doors.contains(link))return AccessBadges.Result.fail("Cette porte est déjà liée au terminal.");
        boolean firstBinding=!isSecured(door);
        if(!registry.link(source,link))return AccessBadges.Result.fail("Ce terminal a atteint la limite de 64 portes.");
        if(firstBinding)door.allowLinkedRedstone=false;
        door.sync();return AccessBadges.Result.success("Porte liée au terminal "+source+".");
    }
    public static AccessBadges.Result activate(Player player,AccessTerminalEntity terminal,ItemStack badge){
        if(!(player instanceof ServerPlayer serverPlayer)||!(terminal.getLevel() instanceof ServerLevel source)||terminal.encoder||terminal.bank||terminal.terminalId==null)return AccessBadges.Result.fail("Ce lecteur n'est pas configuré.");
        if(!player.isAlive()||player.level()!=source||player.distanceToSqr(terminal.getBlockPos().getCenter())>64||AccessTerminalEntity.resolve(source,terminal.getBlockPos())!=terminal)return AccessBadges.Result.fail("Le terminal est hors de portée.");
        var registry=AccessRegistry.get(source.getServer());var entry=registry.find(terminal.terminalId);
        if(TerminalLinks.loadedReader(source,entry)!=terminal)return AccessBadges.Result.fail("Ce lecteur n'est plus enregistré.");
        if(!AccessBadges.canUse(badge,terminal))return AccessBadges.Result.fail(AccessBadges.remaining(badge)==0?"Ce badge est épuisé.":"Accès refusé : badge ou niveau insuffisant.");
        List<IndustrialDoorEntity> usable=new ArrayList<>();
        for(var link:entry.doors){
            if(!TerminalLinks.admissible(source,terminal.getBlockPos(),link))continue;
            var door=TerminalLinks.loadedDoor(source,link);
            if(door!=null&&door.accessOpenUntil<=source.getGameTime()&&!door.targetOpen)usable.add(door);
        }
        if(usable.isEmpty())return AccessBadges.Result.fail("Aucune porte liée disponible à ouvrir.");
        if(!AccessBadges.consume(serverPlayer,badge))return AccessBadges.Result.fail("Ce badge est épuisé.");
        for(var door:usable){door.accessOpenUntil=door.getLevel().getGameTime()+100;door.sync();}
        terminal.grantedUntil=source.getGameTime()+100;terminal.accessGranted=true;terminal.sync();
        return AccessBadges.Result.success("Accès autorisé.");
    }
    private AccessDoorService(){}
}
