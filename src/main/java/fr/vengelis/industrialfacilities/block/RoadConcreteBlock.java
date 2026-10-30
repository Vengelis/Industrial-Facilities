package fr.vengelis.industrialfacilities.block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
public class RoadConcreteBlock extends Block {
    public RoadConcreteBlock() { super(BlockBehaviour.Properties.of().strength(2.5F, 6F).sound(SoundType.STONE).requiresCorrectToolForDrops()); }
}
