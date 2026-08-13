package com.myyyst.myrpg.entities.item;

import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.editor.EntityEditorNet;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import com.myyyst.myrpg.entities.registry.RpgEntityTypes;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The creator wand (plan: "In-World Entity Tool"):
 *   right-click block            → place the selected definition there, facing you
 *   right-click a custom entity  → open its definition in the editor
 *   sneak + right-click          → cycle the selected definition
 * All actions require gamemaster permission, like /myrpg.
 */
public class EntityWandItem extends Item {

    /** Server-side per-player wand selection. Cleared on server restart — fine. */
    private static final Map<UUID, Identifier> SELECTION = new HashMap<>();

    public EntityWandItem(Properties properties) {
        super(properties);
    }

    private static boolean allowed(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                .test(player.createCommandSourceStack());
    }

    private static void cycleSelection(ServerPlayer player) {
        var ids = EntitiesData.ENTITIES.all().keySet().stream()
                .sorted((a, b) -> a.toString().compareTo(b.toString())).toList();
        if (ids.isEmpty()) {
            player.sendSystemMessage(Component.literal("[wand] No entity definitions loaded"));
            return;
        }
        Identifier current = SELECTION.get(player.getUUID());
        int next = (ids.indexOf(current) + 1) % ids.size();
        SELECTION.put(player.getUUID(), ids.get(next));
        player.sendSystemMessage(Component.literal(
                "[wand] Selected " + ids.get(next) + "  (" + (next + 1) + "/" + ids.size() + ")"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !allowed(sp)) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            cycleSelection(sp);
        } else {
            Identifier selected = SELECTION.get(player.getUUID());
            sp.sendSystemMessage(Component.literal(selected == null
                    ? "[wand] Sneak-right-click to choose a definition, then click a block to place"
                    : "[wand] " + selected + " — click a block to place, sneak-click to change"));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !allowed(sp)) return InteractionResult.PASS;

        if (player.isSecondaryUseActive()) {
            cycleSelection(sp);
            return InteractionResult.SUCCESS_SERVER;
        }

        Identifier defId = SELECTION.get(player.getUUID());
        if (defId == null || EntitiesData.ENTITIES.get(defId).isEmpty()) {
            sp.sendSystemMessage(Component.literal(
                    "[wand] No definition selected — sneak-right-click to choose"));
            return InteractionResult.SUCCESS_SERVER;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        RpgEntity entity = RpgEntityTypes.rpg_entity().create(serverLevel, EntitySpawnReason.COMMAND);
        if (entity == null) return InteractionResult.PASS;

        Vec3 pos = context.getClickLocation();
        entity.snapTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0f, 0.0f);
        entity.applyDefinition(defId);
        serverLevel.addFreshEntity(entity);
        sp.sendSystemMessage(Component.literal("[wand] Placed " + defId));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof RpgEntity rpg)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !allowed(sp)) return InteractionResult.PASS;
        Identifier defId = rpg.definitionId();
        if (defId == null) {
            sp.sendSystemMessage(Component.literal("[wand] Entity has no definition"));
            return InteractionResult.SUCCESS_SERVER;
        }
        EntityEditorNet.sendEditor(sp, defId);
        return InteractionResult.SUCCESS_SERVER;
    }
}
