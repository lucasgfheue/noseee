package com.example.armasreales;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja al soldado con una de 4 skins de camuflaje. */
public class SoldadoRenderer extends HumanoidMobRenderer<SoldadoEntity, SoldadoModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[SoldadoEntity.SKINS];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/soldado_" + i + ".png");
        }
    }

    public SoldadoRenderer(EntityRendererProvider.Context context) {
        super(context, new SoldadoModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldadoEntity entity) {
        return TEXTURES[entity.getSkin()];
    }
}
