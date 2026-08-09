package com.myyyst.myrpg.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.stat.StageEffect;
import com.myyyst.myrpg.core.trigger.RpgTrigger;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A creator-defined stat. The framework never knows what the stat MEANS —
 * only value/range/stages/rules/display. Full schema per the custom-stats
 * design doc; engine support arrives in stages, but files against this
 * schema are forever-valid.
 */
public record StatDef(
        Optional<Display> display,
        ValueConfig value,
        Optional<Persistence> persistence,
        List<Stage> stages,
        List<Rule> rules,
        Optional<Hud> hud
) {
    public static final Codec<StatDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Display.CODEC.optionalFieldOf("display").forGetter(StatDef::display),
            ValueConfig.CODEC.optionalFieldOf("value", ValueConfig.DEFAULT).forGetter(StatDef::value),
            Persistence.CODEC.optionalFieldOf("persistence").forGetter(StatDef::persistence),
            Stage.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(StatDef::stages),
            Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(StatDef::rules),
            Hud.CODEC.optionalFieldOf("hud").forGetter(StatDef::hud)
    ).apply(i, StatDef::new));

    // ------------------------------------------------------------ display

    public record Display(
            Optional<String> name,
            Optional<String> description,
            Optional<Identifier> icon,
            Optional<String> color            // "#RRGGBB"
    ) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name").forGetter(Display::name),
                Codec.STRING.optionalFieldOf("description").forGetter(Display::description),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Display::icon),
                Codec.STRING.optionalFieldOf("color").forGetter(Display::color)
        ).apply(i, Display::new));
    }

    // ------------------------------------------------------------ value

    public record ValueConfig(
            String type,                      // "stored" | "computed"
            double defaultValue,
            double min,
            double max,
            boolean decimal,
            boolean clamp,
            Optional<String> expression       // computed stats only
    ) {
        public static final ValueConfig DEFAULT =
                new ValueConfig("stored", 0, 0, 100, false, true, Optional.empty());

        public static final Codec<ValueConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "stored").forGetter(ValueConfig::type),
                Codec.DOUBLE.optionalFieldOf("default", 0.0).forGetter(ValueConfig::defaultValue),
                Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(ValueConfig::min),
                Codec.DOUBLE.optionalFieldOf("max", 100.0).forGetter(ValueConfig::max),
                Codec.BOOL.optionalFieldOf("decimal", false).forGetter(ValueConfig::decimal),
                Codec.BOOL.optionalFieldOf("clamp", true).forGetter(ValueConfig::clamp),
                Codec.STRING.optionalFieldOf("expression").forGetter(ValueConfig::expression)
        ).apply(i, ValueConfig::new));

        public boolean isComputed() { return "computed".equals(type); }
    }

    // ------------------------------------------------------------ persistence

    public record Persistence(
            boolean keepOnDeath,
            boolean resetOnRespawn,
            boolean resetOnDimensionChange,
            Optional<Identifier> resetOnLeaveRegion   // regions mod hook; ignored until it exists
    ) {
        public static final Codec<Persistence> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("keep_on_death", true).forGetter(Persistence::keepOnDeath),
                Codec.BOOL.optionalFieldOf("reset_on_respawn", false).forGetter(Persistence::resetOnRespawn),
                Codec.BOOL.optionalFieldOf("reset_on_dimension_change", false).forGetter(Persistence::resetOnDimensionChange),
                Identifier.CODEC.optionalFieldOf("reset_on_leave_region").forGetter(Persistence::resetOnLeaveRegion)
        ).apply(i, Persistence::new));
    }

    // ------------------------------------------------------------ stages

    public record Stage(
            String id,
            double min,
            double max,
            Optional<Display> display,
            List<StageEffect> effects,        // continuous while inside
            List<RpgAction> onEnter,          // once, crossing in
            List<RpgAction> onExit            // once, crossing out
    ) {
        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Stage::id),
                Codec.DOUBLE.fieldOf("min").forGetter(Stage::min),
                Codec.DOUBLE.fieldOf("max").forGetter(Stage::max),
                Display.CODEC.optionalFieldOf("display").forGetter(Stage::display),
                StageEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Stage::effects),
                RpgAction.CODEC.listOf().optionalFieldOf("on_enter", List.of()).forGetter(Stage::onEnter),
                RpgAction.CODEC.listOf().optionalFieldOf("on_exit", List.of()).forGetter(Stage::onExit)
        ).apply(i, Stage::new));
    }

    // ------------------------------------------------------------ rules

    public record Rule(
            RpgTrigger trigger,
            List<RpgCondition> conditions,    // AND
            List<RpgAction> actions
    ) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                RpgTrigger.CODEC.fieldOf("trigger").forGetter(Rule::trigger),
                RpgCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Rule::conditions),
                RpgAction.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Rule::actions)
        ).apply(i, Rule::new));
    }

    // ------------------------------------------------------------ hud

    public record Hud(
            boolean visible,
            String type,                      // bar | number | percentage | icons | hidden
            String visibility,                // always | never | when_changed | when_non_default | above_value | below_value | key_held
            Optional<Double> visibilityValue, // for above/below_value
            boolean showValue
    ) {
        public static final Codec<Hud> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("visible", true).forGetter(Hud::visible),
                Codec.STRING.optionalFieldOf("type", "bar").forGetter(Hud::type),
                Codec.STRING.optionalFieldOf("visibility", "always").forGetter(Hud::visibility),
                Codec.DOUBLE.optionalFieldOf("visibility_value").forGetter(Hud::visibilityValue),
                Codec.BOOL.optionalFieldOf("show_value", false).forGetter(Hud::showValue)
        ).apply(i, Hud::new));
    }
}