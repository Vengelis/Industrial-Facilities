package fr.vengelis.industrialfacilities.block;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import io.netty.buffer.Unpooled;

import fr.vengelis.industrialfacilities.world.inventory.AirlockControllerGUIMenu;
import fr.vengelis.industrialfacilities.procedures.AirlockAutomaticTickProcedure;
import fr.vengelis.industrialfacilities.block.entity.AirlockControllerBlockEntity;

public class AirlockControllerBlock extends Block implements EntityBlock {
	public static final net.minecraft.world.level.block.state.properties.BooleanProperty STRUCTURE_VALID =
		net.minecraft.world.level.block.state.properties.BooleanProperty.create("structure_valid");

	public AirlockControllerBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(5f, 1200f));
		this.registerDefaultState(this.stateDefinition.any().setValue(STRUCTURE_VALID, false));
	}

	@Override
	protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(STRUCTURE_VALID);
	}

	@Override
	public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean moving) {
		super.onPlace(blockstate, world, pos, oldState, moving);
		world.scheduleTick(pos, this, 10);
	}

	@Override
	public void tick(BlockState blockstate, ServerLevel world, BlockPos pos, RandomSource random) {
		super.tick(blockstate, world, pos, random);
		if (!fr.vengelis.industrialfacilities.airlock.AirlockDoorManager.cleanupBrokenFrame(world, pos)) {
			AirlockAutomaticTickProcedure.execute(world, pos.getX(), pos.getY(), pos.getZ());
		}
		fr.vengelis.industrialfacilities.airlock.AirlockStatusManager.updateAppearance(world, pos);
		world.scheduleTick(pos, this, 10);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moving) {
		// A property/model update keeps the same block and must preserve the door.
		if (!state.is(newState.getBlock()) && world instanceof ServerLevel serverLevel) {
			if (world.getBlockEntity(pos) instanceof AirlockControllerBlockEntity controller) {
				fr.vengelis.industrialfacilities.airlock.AirlockDoorManager.removeController(serverLevel, controller);
			}
			serverLevel.getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(pos));
		}
		// Vanilla removes the block entity after we have consumed its saved panel positions.
		super.onRemove(state, world, pos, newState, moving);
	}

	@Override
	public InteractionResult useWithoutItem(BlockState blockstate, Level world, BlockPos pos, Player entity, BlockHitResult hit) {
		super.useWithoutItem(blockstate, world, pos, entity, hit);
		if (entity instanceof ServerPlayer player) {
			player.openMenu(new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return Component.literal("Airlock Controller");
				}

				@Override
				public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
					return new AirlockControllerGUIMenu(id, inventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
				}
			}, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
		BlockEntity tileEntity = worldIn.getBlockEntity(pos);
		return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AirlockControllerBlockEntity(pos, state);
	}

	@Override
	public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
		super.triggerEvent(state, world, pos, eventID, eventParam);
		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity != null && blockEntity.triggerEvent(eventID, eventParam);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos) {
		BlockEntity tileentity = world.getBlockEntity(pos);
		if (tileentity instanceof AirlockControllerBlockEntity be)
			return AbstractContainerMenu.getRedstoneSignalFromContainer(be);
		else
			return 0;
	}
}
