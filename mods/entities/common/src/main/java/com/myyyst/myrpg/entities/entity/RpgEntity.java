package com.myyyst.myrpg.entities.entity;

import com.myyyst.myrpg.core.action.RpgAction;
import com.myyyst.myrpg.core.condition.RpgCondition;
import com.myyyst.myrpg.core.stat.StatEngine;
import com.myyyst.myrpg.core.stat.StatHolder;
import com.myyyst.myrpg.core.stat.StatStore;
import com.myyyst.myrpg.entities.Constants;
import com.myyyst.myrpg.entities.ai.AiGoalDef;
import com.myyyst.myrpg.entities.ai.TargetDef;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.data.EntityDefinition;
import com.myyyst.myrpg.entities.event.EntityEvents;
import com.myyyst.myrpg.entities.rule.EntityRuleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** The one generic base entity. Everything else is data. */
public class RpgEntity extends PathfinderMob implements StatHolder, RangedAttackMob {

    private final StatStore stats = new StatStore();
    @Nullable private Identifier definitionId;
    @Nullable private BlockPos guardAnchor;
    private boolean allowDespawn = false;

    private static final EntityDataAccessor<String> DATA_DEFINITION =
            SynchedEntityData.defineId(RpgEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODEL =
            SynchedEntityData.defineId(RpgEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TEXTURE =
            SynchedEntityData.defineId(RpgEntity.class, EntityDataSerializers.STRING);

    public RpgEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEFINITION, "");
        builder.define(DATA_MODEL, "");
        builder.define(DATA_TEXTURE, "");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0);
    }

    // ------------------------------------------------------------ definition

    /** Definition id as synced string — readable client-side (rendering uses this). */
    public String definitionIdString() {
        return entityData.get(DATA_DEFINITION);
    }

    /** Model id from the definition's appearance — client render picks by this. */
    public String modelIdString() {
        return entityData.get(DATA_MODEL);
    }

    /** Explicit texture from the definition, or "" for the model's default. */
    public String textureString() {
        return entityData.get(DATA_TEXTURE);
    }

    /**
     * Editor-preview support: pushes appearance straight into the synced
     * fields of a client-side, never-added entity so the GUI renderer shows
     * live edits. Touches nothing the server cares about.
     */
    public void applyPreview(String modelId, String texture, double scale) {
        entityData.set(DATA_MODEL, modelId);
        entityData.set(DATA_TEXTURE, texture);
        AttributeInstance scaleAttr = getAttribute(Attributes.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(scale);
    }

    @Nullable
    public Identifier definitionId() { return definitionId; }

    public Optional<EntityDefinition> definition() {
        return definitionId == null ? Optional.empty()
                : EntitiesData.ENTITIES.get(definitionId);
    }

    /**
     * Full application for a FRESH spawn: attributes, stat seeds, equipment,
     * display, then the runtime side (AI, movement, persistence), then heal
     * to full. Loading a saved entity must NOT come through here — it uses
     * {@link #applyRuntime(EntityDefinition)} so saved health/equipment/stats
     * survive.
     */
    public void applyDefinition(Identifier defId) {
        EntityDefinition def = EntitiesData.ENTITIES.get(defId).orElse(null);
        if (def == null) {
            Constants.LOG.warn("[myrpg] Unknown entity definition {}", defId);
            return;
        }
        this.definitionId = defId;
        entityData.set(DATA_DEFINITION, defId.toString());

        // attributes
        for (var attrEntry : def.attributes().entrySet()) {
            Identifier attrId = Identifier.tryParse(attrEntry.getKey());
            if (attrId == null) continue;
            Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(attrId).orElse(null);
            if (attr == null) {
                Constants.LOG.warn("[myrpg] {} references unknown attribute {}", defId, attrId);
                continue;
            }
            AttributeInstance instance = getAttribute(attr);
            if (instance != null) instance.setBaseValue(attrEntry.getValue());
        }

        // stat seeds
        for (var statEntry : def.stats().entrySet()) {
            stats.set(this, statEntry.getKey(), statEntry.getValue());
        }

        // equipment
        def.equipment().ifPresent(this::applyEquipment);

        // display
        def.display().flatMap(EntityDefinition.Display::name)
                .ifPresent(n -> setCustomName(Component.literal(n)));
        def.display().ifPresent(d -> setCustomNameVisible(d.nameVisible()));

        // guard goals anchor to the spawn position by default
        this.guardAnchor = blockPosition();

        applyRuntime(def);
        setHealth(getMaxHealth());

        EntityEvents.fire(this, EntityEvents.SPAWN, null);
    }

    /**
     * The non-destructive, rebuild-every-time side: AI goals, targeting,
     * navigation flags, scale, despawn policy. Safe on both fresh spawn and
     * load, because it writes no health/stats/equipment.
     */
    private void applyRuntime(EntityDefinition def) {
        // appearance sync — the client renderer reads these
        entityData.set(DATA_MODEL, def.appearance()
                .map(EntityDefinition.Appearance::model).orElse("myrpg_entities:humanoid"));
        entityData.set(DATA_TEXTURE, def.appearance()
                .flatMap(EntityDefinition.Appearance::texture)
                .map(Identifier::toString).orElse(""));

        // scale (also scales the hitbox via the vanilla scale attribute)
        def.appearance().ifPresent(a -> {
            AttributeInstance scale = getAttribute(Attributes.SCALE);
            if (scale != null && a.scale() != 1.0) scale.setBaseValue(a.scale());
        });

        boolean canSwim = def.movement().map(EntityDefinition.Movement::canSwim).orElse(true);
        boolean canOpenDoors = def.movement().map(EntityDefinition.Movement::canOpenDoors).orElse(false);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(canOpenDoors);
        }

        this.allowDespawn = def.persistence().map(EntityDefinition.Persistence::despawn).orElse(false);

        rebuildAi(def, canSwim);
    }

    private void rebuildAi(EntityDefinition def, boolean canSwim) {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);

        if (canSwim) goalSelector.addGoal(0, new FloatGoal(this));

        boolean hasMeleeGoal = false;
        boolean hasRangedGoal = false;
        for (AiGoalDef goalDef : def.ai()) {
            Goal goal = goalDef.build(this);
            if (goal == null) continue;
            if (goal instanceof MeleeAttackGoal) hasMeleeGoal = true;
            if (goal instanceof RangedAttackGoal) hasRangedGoal = true;
            goalSelector.addGoal(goalDef.priority(), goal);
        }

        // combat: the configured style implies an attack goal even if the
        // ai list omits it
        String combatType = def.combat().map(EntityDefinition.Combat::type).orElse("none");
        if (!hasMeleeGoal && "melee".equals(combatType)) {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        }
        if (!hasRangedGoal && "ranged".equals(combatType)) {
            EntityDefinition.Combat combat = def.combat().orElseThrow();
            // the record's range default (2.0) is a melee reach — useless for
            // ranged, so fall back to a sensible bow range
            float range = (float) (combat.range() > 2.0 ? combat.range() : 15.0);
            goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0,
                    Math.max(1, combat.cooldown()), range));
        }

        for (TargetDef targetDef : def.targeting()) {
            Goal goal = targetDef.build(this);
            if (goal != null) targetSelector.addGoal(targetDef.priority(), goal);
        }
    }

    private void applyEquipment(EntityDefinition.Equipment eq) {
        equip(EquipmentSlot.MAINHAND, eq.mainhand());
        equip(EquipmentSlot.OFFHAND, eq.offhand());
        equip(EquipmentSlot.HEAD, eq.head());
        equip(EquipmentSlot.CHEST, eq.chest());
        equip(EquipmentSlot.LEGS, eq.legs());
        equip(EquipmentSlot.FEET, eq.feet());
    }

    private void equip(EquipmentSlot slot, Optional<String> itemId) {
        if (itemId.isEmpty()) return;
        Identifier id = Identifier.tryParse(itemId.get());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            Constants.LOG.warn("[myrpg] {} references unknown item {}", definitionId, itemId.get());
            return;
        }
        setItemSlot(slot, new ItemStack(item));
        setDropChance(slot, 0.0f);   // definition gear never drops; loot tables handle drops
    }

    // ------------------------------------------------------------ stats

    @Override
    public StatStore rpgStats() { return stats; }

    @Nullable
    public BlockPos guardAnchor() { return guardAnchor; }

    public void setGuardAnchor(@Nullable BlockPos pos) { this.guardAnchor = pos; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        // deliberate perf asymmetry: entities with no touched stats skip the engine
        if (!stats.isEmpty()) {
            StatEngine.tick(stats, this);
        }
        definition().ifPresent(def -> EntityRuleEngine.tick(this, def));
    }

    // ------------------------------------------------------------ ranged combat

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack projectileStack = rangedProjectile();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectileStack, power, getMainHandItem());
        double xd = target.getX() - getX();
        double yd = target.getY(0.3333333333333333) - arrow.getY();
        double zd = target.getZ() - getZ();
        double horizontal = Math.sqrt(xd * xd + zd * zd);
        double speed = definition().flatMap(EntityDefinition::combat)
                .map(EntityDefinition.Combat::projectileSpeed).orElse(1.6);
        if (level() instanceof ServerLevel serverLevel) {
            Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectileStack,
                    xd, yd + horizontal * 0.2, zd, (float) speed, 2.0f);
        }
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f,
                1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
    }

    /** Arrow-family item from combat.projectile, defaulting to a plain arrow. */
    private ItemStack rangedProjectile() {
        Identifier id = definition().flatMap(EntityDefinition::combat)
                .flatMap(EntityDefinition.Combat::projectile)
                .map(Identifier::tryParse).orElse(null);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) return new ItemStack(Items.ARROW);
        return new ItemStack(item);
    }

    // ------------------------------------------------------------ interactions

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        EntityDefinition def = definition().orElse(null);
        if (def == null || def.interactions().isEmpty()) return super.mobInteract(player, hand);
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        EntityEvents.fire(this, EntityEvents.INTERACT, serverPlayer);

        // first interaction whose conditions all pass wins
        for (EntityDefinition.Interaction interaction : def.interactions()) {
            if (!EntityRuleEngine.allPass(interaction.conditions(),
                    new RpgCondition.ConditionContext(this, serverPlayer, serverPlayer))) {
                continue;
            }
            EntityRuleEngine.run(interaction.actions(),
                    new RpgAction.ActionContext(this, serverPlayer));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------ events

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) {
            ServerPlayer attacker = source.getEntity() instanceof ServerPlayer p ? p : null;
            EntityEvents.fire(this, EntityEvents.HURT, attacker);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        boolean firstDeath = !this.dead;
        super.die(source);
        if (firstDeath && this.dead && !level().isClientSide()) {
            ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
            EntityEvents.fire(this, EntityEvents.DEATH, killer);
        }
    }

    // ------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (definitionId != null) {
            output.store("myrpg_definition", Identifier.CODEC, definitionId);
        }
        if (guardAnchor != null) {
            output.store("myrpg_guard_pos", BlockPos.CODEC, guardAnchor);
        }
        stats.save(output, "myrpg_stats");
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("myrpg_definition", Identifier.CODEC).ifPresent(id -> {
            this.definitionId = id;
            entityData.set(DATA_DEFINITION, id.toString());
        });
        input.read("myrpg_guard_pos", BlockPos.CODEC).ifPresent(pos -> this.guardAnchor = pos);
        stats.load(input, "myrpg_stats");   // saved values override definition seeds
        stats.reapplyStages(this);          // effects re-applied, no enter/exit events
        // goals/targets are never serialized — rebuild them from the definition
        definition().ifPresent(this::applyRuntime);
        // NO setHealth — a loaded entity keeps its wounds.
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public boolean removeWhenFarAway(double distance) {
        return allowDespawn;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        // NOTE drift: if this override doesn't match, the vanilla method is
        // getExperienceReward/getBaseExperienceReward — align with your mappings.
        return definition().flatMap(EntityDefinition::loot)
                .map(EntityDefinition.Loot::xp).orElse(0);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        // Mob#getLootTable is final in these mappings, so the definition's
        // custom loot table is rolled here instead.
        Identifier tableId = definition().flatMap(EntityDefinition::loot)
                .flatMap(EntityDefinition.Loot::lootTable).orElse(null);
        if (tableId == null) return;

        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));
        if (table == LootTable.EMPTY) {
            Constants.LOG.warn("[myrpg] {} references missing loot table {}", definitionId, tableId);
            return;
        }
        // NOTE drift: ATTACKING_ENTITY was KILLER_ENTITY in older mappings;
        // spawnAtLocation(level, stack) took only the stack before 1.21.4-era.
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                .create(LootContextParamSets.ENTITY);
        table.getRandomItems(params, getLootTableSeed(), stack -> spawnAtLocation(level, stack));
    }
}
