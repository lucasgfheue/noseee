package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja al campista con modelo de jugador y una de 5 skins. */
public class CampistaRenderer extends HumanoidMobRenderer<CampistaEntity, HumanoidModel<CampistaEntity>> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[CampistaEntity.SKINS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/campista_" + i + ".png");
        }
    }

    public CampistaRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(CampistaEntity entity) {
        return TEXTURES[entity.getSkin()];
    }
}
