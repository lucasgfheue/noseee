package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo de jugador; al ver un objetivo apunta con el arma con las dos manos. */
public class SoldadoModel extends HumanoidModel<SoldadoEntity> {
    public SoldadoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(SoldadoEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (entity.isAggressive()) {
            float pitch = headPitch * ((float) Math.PI / 180F);
            this.rightArm.xRot = -1.5F + pitch;
            this.rightArm.yRot = -0.15F;
            this.leftArm.xRot = -1.45F + pitch;
            this.leftArm.yRot = 0.45F;
        }
    }
}
