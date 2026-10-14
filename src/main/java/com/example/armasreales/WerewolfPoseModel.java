package com.example.armasreales;

import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Modelo humanoide del hombre lobo: siempre con los brazos abajo.
 *
 * HumanoidModel.setupAnim vuelve a poner en cada fotograma la altura de las piernas (12), de la cabeza (0)
 * y la posición lateral de los brazos, lo que desarma modelos con otras proporciones. Aquí se guardan las
 * posiciones originales de cada pieza raíz y se restauran después de la animación.
 */
public class WerewolfPoseModel extends AbstractZombieModel<WerewolfEntity> {
    private final ModelPart[] parts;
    private final float[][] home;

    public WerewolfPoseModel(ModelPart root) {
        super(root);
        this.parts = new ModelPart[]{this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
        this.home = new float[parts.length][3];
        for (int i = 0; i < parts.length; i++) {
            home[i][0] = parts[i].x;
            home[i][1] = parts[i].y;
            home[i][2] = parts[i].z;
        }
    }

    @Override
    public void setupAnim(WerewolfEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        for (int i = 0; i < parts.length; i++) {
            parts[i].x = home[i][0];
            parts[i].y = home[i][1];
            parts[i].z = home[i][2];
        }
        this.hat.copyFrom(this.head);
    }

    @Override
    public boolean isAggressive(WerewolfEntity entity) {
        return false;
    }
}
