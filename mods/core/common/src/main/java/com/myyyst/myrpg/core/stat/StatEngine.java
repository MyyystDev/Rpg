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
 *
 * <p>Two entry points, both server side:</p>
 * <ul>
 *   <li>{@link #tick} - called every server tick per player by {@code PlayerStatTicker};
 *       runs stage effects and time-based rules.</li>
 *   <li>{@link #onEvent} - called when something happens in the world (damage, kill, ...);
 *       runs rules whose trigger matches the event id.</li>
 * </ul>
 *
 * <p>Note that both loops walk <em>every</em> loaded stat definition, not just the ones the
 * owner has values for: a rule may well be what first gives the owner a value.</p>
 */
public final class StatEngine {

    // ------------------------------------------------------------ tick side

    /** Per-tick pass: continuous stage effects, then any rule whose trigger is due now. */
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

            // interval / time based rules ("every N ticks", "on a schedule")
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

    /**
     * Event side: runs every rule whose trigger matches {@code event.id()}.
     *
     * <p>Subscribed to {@code RpgEvents} in {@code MyRpgCommon.init}. The "owner" of the
     * reaction is the player when one is involved, otherwise the event's subject entity;
     * entities without a stat store (plain vanilla mobs) are skipped.</p>
     */
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

    /** Logical AND over a rule's conditions; an empty list passes. */
    private static boolean allPass(List<RpgCondition> conditions, RpgCondition.ConditionContext ctx) {
        for (RpgCondition condition : conditions) {
            if (!condition.test(ctx)) return false;
        }
        return true;
    }

    /** Packs owner/player/target into the context conditions read from. */
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

    /** Static-only engine: never instantiated. */
    private StatEngine() {}
}