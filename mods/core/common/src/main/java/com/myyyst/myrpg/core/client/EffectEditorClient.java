package com.myyyst.myrpg.core.client;

import com.myyyst.myrpg.core.Constants;
import com.myyyst.myrpg.core.client.editor.EffectLibraryScreen;
import com.myyyst.myrpg.core.client.editor.EffectWorkingSet;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.client.Minecraft;

/**
 * Client handler for the "open the effect editor" packet - the effect-side twin of
 * {@code StatEditorClient}.
 */
public final class EffectEditorClient {
    /** Called when {@code OpenEffectEditor} arrives; opens the editor UI. */
    public static void open(RpgPayloads.OpenEffectEditor payload) {
        Constants.LOG.info("[myrpg/editor] Received {} effect definitions", payload.effects().size());
        Minecraft.getInstance().gui.setScreen(
                new EffectLibraryScreen(EffectWorkingSet.from(payload))
        );
    }
    /** Static-only handler: never instantiated. */
    private EffectEditorClient() {}
}
