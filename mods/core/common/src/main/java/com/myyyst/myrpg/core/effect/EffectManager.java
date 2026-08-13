package com.myyyst.myrpg.core.effect;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.EffectDefinition;
import com.myyyst.myrpg.core.data.StatDef;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The one gateway for effect state (per the design doc: other systems talk
 * to the manager, never to instance state directly). Handles stacking
 * modes, lifecycle events, attribute modifiers with stack/level scaling,
 * restriction modifiers, interval rules, and expiry.
 */
public final class EffectManager {

    // ------------------------------------------------------------ apply

    /**
     * @param durationOverride ticks; negative = use the definition default
     * @param stacks           applications to add (>= 1)
     * @return true if anything changed
     */
    public static boolean apply(LivingEntity target, Identifier effectId,
                                int durationOverride, int level, int stacks,
                                @Nullable UUID source) {
        EffectDefinition def = CoreData.EFFECTS.get(effectId).orElse(null);
        if (def == null) {
            Constants.LOG.warn("[myrpg] apply of unknown effect {}", effectId);
            return false;
        }
        EffectStore store = EffectHolder.resolve(target);
        if (store == null) return false;
        if (target instanceof EffectImmune immune && immune.isEffectImmune(effectId, def)) {
            return false;
        }

        int duration = def.duration().infinite() ? -1
                : def.duration().clamp(durationOverride >= 0 ? durationOverride
                        : def.duration().defaultTicks());

        EffectInstance existing = store.get(effectId);
        if (existing == null) {
            EffectInstance instance = new EffectInstance(effectId, duration, level,
                    Math.min(Math.max(1, stacks), Math.max(1, def.stacking().maxStacks())), source);
            store.all().add(instance);
            applyModifiers(target, def, instance);
            runActions(target, def.events().onApplied());
            dirty(target);
            return true;
        }

        switch (def.stacking().mode()) {
            case "replace" -> {
                existing.remaining = duration;
                existing.level = Math.max(1, level);
                existing.stacks = Math.max(1, stacks);
                existing.source = source;
            }
            case "extend" -> {
                if (!existing.infinite() && duration > 0) {
                    existing.remaining = def.duration().clamp(existing.remaining + duration);
                }
            }
            case "stacks" -> {
                int max = Math.max(1, def.stacking().maxStacks());
                int before = existing.stacks;
                existing.stacks = Math.min(max, existing.stacks + Math.max(1, stacks));
                if (def.stacking().refreshDuration()) existing.remaining = duration;
                if (existing.stacks > before) {
                    runActions(target, def.events().onStackAdded());
                    if (existing.stacks >= max) {
                        runActions(target, def.events().onMaxStacks());
                    }
                }
            }
            default -> existing.remaining = duration;   // refresh
        }
        // the on_max_stacks handler may have removed the effect — re-check
        EffectInstance current = store.get(effectId);
        if (current != null) applyModifiers(target, def, current);
        dirty(target);
        return true;
    }

    // ------------------------------------------------------------ remove

    /** expired=true fires on_expired, false fires on_removed (cleanse). */
    public static boolean remove(LivingEntity target, Identifier effectId, boolean expired) {
        EffectStore store = EffectHolder.resolve(target);
        if (store == null) return false;
        EffectInstance instance = store.get(effectId);
        if (instance == null) return false;
        store.all().remove(instance);

        EffectDefinition def = CoreData.EFFECTS.get(effectId).orElse(null);
        if (def != null) {
            removeModifiers(target, def, effectId);
            runActions(target, expired ? def.events().onExpired() : def.events().onRemoved());
        }
        dirty(target);
        return true;
    }

    /** Removes every effect whose definition matches; returns count. */
    public static int removeWhere(LivingEntity target, Predicate<EffectDefinition> predicate) {
        EffectStore store = EffectHolder.resolve(target);
        if (store == null) return 0;
        int removed = 0;
        for (EffectInstance instance : List.copyOf(store.all())) {
            EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
            if (def != null && predicate.test(def)) {
                if (remove(target, instance.effectId, false)) removed++;
            }
        }
        return removed;
    }

    public static boolean removeStacks(LivingEntity target, Identifier effectId, int count) {
        EffectStore store = EffectHolder.resolve(target);
        if (store == null) return false;
        EffectInstance instance = store.get(effectId);
        if (instance == null) return false;
        instance.stacks -= Math.max(1, count);
        if (instance.stacks <= 0) return remove(target, effectId, false);
        EffectDefinition def = CoreData.EFFECTS.get(effectId).orElse(null);
        if (def != null) applyModifiers(target, def, instance);
        dirty(target);
        return true;
    }

    // ------------------------------------------------------------ tick

    /** Per-owner tick: durations, expiry, interval rules. */
    public static void tick(EffectStore store, LivingEntity owner) {
        if (store.isEmpty()) return;
        long gameTime = owner.level().getGameTime();

        for (EffectInstance instance : List.copyOf(store.all())) {
            EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
            if (def == null) continue;   // dormant until a datapack defines it again

            for (StatDef.Rule rule : def.rules()) {
                if (!rule.trigger().shouldFireAt(gameTime, owner)) continue;
                if (!allPass(rule.conditions(), owner)) continue;
                runActions(owner, rule.actions());
            }

            if (!instance.infinite()) {
                instance.remaining--;
                if (instance.remaining <= 0) {
                    remove(owner, instance.effectId, true);
                }
            }
        }
    }

