package com.myyyst.myrpg.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RpgCommands {
    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> CONTRIBUTORS = new ArrayList<>();

    /** Mods call this from init to add subcommands under /myrpg. */
    public static void contribute(Consumer<LiteralArgumentBuilder<CommandSourceStack>> contributor) {
        CONTRIBUTORS.add(contributor);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("myrpg")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)::test);
        CONTRIBUTORS.forEach(c -> c.accept(root));
        dispatcher.register(root);
    }
}