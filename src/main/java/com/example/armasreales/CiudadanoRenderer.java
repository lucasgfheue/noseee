package com.example.armasreales;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja a los ciudadanos con modelo de jugador y una de 20 skins. */
public class CiudadanoRenderer extends HumanoidMobRenderer<CiudadanoEntity, CiudadanoModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[CiudadanoEntity.TOTAL_SKINS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/ciudadano_" + i + ".png");
        }
    }

    public CiudadanoRenderer(EntityRendererProvider.Context context) {
        super(context, new CiudadanoModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(CiudadanoEntity entity) {
        return TEXTURES[entity.getSkin()];
    }
}
