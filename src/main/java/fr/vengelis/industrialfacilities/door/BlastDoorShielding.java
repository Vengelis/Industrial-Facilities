package fr.vengelis.industrialfacilities.door;

import fr.vengelis.industrialfacilities.block.IndustrialDoorPartBlock;
import fr.vengelis.industrialfacilities.block.IndustrialLiftDoorBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BlastDoorShielding {
    /** Vanilla applies a minimum of one damage even at zero blast exposure. */
    public static boolean protects(ServerLevel level, Explosion explosion, Entity entity) {
        Vec3 center = explosion.center();
        double reach = 2.0 * explosion.radius();
        if (entity.distanceToSqr(center) > reach * reach) return false;

        // Identify a reinforced door between the blast and the entity. Then use
        // vanilla exposure to account for gaps around the door and adjoining walls.
        return intercepts(level, center, entity.getBoundingBox().getCenter(), CollisionContext.of(entity))
            && Explosion.getSeenPercent(center, entity) == 0;
    }

    private static boolean intercepts(ServerLevel level, Vec3 center, Vec3 sample, CollisionContext collision) {
        return BlockGetter.traverseBlocks(center, sample, level, (world, pos) -> {
            if (!world.hasChunkAt(pos)) return null;
            var state = world.getBlockState(pos);
            boolean reinforced = switch (state.getBlock()) {
                case IndustrialLiftDoorBlock lift -> true;
                case IndustrialDoorBlock frame -> frame.style.reinforced();
                case IndustrialDoorPartBlock part -> {
                    var door = IndustrialDoorPartBlock.controller(world, pos);
                    yield door != null && door.style().reinforced();
                }
                default -> false;
            };
            if (reinforced && state.getCollisionShape(world, pos, collision).clip(center, sample, pos) != null) return true;
            return null;
        }, world -> false);
    }

    private BlastDoorShielding() {}
}
