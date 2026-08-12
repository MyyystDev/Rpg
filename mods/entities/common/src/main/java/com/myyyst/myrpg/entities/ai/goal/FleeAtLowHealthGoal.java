package com.myyyst.myrpg.entities.ai.goal;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * When health drops to or below threshold (fraction of max), run away from
 * whoever last hurt us. Re-picks a flee position while the attacker is
 * still nearby and health stays low.
 */
public class FleeAtLowHealthGoal extends Goal {

    private final RpgEntity mob;
    private final double speed;
    private final double threshold;

    @Nullable private Vec3 fleePos;

    public FleeAtLowHealthGoal(RpgEntity mob, double speed, double threshold) {
        this.mob = mob;
        this.speed = speed;
        this.threshold = threshold;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private boolean lowHealth() {
        return mob.getHealth() <= mob.getMaxHealth() * threshold;
    }

    @Override
    public boolean canUse() {
        if (!lowHealth()) return false;
        LivingEntity attacker = mob.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()) return false;
        fleePos = DefaultRandomPos.getPosAway(mob, 16, 7, attacker.position());
        return fleePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return lowHealth() && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (fleePos != null) {
            mob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, speed);
        }
    }

    @Override
    public void stop() {
        fleePos = null;
    }
}
