package com.myyyst.myrpg.core.stat;

import com.mojang.serialization.Codec;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stat values + stage state for one owner. Mutators take the owner because
 * a value change can cross a stage boundary, which fires effects/actions
 * immediately.
 *
 * Stage state is transient: recomputed from values on load via
 * reapplyStages(), which re-runs effect apply() idempotently (relog-safe)
 * WITHOUT firing on_enter events (crossing happened in the past).
 *
 * <p>One instance exists per player (kept by {@code PlayerStats}) and per RPG entity.
 * It only stores numbers; the meaning of each number lives in the matching
 * {@link StatDef} loaded from datapacks.</p>
 */
public class StatStore {

    /** Persisted shape: a plain map of stat id -> number. */
    private static final Codec<Map<Identifier, Double>> VALUES_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE);

    /** Only explicitly written stats appear here; anything else falls back to the definition default. */
    private final Map<Identifier, Double> values = new HashMap<>();
    /** stat id -> id of the stage it currently sits in. Derived state, never saved. */
    private final Map<Identifier, String> currentStages = new HashMap<>();   // transient

    /** Stats changed since the last network sync; drained by {@code PlayerStatTicker}. */
    private final java.util.Set<Identifier> dirtySync = new java.util.HashSet<>();

    // ------------------------------------------------------------ reads

    /** @return the stored value, or the definition's default (0 if the stat is unknown). */
    public double get(Identifier stat) {
        Double stored = values.get(stat);
        if (stored != null) return stored;
        return CoreData.STATS.get(stat).map(d -> d.value().defaultValue()).orElse(0.0);
    }

    /** @return the id of the stage the stat is in, or null if it has no stages / sits outside them. */
    @Nullable
    public String currentStage(Identifier stat) {
        return currentStages.get(stat);
    }

    /** Live view of every explicitly set stat. */
    public Map<Identifier, Double> all() { return values; }
    /** True when nothing has been written yet - lets callers skip saving entirely. */
    public boolean isEmpty() { return values.isEmpty(); }

    // ------------------------------------------------------------ writes

    /**
     * Writes a value after applying the definition's clamp/rounding rules, then re-evaluates
     * the stage - which may apply or remove effects and fire on_enter / on_exit actions.
     *
     * <p>Unknown stats are still stored (with a warning) so that a datapack reload which
     * temporarily removes a definition does not silently destroy player data.</p>
     */
    public void set(LivingEntity owner, Identifier stat, double value) {
        StatDef def = CoreData.STATS.get(stat).orElse(null);
        if (def == null) {
            Constants.LOG.warn("[myrpg] Setting undefined stat {}", stat);
            values.put(stat, value);
            return;
        }
        StatDef.ValueConfig cfg = def.value();
        if (cfg.clamp()) value = Mth.clamp(value, cfg.min(), cfg.max());
        if (!cfg.decimal()) value = Math.rint(value);   // integer stats round to nearest

        dirtySync.add(stat);   // queue a client sync even if the number ends up unchanged

        double old = get(stat);
        values.put(stat, value);
        if (value != old) {
            evaluateStage(owner, stat, def, true);   // may cross a stage boundary
        }
    }

    /** Convenience for relative changes; goes through {@link #set} so clamping still applies. */
    public void add(LivingEntity owner, Identifier stat, double delta) {
        set(owner, stat, get(stat) + delta);
    }

    /**
     * Applies a datapack-named arithmetic operation.
     *
     * @param operation "add", "subtract", "multiply", "divide" or anything else for "set"
     *                  (division by zero is ignored and leaves the value untouched)
     */
    public void modify(LivingEntity owner, Identifier stat, String operation, double operand) {
        double current = get(stat);
        double result = switch (operation) {
            case "add" -> current + operand;
            case "subtract" -> current - operand;
            case "multiply" -> current * operand;
            case "divide" -> operand == 0 ? current : current / operand;
            default -> operand;   // "set"
        };
        set(owner, stat, result);
    }

    /**
     * Drops every value and stage. Note this does not remove effects that stages had applied -
     * callers that need that must exit the stages first.
     */
    public void clear() {
        values.clear();
        currentStages.clear();
    }

    // ------------------------------------------------------------ stages

    /**
     * Recomputes the stage for one stat; fireEvents=false replays effect
     * application without on_enter/on_exit (the load path).
     */
    void evaluateStage(LivingEntity owner, Identifier statId, StatDef def, boolean fireEvents) {
        if (def.stages().isEmpty()) return;
        double value = get(statId);

        // First stage whose inclusive range contains the value wins; null = between stages.
        StatDef.Stage newStage = null;
        for (StatDef.Stage stage : def.stages()) {
            if (value >= stage.min() && value <= stage.max()) {
                newStage = stage;
                break;
            }
        }
        String oldId = currentStages.get(statId);
        String newId = newStage == null ? null : newStage.id();
        if (java.util.Objects.equals(oldId, newId)) {
            return;   // no crossing
        }

        // exit the old stage
        if (oldId != null) {
            StatDef.Stage oldStage = stageById(def, oldId);
            if (oldStage != null) {
                for (StageEffect effect : oldStage.effects()) {
                    effect.remove(owner, statId, oldId);
                }
                if (fireEvents) {
                    runActions(owner, oldStage.onExit());
                }
            }
        }
        // enter the new one
        if (newStage != null) {
            currentStages.put(statId, newId);
            for (StageEffect effect : newStage.effects()) {
                effect.apply(owner, statId, newId);
            }
            if (fireEvents) {
                runActions(owner, newStage.onEnter());
            }
        } else {
            currentStages.remove(statId);
        }
    }

    /** Load path: recompute all stages, re-apply effects, fire nothing. */
    public void reapplyStages(LivingEntity owner) {
        for (Identifier statId : values.keySet()) {
            CoreData.STATS.get(statId).ifPresent(def ->
                    evaluateStage(owner, statId, def, false));
        }
    }

    /** Looks a stage up by its string id; null when a datapack reload removed it. */
    static StatDef.@Nullable Stage stageById(StatDef def, String id) {
        for (StatDef.Stage stage : def.stages()) {
            if (stage.id().equals(id)) return stage;
        }
        return null;
    }

    /**
     * Runs stage on_enter/on_exit actions against the owner.
     * Players are passed as both source and target so player-only actions still work.
     */
    private static void runActions(LivingEntity owner, java.util.List<RpgAction> actions) {
        RpgAction.ActionContext ctx = owner instanceof net.minecraft.server.level.ServerPlayer player
                ? RpgAction.ActionContext.of(player, player)
                : RpgAction.ActionContext.of(owner);
        for (RpgAction action : actions) {
            action.execute(ctx);
        }
    }

    // ------------------------------------------------------------ persistence

    /** Writes the values under {@code key}; writes nothing at all when empty, to keep NBT small. */
    public void save(ValueOutput output, String key) {
        if (values.isEmpty()) return;
        output.store(key, VALUES_CODEC, Map.copyOf(values));
    }

    /** Call reapplyStages(owner) after this. */
    public void load(ValueInput input, String key) {
        values.clear();
        currentStages.clear();
        input.read(key, VALUES_CODEC).ifPresent(values::putAll);
    }

    /**
     * Returns the stats changed since the previous call and clears the pending set,
     * so each change is synced to the client exactly once.
     */
    public java.util.Set<Identifier> drainDirty() {
        if (dirtySync.isEmpty()) return java.util.Set.of();
        var out = java.util.Set.copyOf(dirtySync);
        dirtySync.clear();
        return out;
    }
}