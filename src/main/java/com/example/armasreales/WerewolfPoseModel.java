package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Modelo humanoide del hombre lobo: siempre con los brazos abajo.
 *
 * HumanoidModel.setupAnim vuelve a poner en cada fotograma la altura de las piernas (12), de la cabeza (0)
 * y la posición lateral de los brazos, lo que desarma modelos con otras proporciones. Aquí se guardan las
 * posiciones originales de cada pieza raíz y se restauran después de la animación.
 */
public class WerewolfPoseModel extends HumanoidModel<WerewolfEntity> {
    private final ModelPart[] parts;
    private final float[][] home;
    /** Mandíbula inferior de la bestia (el modelo humano no la tiene). */
    private final ModelPart jaw;

    public WerewolfPoseModel(ModelPart root) {
        super(root);
        this.parts = new ModelPart[]{this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
        this.jaw = root.getChild("head").hasChild("jaw") ? root.getChild("head").getChild("jaw") : null;
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
        if (jaw != null) {
            boolean angry = entity.isAggressive();
            float open;
            if (this.attackTime > 0.0F) {
                // mordisco: de boca bien abierta a cerrarla de golpe y volver a abrir
                open = 0.95F * Math.abs((float) Math.cos(this.attackTime * Math.PI));
            } else if (angry) {
                open = 0.6F + 0.12F * (float) Math.sin(ageInTicks * 0.9F);      // gruñido
            } else {
                open = 0.16F + 0.05F * (float) Math.sin(ageInTicks * 0.15F);    // respiración
            }
            jaw.xRot = open;
        }
    }
}