    /** Join/load path: re-apply modifiers without firing events. */
    public static void reapplyAll(LivingEntity owner) {
        EffectStore store = EffectHolder.resolve(owner);
        if (store == null) return;
        for (EffectInstance instance : store.all()) {
            CoreData.EFFECTS.get(instance.effectId)
                    .ifPresent(def -> applyModifiers(owner, def, instance));
        }
    }

    /** Death: drop effects unless keep_on_death. */
    public static void onDeath(LivingEntity owner) {
        EffectStore store = EffectHolder.resolve(owner);
        if (store == null) return;
        for (EffectInstance instance : List.copyOf(store.all())) {
            EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
            if (def == null || !def.persistence().keepOnDeath()) {
                remove(owner, instance.effectId, false);
            }
        }
    }

    // ------------------------------------------------------------ restrictions

    /** what: "move" | "jump" | "attack" | "use_items" */
    public static boolean isRestricted(LivingEntity owner, String what) {
        EffectStore store = EffectHolder.resolve(owner);
        if (store == null || store.isEmpty()) return false;
        for (EffectInstance instance : store.all()) {
            EffectDefinition def = CoreData.EFFECTS.get(instance.effectId).orElse(null);
            if (def == null || def.restrictions().isEmpty()) continue;
            EffectDefinition.Restrictions r = def.restrictions().get();
            boolean restricted = switch (what) {
                case "move" -> !r.canMove();
                case "jump" -> !r.canJump();
                case "attack" -> !r.canAttack();
                case "use_items" -> !r.canUseItems();
                default -> false;
            };
            if (restricted) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ modifiers

    private static Identifier modifierId(Identifier effectId, String suffix) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                "effect/" + effectId.getNamespace() + "/" + effectId.getPath() + "/" + suffix);
    }

    private static void applyModifiers(LivingEntity owner, EffectDefinition def,
                                       EffectInstance instance) {
        for (int i = 0; i < def.attributes().size(); i++) {
            EffectDefinition.AttrMod mod = def.attributes().get(i);
            AttributeInstance attribute = resolve(owner, mod.attribute());
            if (attribute == null) continue;
            Identifier id = modifierId(instance.effectId, "attr" + i);
            attribute.removeModifier(id);
            attribute.addPermanentModifier(new AttributeModifier(id,
                    mod.total(instance.level, instance.stacks), mapOperation(mod.operation())));
        }
        def.restrictions().ifPresent(r -> {
            applyRestrictionModifier(owner, instance.effectId, "move",
                    Attributes.MOVEMENT_SPEED, !r.canMove());
            applyRestrictionModifier(owner, instance.effectId, "jump",
                    Attributes.JUMP_STRENGTH, !r.canJump());
        });
    }

    private static void applyRestrictionModifier(LivingEntity owner, Identifier effectId,
                                                 String suffix, Holder<Attribute> attribute,
                                                 boolean active) {
        AttributeInstance instance = owner.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = modifierId(effectId, suffix);
        instance.removeModifier(id);
        if (active) {
            instance.addPermanentModifier(new AttributeModifier(id, -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifiers(LivingEntity owner, EffectDefinition def,
                                        Identifier effectId) {
        for (int i = 0; i < def.attributes().size(); i++) {
            AttributeInstance attribute = resolve(owner, def.attributes().get(i).attribute());
            if (attribute != null) attribute.removeModifier(modifierId(effectId, "attr" + i));
        }
        for (String suffix : new String[]{"move", "jump"}) {
            Holder<Attribute> holder = suffix.equals("move")
                    ? Attributes.MOVEMENT_SPEED : Attributes.JUMP_STRENGTH;
            AttributeInstance instance = owner.getAttribute(holder);
            if (instance != null) instance.removeModifier(modifierId(effectId, suffix));
        }
    }

    @Nullable
    private static AttributeInstance resolve(LivingEntity owner, Identifier attributeId) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
        if (holder == null) {
            Constants.LOG.warn("[myrpg] effect references unknown attribute {}", attributeId);
            return null;
        }
        return owner.getAttribute(holder);
    }

    private static AttributeModifier.Operation mapOperation(String operation) {
        return switch (operation) {
            case "add_multiplied_base", "multiply_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total", "multiply_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    // ------------------------------------------------------------ helpers

    private static boolean allPass(List<RpgCondition> conditions, LivingEntity owner) {
        ServerPlayer player = owner instanceof ServerPlayer p ? p : null;
        RpgCondition.ConditionContext ctx = new RpgCondition.ConditionContext(owner, player, null);
        for (RpgCondition condition : conditions) {
            if (!condition.test(ctx)) return false;
        }
        return true;
    }

    private static void runActions(LivingEntity owner, List<RpgAction> actions) {
        if (actions.isEmpty()) return;
        ServerPlayer player = owner instanceof ServerPlayer p ? p : null;
        RpgAction.ActionContext ctx = new RpgAction.ActionContext(owner, player);
        for (RpgAction action : actions) {
            action.execute(ctx);
        }
    }

    private static void dirty(LivingEntity owner) {
        if (owner instanceof ServerPlayer player) {
            PlayerEffects.markDirty(player);
        }
    }

    private EffectManager() {}
}
