package com.myyyst.myrpg.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Assembles the single {@code /myrpg} command tree out of contributions from every module.
 *
 * <p>Each feature adds its own subcommand during init via {@link #contribute}; the loader
 * then calls {@link #register} once, when the server builds its command dispatcher. This
 * keeps command code next to the feature it belongs to instead of in one giant class.</p>
 */
public final class RpgCommands {
    /** Subcommand builders, applied in registration order. */
    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> CONTRIBUTORS = new ArrayList<>();

    /** Mods call this from init to add subcommands under /myrpg. */
    public static void contribute(Consumer<LiteralArgumentBuilder<CommandSourceStack>> contributor) {
        CONTRIBUTORS.add(contributor);
    }

    /**
     * Builds and registers {@code /myrpg}. Called from each loader's command-registration hook.
     * The whole tree requires gamemaster permission (level 2), so players cannot edit content.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("myrpg")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)::test);
        CONTRIBUTORS.forEach(c -> c.accept(root));
        dispatcher.register(root);
    }
}