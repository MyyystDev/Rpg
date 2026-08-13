package com.myyyst.myrpg.entities.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.entities.ai.AiGoalDef;
import com.myyyst.myrpg.entities.ai.TargetDef;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One custom entity definition, loaded from the myrpg/entities data folder. */
public record EntityDefinition(
        Optional<Display> display,
        List<String> tags,
        Optional<Appearance> appearance,
        Map<String, Double> attributes,
        Map<Identifier, Double> stats,
        Optional<Equipment> equipment,
        Optional<Movement> movement,
        List<AiGoalDef> ai,
        List<TargetDef> targeting,
        Optional<Combat> combat,
        List<Interaction> interactions,
        Optional<Loot> loot,
        Optional<Persistence> persistence,
        List<StatDef.Rule> rules,
        List<String> effectImmunities   // "mypack:frozen" or "#rpg:crowd_control"
) {

    public record Display(Optional<String> name, Optional<String> description, boolean nameVisible) {
        public static final Codec<Display> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name").forGetter(Display::name),
                Codec.STRING.optionalFieldOf("description").forGetter(Display::description),
                Codec.BOOL.optionalFieldOf("name_visible", true).forGetter(Display::nameVisible)
        ).apply(i, Display::new));
    }

    public record Appearance(String model, Optional<Identifier> texture, double scale,
                             Optional<Double> hitboxWidth, Optional<Double> hitboxHeight,
                             boolean glow) {
        public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("model", "myrpg_entities:humanoid").forGetter(Appearance::model),
                Identifier.CODEC.optionalFieldOf("texture").forGetter(Appearance::texture),
                Codec.DOUBLE.optionalFieldOf("scale", 1.0).forGetter(Appearance::scale),
                Codec.DOUBLE.optionalFieldOf("hitbox_width").forGetter(Appearance::hitboxWidth),
                Codec.DOUBLE.optionalFieldOf("hitbox_height").forGetter(Appearance::hitboxHeight),
                Codec.BOOL.optionalFieldOf("glow", false).forGetter(Appearance::glow)
        ).apply(i, Appearance::new));
    }

    public record Equipment(Optional<String> mainhand, Optional<String> offhand,
                            Optional<String> head, Optional<String> chest,
                            Optional<String> legs, Optional<String> feet) {
        public static final Codec<Equipment> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("mainhand").forGetter(Equipment::mainhand),
                Codec.STRING.optionalFieldOf("offhand").forGetter(Equipment::offhand),
                Codec.STRING.optionalFieldOf("head").forGetter(Equipment::head),
                Codec.STRING.optionalFieldOf("chest").forGetter(Equipment::chest),
                Codec.STRING.optionalFieldOf("legs").forGetter(Equipment::legs),
                Codec.STRING.optionalFieldOf("feet").forGetter(Equipment::feet)
        ).apply(i, Equipment::new));
    }

    public record Movement(String type, boolean canSwim, boolean canOpenDoors, boolean avoidWater,
                           boolean canJump, boolean canClimb, boolean canFly) {
        public static final Codec<Movement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "ground").forGetter(Movement::type),
                Codec.BOOL.optionalFieldOf("can_swim", true).forGetter(Movement::canSwim),
                Codec.BOOL.optionalFieldOf("can_open_doors", false).forGetter(Movement::canOpenDoors),
                Codec.BOOL.optionalFieldOf("avoid_water", false).forGetter(Movement::avoidWater),
                Codec.BOOL.optionalFieldOf("can_jump", true).forGetter(Movement::canJump),
                Codec.BOOL.optionalFieldOf("can_climb", true).forGetter(Movement::canClimb),
                Codec.BOOL.optionalFieldOf("can_fly", false).forGetter(Movement::canFly)
        ).apply(i, Movement::new));
    }

    public record Combat(String type, double range, int cooldown, double speed,
                         double knockback, double accuracy, double meleeRange,
                         Optional<String> projectile, double projectileSpeed) {
        public static final Codec<Combat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "none").forGetter(Combat::type),
                Codec.DOUBLE.optionalFieldOf("range", 2.0).forGetter(Combat::range),
                Codec.INT.optionalFieldOf("cooldown", 20).forGetter(Combat::cooldown),
                Codec.DOUBLE.optionalFieldOf("speed", 1.2).forGetter(Combat::speed),
                Codec.DOUBLE.optionalFieldOf("knockback", 0.0).forGetter(Combat::knockback),
                Codec.DOUBLE.optionalFieldOf("accuracy", 90.0).forGetter(Combat::accuracy),
                Codec.DOUBLE.optionalFieldOf("melee_range", 4.0).forGetter(Combat::meleeRange),
                Codec.STRING.optionalFieldOf("projectile").forGetter(Combat::projectile),
                Codec.DOUBLE.optionalFieldOf("projectile_speed", 1.6).forGetter(Combat::projectileSpeed)
        ).apply(i, Combat::new));
    }

    /** Right-click behavior: conditions gate, actions run.
     *  NOTE drift: the condition/action types + CODEC constants must match
     *  the exact classes StatDef.Rule imports in your core. */
    public record Interaction(List<com.myyyst.myrpg.core.condition.RpgCondition> conditions,
                              List<com.myyyst.myrpg.core.action.RpgAction> actions) {
        public static final Codec<Interaction> CODEC = RecordCodecBuilder.create(i -> i.group(
                com.myyyst.myrpg.core.condition.RpgCondition.CODEC.listOf()
                        .optionalFieldOf("conditions", List.of()).forGetter(Interaction::conditions),
                com.myyyst.myrpg.core.action.RpgAction.CODEC.listOf()
                        .optionalFieldOf("actions", List.of()).forGetter(Interaction::actions)
        ).apply(i, Interaction::new));
    }

    public record Loot(Optional<Identifier> lootTable, int xp) {
        public static final Codec<Loot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.optionalFieldOf("loot_table").forGetter(Loot::lootTable),
                Codec.INT.optionalFieldOf("xp", 0).forGetter(Loot::xp)
        ).apply(i, Loot::new));
    }

    public record Persistence(boolean despawn) {
        public static final Codec<Persistence> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("despawn", false).forGetter(Persistence::despawn)
        ).apply(i, Persistence::new));
    }

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Display.CODEC.optionalFieldOf("display").forGetter(EntityDefinition::display),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(EntityDefinition::tags),
            Appearance.CODEC.optionalFieldOf("appearance").forGetter(EntityDefinition::appearance),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                    .optionalFieldOf("attributes", Map.of()).forGetter(EntityDefinition::attributes),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE)
                    .optionalFieldOf("stats", Map.of()).forGetter(EntityDefinition::stats),
            Equipment.CODEC.optionalFieldOf("equipment").forGetter(EntityDefinition::equipment),
            Movement.CODEC.optionalFieldOf("movement").forGetter(EntityDefinition::movement),
            AiGoalDef.REGISTRY.codec().listOf()
                    .optionalFieldOf("ai", List.of()).forGetter(EntityDefinition::ai),
            TargetDef.REGISTRY.codec().listOf()
                    .optionalFieldOf("targeting", List.of()).forGetter(EntityDefinition::targeting),
            Combat.CODEC.optionalFieldOf("combat").forGetter(EntityDefinition::combat),
            Interaction.CODEC.listOf()
                    .optionalFieldOf("interactions", List.of()).forGetter(EntityDefinition::interactions),
            Loot.CODEC.optionalFieldOf("loot").forGetter(EntityDefinition::loot),
            Persistence.CODEC.optionalFieldOf("persistence").forGetter(EntityDefinition::persistence),
            StatDef.Rule.CODEC.listOf()
                    .optionalFieldOf("rules", List.of()).forGetter(EntityDefinition::rules),
            Codec.STRING.listOf()
                    .optionalFieldOf("effect_immunities", List.of())
                    .forGetter(EntityDefinition::effectImmunities)
    ).apply(i, EntityDefinition::new));
    // NOTE drift: REGISTRY.codec() — use the same accessor StatDef's CODEC
    // calls on the effect/condition registries.
}