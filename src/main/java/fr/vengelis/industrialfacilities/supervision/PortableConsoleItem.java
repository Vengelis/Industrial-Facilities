package fr.vengelis.industrialfacilities.supervision;

import java.util.List;
import java.util.UUID;
import fr.vengelis.industrialfacilities.access.TerminalLinks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PortableConsoleItem extends Item {
    record Connection(ServerLevel level, SupervisionData.Console console) {}

    public PortableConsoleItem() { super(new Properties().stacksTo(1)); }

    static void pair(ItemStack stack, ServerLevel level, SupervisionData.Console console) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID("Console", console.id());
            tag.putString("Dimension", level.dimension().location().toString());
            tag.remove("Sector");
        });
    }

    private static CompoundTag binding(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static Connection resolve(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof PortableConsoleItem)) return null;
        var tag = binding(stack);
        if (!tag.hasUUID("Console")) return null;
        var dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dim == null) return null;
        for (var level : player.server.getAllLevels()) {
            if (!level.dimension().location().equals(dim)) continue;
            var ref = resolveConsole(level, player.getUUID(), tag.getUUID("Console"));
            return ref == null ? null : new Connection(level, ref);
        }
        return null;
    }

    static SupervisionData.Console resolveConsole(ServerLevel level, UUID owner, UUID id) {
        var ref = SupervisionData.get(level).console(id);
        if (ref == null || !ref.sector().owner().equals(owner)) return null;
        if (TerminalLinks.loaded(level, ref.pos())
                && (!(level.getBlockEntity(ref.pos()) instanceof SupervisionConsoleEntity entity)
                    || entity.isDisplay() || !entity.assembled() || !ref.id().equals(entity.consoleId))) return null;
        return ref;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            var connection = resolve(server, stack);
            if (connection == null) player.displayClientMessage(Component.translatable(NetworkView.UI + "unpaired"), true);
            else SupervisionMenu.openPortable(server, stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var tag = binding(stack);
        if (tag.hasUUID("Console")) lines.add(Component.translatable(NetworkView.UI + "paired_console"));
        lines.add(Component.translatable(NetworkView.UI + "portable_hint"));
    }
}
