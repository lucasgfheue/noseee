package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja al hombre lobo como jugador (de día) o como bestia con su propio modelo (de noche). */
public class WerewolfRenderer extends EntityRenderer<WerewolfEntity> {
    private static final ResourceLocation WOLF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/hombre_lobo.png");
    private static final ResourceLocation[] HUMAN_TEXTURES = new ResourceLocation[WerewolfEntity.HUMAN_SKINS];

    static {
        for (int i = 0; i < HUMAN_TEXTURES.length; i++) {
            HUMAN_TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(
                    ArmasRealesMod.MOD_ID, "textures/entity/hombre_lobo_humano_" + i + ".png");
        }
    }

    private final EntityRenderer<WerewolfEntity> human;
    private final EntityRenderer<WerewolfEntity> beast;

    public WerewolfRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.human = new HumanoidMobRenderer<WerewolfEntity, WerewolfPoseModel>(
                context, new WerewolfPoseModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f) {
            @Override
            public ResourceLocation getTextureLocation(WerewolfEntity entity) {
                return HUMAN_TEXTURES[entity.getHumanSkin()];
            }
        };
        MobRenderer<WerewolfEntity, WerewolfPoseModel> wolf =
                new MobRenderer<WerewolfEntity, WerewolfPoseModel>(
                        context, new WerewolfPoseModel(context.bakeLayer(WerewolfModel.LAYER)), 0.7f) {
                    @Override
                    public ResourceLocation getTextureLocation(WerewolfEntity entity) {
                        return WOLF_TEXTURE;
                    }
                };
        wolf.addLayer(new WerewolfEyesLayer(wolf));
        this.beast = wolf;
        this.shadowRadius = 0.7f;
    }

    @Override
    public void render(WerewolfEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        (entity.isWolf() ? this.beast : this.human).render(entity, yaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WerewolfEntity entity) {
        return entity.isWolf() ? WOLF_TEXTURE : HUMAN_TEXTURES[entity.getHumanSkin()];
    }
}
