package com.myyyst.myrpg.entities.client;

import com.myyyst.myrpg.entities.MyrpgEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side model provider registry (plan: "model providers should be
 * registered, not hardcoded"). A bundle names a vanilla model layer whose
 * humanoid-compatible geometry gets baked into a plain HumanoidModel, plus
 * the texture used when the definition doesn't specify one.
 *
 * Only humanoid-part-tree layers work here (head/body/arms/legs). Non-
 * humanoid families (quadruped, slime, custom geometry) arrive later as
 * their own provider types.
 *
 * Addons register in their client entrypoint, before renderers bake.
 */
public final class RpgModels {

    public record Bundle(ModelLayerLocation layer, Identifier defaultTexture) {}

    private static final Map<Identifier, Bundle> REGISTRY = new LinkedHashMap<>();

    public static final Identifier HUMANOID = id("humanoid");

    public static void register(Identifier modelId, Bundle bundle) {
        REGISTRY.put(modelId, bundle);
    }

    public static Map<Identifier, Bundle> all() {
        return REGISTRY;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MyrpgEntities.MOD_ID, path);
    }

    private static Identifier mc(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    public static void bootstrap() {
        if (!REGISTRY.isEmpty()) return;
        register(HUMANOID, new Bundle(ModelLayers.PLAYER,
                mc("textures/entity/player/wide/steve.png")));
        register(id("humanoid_slim"), new Bundle(ModelLayers.PLAYER_SLIM,
                mc("textures/entity/player/slim/alex.png")));
        register(id("zombie"), new Bundle(ModelLayers.ZOMBIE,
                mc("textures/entity/zombie/zombie.png")));
        register(id("skeleton"), new Bundle(ModelLayers.SKELETON,
                mc("textures/entity/skeleton/skeleton.png")));
    }

    private RpgModels() {}
}
