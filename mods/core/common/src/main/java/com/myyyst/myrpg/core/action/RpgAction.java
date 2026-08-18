package com.myyyst.myrpg.core.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import com.myyyst.myrpg.core.stat.PlayerStats;
import com.myyyst.myrpg.core.stat.StatHolder;
import com.myyyst.myrpg.core.stat.StatStore;
import com.myyyst.myrpg.core.util.TextResolver;
import com.myyyst.myrpg.core.variable.VarValue;
import com.myyyst.myrpg.core.variable.Variables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The "do something" half of the data-driven rule system - the counterpart to
 * {@code RpgCondition}.
 *
 * <p>Actions appear in datapacks as a typed object and are run by the rule engines
 * (stat rules, stage on_enter/on_exit, effect events, entity rules):</p>
 *
 * <pre>{@code { "type": "myrpg_core:damage", "amount": 2.0 } }</pre>
 *
 * <p>Every action is server side and must fail softly: an unknown sound, effect or
 * function is logged and skipped rather than thrown, so one typo in a pack cannot
 * break the rule chain around it.</p>
 *
 * <p>Other mods can add their own types by registering into {@link #REGISTRY}.</p>
 */
public interface RpgAction {

    /** Performs the action. Called on the server thread; implementations must not block. */
    void execute(ActionContext ctx);

    /** The codec this instance was registered with; used when writing back to JSON. */
    MapCodec<? extends RpgAction> codec();

    /**
     * Who the action operates on.
     *
     * @param self   the entity the rule belongs to - the default subject of most actions
     * @param player the player involved, if any; player-scoped actions no-op without it
     */
    record ActionContext(LivingEntity self, @Nullable ServerPlayer player) {
        /** Context for a non-player entity. */
        public static ActionContext of(LivingEntity self) {
            return new ActionContext(self, null);
        }
        /** Context where a player is involved (usually self == player). */
        public static ActionContext of(LivingEntity self, ServerPlayer player) {
            return new ActionContext(self, player);
        }
    }

    // ---------------------------------------------------------------- registry

    /** Type registry; addons register extra action types here. */
    DispatchRegistry<RpgAction> REGISTRY = new DispatchRegistry<>(RpgAction::codec);
    /** Polymorphic codec used wherever a datapack lists actions. */
    Codec<RpgAction> CODEC = REGISTRY.codec();

    /** Core's builtins only. Called once from RpgCore.init(). */
    static void bootstrap() {
        REGISTRY.register(core("run_function"), RunFunction.CODEC);
        REGISTRY.register(core("play_sound"), PlaySound.CODEC);
        REGISTRY.register(core("speak"), Speak.CODEC);
        REGISTRY.register(core("damage"), Damage.CODEC);
        REGISTRY.register(core("apply_effect"), ApplyEffect.CODEC);
        REGISTRY.register(core("modify_stat"), ModifyStat.CODEC);
        REGISTRY.register(core("set_variable"), SetVariable.CODEC);
        REGISTRY.register(core("modify_variable"), ModifyVariable.CODEC);
    }

    /** Shorthand for an id in this mod's namespace. */
    private static Identifier core(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    // ---------------------------------------------------------------- builtins

    /**
     * Runs a vanilla datapack function ({@code data/&lt;ns&gt;/function/...}) with the entity
     * as {@code @s} and its position as the execution position. Command output is suppressed
     * so rules firing every tick do not spam the chat/log.
     */
    record RunFunction(Identifier function) implements RpgAction {
        static final MapCodec<RunFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("function").forGetter(RunFunction::function)
        ).apply(i, RunFunction::new));

        @Override public void execute(ActionContext ctx) {
            LivingEntity self = ctx.self();
            if (!(self.level() instanceof ServerLevel level)) return;
            MinecraftServer server = level.getServer();
            server.getFunctions().get(function).ifPresentOrElse(
                    fn -> server.getFunctions().execute(fn,
                            server.createCommandSourceStack()
                                    .withEntity(self)
                                    .withPosition(self.position())
                                    .withSuppressedOutput()),
                    () -> Constants.LOG.warn("[myrpg] Action references missing function {}", function));
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** Plays a sound at the entity's block position for everyone in earshot. */
    record PlaySound(Identifier sound, float volume, float pitch) implements RpgAction {
        static final MapCodec<PlaySound> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("sound").forGetter(PlaySound::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(PlaySound::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(PlaySound::pitch)
        ).apply(i, PlaySound::new));

        @Override public void execute(ActionContext ctx) {
            LivingEntity self = ctx.self();
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(sound);
            if (event == null) {
                Constants.LOG.warn("[myrpg] Action references unknown sound {}", sound);
                return;
            }
            self.level().playSound(null, self.blockPosition(), event, SoundSource.NEUTRAL, volume, pitch);
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** Chat line, as the entity, to players within range. Text: literal or lang key. */
    record Speak(String text, double range) implements RpgAction {
        static final MapCodec<Speak> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Speak::text),
                Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(Speak::range)
        ).apply(i, Speak::new));

        @Override public void execute(ActionContext ctx) {
            LivingEntity self = ctx.self();
            if (!(self.level() instanceof ServerLevel level)) return;
            // Rendered as "<EntityName> message", like a player chat line.
            Component line = Component.empty()
                    .append("<").append(self.getDisplayName()).append("> ")
                    .append(TextResolver.resolve(text));
            for (ServerPlayer player : level.players()) {
                // squared compare avoids a sqrt per player per line
                if (player.distanceToSqr(self) <= range * range) {
                    player.sendSystemMessage(line);
                }
            }
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** Deals flat damage to self (magic-type: bypasses armor). */
    record Damage(float amount) implements RpgAction {
        static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Damage::amount)
        ).apply(i, Damage::new));

        @Override public void execute(ActionContext ctx) {
            // NOTE drift: hurt vs hurtServer — mirror the old project's compiled call.
            ctx.self().hurt(ctx.self().damageSources().magic(), amount);
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /** Applies a vanilla status effect to self. Duration in ticks. */
    record ApplyEffect(Identifier effect, int duration, int amplifier) implements RpgAction {
        static final MapCodec<ApplyEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("effect").forGetter(ApplyEffect::effect),
                Codec.INT.optionalFieldOf("duration", 100).forGetter(ApplyEffect::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyEffect::amplifier)
        ).apply(i, ApplyEffect::new));

        @Override public void execute(ActionContext ctx) {
            // NOTE drift: MOB_EFFECT lookup returns a Holder; mirror old spelling.
            var holder = BuiltInRegistries.MOB_EFFECT.get(effect).orElse(null);
            if (holder == null) {
                Constants.LOG.warn("[myrpg] apply_effect: unknown effect {}", effect);
                return;
            }
            ctx.self().addEffect(new MobEffectInstance(holder, duration, amplifier));
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /**
     * Modifies a stat on self via the standard operations.
     * { "type": "myrpg_core:modify_stat", "stat": "mypack:mana",
     *   "operation": "add", "value": -20 }
     * Operations: set | add | subtract | multiply | divide.
     */
    record ModifyStat(Identifier stat, String operation, double value) implements RpgAction {
        static final MapCodec<ModifyStat> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("stat").forGetter(ModifyStat::stat),
                Codec.STRING.optionalFieldOf("operation", "add").forGetter(ModifyStat::operation),
                Codec.DOUBLE.fieldOf("value").forGetter(ModifyStat::value)
        ).apply(i, ModifyStat::new));

        // ModifyStat.execute:
        @Override public void execute(ActionContext ctx) {
            StatStore store = StatHolder.resolve(ctx.self());
            if (store == null) return;   // entity has no stats (plain vanilla mob)
            store.modify(ctx.self(), stat, operation, value);
            if (ctx.self() instanceof ServerPlayer player) {
                PlayerStats.markDirty(player);   // player stores are world-saved, flag for write
            }
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /**
     * Sets a variable. Player scope requires a player in context.
     * { "type": "myrpg_core:set_variable", "scope": "player",
     *   "name": "chose_dark_path", "value": { "string": "yes" } }
     */
    record SetVariable(String scope, String name, VarValue value) implements RpgAction {
        static final MapCodec<SetVariable> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("scope", "player").forGetter(SetVariable::scope),
                Codec.STRING.fieldOf("name").forGetter(SetVariable::name),
                VarValue.CODEC.fieldOf("value").forGetter(SetVariable::value)
        ).apply(i, SetVariable::new));

        @Override public void execute(ActionContext ctx) {
            Variables.set(ctx.self().level(), scope, name, ctx.player(), value);
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }

    /**
     * Arithmetic on a numeric variable; unset treats as 0 (or "default").
     * { "type": "myrpg_core:modify_variable", "scope": "world",
     *   "name": "sacrifices", "operation": "add", "value": 1 }
     */
    record ModifyVariable(String scope, String name, String operation,
                          double value, double defaultValue) implements RpgAction {
        static final MapCodec<ModifyVariable> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("scope", "player").forGetter(ModifyVariable::scope),
                Codec.STRING.fieldOf("name").forGetter(ModifyVariable::name),
                Codec.STRING.optionalFieldOf("operation", "add").forGetter(ModifyVariable::operation),
                Codec.DOUBLE.fieldOf("value").forGetter(ModifyVariable::value),
                Codec.DOUBLE.optionalFieldOf("default", 0.0).forGetter(ModifyVariable::defaultValue)
        ).apply(i, ModifyVariable::new));

        @Override public void execute(ActionContext ctx) {
            var current = Variables.get(ctx.self().level(), scope, name, ctx.player());
            // Refuse to do arithmetic on a string variable rather than silently overwriting it.
            if (current.isPresent() && !current.get().isNumber()) {
                Constants.LOG.warn("[myrpg] modify_variable on non-numeric variable '{}'", name);
                return;
            }
            double base = current.map(VarValue::asNumber).orElse(defaultValue);   // unset -> "default"
            double result = switch (operation) {
                case "subtract" -> base - value;
                case "multiply" -> base * value;
                case "divide" -> value == 0 ? base : base / value;
                case "set" -> value;
                default -> base + value;   // add
            };
            Variables.set(ctx.self().level(), scope, name, ctx.player(), VarValue.of(result));
        }
        @Override public MapCodec<? extends RpgAction> codec() { return CODEC; }
    }
}