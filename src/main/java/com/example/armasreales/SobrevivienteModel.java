package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo de jugador; con pistola en mano apunta cuando ve un objetivo. */
public class SobrevivienteModel extends HumanoidModel<SobrevivienteEntity> {
    public SobrevivienteModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(SobrevivienteEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (entity.isAggressive() && !entity.getMainHandItem().isEmpty()) {
            float pitch = headPitch * ((float) Math.PI / 180F);
            this.rightArm.xRot = -1.5F + pitch;
            this.rightArm.yRot = -0.15F;
            this.leftArm.xRot = -1.45F + pitch;
            this.leftArm.yRot = 0.45F;
        }
    }
}
