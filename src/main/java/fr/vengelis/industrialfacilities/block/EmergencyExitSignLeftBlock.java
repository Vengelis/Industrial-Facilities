package fr.vengelis.industrialfacilities.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import com.google.common.collect.ImmutableMap;

public class EmergencyExitSignLeftBlock extends Block implements SimpleWaterloggedBlock {
	public static final DirectionProperty FACING = DirectionalBlock.FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	public static final EnumProperty<MountProperty> MOUNT = EnumProperty.create("mount", MountProperty.class);
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	private final ImmutableMap<BlockState, VoxelShape> shapes = this.makeShapes();
	public static final DirectionProperty HORIZONTAL_FACING = DirectionProperty.create("horizontal_facing", Direction.Plane.HORIZONTAL);

	public EmergencyExitSignLeftBlock() {
	    super(BlockBehaviour.Properties.of()
	        .sound(SoundType.LANTERN)
	        .strength(1f, 10f)
	        .noOcclusion()
	        .lightLevel(state -> state.getValue(POWERED) ? 7 : 0)
	        .isRedstoneConductor((bs, br, bp) -> false));
	
	    this.registerDefaultState(this.stateDefinition.any()
	        .setValue(FACING, Direction.NORTH)
	        .setValue(HORIZONTAL_FACING, Direction.NORTH)
	        .setValue(MOUNT, MountProperty.WALL)
	        .setValue(POWERED, false)
	        .setValue(WATERLOGGED, false));
	}

