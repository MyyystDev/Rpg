package com.myyyst.myrpg.entities.ai.goal;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Follows the nearest (non-spectator) player, stopping at stopDistance.
 * Ownerless companion behavior — an owned "follow_owner" variant can come
 * later when taming/recruiting exists.
 */
public class FollowPlayerGoal extends Goal {

    private final RpgEntity mob;
    private final double speed;
    private final double stopDistance;
    private final double range;

    /** Player being followed while the goal runs; null when idle. */
    @Nullable private Player target;
    /** Ticks until the path is recalculated - repathing every tick would be wasteful. */
    private int repathCooldown;

    public FollowPlayerGoal(RpgEntity mob, double speed, double stopDistance, double range) {
        this.mob = mob;
        this.speed = speed;
        this.stopDistance = Math.max(1.0, stopDistance);
        this.range = range;
        // Claims movement and looking, so goals needing either cannot run at the same time.
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** Starts when a living player is within range and further away than the stop distance. */
    @Override
    public boolean canUse() {
        Player nearest = mob.level().getNearestPlayer(mob, range);
        if (nearest == null || nearest.isSpectator()) return false;
        if (mob.distanceToSqr(nearest) <= stopDistance * stopDistance) return false;
        this.target = nearest;
        return true;
    }

    /**
     * Keeps running until the target is reached or lost. The generous outer bound
     * (4x range squared = 2x range) gives the follower some hysteresis, so it does not
     * drop the player the instant they step past the pickup range.
     */
    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        double distSqr = mob.distanceToSqr(target);
        // keep following until close enough; give up entirely if far outside range
        return distSqr > stopDistance * stopDistance && distSqr < range * range * 4;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }

    /** Looks at the target every tick, but only recalculates the path every ~10 ticks. */
    @Override
    public void tick() {
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 10.0f, mob.getMaxHeadXRot());
        if (--repathCooldown <= 0) {
            repathCooldown = adjustedTickDelay(10);   // scales with the game's AI tick rate
            mob.getNavigation().moveTo(target, speed);
        }
    }
}
