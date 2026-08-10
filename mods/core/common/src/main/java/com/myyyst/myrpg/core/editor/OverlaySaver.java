package com.myyyst.myrpg.core.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.myyyst.myrpg.core.Constants;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes editor-produced JSON into the world's overlay datapack
 * (saves/<world>/datapacks/myrpg_editor/). Datapack stacking makes the
 * overlay override originals; deleting the overlay reverts everything.
 *
 * Every save: permission-gated, size-capped, validated through the SAME
 * codec the loader uses, re-encoded clean, traversal-guarded, then /reload.
 */
public final class OverlaySaver {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_JSON_LENGTH = 256 * 1024;
    private static final String PACK_NAME = "myrpg_editor";

    /**
     * @param folder datapack folder under data/<ns>/, e.g. "myrpg/stats"
     * @return true on success (feedback already sent to the player either way)
     */
    public static <T> boolean save(ServerPlayer player, String folder,
                                   Identifier id, String json, Codec<T> codec) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            // NOTE drift: permission idiom — mirror RpgCommands' compiled spelling.
            fail(player, "No permission to save");
            return false;
        }
        if (json.length() > MAX_JSON_LENGTH) {
            fail(player, "Content too large");
            return false;
        }

        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (Exception e) {
            fail(player, "Malformed JSON");
            return false;
        }
        var result = codec.parse(JsonOps.INSTANCE, element);
        if (result.result().isEmpty()) {
            fail(player, "Invalid: " + result.error().map(Object::toString).orElse("unknown"));
            return false;
        }
        JsonElement clean = codec.encodeStart(JsonOps.INSTANCE, result.result().get())
                .result().orElse(element);

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(PACK_NAME).toAbsolutePath().normalize();
        Path file = packRoot
                .resolve("data").resolve(id.getNamespace())
                .resolve(folder).resolve(id.getPath() + ".json")
                .toAbsolutePath().normalize();
        if (!file.startsWith(packRoot)) {              // both sides normalized — the NeoForge lesson
            fail(player, "Invalid path");
            return false;
        }

        try {
            ensurePackMcmeta(packRoot);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(clean));
        } catch (IOException e) {
            Constants.LOG.error("[myrpg] Overlay write failed for {}", id, e);
            fail(player, "Write failed (see log)");
            return false;
        }

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "reload");
        player.sendSystemMessage(Component.literal("Saved " + id + " — reloaded."));
        Constants.LOG.info("[myrpg] {} saved {} via editor", player.getName().getString(), id);
        return true;
    }

    /** Deletes an overlay file (only overlay content can be deleted). */
    public static boolean delete(ServerPlayer player, String folder, Identifier id) {
        if (!Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            fail(player, "No permission");
            return false;
        }
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(PACK_NAME).toAbsolutePath().normalize();
        Path file = packRoot.resolve("data").resolve(id.getNamespace())
                .resolve(folder).resolve(id.getPath() + ".json")
                .toAbsolutePath().normalize();
        if (!file.startsWith(packRoot) || !Files.exists(file)) {
            fail(player, "Not an editor-created file (originals can't be deleted)");
            return false;
        }
        try {
            Files.delete(file);
        } catch (IOException e) {
            fail(player, "Delete failed");
            return false;
        }
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "reload");
        player.sendSystemMessage(Component.literal("Deleted overlay " + id + " — reloaded."));
        return true;
    }

    private static void ensurePackMcmeta(Path packRoot) throws IOException {
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) return;
        Files.createDirectories(packRoot);
        Files.writeString(mcmeta, """
                {
                  "pack": {
                    "description": "Myyyst RPG editor output",
                    "pack_format": 81,
                    "supported_formats": [81, 81]
                  }
                }
                """);
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[editor] " + message));
    }
}