	private ImmutableMap<BlockState, VoxelShape> makeShapes() {
		return this.getShapeForEachState(state -> {
			if (state.getValue(MOUNT) == MountProperty.CEILING) {
				if (state.getValue(FACING) == Direction.DOWN) {
				    return switch (state.getValue(HORIZONTAL_FACING)) {
				        case NORTH, SOUTH -> box(3, 10, 7, 13, 16, 9);
				        case EAST, WEST   -> box(7, 10, 3, 9, 16, 13);
				        default           -> box(3, 10, 7, 13, 16, 9);
				    };
				}
			}
			
			return switch (state.getValue(FACING)) {
			    case NORTH -> box(3, 5, 14, 13, 11, 16);
			    case EAST  -> box(0, 5, 3, 2, 11, 13);
			    case SOUTH -> box(3, 5, 0, 13, 11, 2);
			    case WEST  -> box(14, 5, 3, 16, 11, 13);
			    case UP    -> box(3, 0, 5, 13, 2, 11);
			    case DOWN  -> box(3, 14, 5, 13, 16, 11);
			    default    -> box(3, 5, 14, 13, 11, 16);
			};

		});
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return shapes.get(state);
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
		return state.getFluidState().isEmpty();
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return propagatesSkylightDown(state, worldIn, pos) ? 0 : 1;
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	    super.createBlockStateDefinition(builder);
	    builder.add(FACING, HORIZONTAL_FACING, MOUNT, POWERED, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
	    Direction face = context.getClickedFace();
	    
	
	    // Interdire le placement au sol
	    if (face == Direction.UP) {
	        return null;
	    }
	
	    MountProperty mount = face == Direction.DOWN
	        ? MountProperty.CEILING
	        : MountProperty.WALL;
	
	    boolean waterlogged = context.getLevel()
	        .getFluidState(context.getClickedPos())
	        .getType() == Fluids.WATER;
	
	    BlockState state = this.defaultBlockState()
	        // Fixation : mur ou plafond
	        .setValue(FACING, face)
	
	        // Orientation horizontale du joueur, savec inversion
	        .setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
	
	        .setValue(MOUNT, mount)
	        .setValue(POWERED,
    			hasRedstonePower(context.getLevel(), context.getClickedPos()))
	        .setValue(WATERLOGGED, waterlogged);

	    System.out.println(
		    "[LEFT DEBUG] Face = " + face
		    + " | Mount = " + state.getValue(MOUNT)
		);
	
	    return state.canSurvive(context.getLevel(), context.getClickedPos())
	        ? state
	        : null;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
	    return state
	        .setValue(FACING, rot.rotate(state.getValue(FACING)))
	        .setValue(HORIZONTAL_FACING,
	                  rot.rotate(state.getValue(HORIZONTAL_FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
	    return this.rotate(
	        state,
	        mirror.getRotation(state.getValue(
	            state.getValue(MOUNT) == MountProperty.CEILING
	                ? HORIZONTAL_FACING
	                : FACING
	        ))
	    );
	}
	
	@Override
	public void animateTick(BlockState state, Level level,
	                        BlockPos pos, RandomSource random) {
	
	    super.animateTick(state, level, pos, random);
	
	    if (!state.getValue(POWERED)) {
	        return;
	    }
	
	    // Intensité des particules
	    if (random.nextInt(3) != 0) {
	        return;
	    }
	
	    DustParticleOptions greenGlow = new DustParticleOptions(
	        new Vector3f(0.1f, 0.95f, 0.25f),
	        0.5f
	    );
	
	    // Récupérer les limites de la hitbox
	    AABB bounds = state.getShape(level, pos).bounds();
	
	    double x;
	    double y;
	    double z;
	
	    if (state.getValue(MOUNT) == MountProperty.CEILING) {
	
	        // Particules autour du panneau suspendu
	        x = pos.getX() + bounds.minX
	            + random.nextDouble() * (bounds.maxX - bounds.minX);
	
	        y = pos.getY() + bounds.minY - 0.08;
	
	        z = pos.getZ() + bounds.minZ
	            + random.nextDouble() * (bounds.maxZ - bounds.minZ);
	
	    } else {
	
	        // Particules devant la surface du panneau mural
	        Direction facing = state.getValue(FACING);
	
	        x = pos.getX() + (bounds.minX + bounds.maxX) / 2;
	        y = pos.getY() + bounds.minY
	            + random.nextDouble() * (bounds.maxY - bounds.minY);
	        z = pos.getZ() + (bounds.minZ + bounds.maxZ) / 2;
	
	        // Petite dispersion le long du panneau
	        if (facing.getAxis() == Direction.Axis.Z) {
	            x += (random.nextDouble() - 0.5)
	                * (bounds.maxX - bounds.minX);
	        } else {
	            z += (random.nextDouble() - 0.5)
	                * (bounds.maxZ - bounds.minZ);
	        }
	
	        // Positionner les particules juste devant la face
	        switch (facing) {
			    case NORTH -> z = pos.getZ() + bounds.minZ - 0.08;
			    case SOUTH -> z = pos.getZ() + bounds.maxZ + 0.08;
			    case EAST  -> x = pos.getX() + bounds.maxX + 0.08;
			    case WEST  -> x = pos.getX() + bounds.minX - 0.08;
			    default -> {}
			}
	    }
	
	    level.addParticle(
	        greenGlow,
	        x, y, z,
	        0, 0, 0
	    );
	}
	
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos,
	                            Block neighborBlock, BlockPos neighborPos,
	                            boolean movedByPiston) {
	
	    super.neighborChanged(state, level, pos, neighborBlock,
	                          neighborPos, movedByPiston);
	
	    if (level.isClientSide()) {
	        return;
	    }
	
	    boolean powered = hasRedstonePower(level, pos);
	
	    BlockState currentState = level.getBlockState(pos);
	
	    if (currentState.is(this)
	            && currentState.getValue(POWERED) != powered) {
	
	        level.setBlock(
	            pos,
	            currentState.setValue(POWERED, powered),
	            3
	        );
	    }
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
	    Direction facing = state.getValue(FACING);

	    // Le panneau ne peut pas être fixé au sol
	    if (facing == Direction.UP) {
	        return false;
	    }
	
	    // Vérification de la fixation au plafond
	    if (state.getValue(MOUNT) == MountProperty.CEILING) {
	        if (facing != Direction.DOWN) {
	            return false;
	        }
	
	        BlockPos supportPos = pos.above();
	
	        return world.getBlockState(supportPos)
	            .isFaceSturdy(world, supportPos, Direction.DOWN);
	    }
	
	    // Vérification de la fixation murale
	    if (!facing.getAxis().isHorizontal()) {
	        return false;
	    }
	
	    BlockPos supportPos = pos.relative(facing.getOpposite());
	
	    return world.getBlockState(supportPos)
	        .isFaceSturdy(world, supportPos, facing);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos currentPos, BlockPos facingPos) {
		if (state.getValue(WATERLOGGED)) {
			world.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		}
		return !state.canSurvive(world, currentPos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, facing, facingState, world, currentPos, facingPos);
	}

	@Override
	public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
		return true;
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState blockstate,
	                        LivingEntity entity, ItemStack itemstack) {
	    super.setPlacedBy(world, pos, blockstate, entity, itemstack);
	}

	
	private boolean hasRedstonePower(Level level, BlockPos pos) {
	    // Vérifie la puissance reçue des blocs voisins.
	    return level.getBestNeighborSignal(pos) > 0;
	}


	public enum MountProperty implements StringRepresentable {
		WALL("wall"), CEILING("ceiling");

		private final String name;

		private MountProperty(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}
}