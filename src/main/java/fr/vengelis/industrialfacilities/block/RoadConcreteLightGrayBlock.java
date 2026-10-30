package fr.vengelis.industrialfacilities.block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
public class RoadConcreteLightGrayBlock extends Block {
    public RoadConcreteLightGrayBlock() { super(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_GRAY).strength(2.5F, 6F).sound(SoundType.STONE).requiresCorrectToolForDrops()); }
}
