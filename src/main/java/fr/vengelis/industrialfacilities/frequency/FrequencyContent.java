package fr.vengelis.industrialfacilities.frequency;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;
import fr.vengelis.industrialfacilities.access.ui.AccessMenu;
import fr.vengelis.industrialfacilities.init.IndustrialFacilitiesModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.DeferredItem;

@EventBusSubscriber(modid = IndustrialFacilitiesMod.MODID)
public final class FrequencyContent {
    public static final String UI = "gui.industrial_facilities.frequency.";
    public static final DeferredItem<Item> CHIP = IndustrialFacilitiesModItems.TERMINAL_LINK_CARD;
    public static MenuType<FrequencyMenu> MENU;
    static Consumer<Names> namesReceiver = packet -> {};
    static Function<FrequencyChip, Optional<String>> displayNames = chip -> Optional.empty();

    public static String displayName(ItemStack stack, FrequencyChip chip) {
        return displayNames.apply(chip).orElseGet(() -> FrequencyChip.savedName(stack));
    }

    public static ResourceLocation id(String value) {
        return ResourceLocation.fromNamespaceAndPath(IndustrialFacilitiesMod.MODID, value);
    }

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.MENU, registry -> {
            MENU = IMenuTypeExtension.create(FrequencyMenu::new);
            registry.register(id("frequency"), MENU);
        });
    }

    public static void tooltip(ItemStack stack, List<Component> lines) {
        FrequencyChip chip = FrequencyChip.read(stack);
        if (chip != null) {
            lines.add(Component.translatable(UI + (chip.issuer() == null ? "public_chip" : "private_chip"), chip.number()));
            lines.add(FrequencyChip.mode(stack).label());
        }
        lines.add(Component.translatable(UI + (chip == null ? "blank_hint" : "configure_hint")));
    }

    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(CHIP.get())
            || !(event.getEntity() instanceof ServerPlayer player)) return;
        var pos = event.getPos();
        FrequencyEndpoint device = FrequencyEndpoint.resolve(player.serverLevel(), pos);
        if (device == null) return;
        FrequencyMenu.openDevice(player, device, pos);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    public record Encode(int menu, int operation, int frequency, String name, int mode) implements CustomPacketPayload {
        public Encode(int menu, int operation, int frequency, String name) { this(menu, operation, frequency, name, -1); }
        public Encode(int menu, int operation, int frequency) { this(menu, operation, frequency, ""); }
        static final Type<Encode> TYPE = new Type<>(id("encode_frequency"));
        static final StreamCodec<RegistryFriendlyByteBuf, Encode> CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.menu);
                buf.writeByte(data.operation);
                buf.writeVarInt(data.frequency);
                buf.writeUtf(data.name, FrequencyNames.MAX_LENGTH);
                buf.writeByte(data.mode);
            },
            buf -> new Encode(buf.readVarInt(), buf.readUnsignedByte(), buf.readVarInt(), buf.readUtf(FrequencyNames.MAX_LENGTH), buf.readByte()));
        @Override public Type<Encode> type() { return TYPE; }
    }

    public record OpenSlots(BlockPos pos) implements CustomPacketPayload {
        static final Type<OpenSlots> TYPE = new Type<>(id("open_frequency_slots"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenSlots> CODEC =
            StreamCodec.of((buf, message) -> buf.writeBlockPos(message.pos), buf -> new OpenSlots(buf.readBlockPos()));
        @Override public Type<OpenSlots> type() { return TYPE; }
    }

    record Names(boolean reset, List<FrequencyNames.Entry> entries) implements CustomPacketPayload {
        static final int MAX_ENTRIES = 128;
        static final Type<Names> TYPE = new Type<>(id("frequency_names"));
        static final StreamCodec<RegistryFriendlyByteBuf, Names> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeBoolean(packet.reset);
            buf.writeVarInt(packet.entries.size());
            for (var entry : packet.entries) {
                buf.writeVarInt(entry.frequency().number());
                buf.writeNullable(entry.frequency().issuer(), (out, issuer) -> out.writeUUID(issuer));
                buf.writeUtf(entry.name(), FrequencyNames.MAX_LENGTH);
            }
        }, buf -> {
            boolean reset = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) throw new io.netty.handler.codec.DecoderException("Invalid frequency name list");
            var entries = new ArrayList<FrequencyNames.Entry>(count);
            for (int i = 0; i < count; i++) {
                var chip = new FrequencyChip(buf.readVarInt(), buf.readNullable(in -> in.readUUID()));
                entries.add(new FrequencyNames.Entry(chip, buf.readUtf(FrequencyNames.MAX_LENGTH)));
            }
            return new Names(reset, List.copyOf(entries));
        });
        @Override public Type<Names> type() { return TYPE; }
    }

    public record CatalogEntry(int number, boolean privateFrequency, String name) {}

    public record BrowseCatalogue(int menu, int offset) implements CustomPacketPayload {
        static final Type<BrowseCatalogue> TYPE = new Type<>(id("browse_frequency_catalogue"));
        static final StreamCodec<RegistryFriendlyByteBuf, BrowseCatalogue> CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeVarInt(packet.menu); buf.writeVarInt(packet.offset); },
            buf -> new BrowseCatalogue(buf.readVarInt(), buf.readVarInt()));
        @Override public Type<BrowseCatalogue> type() { return TYPE; }
    }

    public record EditCatalogue(int menu, int number, boolean privateFrequency, boolean remove, String name)
            implements CustomPacketPayload {
        static final Type<EditCatalogue> TYPE = new Type<>(id("edit_frequency_catalogue"));
        static final StreamCodec<RegistryFriendlyByteBuf, EditCatalogue> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeVarInt(packet.menu);
            buf.writeVarInt(packet.number);
            buf.writeBoolean(packet.privateFrequency);
            buf.writeBoolean(packet.remove);
            buf.writeUtf(packet.name, FrequencyNames.MAX_LENGTH);
        }, buf -> new EditCatalogue(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
            buf.readUtf(FrequencyNames.MAX_LENGTH)));
        @Override public Type<EditCatalogue> type() { return TYPE; }
    }

    public record CatalogWindow(int menu, int first, int total, List<CatalogEntry> entries) implements CustomPacketPayload {
        static final int MAX_ENTRIES = 64;
        static final Type<CatalogWindow> TYPE = new Type<>(id("frequency_catalogue"));
        static final StreamCodec<RegistryFriendlyByteBuf, CatalogWindow> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeVarInt(packet.menu);
            buf.writeVarInt(packet.first);
            buf.writeVarInt(packet.total);
            buf.writeVarInt(packet.entries.size());
            for (CatalogEntry entry : packet.entries) {
                buf.writeVarInt(entry.number);
                buf.writeBoolean(entry.privateFrequency);
                buf.writeUtf(entry.name, FrequencyNames.MAX_LENGTH);
            }
        }, buf -> {
            int menu = buf.readVarInt(), first = buf.readVarInt(), total = buf.readVarInt(), count = buf.readVarInt();
            if (first < 0 || first > total || count < 0 || count > MAX_ENTRIES || count > total - first)
                throw new io.netty.handler.codec.DecoderException("Invalid frequency catalogue window");
            var entries = new ArrayList<CatalogEntry>(count);
            for (int i = 0; i < count; i++)
                entries.add(new CatalogEntry(buf.readVarInt(), buf.readBoolean(), buf.readUtf(FrequencyNames.MAX_LENGTH)));
            return new CatalogWindow(menu, first, total, List.copyOf(entries));
        });
        @Override public Type<CatalogWindow> type() { return TYPE; }

        public CatalogEntry entryAt(int index) {
            return index >= first && index - first < entries.size() ? entries.get(index - first) : null;
        }

        public boolean covers(int offset, int visibleRows) {
            return offset >= first && Math.min((long) offset + visibleRows, total) <= first + entries.size();
        }
    }

    @SubscribeEvent public static void packets(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("frequency_v7");
        registrar.playToServer(EditCatalogue.TYPE, EditCatalogue.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AccessMenu menu
                    && menu.containerId == packet.menu) menu.editCatalogue(player, packet);
        }));
        registrar.playToClient(CatalogWindow.TYPE, CatalogWindow.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof AccessMenu menu) menu.receiveCatalogue(packet);
        }));
        registrar.playToServer(BrowseCatalogue.TYPE, BrowseCatalogue.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AccessMenu menu
                    && menu.containerId == packet.menu) menu.browseCatalogue(player, packet.offset);
        }));
        registrar.playToClient(Names.TYPE, Names.CODEC, (packet, context) -> context.enqueueWork(() -> namesReceiver.accept(packet)));
        registrar.playToServer(OpenSlots.TYPE, OpenSlots.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || player.distanceToSqr(packet.pos.getCenter()) > 64) return;
            if (!(player.pick(player.blockInteractionRange(), 1f, false) instanceof BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !hit.getBlockPos().equals(packet.pos)) return;
            FrequencyEndpoint endpoint = FrequencyEndpoint.resolve(player.serverLevel(), packet.pos);
            if (endpoint != null) FrequencyMenu.openDevice(player, endpoint, packet.pos);
        }));
        registrar.playToServer(Encode.TYPE, Encode.CODEC, (packet, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AccessMenu menu
                        && menu.containerId == packet.menu) menu.encodeFrequency(player, packet);
            }));
    }
    private FrequencyContent() {}
}

