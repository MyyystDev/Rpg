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

    /** Pretty printing keeps the written files hand-editable afterwards. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Hard cap on client-submitted JSON (256 KB) so a bad client cannot fill the disk. */
    private static final int MAX_JSON_LENGTH = 256 * 1024;
    /** Folder name of the generated datapack inside the world's datapacks directory. */
    private static final String PACK_NAME = "myrpg_editor";

    /**
     * Validates and writes one definition file, then reloads datapacks.
     *
     * <p>The checks run in a deliberate order - permission, size, syntax, schema, path -
     * so that the cheapest rejections happen first and nothing touches the filesystem
     * until the content is known to be valid.</p>
     *
     * @param folder datapack folder under data/<ns>/, e.g. "myrpg/stats"
     * @param codec  the same codec the loader uses, so anything saved is guaranteed loadable
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
        // Re-encode from the parsed object: drops unknown fields and normalises formatting,
        // so what lands on disk is exactly what the game will read back.
        JsonElement clean = codec.encodeStart(JsonOps.INSTANCE, result.result().get())
                .result().orElse(element);

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(PACK_NAME).toAbsolutePath().normalize();
        Path file = packRoot
                .resolve("data").resolve(id.getNamespace())
                .resolve(folder).resolve(id.getPath() + ".json")
                .toAbsolutePath().normalize();
        // Path-traversal guard: an id like "../../evil" would otherwise escape the pack.
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

        // Reload datapacks so the change is live immediately, without a server restart.
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
        // Existence inside the overlay is what makes a file deletable: files shipped by
        // the mod or by another datapack live elsewhere and can never be reached here.
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

    /**
     * Creates the overlay pack's pack.mcmeta on first save - without it Minecraft would
     * not recognise the folder as a datapack at all. Existing files are left alone.
     */
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

    /** Sends a short "[editor] ..." message; every rejection path goes through here. */
    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[editor] " + message));
    }
}