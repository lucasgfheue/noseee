package com.example.armasreales;

import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo humanoide del hombre lobo: siempre con los brazos abajo (nunca los levanta al atacar). */
public class WerewolfPoseModel extends AbstractZombieModel<WerewolfEntity> {
    public WerewolfPoseModel(ModelPart root) {
        super(root);
    }

    @Override
    public boolean isAggressive(WerewolfEntity entity) {
        return false;
    }
}
