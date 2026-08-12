package com.myyyst.myrpg.entities.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Render state for RpgEntity: which model + texture this instance uses. */
public class RpgEntityRenderState extends HumanoidRenderState {
    @Nullable public HumanoidModel<RpgEntityRenderState> rpgModel;
    @Nullable public Identifier rpgTexture;
}
