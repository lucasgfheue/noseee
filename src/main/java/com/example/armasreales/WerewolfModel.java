package com.example.armasreales;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Modelo del hombre lobo (forma de bestia): postura encorvada, hocico largo con la boca abierta y todos los
 * dientes, orejas altas, brazos fuertes con codo y garras, patas largas con articulaciones y cola espesa.
 * Se apoya en el esqueleto de ZombieModel (mismas partes raíz), y las
 * articulaciones extra son partes hijas con rotación fija. (Archivo generado junto con la textura.)
 */
public final class WerewolfModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "hombre_lobo"), "main");

    private WerewolfModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                .texOffs(78, 0).addBox(-4F, -4F, -3.5F, 8F, 8F, 7F)
                .texOffs(72, 33).addBox(-2.5F, -0.5F, -10.5F, 5F, 4F, 7F)
                .texOffs(34, 54).addBox(-1.5F, -1F, -11.5F, 3F, 2F, 1F)
                .texOffs(66, 45).addBox(-2.5F, 3.5F, -6F, 5F, 3F, 3F)
                .texOffs(46, 45).addBox(2F, -9F, -2.5F, 3F, 5F, 2F)
                .texOffs(56, 45).addBox(-5F, -9F, -2.5F, 3F, 5F, 2F)
                .texOffs(42, 54).addBox(-2.5F, 3.5F, -10F, 1F, 2F, 1F)
                .texOffs(46, 54).addBox(1.5F, 3.5F, -10F, 1F, 2F, 1F)
                .texOffs(50, 54).addBox(-2.5F, 3.5F, -8.7F, 1F, 2F, 1F)
                .texOffs(54, 54).addBox(1.5F, 3.5F, -8.7F, 1F, 2F, 1F)
                .texOffs(58, 54).addBox(-2.5F, 3.5F, -7.4F, 1F, 2F, 1F)
                .texOffs(62, 54).addBox(1.5F, 3.5F, -7.4F, 1F, 2F, 1F)
                .texOffs(66, 54).addBox(-2.5F, 3.5F, -6.1F, 1F, 2F, 1F)
                .texOffs(70, 54).addBox(1.5F, 3.5F, -6.1F, 1F, 2F, 1F)
                .texOffs(74, 54).addBox(-2.5F, 3.5F, -4.8F, 1F, 2F, 1F)
                .texOffs(78, 54).addBox(1.5F, 3.5F, -4.8F, 1F, 2F, 1F)
                .texOffs(82, 54).addBox(-2F, 3.5F, -10.5F, 1F, 2F, 1F)
                .texOffs(86, 54).addBox(-0.5F, 3.5F, -10.5F, 1F, 2F, 1F)
                .texOffs(90, 54).addBox(1F, 3.5F, -10.5F, 1F, 2F, 1F),
                PartPose.offsetAndRotation(0F, -3.6973F, -7F, 0F, 0.0F, 0.0F));
        PartDefinition head_jaw = head.addOrReplaceChild("jaw",
                CubeListBuilder.create()
                .texOffs(0, 45).addBox(-2.5F, -0.5F, -7F, 5F, 2F, 7F)
                .texOffs(94, 54).addBox(-2.5F, -2.5F, -6.5F, 1F, 2F, 1F)
                .texOffs(98, 54).addBox(1.5F, -2.5F, -6.5F, 1F, 2F, 1F)
                .texOffs(102, 54).addBox(-2.5F, -2.5F, -5.2F, 1F, 2F, 1F)
                .texOffs(106, 54).addBox(1.5F, -2.5F, -5.2F, 1F, 2F, 1F)
                .texOffs(110, 54).addBox(-2.5F, -2.5F, -3.9F, 1F, 2F, 1F)
                .texOffs(114, 54).addBox(1.5F, -2.5F, -3.9F, 1F, 2F, 1F)
                .texOffs(118, 54).addBox(-2F, -2.5F, -7F, 1F, 2F, 1F)
                .texOffs(122, 54).addBox(-0.5F, -2.5F, -7F, 1F, 2F, 1F)
                .texOffs(0, 59).addBox(1F, -2.5F, -7F, 1F, 2F, 1F),
                PartPose.offsetAndRotation(0F, 5F, -4F, 0.55F, 0.0F, 0.0F));
        PartDefinition hat = root.addOrReplaceChild("hat",
                CubeListBuilder.create()
                .texOffs(44, 0).addBox(-5F, -4F, 0F, 10F, 10F, 7F),
                PartPose.offsetAndRotation(0F, -3.6973F, -7F, 0F, 0.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 7.4027F, 1F, 0F, 0.0F, 0.0F));
        PartDefinition body_torso = body.addOrReplaceChild("torso",
                CubeListBuilder.create()
                .texOffs(24, 18).addBox(-4.5F, -6F, -3.5F, 9F, 7F, 7F)
                .texOffs(0, 0).addBox(-6.5F, -14F, -4.5F, 13F, 9F, 9F)
                .texOffs(94, 45).addBox(-1F, -13F, 4.5F, 2F, 3F, 2F)
                .texOffs(102, 45).addBox(-1F, -9.5F, 4.5F, 2F, 3F, 2F)
                .texOffs(110, 45).addBox(-1F, -6F, 4.5F, 2F, 3F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.5F, 0.0F, 0.0F));
        PartDefinition body_tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create()
                .texOffs(0, 33).addBox(-2F, -2F, 0F, 4F, 4F, 8F)
                .texOffs(24, 33).addBox(-2.5F, -2.5F, 0F, 5F, 5F, 7F)
                .texOffs(82, 45).addBox(-1.5F, -1.5F, 7F, 3F, 3F, 3F),
                PartPose.offsetAndRotation(0F, -1F, 4F, 0.5F, 0.0F, 0.0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                .texOffs(48, 33).addBox(-3F, -3F, -3F, 6F, 6F, 6F),
                PartPose.offsetAndRotation(-8.5F, -3.0973F, -5F, 0F, 0.0F, 0.0F));
        PartDefinition right_arm_upper = right_arm.addOrReplaceChild("upper",
                CubeListBuilder.create()
                .texOffs(72, 18).addBox(-2.5F, 0F, -2.5F, 5F, 8F, 5F),
                PartPose.offsetAndRotation(0F, 2F, 0F, -0.45F, 0.0F, 0.0F));
        PartDefinition right_arm_upper_fore = right_arm_upper.addOrReplaceChild("fore",
                CubeListBuilder.create()
                .texOffs(92, 18).addBox(-2.5F, 0F, -2.5F, 5F, 8F, 5F)
                .texOffs(118, 45).addBox(-1F, -1F, 1.5F, 2F, 3F, 2F)
                .texOffs(24, 45).addBox(-2.5F, 8F, -3F, 5F, 3F, 6F)
                .texOffs(0, 54).addBox(-2.5F, 11F, -3F, 1F, 4F, 1F)
                .texOffs(4, 54).addBox(-0.5F, 11F, -3F, 1F, 4F, 1F)
                .texOffs(8, 54).addBox(1.5F, 11F, -3F, 1F, 4F, 1F),
                PartPose.offsetAndRotation(0F, 8F, 0F, -0.9F, 0.0F, 0.0F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().mirror()
                .texOffs(48, 33).addBox(-3F, -3F, -3F, 6F, 6F, 6F),
                PartPose.offsetAndRotation(8.5F, -3.0973F, -5F, 0F, 0.0F, 0.0F));
        PartDefinition left_arm_upper = left_arm.addOrReplaceChild("upper",
                CubeListBuilder.create().mirror()
                .texOffs(72, 18).addBox(-2.5F, 0F, -2.5F, 5F, 8F, 5F),
                PartPose.offsetAndRotation(0F, 2F, 0F, -0.45F, 0.0F, 0.0F));
        PartDefinition left_arm_upper_fore = left_arm_upper.addOrReplaceChild("fore",
                CubeListBuilder.create().mirror()
                .texOffs(92, 18).addBox(-2.5F, 0F, -2.5F, 5F, 8F, 5F)
                .texOffs(118, 45).addBox(-1F, -1F, 1.5F, 2F, 3F, 2F)
                .texOffs(24, 45).addBox(-2.5F, 8F, -3F, 5F, 3F, 6F)
                .texOffs(0, 54).addBox(-2.5F, 11F, -3F, 1F, 4F, 1F)
                .texOffs(4, 54).addBox(-0.5F, 11F, -3F, 1F, 4F, 1F)
                .texOffs(8, 54).addBox(1.5F, 11F, -3F, 1F, 4F, 1F),
                PartPose.offsetAndRotation(0F, 8F, 0F, -0.9F, 0.0F, 0.0F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-4F, 6.4027F, 1F, 0F, 0.0F, 0.0F));
        PartDefinition right_leg_thigh = right_leg.addOrReplaceChild("thigh",
                CubeListBuilder.create()
                .texOffs(0, 18).addBox(-3F, -1F, -3F, 6F, 9F, 6F),
                PartPose.offsetAndRotation(0F, 0F, 0F, -0.45F, 0.0F, 0.0F));
        PartDefinition right_leg_thigh_shin = right_leg_thigh.addOrReplaceChild("shin",
                CubeListBuilder.create()
                .texOffs(56, 18).addBox(-2F, 0F, -2F, 4F, 10F, 4F),
                PartPose.offsetAndRotation(0F, 8F, 0F, 0.8F, 0.0F, 0.0F));
        PartDefinition right_leg_thigh_shin_foot = right_leg_thigh_shin.addOrReplaceChild("foot",
                CubeListBuilder.create()
                .texOffs(96, 33).addBox(-3F, -1F, -7F, 6F, 2F, 8F)
                .texOffs(4, 59).addBox(-2.5F, -0.5F, -9F, 1F, 1F, 2F)
                .texOffs(10, 59).addBox(-0.5F, -0.5F, -9F, 1F, 1F, 2F)
                .texOffs(16, 59).addBox(1.5F, -0.5F, -9F, 1F, 1F, 2F),
                PartPose.offsetAndRotation(0F, 10F, 0F, -0.35F, 0.0F, 0.0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().mirror(),
                PartPose.offsetAndRotation(4F, 6.4027F, 1F, 0F, 0.0F, 0.0F));
        PartDefinition left_leg_thigh = left_leg.addOrReplaceChild("thigh",
                CubeListBuilder.create().mirror()
                .texOffs(0, 18).addBox(-3F, -1F, -3F, 6F, 9F, 6F),
                PartPose.offsetAndRotation(0F, 0F, 0F, -0.45F, 0.0F, 0.0F));
        PartDefinition left_leg_thigh_shin = left_leg_thigh.addOrReplaceChild("shin",
                CubeListBuilder.create().mirror()
                .texOffs(56, 18).addBox(-2F, 0F, -2F, 4F, 10F, 4F),
                PartPose.offsetAndRotation(0F, 8F, 0F, 0.8F, 0.0F, 0.0F));
        PartDefinition left_leg_thigh_shin_foot = left_leg_thigh_shin.addOrReplaceChild("foot",
                CubeListBuilder.create().mirror()
                .texOffs(96, 33).addBox(-3F, -1F, -7F, 6F, 2F, 8F)
                .texOffs(4, 59).addBox(-2.5F, -0.5F, -9F, 1F, 1F, 2F)
                .texOffs(10, 59).addBox(-0.5F, -0.5F, -9F, 1F, 1F, 2F)
                .texOffs(16, 59).addBox(1.5F, -0.5F, -9F, 1F, 1F, 2F),
                PartPose.offsetAndRotation(0F, 10F, 0F, -0.35F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
