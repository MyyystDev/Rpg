package com.myyyst.myrpg.core.stat;

import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drives the time-based side of stats: continuous stage-effect ticking and
 * interval rules. Owners (RpgEntity now, the player attachment later) call
 * tick(store, owner) from their server tick.
 */
public final class StatEngine {

    public static void tick(StatStore store, LivingEntity owner) {
        long gameTime = owner.level().getGameTime();

        for (Identifier statId : store.all().keySet()) {
            StatDef def = CoreData.STATS.get(statId).orElse(null);
            if (def == null) continue;

            // continuous stage effects
            String stageId = store.currentStage(statId);
            if (stageId != null) {
                StatDef.Stage stage = StatStore.stageById(def, stageId);
                if (stage != null) {
                    for (StageEffect effect : stage.effects()) {
                        effect.tick(owner);
                    }
                }
            }

            // interval rules
            for (StatDef.Rule rule : def.rules()) {
                if (!rule.trigger().shouldFireAt(gameTime, owner)) continue;
                if (!allPass(rule.conditions(), owner)) continue;
                RpgAction.ActionContext ctx = context(owner);
                for (RpgAction action : rule.actions()) {
                    action.execute(ctx);
                }
            }
        }
    }

    private static boolean allPass(java.util.List<RpgCondition> conditions, LivingEntity owner) {
        if (conditions.isEmpty()) return true;
        RpgCondition.ConditionContext ctx = owner instanceof ServerPlayer player
                ? RpgCondition.ConditionContext.of(player, player)
                : RpgCondition.ConditionContext.of(owner);
        for (RpgCondition condition : conditions) {
            if (!condition.test(ctx)) return false;
        }
        return true;
    }

    private static RpgAction.ActionContext context(LivingEntity owner) {
        return owner instanceof ServerPlayer player
                ? RpgAction.ActionContext.of(player, player)
                : RpgAction.ActionContext.of(owner);
    }
}