package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.network.RpgPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view of the local player's active custom effects. The server
 * sends a full replace on every structural change; between syncs the client
 * counts remaining ticks down itself (vanilla-potion style).
 */
public final class ClientEffectCache {

    /** Mutable countdown wrapper around the immutable payload entry. */
    public static final class ActiveEffect {
        public final RpgPayloads.EffectEntry entry;
        public int remaining;   // ticks; -1 = infinite

        ActiveEffect(RpgPayloads.EffectEntry entry) {
            this.entry = entry;
            this.remaining = entry.remaining();
        }
    }

    private static final List<ActiveEffect> ENTRIES = new ArrayList<>();

    /** Full replace: the server always sends the complete list, so drop the old one first. */
    public static void accept(RpgPayloads.SyncEffects payload) {
        ENTRIES.clear();
        for (RpgPayloads.EffectEntry entry : payload.entries()) {
            ENTRIES.add(new ActiveEffect(entry));
        }
    }

    /** Once per client tick: local countdown between server syncs. */
    public static void tick() {
        // Expiring locally keeps the HUD smooth; the authoritative removal still comes
        // from the server, which will re-sync the real list right after.
        ENTRIES.removeIf(active -> {
            if (active.remaining < 0) return false;   // infinite
            active.remaining--;
            return active.remaining <= 0;
        });
    }

    /** Live list for the HUD to draw. */
    public static List<ActiveEffect> entries() { return ENTRIES; }

    /** Drops all cached effects so the next world starts clean. */
    public static void clear() { ENTRIES.clear(); }   // call on disconnect

    /** Static-only cache: never instantiated. */
    private ClientEffectCache() {}
}
