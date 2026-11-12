package fr.vengelis.industrialfacilities.access;

import java.util.*;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** All badge rights and counters come from server-written data, never the item variant. */
public final class AccessBadges {
    public static final String ROOT="IndustrialAccess";
    public static final int MAX_CODES=64,MAX_USES=1_000_000;
    public record Result(boolean ok,String message){public static Result success(String message){return new Result(true,message);}public static Result fail(String message){return new Result(false,message);}}
    public static boolean isBadge(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof AccessBadgeItem;}
    public static boolean renameable(ItemStack stack){return isBadge(stack)||stack.getItem() instanceof fr.vengelis.industrialfacilities.item.TerminalLinkCardItem;}
    public static CompoundTag read(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(ROOT);}
    static void write(ItemStack stack,CompoundTag data){CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.put(ROOT,data));}
    public static List<UUID> codes(ItemStack stack){List<UUID> result=new ArrayList<>();for(Tag t:read(stack).getList("Codes",Tag.TAG_STRING)){try{result.add(UUID.fromString(t.getAsString()));}catch(IllegalArgumentException ignored){}if(result.size()>=MAX_CODES)break;}return result;}
    public static int remaining(ItemStack stack){CompoundTag t=read(stack);return t.contains("Remaining")?Math.clamp(t.getInt("Remaining"),0,MAX_USES):-1;}
    /** Opt-in only: absence on old badges always means private bank storage. */
    public static boolean foreignBankAllowed(ItemStack stack){return isBadge(stack)&&read(stack).getBoolean("ForeignBankAllowed");}
    private static Result editable(Player player,ItemStack stack){
        if(!(player instanceof ServerPlayer)||!isBadge(stack)||stack.getCount()!=1)return Result.fail("Placez un seul badge dans l'encodeur.");
        CompoundTag t=read(stack);if(t.hasUUID("Issuer")&&!t.getUUID("Issuer").equals(player.getUUID()))return Result.fail("Seul le créateur du badge peut modifier son encodage.");
        return BadgeBankData.get(((ServerPlayer)player).server).editable((ServerPlayer)player,stack);
    }
    private static Result prepareEdit(Player player,ItemStack stack){return BadgeBankData.get(((ServerPlayer)player).server).prepareEdit((ServerPlayer)player,stack);}
    private static void archiveEdit(Player player,ItemStack stack){BadgeBankData.get(((ServerPlayer)player).server).archiveEdit((ServerPlayer)player,stack);}
    private static CompoundTag issued(Player player,ItemStack stack){CompoundTag t=read(stack);if(!t.hasUUID("Issuer")){t.putUUID("Issuer",player.getUUID());t.putUUID("BadgeId",UUID.randomUUID());}else if(!t.hasUUID("BadgeId"))t.putUUID("BadgeId",UUID.randomUUID());return t;}
    public static Result encodeCode(Player player,ItemStack stack,String code,boolean add){
        Result editable=editable(player,stack);if(!editable.ok())return editable;
        UUID id;try{id=UUID.fromString(code.trim());}catch(IllegalArgumentException e){return Result.fail("Code de terminal invalide.");}
        AccessRegistry.Entry terminal=AccessRegistry.get(((ServerPlayer)player).server).find(id);
        if(terminal==null||terminal.keypass||!terminal.owner.equals(player.getUUID()))return Result.fail("Ce code doit correspondre à un de vos lecteurs à badge.");
        Result prepared=prepareEdit(player,stack);if(!prepared.ok())return prepared;
        List<UUID> codes=new ArrayList<>(codes(stack));
        if(add){if(codes.contains(id))return Result.fail("Ce code figure déjà sur le badge.");if(codes.size()>=MAX_CODES)return Result.fail("Le badge contient déjà 64 codes.");codes.add(id);}
        else if(!codes.remove(id))return Result.fail("Ce code ne figure pas sur le badge.");
        CompoundTag data=issued(player,stack);ListTag list=new ListTag();for(UUID value:codes)list.add(StringTag.valueOf(value.toString()));data.put("Codes",list);if(add)data.remove("MasterLevel");write(stack,data);archiveEdit(player,stack);
        return Result.success(add?"Code ajouté : badge en mode codes de terminaux.":"Code retiré du badge.");
    }
    public static Result encodeMaster(Player player,ItemStack stack,int level){
        Result editable=editable(player,stack);if(!editable.ok())return editable;
        if(level<1||level>5)return Result.fail("Le niveau doit être compris entre 1 et 5.");
        boolean authorized=AccessRegistry.get(((ServerPlayer)player).server).entries().stream().anyMatch(e->!e.keypass&&e.owner.equals(player.getUUID())&&e.level>=level);
        if(!authorized)return Result.fail("Créez et configurez un lecteur de ce niveau avant d'encoder un passe-partout.");
        Result prepared=prepareEdit(player,stack);if(!prepared.ok())return prepared;
        CompoundTag data=issued(player,stack);data.remove("Codes");data.putInt("MasterLevel",level);write(stack,data);archiveEdit(player,stack);return Result.success("Passe-partout niveau "+level+" : les anciens codes ont été remplacés.");
    }
    public static Result setLimit(Player player,ItemStack stack,int uses){
        Result editable=editable(player,stack);if(!editable.ok())return editable;
        if(uses<0||uses>MAX_USES)return Result.fail("Choisissez de 1 à 1 000 000 utilisations, ou 0 pour illimité.");
        Result prepared=prepareEdit(player,stack);if(!prepared.ok())return prepared;
        CompoundTag data=issued(player,stack);if(uses==0){data.remove("Remaining");data.remove("InitialUses");}else{data.putInt("Remaining",uses);data.putInt("InitialUses",uses);}write(stack,data);archiveEdit(player,stack);
        return Result.success(uses==0?"Badge à utilisations illimitées.":"Limite fixée à "+uses+" utilisations.");
    }
    public static Result setBankSharing(Player player,ItemStack stack,boolean allowed){
        Result editable=editable(player,stack);if(!editable.ok())return editable;
        Result prepared=prepareEdit(player,stack);if(!prepared.ok())return prepared;
        CompoundTag data=issued(player,stack);if(allowed)data.putBoolean("ForeignBankAllowed",true);else data.remove("ForeignBankAllowed");
        write(stack,data);archiveEdit(player,stack);
        return Result.success("gui.industrial_facilities.access."+(allowed?"bank_sharing_enabled":"bank_sharing_disabled"));
    }
    /** Literal, bounded display text shared by the encoder and local bank catalogue. */
    static String cleanName(String name){
        if(name==null||name.length()>64)return null;
        String plain=net.minecraft.ChatFormatting.stripFormatting(name);StringBuilder clean=new StringBuilder();
        plain.codePoints().filter(c->!Character.isISOControl(c)&&Character.getType(c)!=Character.FORMAT
            &&Character.getType(c)!=Character.SURROGATE&&c!=0x00a7).forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }
    /** A display name never grants access, changes the issuer or refills the badge. */
    public static Result rename(Player player,ItemStack stack,String name){
        if(!(player instanceof ServerPlayer)||!renameable(stack)||stack.getCount()!=1)return Result.fail("gui.industrial_facilities.access.insert_card");
        if(isBadge(stack)){Result editable=editable(player,stack);if(!editable.ok())return editable;}
        String normalized=cleanName(name);if(normalized==null)return Result.fail("gui.industrial_facilities.access.invalid_name");
        boolean issuedBadge=isBadge(stack)&&read(stack).hasUUID("Issuer");
        if(issuedBadge){Result prepared=prepareEdit(player,stack);if(!prepared.ok())return prepared;}
        if(normalized.isEmpty())stack.remove(DataComponents.CUSTOM_NAME);
        else stack.set(DataComponents.CUSTOM_NAME,Component.literal(normalized));
        if(issuedBadge){archiveEdit(player,stack);BadgeBankData.get(((ServerPlayer)player).server).encoderNameChanged((ServerPlayer)player,stack);}
        return Result.success("gui.industrial_facilities.access."+(normalized.isEmpty()?"name_cleared":"name_saved"));
    }
    public static Result clear(Player player,ItemStack stack){Result editable=editable(player,stack);if(!editable.ok())return editable;BadgeBankData.get(((ServerPlayer)player).server).clearIdentity((ServerPlayer)player,stack);CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.remove(ROOT));return Result.success("Badge effacé.");}
    public static boolean canUse(ItemStack stack,AccessTerminalEntity terminal){
        if(!isBadge(stack)||stack.getCount()!=1||terminal.encoder||terminal.bank||terminal instanceof KeypassTerminalEntity||terminal.terminalId==null||terminal.creator==null)return false;
        if(terminal.getLevel() instanceof net.minecraft.server.level.ServerLevel level&&BadgeBankData.get(level.getServer()).current(stack).isEmpty())return false;
        if(remaining(stack)==0)return false;
        CompoundTag data=read(stack);if(!data.hasUUID("Issuer"))return false;
        // A pass is scoped to its issuer's terminals, and only levels <= its level.
        if(!data.getUUID("Issuer").equals(terminal.creator))return false;
        int master=Math.clamp(data.getInt("MasterLevel"),0,5);
        return master>0?master>=terminal.accessLevel:codes(stack).contains(terminal.terminalId);
    }
    /** Standalone compatibility helper; actual reader activations use the server-aware overload. */
    public static boolean consume(ItemStack stack){if(!isBadge(stack)||stack.getCount()!=1||remaining(stack)==0)return false;int remaining=remaining(stack);if(remaining>0){CompoundTag data=read(stack);data.putInt("Remaining",remaining-1);write(stack,data);}return true;}
    public static boolean consume(ServerPlayer player,ItemStack stack){return BadgeBankData.get(player.server).consume(player,stack);}
    public static ItemStack normalize(ItemStack stack){
        if(!isBadge(stack))return stack;
        CompoundTag t=read(stack);Item item;
        if(remaining(stack)>=0)item=IndustrialFacilitiesModItems.ACCESS_BADGE_LIMITED.get();
        else if(t.getInt("MasterLevel")>0)item=IndustrialFacilitiesModItems.ACCESS_BADGE_MASTER.get();
        else if(!codes(stack).isEmpty())item=IndustrialFacilitiesModItems.ACCESS_BADGE_ENCODED.get();
        else item=IndustrialFacilitiesModItems.ACCESS_BADGE_BLANK.get();
        return stack.is(item)?stack:stack.transmuteCopy(item);
    }
    public static List<Component> describe(ItemStack stack){
        List<Component> lines=new ArrayList<>();CompoundTag t=read(stack);int count=codes(stack).size(),master=Math.clamp(t.getInt("MasterLevel"),0,5);
        if(count==0&&master==0)lines.add(Component.literal("Non encodé : aucun accès."));
        if(count>0)lines.add(Component.literal("Terminaux autorisés : "+count));
        if(master>0)lines.add(Component.literal("Passe-partout personnel : niveaux 1 à "+master));
        int remaining=remaining(stack);lines.add(Component.literal(remaining<0?"Utilisations illimitées":"Utilisations restantes : "+remaining));
        if(remaining==0)lines.add(Component.literal("ÉPUISÉ"));
        return lines;
    }
    private AccessBadges(){}
}
