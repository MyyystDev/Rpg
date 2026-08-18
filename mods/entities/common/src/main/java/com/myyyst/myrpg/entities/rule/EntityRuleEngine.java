package com.myyyst.myrpg.entities.rule;

import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.entities.data.EntityDefinition;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Runs an entity definition's rules (same Rule shape as stat definitions:
 * trigger / conditions / actions), always with self = the entity. This is
 * intentionally separate from StatEngine.onEvent, whose owner-resolution
 * prefers the player — entity rules must run on the entity.
 */
public final class EntityRuleEngine {

    /** Polled side — called from RpgEntity server tick. */
    public static void tick(RpgEntity entity, EntityDefinition def) {
        if (def.rules().isEmpty()) return;
        long gameTime = entity.level().getGameTime();
        for (StatDef.Rule rule : def.rules()) {
            if (!rule.trigger().shouldFireAt(gameTime, entity)) continue;
            if (!allPass(rule.conditions(), new RpgCondition.ConditionContext(entity, null, null))) continue;
            run(rule.actions(), new RpgAction.ActionContext(entity, null));
        }
    }

    /** Event side — called from EntityEvents.fire. */
    public static void onEvent(RpgEntity entity, Identifier eventId, @Nullable ServerPlayer player) {
        EntityDefinition def = entity.definition().orElse(null);
        if (def == null || def.rules().isEmpty()) return;
        for (StatDef.Rule rule : def.rules()) {
            if (!rule.trigger().matchesEvent(eventId)) continue;
            if (!allPass(rule.conditions(), new RpgCondition.ConditionContext(entity, player, player))) continue;
            run(rule.actions(), new RpgAction.ActionContext(entity, player));
        }
    }

    /** Logical AND over a rule's conditions; public because interactions reuse it. */
    public static boolean allPass(List<RpgCondition> conditions, RpgCondition.ConditionContext ctx) {
        for (RpgCondition condition : conditions) {
            if (!condition.test(ctx)) return false;
        }
        return true;
    }

    /** Runs an action list in order; public because interactions reuse it. */
    public static void run(List<RpgAction> actions, RpgAction.ActionContext ctx) {
        for (RpgAction action : actions) {
            action.execute(ctx);
        }
    }

    /** Static-only engine: never instantiated. */
    private EntityRuleEngine() {}
}
