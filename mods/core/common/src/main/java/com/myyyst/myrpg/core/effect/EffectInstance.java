package com.myyyst.myrpg.core.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** One active effect on one entity. Definition data stays in the datapack. */
public class EffectInstance {

    public final Identifier effectId;
    public int remaining;          // ticks; -1 = infinite
    public int level;              // strength tier, >= 1
    public int stacks;             // accumulated applications, >= 1
    @Nullable public UUID source;  // who applied it (kill attribution etc.)

    public EffectInstance(Identifier effectId, int remaining, int level, int stacks,
                          @Nullable UUID source) {
        this.effectId = effectId;
        this.remaining = remaining;
        this.level = Math.max(1, level);
        this.stacks = Math.max(1, stacks);
        this.source = source;
    }

    public boolean infinite() { return remaining < 0; }

    public static final Codec<EffectInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(e -> e.effectId),
            Codec.INT.optionalFieldOf("remaining", -1).forGetter(e -> e.remaining),
            Codec.INT.optionalFieldOf("level", 1).forGetter(e -> e.level),
            Codec.INT.optionalFieldOf("stacks", 1).forGetter(e -> e.stacks),
            UUIDUtil.STRING_CODEC.optionalFieldOf("source").forGetter(e -> Optional.ofNullable(e.source))
    ).apply(i, (id, remaining, level, stacks, source) ->
            new EffectInstance(id, remaining, level, stacks, source.orElse(null))));
}
