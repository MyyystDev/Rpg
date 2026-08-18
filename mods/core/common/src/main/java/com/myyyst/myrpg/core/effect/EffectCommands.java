package com.myyyst.myrpg.core.effect;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.EffectDefinition;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * /myrpg effect apply <player> <effect> [duration] [level] [stacks]
 * /myrpg effect remove <player> <effect>
 * /myrpg effect clear <player>
 * /myrpg effect list <player>
 *
 * <p>Operator tooling for testing effects without writing a datapack rule. The whole tree
 * is built with Brigadier: each {@code .then(...)} adds a branch and each {@code .executes}
 * marks a point where the command is complete, which is how the optional trailing
 * duration/level/stacks arguments are expressed.</p>
 */
public final class EffectCommands {

    /** Called once from MyRpgCommon.init(). */
    public static void init() {
        RpgCommands.contribute(root -> root.then(Commands.literal("effect")
                .then(Commands.literal("apply")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("effect", IdentifierArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                CoreData.EFFECTS.all().keySet(), b))
                                        .executes(ctx -> apply(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IdentifierArgument.getId(ctx, "effect"), -1, 1, 1))
                                        .then(Commands.argument("duration", IntegerArgumentType.integer(-1))
                                                .executes(ctx -> apply(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IdentifierArgument.getId(ctx, "effect"),
                                                        IntegerArgumentType.getInteger(ctx, "duration"), 1, 1))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> apply(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                IdentifierArgument.getId(ctx, "effect"),
                                                                IntegerArgumentType.getInteger(ctx, "duration"),
                                                                IntegerArgumentType.getInteger(ctx, "level"), 1))
                                                        .then(Commands.argument("stacks", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> apply(ctx.getSource(),
                                                                        EntityArgument.getPlayer(ctx, "player"),
                                                                        IdentifierArgument.getId(ctx, "effect"),
                                                                        IntegerArgumentType.getInteger(ctx, "duration"),
                                                                        IntegerArgumentType.getInteger(ctx, "level"),
                                                                        IntegerArgumentType.getInteger(ctx, "stacks")))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("effect", IdentifierArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                CoreData.EFFECTS.all().keySet(), b))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            Identifier id = IdentifierArgument.getId(ctx, "effect");
                                            boolean removed = EffectManager.remove(target, id, false);
                                            if (removed) {
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "Removed " + id + " from "
                                                                + target.getName().getString()), true);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        target.getName().getString()
                                                                + " does not have " + id));
                                            }
                                            return removed ? 1 : 0;
                                        }))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    int removed = EffectManager.removeWhere(target, def -> true);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Cleared " + removed + " effect(s) from "
                                                    + target.getName().getString()), true);
                                    return removed;
                                })))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    EffectStore store = PlayerEffects.get(target);
                                    if (store.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                target.getName().getString()
                                                        + " has no custom effects"), false);
                                        return 0;
                                    }
                                    for (EffectInstance instance : store.all()) {
                                        String line = instance.effectId
                                                + "  level " + instance.level
                                                + "  x" + instance.stacks
                                                + (instance.remaining < 0
                                                        ? "  (infinite)"
                                                        : "  (" + instance.remaining + "t left)");
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(line), false);
                                    }
                                    return store.all().size();
                                })))));
    }

    /**
     * Shared handler for all four arities of "effect apply".
     *
     * @param duration -1 to use the definition's default
     * @return 1 on success, 0 when the effect is unknown or the target refused it
     */
    private static int apply(net.minecraft.commands.CommandSourceStack source, ServerPlayer target,
                             Identifier effectId, int duration, int level, int stacks) {
        EffectDefinition def = CoreData.EFFECTS.get(effectId).orElse(null);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown effect " + effectId));
            return 0;
        }
        boolean changed = EffectManager.apply(target, effectId, duration, level, stacks, null);
        if (changed) {
            source.sendSuccess(() -> Component.literal(
                    "Applied " + effectId + " to " + target.getName().getString()), true);
        } else {
            source.sendFailure(Component.literal(
                    "Could not apply " + effectId + " (immune?)"));
        }
        return changed ? 1 : 0;
    }

    /** Static-only command registrar: never instantiated. */
    private EffectCommands() {}
}
