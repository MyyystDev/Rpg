package com.myyyst.myrpg.core.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.registry.DispatchRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.rmi.registry.Registry;

/**
 * When rules fire. A trigger doesn't run anything itself — the rule engine
 * asks it questions. Two kinds coexist behind one interface:
 *
 *  - Polled triggers (interval): shouldFireAt(gameTime) is checked on the
 *    owner's tick.
 *  - Event triggers (player_death, kill, item_use... arriving with the
 *    event bus): matchesEvent(eventId) is checked when the bus posts.
 *
 * A trigger type implements whichever side applies and leaves the other
 * at its default (never fires).
 */
public interface RpgTrigger {

    /** Polled side: should this trigger fire on this tick? */
    default boolean shouldFireAt(long gameTime, LivingEntity owner) { return false; }

    /** Event side: does this trigger respond to the posted event id? */
    default boolean matchesEvent(Identifier eventId) { return false; }

    /** The codec this instance was registered with; used when writing back to JSON. */
    MapCodec<? extends RpgTrigger> codec();

    /** Type registry; addons register extra trigger types here. */
    DispatchRegistry<RpgTrigger> REGISTRY = new DispatchRegistry<>(RpgTrigger::codec);
    /** Polymorphic codec used for the "trigger" field of a rule. */
    Codec<RpgTrigger> CODEC = REGISTRY.codec();

    /** Registers the built-in trigger types. Called once from {@code MyRpgCommon.init}. */
    static void bootstrap() {
        REGISTRY.register(core("interval"), Interval.CODEC);
        REGISTRY.register(core("event"), EventTrigger.CODEC);
    }

    /** Shorthand for an id in this mod's namespace. */
    private static Identifier core(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    // ---------------------------------------------------------------- builtins

    /**
     * Fires every N ticks (aligned to game time, so all owners with the
     * same interval fire on the same tick — cheap and predictable).
     * Optional offset staggers rules that shouldn't all land together.
     */
    record Interval(int ticks, int offset) implements RpgTrigger {
        public static final MapCodec<Interval> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("ticks").forGetter(Interval::ticks),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("offset", 0).forGetter(Interval::offset)
        ).apply(i, Interval::new));

        @Override
        public boolean shouldFireAt(long gameTime, LivingEntity owner) {
            // Phase comes from world time, not from when the rule was created, so the
            // firing tick is the same for every owner - and offset shifts a whole group.
            return (gameTime + offset) % ticks == 0;
        }

        @Override public MapCodec<? extends RpgTrigger> codec() { return CODEC; }
    }

    /** Fires when the named game event is posted for this owner. */
    record EventTrigger(Identifier event) implements RpgTrigger {
        public static final MapCodec<EventTrigger> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("event").forGetter(EventTrigger::event)
        ).apply(i, EventTrigger::new));

        @Override
        public boolean matchesEvent(Identifier eventId) {
            return event.equals(eventId);
        }

        @Override public MapCodec<? extends RpgTrigger> codec() { return CODEC; }
    }
}