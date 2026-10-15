package com.example.armasreales;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja al sobreviviente con una de 5 skins. */
public class SobrevivienteRenderer extends HumanoidMobRenderer<SobrevivienteEntity, SobrevivienteModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[SobrevivienteEntity.SKINS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/sobreviviente_" + i + ".png");
        }
    }

    public SobrevivienteRenderer(EntityRendererProvider.Context context) {
        super(context, new SobrevivienteModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SobrevivienteEntity entity) {
        return TEXTURES[entity.getSkin()];
    }
}
