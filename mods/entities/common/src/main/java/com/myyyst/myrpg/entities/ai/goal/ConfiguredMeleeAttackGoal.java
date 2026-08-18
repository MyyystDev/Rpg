package com.myyyst.myrpg.entities.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla melee with a data-driven swing interval (combat.cooldown).
 *
 * <p>Vanilla hard-codes its attack interval; this subclass reads it from the entity
 * definition instead, and can optionally restrict itself to close quarters so a
 * lower-priority ranged goal covers longer distances.</p>
 */
public class ConfiguredMeleeAttackGoal extends MeleeAttackGoal {

    private final PathfinderMob owner;
    /** Ticks between swings, floored at 1. */
    private final int cooldown;
    private final double maxRangeSqr;   // 0 = melee at any distance

    /** Melee at any distance, using only the configured cooldown. */
    public ConfiguredMeleeAttackGoal(PathfinderMob mob, double speedModifier,
                                     boolean followEvenIfNotSeen, int cooldown) {
        this(mob, speedModifier, followEvenIfNotSeen, cooldown, 0.0);
    }

    /** maxRange > 0 makes this a close-quarters goal — beyond it the goal
     *  yields, letting a lower-priority ranged goal take over (hybrid). */
    public ConfiguredMeleeAttackGoal(PathfinderMob mob, double speedModifier,
                                     boolean followEvenIfNotSeen, int cooldown, double maxRange) {
        super(mob, speedModifier, followEvenIfNotSeen);
        this.owner = mob;
        this.cooldown = Math.max(1, cooldown);
        this.maxRangeSqr = maxRange > 0 ? maxRange * maxRange : 0;
    }

    /** True when unrestricted, or when the target is inside the close-quarters range. */
    private boolean inEngageRange() {
        if (maxRangeSqr <= 0) return true;
        var target = owner.getTarget();
        return target != null && owner.distanceToSqr(target) <= maxRangeSqr;
    }

    @Override
    public boolean canUse() {
        return inEngageRange() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return inEngageRange() && super.canContinueToUse();
    }

    /** The one behaviour change over vanilla: a configurable swing interval. */
    @Override
    protected int getAttackInterval() {
        return adjustedTickDelay(cooldown);
    }
}
