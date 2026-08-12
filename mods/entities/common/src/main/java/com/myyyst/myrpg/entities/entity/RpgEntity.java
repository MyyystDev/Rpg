package com.myyyst.myrpg.entities.entity;

import com.myyyst.myrpg.core.stat.StatHolder;
import com.myyyst.myrpg.core.stat.StatStore;
import com.myyyst.myrpg.entities.data.EntitiesData;
import com.myyyst.myrpg.entities.data.EntityDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** The one generic base entity. Everything else is data. */
public class RpgEntity extends PathfinderMob implements StatHolder {

    private final StatStore stats = new StatStore();
    @Nullable private Identifier definitionId;

    private static final EntityDataAccessor<String> DATA_DEFINITION =
            SynchedEntityData.defineId(RpgEntity.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEFINITION, "");
        // NOTE drift: defineSynchedData signature (Builder param vs no-arg) —
        // copy the override shape from the OLD RpgEntity via Local History;
        // it compiled there.
    }

    /** Definition id as synced string — readable client-side (slice 3 uses this). */
    public String definitionIdString() {
        return entityData.get(DATA_DEFINITION);
    }

    public RpgEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes();
    }

    // ------------------------------------------------------------ definition

    public void applyDefinition(Identifier defId) {
        EntityDefinition def = EntitiesData.ENTITIES.all().get(defId);
        if (def == null) return;
        this.definitionId = defId;
        entityData.set(DATA_DEFINITION, defId.toString());

        // attributes
        for (var attrEntry : def.attributes().entrySet()) {
            Identifier attrId = Identifier.tryParse(attrEntry.getKey());
            if (attrId == null) continue;
            // NOTE drift: registry lookup shape — keep whatever the archetype
            // era compiled here (getOptional / get returning Optional<Holder>).
            Holder<Attribute> attr = BuiltInRegistries.ATTRIBUTE.get(attrId).orElse(null);
            if (attr == null) continue;
            AttributeInstance instance = getAttribute(attr);
            if (instance != null) instance.setBaseValue(attrEntry.getValue());
        }

        // stat seeds
        for (var statEntry : def.stats().entrySet()) {
            stats.set(this, statEntry.getKey(), statEntry.getValue());
            // NOTE drift: StatStore.set signature per your core.
        }

        // display
        def.display().flatMap(EntityDefinition.Display::name)
                .ifPresent(n -> setCustomName(Component.literal(n)));
        def.display().ifPresent(d -> setCustomNameVisible(d.nameVisible()));

        setHealth(getMaxHealth());
        // slice 2: equipment, AI goals, targeting, movement, persistence, rules
    }

    @Nullable
    public Identifier definitionId() { return definitionId; }

    // ------------------------------------------------------------ stats

    @Override
    public StatStore rpgStats() { return stats; }
    // NOTE drift: method name per your StatHolder interface — keep yours.

    @Override
    public void tick() {
        super.tick();
        // entity stat rules tick in slice 2; the !stats.isEmpty() guard
        // (deliberate perf asymmetry) returns with it
    }

    // ------------------------------------------------------------ NBT
    // NOTE drift: method names + ValueOutput/ValueInput API per what the
    // archetype era compiled — keep your working bodies, ensuring the
    // definition id and stats both persist.

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("myrpg_archetype", this.entityData.get(DATA_DEFINITION));
        stats.save(output, "myrpg_stats");
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("myrpg_definition", Identifier.CODEC).ifPresent(id -> {
            this.definitionId = id;
            entityData.set(DATA_DEFINITION, id.toString());
        });
        stats.load(input, "myrpg_stats");   // saved values override archetype seeds
        stats.reapplyStages(this);          // effects re-applied, no enter/exit events
        // NO setHealth — a loaded entity keeps its wounds.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // slice 2 reads persistence.despawn; archetype-era default: persistent
        return false;
    }
}