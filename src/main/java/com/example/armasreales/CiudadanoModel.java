package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo de jugador; al apuntar sube los dos brazos. */
public class CiudadanoModel extends HumanoidModel<CiudadanoEntity> {
    public CiudadanoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(CiudadanoEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
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
