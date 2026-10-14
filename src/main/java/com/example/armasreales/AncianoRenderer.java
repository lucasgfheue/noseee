package com.example.armasreales;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AncianoRenderer extends HumanoidMobRenderer<AncianoEntity, AncianoModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/anciano.png");

    public AncianoRenderer(EntityRendererProvider.Context context) {
        super(context, new AncianoModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AncianoEntity entity) {
        return TEXTURE;
    }
}
