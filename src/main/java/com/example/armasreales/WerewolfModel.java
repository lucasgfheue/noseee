package com.example.armasreales;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Modelo del hombre lobo (forma de bestia). Reutiliza el esqueleto de {@link ZombieModel} para caminar,
 * mirar y levantar los brazos al atacar, pero con geometría propia: cráneo con hocico y colmillos,
 * melena, torso ancho, brazos larguísimos con garras, piernas con pezuñas y cola.
 * (Archivo generado: la textura se pinta con el mismo diseño de cubos.)
 */
public final class WerewolfModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "hombre_lobo"), "main");

    private WerewolfModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(80, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F)
                        .texOffs(72, 44).addBox(-4.5F, -6F, -5.5F, 9F, 2F, 2F)
                        .texOffs(46, 32).addBox(-2.5F, -4F, -10F, 5F, 4F, 6F)
                        .texOffs(16, 44).addBox(-2.5F, 0F, -9F, 5F, 2F, 5F)
                        .texOffs(106, 44).addBox(-1.5F, -4.5F, -11F, 3F, 2F, 1F)
                        .texOffs(114, 44).addBox(1.5F, 0F, -9.6F, 1F, 2F, 1F)
                        .texOffs(118, 44).addBox(-2.5F, 0F, -9.6F, 1F, 2F, 1F)
                        .texOffs(36, 44).addBox(2F, -11F, -3F, 3F, 4F, 2F)
                        .texOffs(46, 44).addBox(-5F, -11F, -3F, 3F, 4F, 2F),
                PartPose.offset(0.0F, -1.0F, -2.0F));
        root.addOrReplaceChild("hat",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.5F, -9F, 0F, 11F, 12F, 7F),
                PartPose.offset(0.0F, -1.0F, -2.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-7F, -1F, -4F, 14F, 9F, 8F)
                        .texOffs(72, 19).addBox(-5F, 8F, -3F, 10F, 6F, 6F)
                        .texOffs(56, 44).addBox(-1F, -3.5F, -2F, 2F, 3F, 2F)
                        .texOffs(64, 44).addBox(-1F, -3.5F, 1.5F, 2F, 3F, 2F),
                PartPose.ZERO);
        body.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(0, 19).addBox(-1.5F, 0F, 0F, 3F, 3F, 10F),
                PartPose.offsetAndRotation(0.0F, 11.0F, 3.0F, 0.9F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-3F, -3F, -3.5F, 6F, 5F, 7F)
                        .texOffs(26, 19).addBox(-2.5F, 2F, -2.5F, 5F, 8F, 5F)
                        .texOffs(26, 32).addBox(-2.5F, 10F, -2.5F, 5F, 7F, 5F)
                        .texOffs(96, 32).addBox(-2.5F, 17F, -3F, 5F, 3F, 6F)
                        .texOffs(94, 44).addBox(-2.5F, 20F, -3F, 1F, 3F, 1F)
                        .texOffs(98, 44).addBox(-0.5F, 20F, -3F, 1F, 3F, 1F)
                        .texOffs(102, 44).addBox(1.5F, 20F, -3F, 1F, 3F, 1F),
                PartPose.offset(-9.0F, 1.0F, 0.0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().mirror()
                        .texOffs(0, 32).addBox(-3F, -3F, -3.5F, 6F, 5F, 7F)
                        .texOffs(26, 19).addBox(-2.5F, 2F, -2.5F, 5F, 8F, 5F)
                        .texOffs(26, 32).addBox(-2.5F, 10F, -2.5F, 5F, 7F, 5F)
                        .texOffs(96, 32).addBox(-2.5F, 17F, -3F, 5F, 3F, 6F)
                        .texOffs(94, 44).addBox(-2.5F, 20F, -3F, 1F, 3F, 1F)
                        .texOffs(98, 44).addBox(-0.5F, 20F, -3F, 1F, 3F, 1F)
                        .texOffs(102, 44).addBox(1.5F, 20F, -3F, 1F, 3F, 1F),
                PartPose.offset(9.0F, 1.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(46, 19).addBox(-3F, 0F, -3.5F, 6F, 6F, 7F)
                        .texOffs(0, 44).addBox(-2F, 6F, -1.5F, 4F, 5F, 4F)
                        .texOffs(68, 32).addBox(-3F, 9F, -6F, 6F, 2F, 8F)
                        .texOffs(122, 44).addBox(-2.5F, 10F, -8F, 1F, 1F, 2F)
                        .texOffs(0, 53).addBox(-0.5F, 10F, -8F, 1F, 1F, 2F)
                        .texOffs(6, 53).addBox(1.5F, 10F, -8F, 1F, 1F, 2F),
                PartPose.offset(-3.5F, 13.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().mirror()
                        .texOffs(46, 19).addBox(-3F, 0F, -3.5F, 6F, 6F, 7F)
                        .texOffs(0, 44).addBox(-2F, 6F, -1.5F, 4F, 5F, 4F)
                        .texOffs(68, 32).addBox(-3F, 9F, -6F, 6F, 2F, 8F)
                        .texOffs(122, 44).addBox(-2.5F, 10F, -8F, 1F, 1F, 2F)
                        .texOffs(0, 53).addBox(-0.5F, 10F, -8F, 1F, 1F, 2F)
                        .texOffs(6, 53).addBox(1.5F, 10F, -8F, 1F, 1F, 2F),
                PartPose.offset(3.5F, 13.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
