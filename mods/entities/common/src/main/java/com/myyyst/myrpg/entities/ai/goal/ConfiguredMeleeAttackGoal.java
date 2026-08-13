package com.myyyst.myrpg.entities.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Vanilla melee with a data-driven swing interval (combat.cooldown). */
public class ConfiguredMeleeAttackGoal extends MeleeAttackGoal {

    private final PathfinderMob owner;
    private final int cooldown;
    private final double maxRangeSqr;   // 0 = melee at any distance

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

    @Override
    protected int getAttackInterval() {
        return adjustedTickDelay(cooldown);
    }
}
