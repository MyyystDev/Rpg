package com.myyyst.myrpg.core;

import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.core.condition.CoreConditions;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.platform.Services;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MyRpgCommon {
    public static void init() {
        Constants.LOG.info("Hello from Common init on {}! we are currently in a {} environment!", Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        CoreConditions.bootstrap();
        RpgAction.bootstrap();

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
    }
}