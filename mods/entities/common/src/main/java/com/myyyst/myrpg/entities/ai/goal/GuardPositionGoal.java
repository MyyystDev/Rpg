package com.myyyst.myrpg.entities.ai.goal;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks back toward the guard anchor whenever the entity strays beyond
 * radius. The anchor is the spawn position by default (persisted in NBT);
 * instance overrides can move it later.
 *
 * Wander-style goals at lower priority still run inside the radius, so a
 * guard idles around its post instead of freezing on it.
 */
public class GuardPositionGoal extends Goal {

    private final RpgEntity mob;
    private final double speed;
    /** Squared, so the check needs no square root. */
    private final double radiusSqr;

    public GuardPositionGoal(RpgEntity mob, double speed, double radius) {
        this.mob = mob;
        this.speed = speed;
        this.radiusSqr = radius * radius;
        // Only claims MOVE, so the guard can still look around while walking back.
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** False when the entity has no anchor at all, which disables the goal entirely. */
    private boolean outsideRadius() {
        BlockPos anchor = mob.guardAnchor();
        return anchor != null && mob.blockPosition().distSqr(anchor) > radiusSqr;
    }

    @Override
    public boolean canUse() {
        return outsideRadius();
    }

    @Override
    public boolean canContinueToUse() {
        return outsideRadius() && !mob.getNavigation().isDone();
    }

    /** Paths back to the anchor's block centre (+0.5) rather than its corner. */
    @Override
    public void start() {
        BlockPos anchor = mob.guardAnchor();
        if (anchor != null) {
            mob.getNavigation().moveTo(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
