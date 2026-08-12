package com.myyyst.myrpg.entities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.myyyst.myrpg.entities.entity.RpgEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * One renderer for every custom entity. The definition's appearance section
 * (synced through entity data) picks the model bundle + texture per
 * instance; the model field is swapped in submit() before the superclass
 * draws. Scale comes free via the vanilla SCALE attribute.
 */
public class RpgEntityRenderer
        extends HumanoidMobRenderer<RpgEntity, RpgEntityRenderState, HumanoidModel<RpgEntityRenderState>> {

    private final Map<Identifier, HumanoidModel<RpgEntityRenderState>> models = new HashMap<>();
    private final Map<Identifier, Identifier> defaultTextures = new HashMap<>();
    private final HumanoidModel<RpgEntityRenderState> fallbackModel;

    public RpgEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);

        RpgModels.bootstrap();
        for (var entry : RpgModels.all().entrySet()) {
            models.put(entry.getKey(),
                    new HumanoidModel<>(context.bakeLayer(entry.getValue().layer())));
            defaultTextures.put(entry.getKey(), entry.getValue().defaultTexture());
        }
        this.fallbackModel = models.get(RpgModels.HUMANOID);

        ArmorModelSet<HumanoidModel<RpgEntityRenderState>> armor = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new);
        addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
    }

    @Override
    public RpgEntityRenderState createRenderState() {
        return new RpgEntityRenderState();
    }

    @Override
    public void extractRenderState(RpgEntity entity, RpgEntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        Identifier modelId = parse(entity.modelIdString());
        HumanoidModel<RpgEntityRenderState> model = modelId == null ? null : models.get(modelId);
        state.rpgModel = model != null ? model : fallbackModel;

        Identifier explicit = parse(entity.textureString());
        state.rpgTexture = explicit != null ? explicit
                : defaultTextures.getOrDefault(modelId, defaultTextures.get(RpgModels.HUMANOID));
    }

    @Override
    public void submit(RpgEntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.rpgModel != null ? state.rpgModel : fallbackModel;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(RpgEntityRenderState state) {
        return state.rpgTexture != null ? state.rpgTexture
                : defaultTextures.get(RpgModels.HUMANOID);
    }

    @Nullable
    private static Identifier parse(String raw) {
        return raw == null || raw.isEmpty() ? null : Identifier.tryParse(raw);
    }
}
