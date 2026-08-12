package com.myyyst.myrpg.entities.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.myyyst.myrpg.core.command.RpgCommands;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * /myrpg entity spawn|list|inspect — contributed to core's command root.
 */
public final class EntityCommands {

    public static void init() {
        RpgCommands.contribute(root -> root.then(Commands.literal("entity")

                .then(Commands.literal("spawn")
                        .then(Commands.argument("definition", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        EntitiesData.ENTITIES.all().keySet(), builder))
                                .executes(ctx -> spawn(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> spawn(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))

                .then(Commands.literal("list").executes(EntityCommands::list))

                .then(Commands.literal("inspect").executes(EntityCommands::inspect))

                .then(Commands.literal("sethome").executes(EntityCommands::setHome))));
    }

    // ------------------------------------------------------------ spawn

    private static int spawn(CommandContext<CommandSourceStack> ctx, int count) {
        Identifier defId = IdentifierArgument.getId(ctx, "definition");
        if (EntitiesData.ENTITIES.get(defId).isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown entity definition: " + defId));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 pos = ctx.getSource().getPosition();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // NOTE drift: EntitySpawnReason was MobSpawnType in older mappings.
            RpgEntity entity = RpgEntityTypes.rpg_entity().create(level, EntitySpawnReason.COMMAND);
            if (entity == null) break;
            entity.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
            entity.applyDefinition(defId);
            level.addFreshEntity(entity);
            spawned++;
        }

        final int result = spawned;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Spawned " + result + "x " + defId), true);
        return result;
    }

    // ------------------------------------------------------------ list

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var ids = EntitiesData.ENTITIES.all().keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        if (ids.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No entity definitions loaded (data/<ns>/myrpg/entities/*.json)"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                ids.size() + " entity definition(s):"), false);
        for (Identifier id : ids) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
        }
        return ids.size();
    }

    // ------------------------------------------------------------ inspect

    private static int inspect(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 pos = ctx.getSource().getPosition();

        List<RpgEntity> nearby = level.getEntitiesOfClass(
                RpgEntity.class, AABB.ofSize(pos, 16, 16, 16));
        RpgEntity nearest = nearby.stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(pos)))
                .orElse(null);
        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No custom entity within 8 blocks"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "definition: " + nearest.definitionIdString()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "health: " + nearest.getHealth() + " / " + nearest.getMaxHealth()), false);
        var stats = nearest.rpgStats().all();
        if (stats.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("stats: (none)"), false);
        } else {
            for (var e : stats.entrySet()) {
                String stage = nearest.rpgStats().currentStage(e.getKey());
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "stat " + e.getKey() + " = " + e.getValue()
                                + (stage != null ? " (stage: " + stage + ")" : "")), false);
            }
        }
        return 1;
    }

    // ------------------------------------------------------------ sethome

    /** Sets the nearest custom entity's guard/home anchor to where you stand. */
    private static int setHome(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 pos = ctx.getSource().getPosition();
        RpgEntity nearest = level.getEntitiesOfClass(
                        RpgEntity.class, AABB.ofSize(pos, 16, 16, 16)).stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(pos)))
                .orElse(null);
        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal("No custom entity within 8 blocks"));
            return 0;
        }
        BlockPos home = BlockPos.containing(pos);
        nearest.setGuardAnchor(home);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Home of " + nearest.definitionIdString() + " set to "
                        + home.getX() + " " + home.getY() + " " + home.getZ()), true);
        return 1;
    }

    private EntityCommands() {}
}
