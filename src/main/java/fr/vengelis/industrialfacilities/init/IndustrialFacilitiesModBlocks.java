/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package fr.vengelis.industrialfacilities.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.Block;

import fr.vengelis.industrialfacilities.block.*;
import fr.vengelis.industrialfacilities.IndustrialFacilitiesMod;

public class IndustrialFacilitiesModBlocks {
	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(IndustrialFacilitiesMod.MODID);
	public static final DeferredBlock<Block> EXTINGUISHER_PLACED;
	public static final DeferredBlock<Block> EMERGENCY_EXIT_SIGN_RIGHT;
	public static final DeferredBlock<Block> EMERGENCY_EXIT_SIGN_LEFT;
	public static final DeferredBlock<Block> AIRLOCK_FRAME;
	public static final DeferredBlock<Block> AIRLOCK_CONTROLLER;
	public static final DeferredBlock<Block> AIRLOCK_SEAL;
	public static final DeferredBlock<Block> BIOHAZARD_SIGN;
	public static final DeferredBlock<Block> RADIOACTIVE_SIGN;
	public static final DeferredBlock<Block> WARNING_SIGN;
	public static final DeferredBlock<Block> EXTINGUISHER_SIGN;
	public static final DeferredBlock<Block> DEAD_SIGN;
	public static final DeferredBlock<Block> MEDIC_SIGN;
	public static final DeferredBlock<Block> LASER_SIGN;
	public static final DeferredBlock<Block> COLD_SIGN;
	public static final DeferredBlock<Block> FIRE_SIGN;
	public static final DeferredBlock<Block> ASPHYXIA_SIGN;
	public static final DeferredBlock<Block> EXPLOSION_SIGN;
	public static final DeferredBlock<Block> MONSTER_SIGN;
	public static final DeferredBlock<Block> FORBIDDEN_SIGN;
	public static final DeferredBlock<Block> STOP_SIGN;
	public static final DeferredBlock<Block> INFORMATION_SIGN;
	public static final DeferredBlock<Block> STAIRS_SIGN;
	public static final DeferredBlock<Block> ELEVATOR_SIGN;
	public static final DeferredBlock<Block> LEFT_SIGN;
	public static final DeferredBlock<Block> RIGHT_SIGN;
	public static final DeferredBlock<Block> UP_SIGN;
	public static final DeferredBlock<Block> DOWN_SIGN;
	public static final DeferredBlock<Block> STORAGE_SIGN;
	public static final DeferredBlock<Block> EMPTY_SIGN;
	public static final DeferredBlock<Block> FALLING_SIGN;
	static {
		EXTINGUISHER_PLACED = REGISTRY.register("extinguisher_placed", ExtinguisherPlacedBlock::new);
		EMERGENCY_EXIT_SIGN_RIGHT = REGISTRY.register("emergency_exit_sign_right", EmergencyExitSignRightBlock::new);
		EMERGENCY_EXIT_SIGN_LEFT = REGISTRY.register("emergency_exit_sign_left", EmergencyExitSignLeftBlock::new);
		AIRLOCK_FRAME = REGISTRY.register("airlock_frame", AirlockFrameBlock::new);
		AIRLOCK_CONTROLLER = REGISTRY.register("airlock_controller", AirlockControllerBlock::new);
		AIRLOCK_SEAL = REGISTRY.register("airlock_seal", AirlockSealBlock::new);
		BIOHAZARD_SIGN = REGISTRY.register("biohazard_sign", BiohazardSignBlock::new);
		RADIOACTIVE_SIGN = REGISTRY.register("radioactive_sign", RadioactiveSignBlock::new);
		WARNING_SIGN = REGISTRY.register("warning_sign", WarningSignBlock::new);
		EXTINGUISHER_SIGN = REGISTRY.register("extinguisher_sign", ExtinguisherSignBlock::new);
		DEAD_SIGN = REGISTRY.register("dead_sign", DeadSignBlock::new);
		MEDIC_SIGN = REGISTRY.register("medic_sign", MedicSignBlock::new);
		LASER_SIGN = REGISTRY.register("laser_sign", LaserSignBlock::new);
		COLD_SIGN = REGISTRY.register("cold_sign", ColdSignBlock::new);
		FIRE_SIGN = REGISTRY.register("fire_sign", FireSignBlock::new);
		ASPHYXIA_SIGN = REGISTRY.register("asphyxia_sign", AsphyxiaSignBlock::new);
		EXPLOSION_SIGN = REGISTRY.register("explosion_sign", ExplosionSignBlock::new);
		MONSTER_SIGN = REGISTRY.register("monster_sign", MonsterSignBlock::new);
		FORBIDDEN_SIGN = REGISTRY.register("forbidden_sign", ForbiddenSignBlock::new);
		STOP_SIGN = REGISTRY.register("stop_sign", StopSignBlock::new);
		INFORMATION_SIGN = REGISTRY.register("information_sign", InformationSignBlock::new);
		STAIRS_SIGN = REGISTRY.register("stairs_sign", StairsSignBlock::new);
		ELEVATOR_SIGN = REGISTRY.register("elevator_sign", ElevatorSignBlock::new);
		LEFT_SIGN = REGISTRY.register("left_sign", LeftSignBlock::new);
		RIGHT_SIGN = REGISTRY.register("right_sign", RightSignBlock::new);
		UP_SIGN = REGISTRY.register("up_sign", UpSignBlock::new);
		DOWN_SIGN = REGISTRY.register("down_sign", DownSignBlock::new);
		STORAGE_SIGN = REGISTRY.register("storage_sign", StorageSignBlock::new);
		EMPTY_SIGN = REGISTRY.register("empty_sign", EmptySignBlock::new);
		FALLING_SIGN = REGISTRY.register("falling_sign", FallingSignBlock::new);
	}
	// Start of user code block custom blocks
	// End of user code block custom blocks
}