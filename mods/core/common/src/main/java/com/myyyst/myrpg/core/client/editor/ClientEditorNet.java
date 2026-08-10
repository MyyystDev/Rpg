package com.myyyst.myrpg.core.client.editor;

import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client→server sends for the editor. Wired per loader at client init. */
public final class ClientEditorNet {
    public static java.util.function.Consumer<CustomPacketPayload> sender = p -> {};

    public static void sendSave(String statId, String json) {
        sender.accept(new RpgPayloads.SaveStat(statId, json));
    }
    public static void sendDelete(String statId) {
        sender.accept(new RpgPayloads.DeleteStat(statId));
    }
    private ClientEditorNet() {}
}