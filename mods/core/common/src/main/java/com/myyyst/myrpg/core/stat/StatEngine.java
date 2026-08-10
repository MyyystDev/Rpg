package com.myyyst.myrpg.core.stat;

import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.core.event.RpgEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Drives stats: continuous stage effects + interval rules on tick, and
 * event-triggered rules via RpgEvents (subscribed in RpgCore.init()).
 */
public final class StatEngine {

    // ------------------------------------------------------------ tick side

    public static void tick(StatStore store, LivingEntity owner) {
        long gameTime = owner.level().getGameTime();

        for (var entry : CoreData.STATS.all().entrySet()) {
            Identifier statId = entry.getKey();
            StatDef def = entry.getValue();

            // continuous stage effects — stage state only exists for touched
            // stats, so this naturally no-ops on untouched ones:
            String stageId = store.currentStage(statId);
            if (stageId != null) {
                StatDef.Stage stage = StatStore.stageById(def, stageId);
                if (stage != null) {
                    for (StageEffect effect : stage.effects()) {
                        effect.tick(owner);
                    }
                }
            }

            for (StatDef.Rule rule : def.rules()) {
                if (!rule.trigger().shouldFireAt(gameTime, owner)) continue;
                if (!allPass(rule.conditions(), conditionContext(owner, null))) continue;
                RpgAction.ActionContext ctx = actionContext(owner, null);
                for (RpgAction action : rule.actions()) {
                    action.execute(ctx);
                }
            }
        }
    }

    public static void onEvent(RpgEvents.GameEvent event) {
        LivingEntity owner = event.player() != null ? event.player() : event.subject();
        if (owner == null) return;
        StatStore store = StatHolder.resolve(owner);
        if (store == null) return;

        for (var entry : CoreData.STATS.all().entrySet()) {
            StatDef def = entry.getValue();
            for (StatDef.Rule rule : def.rules()) {
                if (!rule.trigger().matchesEvent(event.id())) continue;
                if (!allPass(rule.conditions(), conditionContext(owner, event.subject()))) continue;
                RpgAction.ActionContext ctx = actionContext(owner, event.subject());
                for (RpgAction action : rule.actions()) {
                    action.execute(ctx);
                }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private static boolean allPass(List<RpgCondition> conditions, RpgCondition.ConditionContext ctx) {
        for (RpgCondition condition : conditions) {
            if (!condition.test(ctx)) return false;
        }
        return true;
    }

    private static RpgCondition.ConditionContext conditionContext(LivingEntity owner,
                                                                  @Nullable LivingEntity target) {
        ServerPlayer player = owner instanceof ServerPlayer p ? p : null;
        return new RpgCondition.ConditionContext(owner, player, target);
    }

    private static RpgAction.ActionContext actionContext(LivingEntity owner,
                                                         @Nullable LivingEntity target) {
        // ActionContext has no target field (yet); the player is what matters.
        ServerPlayer player = owner instanceof ServerPlayer p ? p : null;
        return new RpgAction.ActionContext(owner, player);
    }

    private StatEngine() {}
}