package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo humanoide: sentado en la silla o de pie, con la escopeta en las manos. */
public class AncianoModel extends HumanoidModel<AncianoEntity> {
    public AncianoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(AncianoEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean sitting = entity.isSitting();
        if (sitting) {
            // Piernas dobladas: sentado en la silla
            this.rightLeg.xRot = -1.4137F;
            this.rightLeg.yRot = 0.3142F;
            this.rightLeg.zRot = 0.0785F;
            this.leftLeg.xRot = -1.4137F;
            this.leftLeg.yRot = -0.3142F;
            this.leftLeg.zRot = -0.0785F;
        }
        if (entity.isAggressive()) {
            // Apunta con la escopeta
            float pitch = headPitch * ((float) Math.PI / 180F);
            this.rightArm.xRot = -1.5F + pitch;
            this.rightArm.yRot = -0.15F;
            this.leftArm.xRot = -1.45F + pitch;
            this.leftArm.yRot = 0.45F;
        } else if (sitting) {
            this.rightArm.xRot = -0.9F;
            this.rightArm.yRot = -0.3F;
            this.leftArm.xRot = -0.8F;
            this.leftArm.yRot = 0.4F;
        }
        this.hat.copyFrom(this.head);
    }
}
