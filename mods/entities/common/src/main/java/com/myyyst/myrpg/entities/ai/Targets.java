package com.myyyst.myrpg.entities.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Built-in target-selection providers. */
public final class Targets {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("myrpg_entities", path);
    }

    public record AttackPlayer(int priority) implements TargetDef {
        public static final MapCodec<AttackPlayer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 2).forGetter(AttackPlayer::priority)
        ).apply(i, AttackPlayer::new));
        @Override public MapCodec<? extends TargetDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            // NOTE drift: (mob, targetClass, mustSee)
            return new NearestAttackableTargetGoal<>(entity, Player.class, true);
        }
    }

    public record Retaliate(int priority) implements TargetDef {
        public static final MapCodec<Retaliate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 1).forGetter(Retaliate::priority)
        ).apply(i, Retaliate::new));
        @Override public MapCodec<? extends TargetDef> codec() { return CODEC; }
        @Override public Goal build(RpgEntity entity) {
            return new HurtByTargetGoal(entity);
        }
    }

    public record AttackEntityType(int priority, Identifier entityType) implements TargetDef {
        public static final MapCodec<AttackEntityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("priority", 3).forGetter(AttackEntityType::priority),
                Identifier.CODEC.fieldOf("entity").forGetter(AttackEntityType::entityType)
        ).apply(i, AttackEntityType::new));
        @Override public MapCodec<? extends TargetDef> codec() { return CODEC; }
        @Override public @Nullable Goal build(RpgEntity entity) { return null; }   // slice 2
    }

    public static void init() {
        TargetDef.REGISTRY.register(id("player"), AttackPlayer.CODEC);
        TargetDef.REGISTRY.register(id("retaliate"), Retaliate.CODEC);
        TargetDef.REGISTRY.register(id("entity_type"), AttackEntityType.CODEC);
    }

    private Targets() {}
}