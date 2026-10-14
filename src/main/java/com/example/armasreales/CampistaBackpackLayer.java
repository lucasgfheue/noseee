package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Mochila con saco de dormir que llevan los campistas a la espalda. */
public class CampistaBackpackLayer extends RenderLayer<CampistaEntity, HumanoidModel<CampistaEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "campista_mochila"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/campista_mochila.png");

    private final ModelPart pack;

    public CampistaBackpackLayer(RenderLayerParent<CampistaEntity, HumanoidModel<CampistaEntity>> parent,
                                 ModelPart pack) {
        super(parent);
        this.pack = pack;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pack", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, 1.0F, 2.0F, 7.0F, 9.0F, 4.0F)          // bolsa
                .texOffs(0, 16).addBox(-3.5F, 0.0F, 1.8F, 7.0F, 3.0F, 4.4F)         // solapa
                .texOffs(0, 28).addBox(-4.0F, -2.0F, 2.5F, 8.0F, 2.0F, 3.0F)        // saco de dormir
                .texOffs(0, 36).addBox(-3.0F, 0.0F, -2.5F, 1.0F, 9.0F, 0.5F)        // correa izquierda
                .texOffs(0, 36).addBox(2.0F, 0.0F, -2.5F, 1.0F, 9.0F, 0.5F),        // correa derecha
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, CampistaEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.pack.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
