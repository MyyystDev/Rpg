package com.myyyst.myrpg.entities.entity;

import com.myyyst.myrpg.entities.Constants;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.data.EntityArchetype;
import com.myyyst.myrpg.core.stat.StatEngine;
import com.myyyst.myrpg.core.stat.StatHolder;
import com.myyyst.myrpg.core.stat.StatStore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class RpgEntity extends PathfinderMob implements StatHolder {

    private static final EntityDataAccessor<String> DATA_ARCHETYPE =
            SynchedEntityData.defineId(RpgEntity.class, EntityDataSerializers.STRING);

    @Nullable private EntityArchetype archetype;
    private final StatStore stats = new StatStore();

    public RpgEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARCHETYPE, "");
    }

    // ------------------------------------------------------------ StatHolder

    @Override
    public StatStore rpgStats() {
        return stats;
    }

    // ------------------------------------------------------------ archetype

    public void setArchetype(Identifier id) {
        this.entityData.set(DATA_ARCHETYPE, id.toString());
        applyArchetype();
    }

    @Nullable
    public Identifier getArchetypeId() {
        String raw = this.entityData.get(DATA_ARCHETYPE);
        return raw.isEmpty() ? null : Identifier.tryParse(raw);
    }

    private void applyArchetype() {
        if (level().isClientSide()) return;
        Identifier id = getArchetypeId();
        if (id == null) return;

        this.archetype = EntitiesData.ARCHETYPES.get(id).orElse(null);
        if (archetype == null) {
            Constants.LOG.warn("[myrpg] Entity has unknown archetype {}", id);
            return;
        }

        archetype.displayName().ifPresent(name -> setCustomName(Component.literal(name)));

        archetype.stats().ifPresent(s -> {
            s.maxHealth().ifPresent(v -> setBase(Attributes.MAX_HEALTH, v));
            s.movementSpeed().ifPresent(v -> setBase(Attributes.MOVEMENT_SPEED, v));
            s.attackDamage().ifPresent(v -> setBase(Attributes.ATTACK_DAMAGE, v));

            stats.clear();
            s.custom().forEach((statId, value) -> stats.set(this, statId, value));
        });
        setHealth(getMaxHealth());   // fresh archetype application = full health
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !stats.isEmpty()) {
            StatEngine.tick(stats, this);
        }
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("myrpg_archetype", this.entityData.get(DATA_ARCHETYPE));
        stats.save(output, "myrpg_stats");
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String id = input.getString("myrpg_archetype").orElse("");
        if (!id.isEmpty()) {
            this.entityData.set(DATA_ARCHETYPE, id);
            applyArchetype();
        }
        stats.load(input, "myrpg_stats");   // saved values override archetype seeds
        stats.reapplyStages(this);          // effects re-applied, no enter/exit events
        // NO setHealth — a loaded entity keeps its wounds.
    }
}