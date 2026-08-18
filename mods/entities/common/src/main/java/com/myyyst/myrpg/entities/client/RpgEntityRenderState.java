package com.myyyst.myrpg.entities.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Render state for RpgEntity: which model + texture this instance uses.
 *
 * <p>Render states are the snapshot the renderer takes each frame, so that drawing never
 * touches the live entity. These two fields are resolved in
 * {@code RpgEntityRenderer.extractRenderState} and consumed while drawing.</p>
 */
public class RpgEntityRenderState extends HumanoidRenderState {
    /** Model chosen for this instance; null means "use the renderer's fallback". */
    @Nullable public HumanoidModel<RpgEntityRenderState> rpgModel;
    /** Texture chosen for this instance; null means "use the model's default". */
    @Nullable public Identifier rpgTexture;
}
