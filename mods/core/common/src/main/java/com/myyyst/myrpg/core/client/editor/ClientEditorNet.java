package com.myyyst.myrpg.core.client.editor;

import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client→server sends for the editor. Wired per loader at client init.
 *
 * <p>The editor screens live in the common module and so cannot call a loader's networking
 * API directly. Each loader assigns {@link #sender} during client init; until then the
 * default no-op sender simply swallows packets rather than crashing.</p>
 */
public final class ClientEditorNet {
    /** Set by the loader's client init to the real "send to server" call. */
    public static java.util.function.Consumer<CustomPacketPayload> sender = p -> {};

    /** Asks the server to write this stat definition into the overlay datapack. */
    public static void sendSave(String statId, String json) {
        sender.accept(new RpgPayloads.SaveStat(statId, json));
    }
    /** Asks the server to delete this stat's overlay file. */
    public static void sendDelete(String statId) {
        sender.accept(new RpgPayloads.DeleteStat(statId));
    }
    /** Effect-side twin of {@link #sendSave}. */
    public static void sendSaveEffect(String effectId, String json) {
        sender.accept(new RpgPayloads.SaveEffect(effectId, json));
    }
    /** Effect-side twin of {@link #sendDelete}. */
    public static void sendDeleteEffect(String effectId) {
        sender.accept(new RpgPayloads.DeleteEffect(effectId));
    }
    /** Static-only helper: never instantiated. */
    private ClientEditorNet() {}
}