package fr.vengelis.industrialfacilities.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;

public class AirlockFrameBlock extends Block {
	public AirlockFrameBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1f, 1200f).requiresCorrectToolForDrops());
	}
}