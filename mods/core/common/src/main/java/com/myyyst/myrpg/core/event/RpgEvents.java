package com.myyyst.myrpg.core.event;

import com.myyyst.myrpg.core.Constants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Core's game-event dispatch. A fixed vocabulary of events, fed by loader
 * hooks, consumed by rule engines (stat rules, quest objectives, entity
 * triggers). Deliberately minimal: no cancellation, no priorities —
 * observers observe.
 *
 * Event ids double as the trigger-type vocabulary in JSON:
 *   { "trigger": { "type": "myrpg_core:event", "event": "myrpg_core:player_kill" } }
 */
public final class RpgEvents {

    /** What happened, to whom. Fields are null when not applicable. */
    public record GameEvent(
            Identifier id,
            @Nullable ServerPlayer player,     // the acting player
            @Nullable LivingEntity subject     // the other party (victim, target...)
    ) {}

    /** Observer callback. Implementations must not throw - see {@link #post}. */
    public interface Listener {
        void on(GameEvent event);
    }

    /** Registered in init order; never unsubscribed, so this list is effectively static. */
    private static final List<Listener> LISTENERS = new ArrayList<>();

    /** Adds a listener. Call during mod init, not while an event is being dispatched. */
    public static void subscribe(Listener listener) {
        LISTENERS.add(listener);
    }

    /**
     * Dispatches an event to every listener.
     * A listener that throws is logged and skipped so one broken consumer cannot stop the rest.
     */
    public static void post(GameEvent event) {
        for (Listener listener : LISTENERS) {
            try {
                listener.on(event);
            } catch (Exception e) {
                Constants.LOG.error("[myrpg] Event listener failed on {}", event.id(), e);
            }
        }
    }

    // ---- the event id vocabulary (grow as hooks land) ----
    /** Builds an event id in this mod's namespace; also usable by addons for their own events. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    public static final Identifier PLAYER_JOIN = id("player_join");
    public static final Identifier PLAYER_RESPAWN = id("player_respawn");
    public static final Identifier PLAYER_DEATH = id("player_death");
    public static final Identifier PLAYER_KILL = id("player_kill");           // player killed subject
    public static final Identifier PLAYER_DAMAGED = id("player_damaged");
    public static final Identifier PLAYER_DIMENSION_CHANGE = id("player_dimension_change");

    /** Static-only dispatcher: never instantiated. */
    private RpgEvents() {}
}