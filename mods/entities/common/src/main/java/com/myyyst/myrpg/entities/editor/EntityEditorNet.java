package com.myyyst.myrpg.entities.editor;

import com.mojang.serialization.JsonOps;
import com.myyyst.myrpg.core.editor.OverlaySaver;
import com.myyyst.myrpg.core.platform.Services;
import com.myyyst.myrpg.entities.Constants;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.data.EntityDefinition;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.network.EntitiesPayloads;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the entity browser + editor.
 *
 * <p>Mirrors core's {@code EditorNet}, reusing its {@code OverlaySaver} for the actual
 * permission checks, validation and writes, and adds one thing core has no equivalent of:
 * spawning a definition straight from the browser.</p>
 */
public final class EntityEditorNet {

    /** Datapack folder entity files are written to. Must match {@code EntitiesData.ENTITIES}. */
    public static final String ENTITIES_FOLDER = "myrpg/entities";

    /** C2S: validate through EntityDefinition.CODEC and write to the overlay pack. */
    public static void handleSave(ServerPlayer player, EntitiesPayloads.SaveEntity payload) {
        Identifier entityId = Identifier.tryParse(payload.entityId());
        if (entityId == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[editor] Invalid entity id"));
            return;
        }
        OverlaySaver.save(player, ENTITIES_FOLDER, entityId, payload.json(), EntityDefinition.CODEC);
    }

    /** C2S: delete an editor-created entity file. */
    public static void handleDelete(ServerPlayer player, EntitiesPayloads.DeleteEntity payload) {
        Identifier entityId = Identifier.tryParse(payload.entityId());
        if (entityId == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[editor] Invalid entity id"));
            return;
        }
        OverlaySaver.delete(player, ENTITIES_FOLDER, entityId);
    }

    /**
     * Re-encodes every loaded definition back to JSON for the client editor.
     * Entries that fail to encode are simply omitted rather than breaking the whole list.
     */
    private static List<EntitiesPayloads.EntityFile> collectFiles() {
        List<EntitiesPayloads.EntityFile> files = new ArrayList<>();
        for (var entry : EntitiesData.ENTITIES.all().entrySet()) {
            EntityDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
                    .result()
                    .ifPresent(json -> files.add(new EntitiesPayloads.EntityFile(
                            entry.getKey().toString(), json.toString())));
        }
        return files;
    }

    /** Collect every loaded definition as JSON and open the browser client-side. */
    public static void sendBrowser(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new EntitiesPayloads.OpenEntityBrowser(collectFiles()));
    }

    /** Open the editor directly on one definition (creator wand). */
    public static void sendEditor(ServerPlayer player, Identifier focus) {
        Services.NETWORK.sendToPlayer(player,
                new EntitiesPayloads.OpenEntityEditor(collectFiles(), focus.toString()));
    }

    /** C2S spawn request — same permission bar as /myrpg. */
    public static void handleSpawn(ServerPlayer player, EntitiesPayloads.SpawnEntity payload) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                .test(player.createCommandSourceStack())) {
            Constants.LOG.warn("[myrpg] {} sent spawn_entity without permission",
                    player.getName().getString());
            return;
        }
        Identifier defId = Identifier.tryParse(payload.entityId());
        if (defId == null || EntitiesData.ENTITIES.get(defId).isEmpty()) {
            Constants.LOG.warn("[myrpg] spawn_entity for unknown definition {}", payload.entityId());
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;
        RpgEntity entity = RpgEntityTypes.rpg_entity().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return;
        // Spawn on top of the requesting player, facing a random direction.
        entity.snapTo(player.getX(), player.getY(), player.getZ(),
                level.getRandom().nextFloat() * 360.0f, 0.0f);
        entity.applyDefinition(defId);   // full fresh-spawn setup
        level.addFreshEntity(entity);
    }

    /** Static-only handler class: never instantiated. */
    private EntityEditorNet() {}
}
