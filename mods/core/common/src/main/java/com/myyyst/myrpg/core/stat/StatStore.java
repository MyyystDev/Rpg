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
 */
public class StatStore {

    private static final Codec<Map<Identifier, Double>> VALUES_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE);

    private final Map<Identifier, Double> values = new HashMap<>();
    private final Map<Identifier, String> currentStages = new HashMap<>();   // transient

    // ------------------------------------------------------------ reads

    public double get(Identifier stat) {
        Double stored = values.get(stat);
        if (stored != null) return stored;
        return CoreData.STATS.get(stat).map(d -> d.value().defaultValue()).orElse(0.0);
    }

    @Nullable
    public String currentStage(Identifier stat) {
        return currentStages.get(stat);
    }

    public Map<Identifier, Double> all() { return values; }
    public boolean isEmpty() { return values.isEmpty(); }

    // ------------------------------------------------------------ writes

    public void set(LivingEntity owner, Identifier stat, double value) {
        StatDef def = CoreData.STATS.get(stat).orElse(null);
        if (def == null) {
            Constants.LOG.warn("[myrpg] Setting undefined stat {}", stat);
            values.put(stat, value);
            return;
        }
        StatDef.ValueConfig cfg = def.value();
        if (cfg.clamp()) value = Mth.clamp(value, cfg.min(), cfg.max());
        if (!cfg.decimal()) value = Math.rint(value);

        double old = get(stat);
        values.put(stat, value);
        if (value != old) {
            evaluateStage(owner, stat, def, true);
        }
    }

    public void add(LivingEntity owner, Identifier stat, double delta) {
        set(owner, stat, get(stat) + delta);
    }

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

    static StatDef.@Nullable Stage stageById(StatDef def, String id) {
        for (StatDef.Stage stage : def.stages()) {
            if (stage.id().equals(id)) return stage;
        }
        return null;
    }

    private static void runActions(LivingEntity owner, java.util.List<RpgAction> actions) {
        RpgAction.ActionContext ctx = owner instanceof net.minecraft.server.level.ServerPlayer player
                ? RpgAction.ActionContext.of(player, player)
                : RpgAction.ActionContext.of(owner);
        for (RpgAction action : actions) {
            action.execute(ctx);
        }
    }

    // ------------------------------------------------------------ persistence

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
}