package com.example.armasreales;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Ojos amarillos que brillan en la oscuridad, como los de la araña. */
public class WerewolfEyesLayer extends EyesLayer<WerewolfEntity, ZombieModel<WerewolfEntity>> {
    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/hombre_lobo_ojos.png"));

    public WerewolfEyesLayer(RenderLayerParent<WerewolfEntity, ZombieModel<WerewolfEntity>> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
