package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.entities.ai.goal.FleeAtLowHealthGoal;
import com.myyyst.myrpg.entities.ai.goal.FollowPlayerGoal;
import com.myyyst.myrpg.entities.ai.goal.GuardPositionGoal;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;

/** Built-in AI goal providers. */
public final class AiGoals {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("myrpg_entities", path);
    }

    public record RandomWalk(int priority, double speed) implements AiGoalDef {
        public static final MapCodec<RandomWalk> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 5).forGetter(RandomWalk::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(RandomWalk::speed)
        ).apply(i, RandomWalk::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new RandomStrollGoal(entity, speed);
        }
    }

    public record LookAtPlayer(int priority, double range) implements AiGoalDef {
        public static final MapCodec<LookAtPlayer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 7).forGetter(LookAtPlayer::priority),
                Codec.DOUBLE.optionalFieldOf("range", 8.0).forGetter(LookAtPlayer::range)
        ).apply(i, LookAtPlayer::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new LookAtPlayerGoal(entity, Player.class, (float) range);
        }
    }

    public record LookAround(int priority) implements AiGoalDef {
        public static final MapCodec<LookAround> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 8).forGetter(LookAround::priority)
        ).apply(i, LookAround::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new RandomLookAroundGoal(entity);
        }
    }

    public record MeleeAttack(int priority, double speed) implements AiGoalDef {
        public static final MapCodec<MeleeAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 2).forGetter(MeleeAttack::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(MeleeAttack::speed)
        ).apply(i, MeleeAttack::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new MeleeAttackGoal(entity, speed, true);
        }
    }

    public record FollowPlayer(int priority, double speed, double stopDistance, double range)
            implements AiGoalDef {
        public static final MapCodec<FollowPlayer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 4).forGetter(FollowPlayer::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(FollowPlayer::speed),
                Codec.DOUBLE.optionalFieldOf("stop_distance", 3.0).forGetter(FollowPlayer::stopDistance),
                Codec.DOUBLE.optionalFieldOf("range", 32.0).forGetter(FollowPlayer::range)
        ).apply(i, FollowPlayer::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new FollowPlayerGoal(entity, speed, stopDistance, range);
        }
    }

    public record GuardPosition(int priority, double speed, double radius) implements AiGoalDef {
        public static final MapCodec<GuardPosition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 3).forGetter(GuardPosition::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(GuardPosition::speed),
                Codec.DOUBLE.optionalFieldOf("radius", 16.0).forGetter(GuardPosition::radius)
        ).apply(i, GuardPosition::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new GuardPositionGoal(entity, speed, radius);
        }
    }

    public record RangedAttack(int priority, double speed, int interval, double range)
            implements AiGoalDef {
        public static final MapCodec<RangedAttack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 2).forGetter(RangedAttack::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(RangedAttack::speed),
                Codec.INT.optionalFieldOf("interval", 30).forGetter(RangedAttack::interval),
                Codec.DOUBLE.optionalFieldOf("range", 15.0).forGetter(RangedAttack::range)
        ).apply(i, RangedAttack::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new RangedAttackGoal(entity, speed, Math.max(1, interval), (float) range);
        }
    }

    public record FleeAtLowHealth(int priority, double speed, double threshold) implements AiGoalDef {
        public static final MapCodec<FleeAtLowHealth> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 1).forGetter(FleeAtLowHealth::priority),
                Codec.DOUBLE.optionalFieldOf("speed", 1.25).forGetter(FleeAtLowHealth::speed),
                Codec.DOUBLE.optionalFieldOf("threshold", 0.3).forGetter(FleeAtLowHealth::threshold)
        ).apply(i, FleeAtLowHealth::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new FleeAtLowHealthGoal(entity, speed, threshold);
        }
    }

    public record AvoidEntity(int priority, Identifier entityType, double distance, double speed)
            implements AiGoalDef {
        public static final MapCodec<AvoidEntity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 4).forGetter(AvoidEntity::priority),
                Identifier.CODEC.fieldOf("entity").forGetter(AvoidEntity::entityType),
                Codec.DOUBLE.optionalFieldOf("distance", 8.0).forGetter(AvoidEntity::distance),
                Codec.DOUBLE.optionalFieldOf("speed", 1.2).forGetter(AvoidEntity::speed)
        ).apply(i, AvoidEntity::new));
        @Override public MapCodec<? extends AiGoalDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new AvoidEntityGoal<>(entity, LivingEntity.class,
                    (float) distance, speed, speed * 1.25,
                    living -> entityType.equals(
                            BuiltInRegistries.ENTITY_TYPE.getKey(living.getType())));
        }
    }

    public static void init() {
        AiGoalDef.REGISTRY.register(id("random_walk"), RandomWalk.CODEC);
        AiGoalDef.REGISTRY.register(id("look_at_player"), LookAtPlayer.CODEC);
        AiGoalDef.REGISTRY.register(id("look_around"), LookAround.CODEC);
        AiGoalDef.REGISTRY.register(id("melee_attack"), MeleeAttack.CODEC);
        AiGoalDef.REGISTRY.register(id("follow_player"), FollowPlayer.CODEC);
        AiGoalDef.REGISTRY.register(id("guard_position"), GuardPosition.CODEC);
        AiGoalDef.REGISTRY.register(id("ranged_attack"), RangedAttack.CODEC);
        AiGoalDef.REGISTRY.register(id("flee_low_health"), FleeAtLowHealth.CODEC);
        AiGoalDef.REGISTRY.register(id("avoid_entity"), AvoidEntity.CODEC);
    }

    private AiGoals() {}
}
