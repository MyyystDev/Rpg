package com.myyyst.myrpg.entities.event;

import com.myyyst.myrpg.core.event.RpgEvents;
import com.myyyst.myrpg.entities.MyrpgEntities;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.rule.EntityRuleEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Entity-side event vocabulary. fire() does two things:
 *
 *  1. Posts to core's shared RpgEvents bus — so PLAYER stat rules can react
 *     (StatEngine.onEvent resolves the owner to the player when one is
 *     attached, otherwise to the entity itself).
 *  2. Runs the ENTITY's own definition rules with self = the entity — this
 *     is what makes "when hurt → raise rage" style rules work.
 *
 * Event ids double as trigger vocabulary in JSON:
 *   { "trigger": { "type": "myrpg_core:event", "event": "myrpg_entities:entity_hurt" } }
 */
public final class EntityEvents {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, path);
    }

    public static final Identifier SPAWN = id("entity_spawn");
    public static final Identifier INTERACT = id("entity_interact");
    public static final Identifier HURT = id("entity_hurt");
    public static final Identifier DEATH = id("entity_death");

    public static void fire(RpgEntity entity, Identifier eventId, @Nullable ServerPlayer player) {
        RpgEvents.post(new RpgEvents.GameEvent(eventId, player, entity));
        EntityRuleEngine.onEvent(entity, eventId, player);
    }

    private EntityEvents() {}
}
