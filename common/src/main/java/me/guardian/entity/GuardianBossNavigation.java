package me.guardian.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public final class GuardianBossNavigation extends GroundPathNavigation {
    public GuardianBossNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new SpawnBoundWalkNodeEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static final class SpawnBoundWalkNodeEvaluator extends WalkNodeEvaluator {
        @Override
        protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double floorLevel, Direction direction, PathType previousPathType) {
            Node node = super.findAcceptedNode(x, y, z, maxYStep, floorLevel, direction, previousPathType);
            if (node != null && this.mob != null) {
                GuardianBossAi.addSpawnDistancePenalty(this.mob, node);
            }
            return node;
        }
    }
}
