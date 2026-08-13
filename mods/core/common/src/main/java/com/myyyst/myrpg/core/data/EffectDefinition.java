package com.myyyst.myrpg.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.action.RpgAction;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A creator-defined status effect (custom-status-effects-system doc).
 * The framework never knows what the effect MEANS — only category, tags,
 * duration, stacking, modifiers, restrictions, rules, events, display.
 */
public record EffectDefinition(
        Optional<Display> display,
        String category,                  // beneficial | harmful | neutral
        List<String> tags,
        Duration duration,
        Stacking stacking,
        List<AttrMod> attributes,
        Optional<Restrictions> restrictions,
        List<StatDef.Rule> rules,         // shared trigger/conditions/actions shape
        Events events,
        DisplayOptions displayOptions,
        Persistence persistence
) {

    public static final Codec<EffectDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Display.CODEC.optionalFieldOf("display").forGetter(EffectDefinition::display),
            Codec.STRING.optionalFieldOf("category", "neutral").forGetter(EffectDefinition::category),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(EffectDefinition::tags),
            Duration.CODEC.optionalFieldOf("duration", Duration.DEFAULT).forGetter(EffectDefinition::duration),
            Stacking.CODEC.optionalFieldOf("stacking", Stacking.DEFAULT).forGetter(EffectDefinition::stacking),
            AttrMod.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(EffectDefinition::attributes),
            Restrictions.CODEC.optionalFieldOf("restrictions").forGetter(EffectDefinition::restrictions),
            StatDef.Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(EffectDefinition::rules),
            Events.CODEC.optionalFieldOf("events", Events.EMPTY).forGetter(EffectDefinition::events),
            DisplayOptions.CODEC.optionalFieldOf("display_options", DisplayOptions.DEFAULT).forGetter(EffectDefinition::displayOptions),
            Persistence.CODEC.optionalFieldOf("persistence", Persistence.DEFAULT).forGetter(EffectDefinition::persistence)
    ).apply(i, EffectDefinition::new));

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    // ------------------------------------------------------------ display

    public record Display(Optional<String> name, Optional<String> description,
                          Optional<Identifier> icon, Optional<String> color) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name").forGetter(Display::name),
                Codec.STRING.optionalFieldOf("description").forGetter(Display::description),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Display::icon),
                Codec.STRING.optionalFieldOf("color").forGetter(Display::color)
        ).apply(i, Display::new));
    }

    // ------------------------------------------------------------ duration

    /** type: "timed" | "infinite". Ticks. */
    public record Duration(String type, int defaultTicks, int maximumTicks) {
        public static final Duration DEFAULT = new Duration("timed", 200, 0);
        public static final Codec<Duration> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "timed").forGetter(Duration::type),
                Codec.INT.optionalFieldOf("default", 200).forGetter(Duration::defaultTicks),
                Codec.INT.optionalFieldOf("maximum", 0).forGetter(Duration::maximumTicks)
        ).apply(i, Duration::new));

        public boolean infinite() { return "infinite".equals(type); }

        /** Clamps a requested duration to the configured maximum (0 = uncapped). */
        public int clamp(int ticks) {
            return maximumTicks > 0 ? Math.min(ticks, maximumTicks) : ticks;
        }
    }

    // ------------------------------------------------------------ stacking

    /** mode: replace | refresh | extend | stacks */
    public record Stacking(String mode, int maxStacks, boolean refreshDuration) {
        public static final Stacking DEFAULT = new Stacking("refresh", 1, true);
        public static final Codec<Stacking> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("mode", "refresh").forGetter(Stacking::mode),
                Codec.INT.optionalFieldOf("max_stacks", 1).forGetter(Stacking::maxStacks),
                Codec.BOOL.optionalFieldOf("refresh_duration", true).forGetter(Stacking::refreshDuration)
        ).apply(i, Stacking::new));
    }

    // ------------------------------------------------------------ attribute modifiers

    /** operation: add_value | add_multiplied_base | add_multiplied_total.
     *  total = value + value_per_stack * stacks + value_per_level * level */
    public record AttrMod(Identifier attribute, String operation,
                          double value, double valuePerStack, double valuePerLevel) {
        public static final Codec<AttrMod> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("attribute").forGetter(AttrMod::attribute),
                Codec.STRING.optionalFieldOf("operation", "add_value").forGetter(AttrMod::operation),
                Codec.DOUBLE.optionalFieldOf("value", 0.0).forGetter(AttrMod::value),
                Codec.DOUBLE.optionalFieldOf("value_per_stack", 0.0).forGetter(AttrMod::valuePerStack),
                Codec.DOUBLE.optionalFieldOf("value_per_level", 0.0).forGetter(AttrMod::valuePerLevel)
        ).apply(i, AttrMod::new));

        public double total(int level, int stacks) {
            return value + valuePerStack * stacks + valuePerLevel * level;
        }
    }

    // ------------------------------------------------------------ restrictions

    public record Restrictions(boolean canMove, boolean canJump,
                               boolean canAttack, boolean canUseItems) {
        public static final Codec<Restrictions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("can_move", true).forGetter(Restrictions::canMove),
                Codec.BOOL.optionalFieldOf("can_jump", true).forGetter(Restrictions::canJump),
                Codec.BOOL.optionalFieldOf("can_attack", true).forGetter(Restrictions::canAttack),
                Codec.BOOL.optionalFieldOf("can_use_items", true).forGetter(Restrictions::canUseItems)
        ).apply(i, Restrictions::new));
    }

    // ------------------------------------------------------------ events

    public record Events(List<RpgAction> onApplied, List<RpgAction> onRemoved,
                         List<RpgAction> onExpired, List<RpgAction> onStackAdded,
                         List<RpgAction> onMaxStacks) {
        public static final Events EMPTY = new Events(List.of(), List.of(), List.of(), List.of(), List.of());
        public static final Codec<Events> CODEC = RecordCodecBuilder.create(i -> i.group(
                RpgAction.CODEC.listOf().optionalFieldOf("on_applied", List.of()).forGetter(Events::onApplied),
                RpgAction.CODEC.listOf().optionalFieldOf("on_removed", List.of()).forGetter(Events::onRemoved),
                RpgAction.CODEC.listOf().optionalFieldOf("on_expired", List.of()).forGetter(Events::onExpired),
                RpgAction.CODEC.listOf().optionalFieldOf("on_stack_added", List.of()).forGetter(Events::onStackAdded),
                RpgAction.CODEC.listOf().optionalFieldOf("on_max_stacks", List.of()).forGetter(Events::onMaxStacks)
        ).apply(i, Events::new));
    }

    // ------------------------------------------------------------ display options

    public record DisplayOptions(boolean showIcon, boolean showDuration,
                                 boolean showStacks, boolean showLevel, boolean hidden) {
        public static final DisplayOptions DEFAULT = new DisplayOptions(true, true, true, false, false);
        public static final Codec<DisplayOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(DisplayOptions::showIcon),
                Codec.BOOL.optionalFieldOf("show_duration", true).forGetter(DisplayOptions::showDuration),
                Codec.BOOL.optionalFieldOf("show_stacks", true).forGetter(DisplayOptions::showStacks),
                Codec.BOOL.optionalFieldOf("show_level", false).forGetter(DisplayOptions::showLevel),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(DisplayOptions::hidden)
        ).apply(i, DisplayOptions::new));
    }

    // ------------------------------------------------------------ persistence

    public record Persistence(boolean keepOnDeath, boolean keepOnLogout) {
        public static final Persistence DEFAULT = new Persistence(false, true);
        public static final Codec<Persistence> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("keep_on_death", false).forGetter(Persistence::keepOnDeath),
                Codec.BOOL.optionalFieldOf("keep_on_logout", true).forGetter(Persistence::keepOnLogout)
        ).apply(i, Persistence::new));
    }
}
