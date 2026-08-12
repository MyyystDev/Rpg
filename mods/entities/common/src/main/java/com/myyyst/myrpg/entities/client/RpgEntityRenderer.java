package com.myyyst.myrpg.entities.client;

import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Placeholder renderer: every RpgEntity renders as a humanoid with the
 * default player texture. The appearance component (per-definition models,
 * textures, providers) replaces this in a later slice — the definition id
 * is already synced and client-readable via definitionIdString().
 *
 * HumanoidMobRenderer gives us held-item rendering; the armor layer below
 * adds worn equipment.
 */
public class RpgEntityRenderer
        extends HumanoidMobRenderer<RpgEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public RpgEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        ArmorModelSet<HumanoidModel<HumanoidRenderState>> armor = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new);
        addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
