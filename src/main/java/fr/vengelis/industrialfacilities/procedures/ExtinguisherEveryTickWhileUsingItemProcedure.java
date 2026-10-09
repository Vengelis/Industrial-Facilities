package fr.vengelis.industrialfacilities.procedures;

import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;

public class ExtinguisherEveryTickWhileUsingItemProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
		if (entity == null)
			return;
		double distance = 0;
		double testx = 0;
		double testy = 0;
		double testz = 0;
		double snowDistance = 0;
		double snowX = 0;
		double snowY = 0;
		double snowZ = 0;
		double scanSnowY = 0;
		double randomPoseSnowConst = 0;
		double chuteFactorConst = 0;
		if (!world.isClientSide()) {
			if (!entity.isShiftKeyDown()) {
				if (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("tank") > 0) {
					{
						final String _tagName = "useTick";
						final double _tagValue = (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("useTick") + 1);
						CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
					}
					{
						final String _tagName = "soundTick";
						final double _tagValue = (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("soundTick") + 1);
						CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
					}
					{
						final String _tagName = "animTick";
						final double _tagValue = (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("animTick") + 1);
						CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
					}
					if (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("animTick") >= 12) {
						{
							final String _tagName = "animTick";
							final double _tagValue = 0;
							CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
						}
					}
					if (itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("useTick") >= 5) {
						{
							final String _tagName = "tank";
							final double _tagValue = (Mth.clamp(itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("tank") - 64, 0, 6400));
							CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
						}
						itemstack.setDamageValue((int) (6400 - itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("tank")));
						{
							final String _tagName = "useTick";
							final double _tagValue = 0;
							CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putDouble(_tagName, _tagValue));
						}
					}
					distance = 0.5;
					randomPoseSnowConst = 0.2;
					chuteFactorConst = 0.02;
					for (int _i1 = 0; _i1 < 28; _i1++) {
						testx = x + entity.getLookAngle().x * distance;
						testy = y + 1.5 + entity.getLookAngle().y * distance - chuteFactorConst * distance * distance;
						testz = z + entity.getLookAngle().z * distance;
						if ((world.getBlockState(BlockPos.containing(testx, testy, testz))).getBlock() == Blocks.FIRE || (world.getBlockState(BlockPos.containing(testx, testy, testz))).getBlock() == Blocks.SOUL_FIRE) {
							if (world instanceof ServerLevel _level)
								_level.sendParticles(ParticleTypes.SNOWFLAKE, testx, testy, testz, 5, 0.15, 0.15, 0.15, 0.05);
							world.setBlock(BlockPos.containing(testx, testy, testz), Blocks.AIR.defaultBlockState(), 3);
							distance = distance + 0.5;
						} else if ((world.getBlockState(BlockPos.containing(testx, testy, testz))).getBlock() == Blocks.SNOW) {
							if (Math.random() <= randomPoseSnowConst) {
								if ((getPropertyByName((world.getBlockState(BlockPos.containing(testx, testy, testz))), "layers") instanceof IntegerProperty _getip45
										? (world.getBlockState(BlockPos.containing(testx, testy, testz))).getValue(_getip45)
										: -1) < 8) {
									{
										int _value = (getPropertyByName((world.getBlockState(BlockPos.containing(testx, testy, testz))), "layers") instanceof IntegerProperty _getip47
												? (world.getBlockState(BlockPos.containing(testx, testy, testz))).getValue(_getip47)
												: -1) + 1;
										BlockPos _pos = BlockPos.containing(testx, testy, testz);
										BlockState _bs = world.getBlockState(_pos);
										if (_bs.getBlock().getStateDefinition().getProperty("layers") instanceof IntegerProperty _integerProp && _integerProp.getPossibleValues().contains(_value))
											world.setBlock(_pos, _bs.setValue(_integerProp, _value), 3);
									}
								}
							}
							break;
						} else if ((world.getBlockState(BlockPos.containing(testx, testy, testz))).is(BlockTags.create(ResourceLocation.parse("minecraft:replaceable")))
								|| (world.getBlockState(BlockPos.containing(testx, testy, testz))).getBlock() == Blocks.AIR || (world.getBlockState(BlockPos.containing(testx, testy, testz))).getBlock() == Blocks.CAVE_AIR) {
							distance = distance + 0.5;
							if (world instanceof ServerLevel _level)
								_level.sendParticles(ParticleTypes.SNOWFLAKE, testx, testy, testz, 5, 0.15, 0.15, 0.15, 0.05);
						} else {
							if (Math.random() <= randomPoseSnowConst) {
								snowDistance = distance - 0.5;
								snowX = x + entity.getLookAngle().x * snowDistance;
								snowY = y + 1.5 + entity.getLookAngle().y * snowDistance - chuteFactorConst * snowDistance * snowDistance;
								snowZ = z + entity.getLookAngle().z * snowDistance;
								scanSnowY = Math.floor(snowY);
								for (int _i2 = 0; _i2 < 10; _i2++) {
									if ((world.getBlockState(BlockPos.containing(Math.floor(snowX), scanSnowY, Math.floor(snowZ)))).is(BlockTags.create(ResourceLocation.parse("minecraft:replaceable")))
											&& !(world.getBlockState(BlockPos.containing(Math.floor(snowX), scanSnowY - 1, Math.floor(snowZ)))).is(BlockTags.create(ResourceLocation.parse("minecraft:replaceable")))) {
										world.setBlock(BlockPos.containing(Math.floor(snowX), scanSnowY, Math.floor(snowZ)), Blocks.SNOW.defaultBlockState(), 3);
										break;
									}
									scanSnowY = scanSnowY - 1;
								}
							}
							break;
						}
					}
				}
			}
		}
	}

	private static Property<?> getPropertyByName(BlockState state, String name) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(name)) {
				return property;
			}
		}
		return null;
	}
}