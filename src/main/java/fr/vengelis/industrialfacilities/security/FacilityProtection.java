package fr.vengelis.industrialfacilities.security;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.AccessRegistry;
import fr.vengelis.industrialfacilities.access.AccessTerminalBlock;
import fr.vengelis.industrialfacilities.access.AccessTerminalEntity;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import fr.vengelis.industrialfacilities.door.IndustrialDoorBlock;
import fr.vengelis.industrialfacilities.door.IndustrialDoorEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Server-side protection. A bypass is personal, opt-in and valid for this login only. */
@EventBusSubscriber(modid=IndustrialFacilitiesMod.MODID)
public final class FacilityProtection {
    private static final int ADMIN_LEVEL=2;
    private static final String MESSAGE="protection.industrial_facilities.";
    private static final Map<MinecraftServer,Set<UUID>> BYPASS=new WeakHashMap<>();

    private static boolean protectedBlock(Block block){return block instanceof IndustrialDoorBlock||block instanceof AccessTerminalBlock||block instanceof SecurityCameraBlock;}
    private static UUID placer(BlockEntity entity){
        if(entity instanceof IndustrialDoorEntity door)return door.placedBy;
        if(entity instanceof AccessTerminalEntity terminal)return terminal.creator;
        if(entity instanceof SecurityCameraEntity camera)return camera.creator;
        return null;
    }
    private static boolean bypassing(ServerPlayer player){return player.hasPermissions(ADMIN_LEVEL)&&BYPASS.getOrDefault(player.server,Set.of()).contains(player.getUUID());}
    private static int setBypass(ServerPlayer player,boolean enabled){
        if(enabled)BYPASS.computeIfAbsent(player.server,s->new HashSet<>()).add(player.getUUID());
        else clearBypass(player);
        player.sendSystemMessage(Component.translatable(MESSAGE+(enabled?"bypass_on":"bypass_off")));return 1;
    }
    private static void clearBypass(ServerPlayer player){var users=BYPASS.get(player.server);if(users!=null){users.remove(player.getUUID());if(users.isEmpty())BYPASS.remove(player.server);}}

    private static GameProfile resolvePlayer(MinecraftServer server,String name){
        if(!name.matches("[A-Za-z0-9_]{1,16}"))return null;
        var online=server.getPlayerList().getPlayerByName(name);
        if(online!=null)return online.getGameProfile();
        var cache=server.getProfileCache();if(cache==null)return null;
        // Offline servers synthesize UUIDs even for typos; accept only a profile that has joined.
        return cache.get(name).filter(profile->profile.getId()!=null&&(server.usesAuthentication()
            ||Files.isRegularFile(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(profile.getId()+".dat")))).orElse(null);
    }
    private static int assignOwner(CommandSourceStack source,String name)throws CommandSyntaxException{
        var admin=source.getPlayerOrException();var level=admin.serverLevel();
        if(!admin.isAlive())return assignmentFailure(source,"owner_target");
        var eye=admin.getEyePosition();var direction=admin.calculateViewVector(admin.getXRot(),admin.getYRot());
        var hit=level.clip(new ClipContext(eye,eye.add(direction.scale(admin.blockInteractionRange())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,admin));
        if(hit.getType()!=HitResult.Type.BLOCK||!TerminalLinks.loaded(level,hit.getBlockPos()))return assignmentFailure(source,"owner_target");
        var pos=hit.getBlockPos();var state=level.getBlockState(pos);var entity=level.getBlockEntity(pos);
        if(!protectedBlock(state.getBlock())||entity==null||entity.isRemoved())return assignmentFailure(source,"owner_target");
        if(placer(entity)!=null)return assignmentFailure(source,"owner_exists");
        GameProfile profile=resolvePlayer(level.getServer(),name);if(profile==null)return assignmentFailure(source,"owner_unknown",name);
        UUID id=profile.getId();
        if(entity instanceof IndustrialDoorEntity door){door.placedBy=id;door.setChanged();}
        else if(entity instanceof SecurityCameraEntity camera){camera.creator=id;camera.sync();}
        else if(entity instanceof AccessTerminalEntity terminal){
            var root=AccessTerminalEntity.resolve(level,pos);
            if(root!=null){
                var partner=state.getValue(AccessTerminalBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos.above();
                UUID partnerOwner=placer(level.getBlockEntity(partner));
                if(partnerOwner!=null&&!partnerOwner.equals(id))return assignmentFailure(source,"owner_conflict");
            }
            var registry=AccessRegistry.get(level.getServer());var codeHolder=root==null?terminal:root;
            var registration=codeHolder.terminalId==null?null:registry.find(codeHolder.terminalId);
            if(registration!=null&&(!registration.owner.equals(id)||!registration.position.equals(codeHolder.getBlockPos())
                ||!registration.dimension.equals(level.dimension().location().toString())))return assignmentFailure(source,"owner_conflict");
            terminal.creator=id;terminal.sync();
            AccessTerminalBlock.tryAssemble(level,pos);AccessTerminalBlock.tryAssemble(level,pos.below());
            var assembled=AccessTerminalEntity.resolve(level,pos);
            if(assembled!=null&&!assembled.encoder&&!assembled.bank)registry.put(assembled);
        }else return assignmentFailure(source,"owner_target");
        source.sendSuccess(()->Component.translatable(MESSAGE+"owner_assigned",state.getBlock().getName(),profile.getName(),pos.toShortString()),false);
        return 1;
    }
    private static int assignmentFailure(CommandSourceStack source,String key,Object... args){source.sendFailure(Component.translatable(MESSAGE+key,args));return 0;}

    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("industrialfacilities")
            .then(Commands.literal("bypass").requires(source->source.hasPermission(ADMIN_LEVEL))
                .then(Commands.literal("on").executes(ctx->setBypass(ctx.getSource().getPlayerOrException(),true)))
                .then(Commands.literal("off").executes(ctx->setBypass(ctx.getSource().getPlayerOrException(),false))))
            .then(Commands.literal("owner").requires(source->source.hasPermission(ADMIN_LEVEL))
                .then(Commands.argument("player",StringArgumentType.word())
                    .suggests((ctx,builder)->SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(),builder))
                    .executes(ctx->assignOwner(ctx.getSource(),StringArgumentType.getString(ctx,"player"))))));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)clearBypass(player);}

    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getPlayer() instanceof ServerPlayer player)
            ||!protectedBlock(event.getState().getBlock())||bypassing(player))return;
        UUID owner=placer(level.getBlockEntity(event.getPos()));
        if(player.getUUID().equals(owner))return;
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable(MESSAGE+(owner==null?"unowned":"owner_only")),true);
    }
    @SubscribeEvent public static void detonate(ExplosionEvent.Detonate event){
        if(event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(pos->level.hasChunkAt(pos)&&protectedBlock(level.getBlockState(pos).getBlock()));
    }
    private FacilityProtection(){}
}
