package com.myyyst.myrpg.core;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.condition.CoreConditions;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.data.CoreData;
import com.myyyst.myrpg.core.data.StatDef;
import com.myyyst.myrpg.core.effect.EffectActions;
import com.myyyst.myrpg.core.effect.EffectCommands;
import com.myyyst.myrpg.core.effect.EffectConditions;
import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.core.network.RpgPayloads;
import com.myyyst.myrpg.core.platform.Services;
import com.myyyst.myrpg.core.stat.PlayerStats;
import com.myyyst.myrpg.core.stat.StageEffect;
import com.myyyst.myrpg.core.stat.StatEngine;
import com.myyyst.myrpg.core.stat.StatStore;
import com.myyyst.myrpg.core.trigger.RpgTrigger;
import com.myyyst.myrpg.core.variable.VarValue;
import com.myyyst.myrpg.core.variable.Variables;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class MyRpgCommon {
    public static void init() {
        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        CoreConditions.bootstrap();
        RpgAction.bootstrap();
        StageEffect.bootstrap();
        EffectConditions.bootstrap();
        EffectActions.bootstrap();
        EffectCommands.init();
        RpgTrigger.bootstrap();
        RpgEvents.subscribe(StatEngine::onEvent);

        RpgCommands.contribute(root -> root.then(
                Commands.literal("debug")
                        .then(Commands.literal("on")
                                .executes(ctx -> {
                                    RpgDebug.set(true);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Debug mode enabled"), true);
                                    return 1;
                                }))
                        .then(Commands.literal("off")
                                .executes(ctx -> {
                                    RpgDebug.set(false);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Debug mode disabled"), true);
                                    return 1;
                                }))
                        .executes(ctx -> {   // bare /myrpg debug — query current state
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Debug mode is "
                                            + (RpgDebug.enabled() ? "ON" : "off")), false);
                            return RpgDebug.enabled() ? 1 : 0;
                        })));

        RpgCommands.contribute(root -> root.then(Commands.literal("stat")
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stat", IdentifierArgument.id())
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    Identifier stat = IdentifierArgument.getId(ctx, "stat");
                                                    double value = DoubleArgumentType.getDouble(ctx, "value");
                                                    PlayerStats.get(target).set(target, stat, value);
                                                    PlayerStats.markDirty(target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            target.getName().getString() + " " + stat + " = " + value), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stat", IdentifierArgument.id())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            Identifier stat = IdentifierArgument.getId(ctx, "stat");
                                            StatStore store = PlayerStats.get(target);
                                            double value = store.get(stat);
                                            String stage = store.currentStage(stat);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    stat + " = " + value + (stage != null ? " (stage: " + stage + ")" : "")), false);
                                            return (int) value;
                                        })))))

        );

        RpgCommands.contribute(root -> root.then(Commands.literal("var")
                .then(Commands.literal("set")
                        .then(Commands.argument("scope", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"player", "world"}, b))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String scope = StringArgumentType.getString(ctx, "scope");
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String raw = StringArgumentType.getString(ctx, "value");
                                                    VarValue value;
                                                    try {
                                                        value = VarValue.of(Double.parseDouble(raw));
                                                    } catch (NumberFormatException e) {
                                                        value = VarValue.of(raw);
                                                    }
                                                    ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
                                                    if ("player".equals(scope) && player == null) {
                                                        ctx.getSource().sendFailure(Component.literal("player scope needs a player"));
                                                        return 0;
                                                    }
                                                    Variables.set(ctx.getSource().getLevel(), scope, name, player, value);
                                                    final VarValue shown = value;
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            scope + ":" + name + " = " + shown.asString()), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("get")
                        .then(Commands.argument("scope", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"player", "world"}, b))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String scope = StringArgumentType.getString(ctx, "scope");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
                                            var value = Variables.get(ctx.getSource().getLevel(), scope, name, player);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    scope + ":" + name + " = " + value.map(VarValue::asString).orElse("(unset)")), false);
                                            return value.isPresent() ? 1 : 0;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("scope", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String scope = StringArgumentType.getString(ctx, "scope");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
                                            Variables.remove(ctx.getSource().getLevel(), scope, name, player);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "removed " + scope + ":" + name), true);
                                            return 1;
                                        }))))
        ));

        RpgCommands.contribute(root -> root.then(Commands.literal("editor")
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Players only"));
                                return 0;
                            }
                            List<RpgPayloads.StatFile> files = new ArrayList<>();
                            for (var entry : CoreData.STATS.all().entrySet()) {
                                StatDef.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
                                        .result()
                                        .ifPresent(json -> files.add(new RpgPayloads.StatFile(
                                                entry.getKey().toString(), json.toString())));
                            }
                            Services.NETWORK.sendToPlayer(player, new RpgPayloads.OpenStatEditor(files));
                            return files.size();
                        }))));
    }
}