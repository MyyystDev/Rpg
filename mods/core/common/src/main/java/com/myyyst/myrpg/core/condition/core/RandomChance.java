package com.myyyst.myrpg.core.condition.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.condition.RpgCondition;

import java.util.Random;

/**
 * chance in [0,1]. per_player_seed=true makes the result STABLE per player
 * (and per seed_salt): the same player always gets the same answer — for
 * "30% of players have this dialogue option" rather than per-evaluation dice.
 */
public record RandomChance(double chance, boolean perPlayerSeed, long seedSalt) implements RpgCondition {

    public static final MapCodec<RandomChance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0.0, 1.0).fieldOf("chance").forGetter(RandomChance::chance),
            Codec.BOOL.optionalFieldOf("per_player_seed", false).forGetter(RandomChance::perPlayerSeed),
            Codec.LONG.optionalFieldOf("seed_salt", 0L).forGetter(RandomChance::seedSalt)
    ).apply(i, RandomChance::new));

    @Override
    public boolean test(ConditionContext ctx) {
        if (perPlayerSeed) {
            if (ctx.player() == null) return false;   // stable-per-player needs a player
            // Seed derived from the UUID: same player + same salt = same answer forever.
            // Vary seed_salt to get an independent draw for a different question.
            long seed = ctx.player().getUUID().getLeastSignificantBits() ^ seedSalt;
            return new Random(seed).nextDouble() < chance;
        }
        return ctx.self().getRandom().nextDouble() < chance;   // fresh dice each evaluation
    }

    @Override public MapCodec<? extends RpgCondition> codec() { return CODEC; }
}