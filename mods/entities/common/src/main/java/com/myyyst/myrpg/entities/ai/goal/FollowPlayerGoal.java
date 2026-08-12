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

    @Nullable private Player target;
    private int repathCooldown;

    public FollowPlayerGoal(RpgEntity mob, double speed, double stopDistance, double range) {
        this.mob = mob;
        this.speed = speed;
        this.stopDistance = Math.max(1.0, stopDistance);
        this.range = range;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player nearest = mob.level().getNearestPlayer(mob, range);
        if (nearest == null || nearest.isSpectator()) return false;
        if (mob.distanceToSqr(nearest) <= stopDistance * stopDistance) return false;
        this.target = nearest;
        return true;
    }

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

    @Override
    public void tick() {
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 10.0f, mob.getMaxHeadXRot());
        if (--repathCooldown <= 0) {
            repathCooldown = adjustedTickDelay(10);
            mob.getNavigation().moveTo(target, speed);
        }
    }
}
