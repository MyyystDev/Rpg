package com.myyyst.myrpg.core.editor;

import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.core.data.EffectDefinition;
import com.myyyst.myrpg.core.network.RpgPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side handlers for the in-game editor packets.
 *
 * <p>Each handler does the same three things: parse the id the client sent (never trust it),
 * then hand the raw JSON to {@link OverlaySaver}, which does permission checks, validation
 * and the actual write. Registered by the loader modules when they set up networking.</p>
 */
public final class EditorNet {

    /** Datapack folder stat files are written to. Must match {@code CoreData.STATS}. */
    public static final String STATS_FOLDER = "myrpg/stats";
    /** Datapack folder effect files are written to. Must match {@code CoreData.EFFECTS}. */
    public static final String EFFECTS_FOLDER = "myrpg/effects";

    /** C2S save of a stat definition. Rejects an unparseable id before touching disk. */
    public static void handleSave(ServerPlayer player, RpgPayloads.SaveStat payload) {
        Identifier statId = Identifier.tryParse(payload.statId());
        if (statId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid stat id"));
            return;
        }
        OverlaySaver.save(player, STATS_FOLDER, statId, payload.json(), StatDef.CODEC);
    }

    /** C2S delete of an editor-created stat file. */
    public static void handleDelete(ServerPlayer player, RpgPayloads.DeleteStat payload) {
        Identifier statId = Identifier.tryParse(payload.statId());
        if (statId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid stat id"));
            return;
        }
        OverlaySaver.delete(player, STATS_FOLDER, statId);
    }

    /** C2S save of an effect definition. */
    public static void handleSaveEffect(ServerPlayer player, RpgPayloads.SaveEffect payload) {
        Identifier effectId = Identifier.tryParse(payload.effectId());
        if (effectId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid effect id"));
            return;
        }
        OverlaySaver.save(player, EFFECTS_FOLDER, effectId, payload.json(), EffectDefinition.CODEC);
    }

    /** C2S delete of an editor-created effect file. */
    public static void handleDeleteEffect(ServerPlayer player, RpgPayloads.DeleteEffect payload) {
        Identifier effectId = Identifier.tryParse(payload.effectId());
        if (effectId == null) {
            player.sendSystemMessage(Component.literal("[editor] Invalid effect id"));
            return;
        }
        OverlaySaver.delete(player, EFFECTS_FOLDER, effectId);
    }

    /** Static-only handler class: never instantiated. */
    private EditorNet() {}
}