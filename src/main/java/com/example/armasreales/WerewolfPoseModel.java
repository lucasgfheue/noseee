package com.example.armasreales;

import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.geom.ModelPart;

/** Modelo humanoide que levanta los brazos cuando el hombre lobo tiene un objetivo (te ve). */
public class WerewolfPoseModel extends AbstractZombieModel<WerewolfEntity> {
    public WerewolfPoseModel(ModelPart root) {
        super(root);
    }

    @Override
    public boolean isAggressive(WerewolfEntity entity) {
        return entity.isAggressive();
    }
}
