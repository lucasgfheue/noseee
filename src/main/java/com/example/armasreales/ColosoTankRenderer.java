package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dibuja el tanque «Coloso»: casco alto y cuadrado de acero negro con el frontal en varias capas de blindaje,
 * bloques blindados en los laterales, orugas anchas con ruedas enormes y faldones reforzados, conductos de
 * refrigeración expuestos, torreta sobredimensionada con cilindros hidráulicos y un cañón de plasma larguísimo
 * rodeado de anillos de refrigeración y grandes protectores. Las rejillas tienen ranuras naranjas y rojas que
 * se intensifican en el modo berserker, con destellos alrededor del cañón.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -1.8 a 1.75 y X ±1.2.
 * El eje del cañón (pivotY 1.7, pivotForward 0.9, largo 2.6) debe coincidir con ColosoTankEntity.
 */
public class ColosoTankRenderer extends EntityRenderer<ColosoTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_coloso.png");

    private static final int T_PLATE = 0, T_CORNER = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_DENT = 8, T_HAZARD = 9, T_PIPE = 10;

    private record Mat(int[] c, int tile, boolean glow) {
        Mat(int[] c, int tile) {
            this(c, tile, false);
        }
    }

    private static final int[] W = {255, 255, 255};
    private static final Mat PLATE = new Mat(W, T_PLATE);
    private static final Mat CORNER = new Mat(W, T_CORNER);
    private static final Mat TRACK = new Mat(W, T_TRACK);
    private static final Mat HUB = new Mat(W, T_HUB);
    private static final Mat VENT = new Mat(W, T_VENT);
    private static final Mat PANEL = new Mat(W, T_PANEL);
    private static final Mat DENT = new Mat(W, T_DENT);
    private static final Mat HAZARD = new Mat(W, T_HAZARD);
    private static final Mat PIPE = new Mat(W, T_PIPE);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 132}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{12, 12, 15}, T_FLAT);
    private static final Mat LAMP = new Mat(new int[]{255, 240, 210}, T_FLAT, true);

    private static final int WHEELS = 6;
    private static final float WHEEL_Y = 0.4f;
    private static final float WHEEL_X = 0.83f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;

    private static final float DECK = 1.4f;

    private float time;
    private Mat orange = BLACK;
    private Mat red = BLACK;

    public ColosoTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.4f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(ColosoTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ColosoTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(6.0, 3.0, 6.0));
    }

    @Override
    public void render(ColosoTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        float furyK = Mth.clamp(tank.getFury() / (float) ColosoTankEntity.FURY_MAX, 0.0f, 1.0f);
        boolean berserk = tank.isBerserk();
        float since = tank.ticksSinceShot(partialTick);
        float recoil = since < 14.0f ? 0.32f * (since < 2.5f ? since / 2.5f : 1.0f - (since - 2.5f) / 11.5f) : 0.0f;
        float flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;

        float k = berserk ? 0.85f + 0.15f * Mth.sin(this.time * 0.9f)
                : 0.28f + 0.4f * furyK + 0.04f * Mth.sin(this.time * 0.12f);
        this.orange = orangeMat(k);
        this.red = redMat(k);

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash, berserk);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas anchas, ruedas y faldones

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            mbox(ps, vc, light, s, 0.62f, 0.0f, -1.35f, 1.04f, 0.08f, 1.35f, TRACK);
            mbox(ps, vc, light, s, 0.62f, 0.74f, -1.3f, 1.04f, 0.82f, 1.3f, TRACK);
            float xc = s * 0.83f;
            slab(ps, vc, light, xc, 0.78f, 1.3f, 45.0f, 0.21f, 0.08f, 0.0f, 0.42f, TRACK);
            slab(ps, vc, light, xc, 0.48f, 1.6f, 135.0f, 0.21f, 0.08f, 0.0f, 0.5f, TRACK);
            slab(ps, vc, light, xc, 0.78f, -1.3f, -45.0f, 0.21f, 0.08f, -0.42f, 0.0f, TRACK);
            slab(ps, vc, light, xc, 0.48f, -1.6f, -135.0f, 0.21f, 0.08f, -0.5f, 0.0f, TRACK);
            // Zapatas gruesas
            for (int i = 0; i < 13; i++) {
                float z = -1.34f + i * 0.21f;
                mbox(ps, vc, light, s, 0.6f, -0.015f, z, 1.06f, 0.05f, z + 0.12f, CORNER);
                mbox(ps, vc, light, s, 0.6f, 0.77f, z, 1.06f, 0.835f, z + 0.12f, CORNER);
            }
            float angle = s > 0 ? angleL : angleR;
            for (int i = 0; i < WHEELS; i++) {
                float z = -1.35f + i * 0.54f;
                wheel(ps, vc, light, s * WHEEL_X, WHEEL_Y, z, angle);
            }
            drawSkirts(ps, vc, light, s);
        }
    }

    /** Rueda de gran diámetro con cubo y tornillos. */
    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.19f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -0.27f, -0.27f, h, 0.27f, 0.27f, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -0.2f, -0.2f, h, 0.2f, 0.2f, HUB);
        ps.popPose();
        box(ps, vc, light, -h - 0.02f, -0.08f, -0.08f, h + 0.02f, 0.08f, 0.08f, BOLT);
        ps.popPose();
    }

    /** Faldones reforzados sobre las ruedas: cuatro placas gruesas por lado con marcas de impacto y franja desgastada. */
    private void drawSkirts(PoseStack ps, VertexConsumer vc, int light, int s) {
        float[][] zr = {{-1.75f, -0.97f}, {-0.92f, -0.12f}, {-0.07f, 0.73f}, {0.78f, 1.62f}};
        for (int i = 0; i < zr.length; i++) {
            float z0 = zr[i][0];
            float z1 = zr[i][1];
            mbox(ps, vc, light, s, 1.04f, 0.56f, z0, 1.2f, 0.98f, z1, PLATE);
            if (i % 2 == 0) {
                mbox(ps, vc, light, s, 1.2f, 0.7f, z0 + 0.1f, 1.212f, 0.94f, z1 - 0.1f, DENT);
            }
            mbox(ps, vc, light, s, 1.2f, 0.6f, z0 + 0.1f, 1.21f, 0.68f, z1 - 0.1f, HAZARD);
            mbox(ps, vc, light, s, 1.19f, 0.54f, z0, 1.225f, 1.0f, z0 + 0.06f, CORNER);
            mbox(ps, vc, light, s, 1.19f, 0.54f, z1 - 0.06f, 1.225f, 1.0f, z1, CORNER);
        }
    }

    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Casco bajo, casco alto y reborde del techo
        box(ps, vc, light, -0.66f, 0.18f, -1.78f, 0.66f, 0.86f, 1.5f, PLATE);
        box(ps, vc, light, -0.62f, 0.1f, -1.7f, 0.62f, 0.18f, 1.45f, BLACK);
        box(ps, vc, light, -0.98f, 0.84f, -1.8f, 0.98f, DECK, 1.2f, PLATE);
        box(ps, vc, light, -1.0f, DECK, -1.82f, 1.0f, DECK + 0.04f, 1.22f, CORNER);
        box(ps, vc, light, -0.6f, DECK + 0.04f, 0.85f, 0.6f, DECK + 0.055f, 1.15f, DENT);

        drawFront(ps, vc, light);
        drawSideBlocks(ps, vc, light);
        drawRear(ps, vc, light);
        drawDucts(ps, vc, light);
    }

    /** Frontal en varias capas: glacis inclinado, placas superpuestas, refuerzos angulares y placa de proa. */
    private void drawFront(PoseStack ps, VertexConsumer vc, int light) {
        ps.pushPose();
        ps.translate(0.0, DECK, 1.2);
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.98f, -0.16f, 0.0f, 0.98f, 0.0f, 0.78f, PLATE);          // capa 1
        box(ps, vc, light, -0.8f, 0.0f, 0.1f, 0.8f, 0.06f, 0.64f, DENT);              // capa 2
        box(ps, vc, light, -0.5f, 0.06f, 0.2f, 0.5f, 0.1f, 0.52f, CORNER);            // capa 3
        box(ps, vc, light, -0.04f, 0.1f, 0.2f, 0.04f, 0.13f, 0.52f, BLACK);
        ps.popPose();
        // Placa de proa con rejilla de ranuras
        box(ps, vc, light, -0.9f, 0.3f, 1.5f, 0.9f, 0.84f, 1.75f, PLATE);
        box(ps, vc, light, -0.9f, 0.26f, 1.52f, 0.9f, 0.3f, 1.77f, CORNER);
        grilleZ(ps, vc, light, -0.5f, 0.5f, 0.42f, 0.74f, 1.75f, 1, this.orange, 4);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.58f, 0.35f, 1.75f, 0.86f, 0.8f, 1.762f, DENT);
            // refuerzos angulares en las esquinas del frontal
            ps.pushPose();
            ps.translate(s * 0.96f, 1.1f, 1.42f);
            ps.mulPose(Axis.YP.rotationDegrees(s * 38.0f));
            box(ps, vc, light, -0.06f, -0.3f, -0.26f, 0.06f, 0.3f, 0.26f, CORNER);
            ps.popPose();
            ps.pushPose();
            ps.translate(s * 0.82f, 0.56f, 1.78f);
            ps.mulPose(Axis.YP.rotationDegrees(s * 30.0f));
            box(ps, vc, light, -0.1f, -0.26f, -0.04f, 0.1f, 0.26f, 0.04f, CORNER);
            ps.popPose();
            // faros de trabajo
            mbox(ps, vc, light, s, 0.72f, DECK + 0.04f, 0.98f, 0.9f, DECK + 0.14f, 1.12f, BLACK);
            mbox(ps, vc, light, s, 0.74f, DECK + 0.06f, 1.12f, 0.88f, DECK + 0.12f, 1.125f, LAMP);
        }
    }

    /** Bloques blindados en los laterales del casco alto, con rejillas energéticas en el central. */
    private void drawSideBlocks(PoseStack ps, VertexConsumer vc, int light) {
        float[][] zr = {{-1.72f, -0.96f}, {-0.9f, -0.06f}, {0.0f, 1.1f}};
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < zr.length; i++) {
                float z0 = zr[i][0];
                float z1 = zr[i][1];
                mbox(ps, vc, light, s, 0.98f, 0.98f, z0, 1.2f, 1.5f, z1, PLATE);
                mbox(ps, vc, light, s, 0.98f, 1.5f, z0, 1.2f, 1.54f, z1, CORNER);
                mbox(ps, vc, light, s, 1.19f, 0.98f, z0, 1.225f, 1.54f, z0 + 0.06f, CORNER);
                mbox(ps, vc, light, s, 1.19f, 0.98f, z1 - 0.06f, 1.225f, 1.54f, z1, CORNER);
                if (i != 1) {
                    mbox(ps, vc, light, s, 1.2f, 1.05f, z0 + 0.1f, 1.212f, 1.43f, z1 - 0.1f, DENT);
                }
                for (int b = 0; b < 3; b++) {
                    float z = z0 + 0.16f + b * (z1 - z0 - 0.32f) / 2.0f;
                    mbox(ps, vc, light, s, 1.225f, 1.45f, z - 0.025f, 1.25f, 1.5f, z + 0.025f, BOLT);
                }
            }
            grilleX(ps, vc, light, s, 1.2f, 1.12f, 1.38f, -0.8f, -0.16f, this.orange, 4);
        }
    }

    /** Parte trasera: cubierta del motor con ranuras, rejilla trasera, escapes y antena. */
    private void drawRear(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.45f, DECK, -1.72f, 0.45f, DECK + 0.012f, -1.05f, this.red);
        for (int i = 0; i < 6; i++) {
            float z = -1.7f + i * 0.11f;
            box(ps, vc, light, -0.47f, DECK + 0.012f, z, 0.47f, DECK + 0.03f, z + 0.05f, BLACK);
        }
        grilleZ(ps, vc, light, -0.8f, 0.8f, 0.95f, 1.3f, -1.8f, -1, this.orange, 5);
        box(ps, vc, light, -0.5f, 0.3f, -1.79f, 0.5f, 0.75f, -1.78f, DENT);
        for (int s = -1; s <= 1; s += 2) {
            // escapes con boca incandescente
            mbox(ps, vc, light, s, 0.5f, DECK, -1.75f, 0.8f, 1.78f, -1.45f, PLATE);
            mbox(ps, vc, light, s, 0.47f, 1.52f, -1.78f, 0.83f, 1.56f, -1.42f, CORNER);
            mbox(ps, vc, light, s, 0.54f, 1.78f, -1.7f, 0.76f, 1.8f, -1.5f, this.red);
        }
        // antena
        box(ps, vc, light, 0.8f, DECK, -1.5f, 0.84f, 2.25f, -1.46f, BLACK);
        box(ps, vc, light, 0.79f, 2.25f, -1.51f, 0.85f, 2.3f, -1.45f, this.red);
    }

    /** Conductos de refrigeración expuestos: tuberías verticales atrás y tuberías sobre los bloques laterales. */
    private void drawDucts(PoseStack ps, VertexConsumer vc, int light) {
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 0.9f;
            box(ps, vc, light, x - 0.05f, 0.9f, -1.9f, x + 0.05f, 1.55f, -1.8f, PIPE);
            for (float y : new float[]{1.0f, 1.25f}) {
                box(ps, vc, light, x - 0.07f, y, -1.92f, x + 0.07f, y + 0.04f, -1.78f, CORNER);
            }
            mbox(ps, vc, light, s, 1.05f, 1.56f, -1.6f, 1.15f, 1.64f, 1.0f, PIPE);
            for (float z : new float[]{-1.55f, -0.9f, -0.2f, 0.5f}) {
                mbox(ps, vc, light, s, 1.06f, 1.54f, z, 1.14f, 1.56f, z + 0.06f, BLACK);
                mbox(ps, vc, light, s, 1.03f, 1.6f, z - 0.02f, 1.17f, 1.66f, z + 0.08f, CORNER);
            }
        }
        box(ps, vc, light, -0.9f, 1.45f, -1.9f, 0.9f, 1.55f, -1.82f, PIPE);
        box(ps, vc, light, -0.2f, 1.55f, -1.9f, -0.1f, 1.7f, -1.82f, PIPE);
        box(ps, vc, light, 0.1f, 1.55f, -1.9f, 0.2f, 1.7f, -1.82f, PIPE);
    }

    // ------------------------------------------------------------------ Torreta y cañón de plasma

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash, boolean berserk) {
        ps.pushPose();
        ps.translate(0.0, DECK, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Corona de giro y cuerpo rectangular sobredimensionado
        box(ps, vc, light, -0.82f, 0.0f, -0.82f, 0.82f, 0.06f, 0.82f, BLACK);
        box(ps, vc, light, -0.9f, 0.06f, -1.0f, 0.9f, 0.3f, 0.5f, PLATE);
        box(ps, vc, light, 0.38f, 0.3f, -1.0f, 0.9f, 0.55f, 0.5f, PLATE);
        box(ps, vc, light, -0.9f, 0.3f, -1.0f, -0.38f, 0.55f, 0.5f, PLATE);
        box(ps, vc, light, -0.38f, 0.3f, -1.0f, 0.38f, 0.55f, -0.45f, PLATE);
        box(ps, vc, light, -0.38f, 0.3f, 0.35f, 0.38f, 0.55f, 0.5f, PLATE);
        // Brocal de la escotilla del conductor
        box(ps, vc, light, -0.4f, 0.55f, -0.47f, 0.4f, 0.62f, -0.4f, CORNER);
        box(ps, vc, light, -0.4f, 0.55f, 0.34f, 0.4f, 0.62f, 0.4f, CORNER);
        box(ps, vc, light, 0.34f, 0.55f, -0.47f, 0.4f, 0.62f, 0.4f, CORNER);
        box(ps, vc, light, -0.4f, 0.55f, -0.47f, -0.34f, 0.62f, 0.4f, CORNER);
        // Planchas superiores con marcas de impacto
        box(ps, vc, light, 0.45f, 0.55f, -0.9f, 0.84f, 0.57f, 0.4f, DENT);
        box(ps, vc, light, -0.84f, 0.55f, -0.9f, -0.45f, 0.57f, 0.4f, DENT);
        box(ps, vc, light, -0.3f, 0.55f, -0.95f, 0.3f, 0.57f, -0.52f, PANEL);
        // Armadura lateral con rejillas energéticas
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.9f, 0.1f, -0.9f, 0.98f, 0.55f, 0.45f, PLATE);
            mbox(ps, vc, light, s, 0.98f, 0.14f, 0.05f, 0.99f, 0.5f, 0.4f, DENT);
            mbox(ps, vc, light, s, 0.9f, 0.5f, -0.9f, 0.98f, 0.58f, 0.45f, CORNER);
            grilleX(ps, vc, light, s, 0.98f, 0.2f, 0.44f, -0.85f, -0.15f, s > 0 ? this.orange : this.red, 3);
        }
        // Mantelete y estructura frontal
        box(ps, vc, light, -0.7f, 0.06f, 0.5f, 0.7f, 0.66f, 1.05f, PLATE);
        box(ps, vc, light, -0.7f, 0.66f, 0.52f, 0.7f, 0.7f, 1.0f, CORNER);
        mbox(ps, vc, light, 1, 0.34f, 0.1f, 1.05f, 0.68f, 0.6f, 1.062f, DENT);
        mbox(ps, vc, light, -1, 0.34f, 0.1f, 1.05f, 0.68f, 0.6f, 1.062f, DENT);
        // Compartimento trasero con rejilla y tuberías
        box(ps, vc, light, -0.8f, 0.1f, -1.28f, 0.8f, 0.5f, -1.0f, PLATE);
        box(ps, vc, light, -0.82f, 0.46f, -1.3f, 0.82f, 0.52f, -1.0f, CORNER);
        grilleZ(ps, vc, light, -0.55f, 0.55f, 0.16f, 0.4f, -1.28f, -1, this.red, 4);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.68f - 0.045f, 0.1f, -1.36f, s * 0.68f + 0.045f, 0.62f, -1.28f, PIPE);
            box(ps, vc, light, s * 0.68f - 0.06f, 0.3f, -1.38f, s * 0.68f + 0.06f, 0.34f, -1.26f, CORNER);
        }
        box(ps, vc, light, -0.68f, 0.58f, -1.36f, 0.68f, 0.66f, -1.28f, PIPE);

        // Grandes protectores alrededor de la raíz del cañón
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.52f, 0.06f, 0.7f, 0.66f, 0.92f, 1.7f, PLATE);
            mbox(ps, vc, light, s, 0.66f, 0.2f, 0.85f, 0.672f, 0.8f, 1.55f, DENT);
            mbox(ps, vc, light, s, 0.5f, 0.88f, 0.7f, 0.68f, 0.94f, 1.7f, CORNER);
            mbox(ps, vc, light, s, 0.65f, 0.06f, 1.62f, 0.69f, 0.92f, 1.7f, CORNER);
        }
        box(ps, vc, light, -0.54f, 0.84f, 0.55f, 0.54f, 0.92f, 1.35f, PLATE);
        box(ps, vc, light, -0.5f, 0.92f, 0.62f, 0.5f, 0.945f, 1.28f, DENT);

        // Cilindros hidráulicos visibles (de la torreta a la cuna del cañón)
        float rad = pitch * Mth.DEG_TO_RAD;
        float ay = 0.3f + 0.26f * Mth.cos(rad) - 0.45f * Mth.sin(rad);
        float az = 0.9f + 0.26f * Mth.sin(rad) + 0.45f * Mth.cos(rad);
        for (int s = -1; s <= 1; s += 2) {
            cylinder(ps, vc, light, s * 0.4f, 0.64f, 0.62f, ay, az);
        }

        // Cañón de plasma: eje a (0, 0.3, 0.9) de la torreta
        ps.translate(0.0, 0.3, 0.9);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0.0, 0.0, -recoil);
        drawCannon(ps, vc, light, flash, berserk);
        ps.popPose();
    }

    /** Cilindro hidráulico: cuerpo y vástago desde (bx, by, bz) hasta el punto (cy, cz) de la torreta. */
    private void cylinder(PoseStack ps, VertexConsumer vc, int light, float bx, float by, float bz,
                          float cy, float cz) {
        float dy = cy - by;
        float dz = cz - bz;
        float len = Mth.sqrt(dy * dy + dz * dz);
        float ang = (float) Math.toDegrees(Math.atan2(-dy, dz));
        ps.pushPose();
        ps.translate(bx, by, bz);
        ps.mulPose(Axis.XP.rotationDegrees(ang));
        float body = len * 0.55f;
        box(ps, vc, light, -0.05f, -0.05f, 0.0f, 0.05f, 0.05f, body, PLATE);
        box(ps, vc, light, -0.065f, -0.065f, 0.04f, 0.065f, 0.065f, 0.1f, CORNER);
        box(ps, vc, light, -0.065f, -0.065f, body - 0.1f, 0.065f, 0.065f, body - 0.04f, CORNER);
        box(ps, vc, light, -0.03f, -0.03f, body, 0.03f, 0.03f, len, BOLT);
        box(ps, vc, light, -0.07f, -0.07f, len - 0.04f, 0.07f, 0.07f, len, BLACK);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash, boolean berserk) {
        // Recámara, cuna y apoyos de los cilindros
        box(ps, vc, light, -0.3f, -0.3f, -0.55f, 0.3f, 0.3f, 0.1f, CORNER);
        box(ps, vc, light, -0.24f, -0.24f, 0.1f, 0.24f, 0.24f, 0.7f, PLATE);
        box(ps, vc, light, -0.2f, 0.3f, -0.45f, 0.2f, 0.32f, 0.0f, DENT);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.24f, 0.2f, 0.4f, 0.44f, 0.32f, 0.5f, CORNER);
        }
        // Tubo del cañón
        box(ps, vc, light, -0.14f, -0.14f, 0.1f, 0.14f, 0.14f, 2.55f, BLACK);
        // Cinco anillos de refrigeración con ranuras energéticas
        for (int i = 0; i < 5; i++) {
            float z = 0.5f + i * 0.4f;
            box(ps, vc, light, -0.27f, -0.27f, z, 0.27f, 0.27f, z + 0.16f, CORNER);
            box(ps, vc, light, -0.18f, 0.27f, z + 0.03f, 0.18f, 0.278f, z + 0.13f, this.orange);
            box(ps, vc, light, -0.18f, -0.278f, z + 0.03f, 0.18f, -0.27f, z + 0.13f, this.orange);
            box(ps, vc, light, 0.27f, -0.18f, z + 0.03f, 0.278f, 0.18f, z + 0.13f, this.red);
            box(ps, vc, light, -0.278f, -0.18f, z + 0.03f, -0.27f, 0.18f, z + 0.13f, this.red);
            // núcleo incandescente entre anillos
            if (i < 4) {
                box(ps, vc, light, -0.08f, 0.14f, z + 0.16f, 0.08f, 0.15f, z + 0.4f, this.red);
                box(ps, vc, light, -0.08f, -0.15f, z + 0.16f, 0.08f, -0.14f, z + 0.4f, this.red);
                box(ps, vc, light, 0.14f, -0.08f, z + 0.16f, 0.15f, 0.08f, z + 0.4f, this.orange);
                box(ps, vc, light, -0.15f, -0.08f, z + 0.16f, -0.14f, 0.08f, z + 0.4f, this.orange);
            }
        }
        // Freno de boca con ranuras
        box(ps, vc, light, -0.22f, -0.22f, 2.3f, 0.22f, 0.22f, 2.6f, PLATE);
        box(ps, vc, light, -0.24f, -0.24f, 2.28f, 0.24f, 0.24f, 2.34f, CORNER);
        box(ps, vc, light, -0.15f, 0.22f, 2.38f, 0.15f, 0.228f, 2.55f, this.orange);
        box(ps, vc, light, -0.15f, -0.228f, 2.38f, 0.15f, -0.22f, 2.55f, this.orange);
        box(ps, vc, light, -0.08f, -0.08f, 2.6f, 0.08f, 0.08f, 2.62f, this.orange);
        // Fogonazo al disparar
        if (flash > 0.0f) {
            float f = 0.12f + 0.2f * flash;
            box(ps, vc, light, -f, -f, 2.6f, f, f, 2.6f + 0.25f * flash, new Mat(new int[]{255, 225, 150}, T_FLAT, true));
        }
        // Destellos alrededor del cañón en modo berserker
        if (berserk) {
            int base = (int) (this.time * 0.8f);
            Mat spark = new Mat(new int[]{255, 235, 170}, T_FLAT, true);
            for (int n = 0; n < 7; n++) {
                int sd = base * 31 + n * 17;
                float a = rnd(sd) * Mth.TWO_PI;
                float r = 0.3f + 0.22f * rnd(sd + 1);
                float z = 0.3f + 2.4f * rnd(sd + 2);
                float sz = 0.035f + 0.05f * rnd(sd + 3);
                float x = r * Mth.cos(a);
                float y = r * Mth.sin(a);
                box(ps, vc, light, x - sz, y - sz, z - sz, x + sz, y + sz, z + sz, spark);
            }
        }
    }

    /** Número pseudoaleatorio estable en [0, 1) a partir de una semilla (para los destellos). */
    private static float rnd(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536.0f;
    }

    // ------------------------------------------------------------------ Luz de las rejillas

    private static Mat orangeMat(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{255, (int) (60 + 130 * k), (int) (8 + 60 * k * k)}, T_FLAT, true);
    }

    private static Mat redMat(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{(int) (130 + 125 * k), (int) (10 + 55 * k * k), (int) (8 + 14 * k)}, T_FLAT, true);
    }

    // ------------------------------------------------------------------ Geometría

    /** Rejilla en una cara perpendicular a Z: fondo luminoso y láminas oscuras delante. dir = +1 mira a +Z. */
    private static void grilleZ(PoseStack ps, VertexConsumer vc, int light, float x1, float x2, float y1, float y2,
                                float z, int dir, Mat glow, int slats) {
        float d = 0.012f;
        float ga = dir > 0 ? z : z - d;
        float gb = dir > 0 ? z + d : z;
        box(ps, vc, light, x1, y1, ga, x2, y2, gb, glow);
        float ba = dir > 0 ? gb : ga - 0.03f;
        float bb = dir > 0 ? gb + 0.03f : ga;
        float h = (y2 - y1) / (slats * 2 + 1);
        for (int i = 0; i < slats; i++) {
            float ya = y1 + h * (2 * i + 1);
            box(ps, vc, light, x1, ya, ba, x2, ya + h, bb, BLACK);
        }
        box(ps, vc, light, x1 - 0.02f, y1 - 0.02f, ba, x1 + 0.02f, y2 + 0.02f, bb, CORNER);
        box(ps, vc, light, x2 - 0.02f, y1 - 0.02f, ba, x2 + 0.02f, y2 + 0.02f, bb, CORNER);
    }

    /** Rejilla en una cara perpendicular a X, a la distancia x del eje del lado s (s = +1 izquierda, -1 derecha). */
    private static void grilleX(PoseStack ps, VertexConsumer vc, int light, int s, float x, float y1, float y2,
                                float z1, float z2, Mat glow, int slats) {
        float d = 0.012f;
        float fx = s * x;
        float ga = s > 0 ? fx : fx - d;
        float gb = s > 0 ? fx + d : fx;
        box(ps, vc, light, ga, y1, z1, gb, y2, z2, glow);
        float ba = s > 0 ? gb : ga - 0.03f;
        float bb = s > 0 ? gb + 0.03f : ga;
        float h = (y2 - y1) / (slats * 2 + 1);
        for (int i = 0; i < slats; i++) {
            float ya = y1 + h * (2 * i + 1);
            box(ps, vc, light, ba, ya, z1, bb, ya + h, z2, BLACK);
        }
        box(ps, vc, light, ba, y1 - 0.02f, z1 - 0.02f, bb, y2 + 0.02f, z1 + 0.02f, CORNER);
        box(ps, vc, light, ba, y1 - 0.02f, z2 - 0.02f, bb, y2 + 0.02f, z2 + 0.02f, CORNER);
    }

    /** Caja espejada: s > 0 la dibuja tal cual; s < 0 la refleja respecto al plano X = 0. */
    private static void mbox(PoseStack ps, VertexConsumer vc, int light, int s,
                             float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        if (s > 0) {
            box(ps, vc, light, x1, y1, z1, x2, y2, z2, m);
        } else {
            box(ps, vc, light, -x2, y1, z1, -x1, y2, z2, m);
        }
    }

    /** Placa inclinada alrededor del eje X, con el pivote en (cx, cy, cz). */
    private static void slab(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle,
                             float halfX, float thick, float z1, float z2, Mat m) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -halfX, -thick * 0.5f, z1, halfX, thick * 0.5f, z2, m);
        ps.popPose();
    }

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        PoseStack.Pose pose = ps.last();
        int l = m.glow() ? LightTexture.FULL_BRIGHT : light;
        float xl = x2 - x1;
        float yl = y2 - y1;
        float zl = z2 - z1;
        quad(pose, vc, l, m, zl, xl, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(pose, vc, l, m, xl, zl, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(pose, vc, l, m, yl, xl, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(pose, vc, l, m, xl, yl, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(pose, vc, l, m, zl, yl, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(pose, vc, l, m, yl, zl, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, Mat m, float spanU, float spanV,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float tu = (m.tile() % 4) * 0.25f;
        float tv = (m.tile() / 4) * 0.25f;
        float fu = Math.min(spanU, 1.0f);
        float fv = Math.min(spanV, 1.0f);
        float u0 = tu + 0.25f * (1.0f - fu) * 0.5f;
        float u1 = tu + 0.25f - 0.25f * (1.0f - fu) * 0.5f;
        float v0 = tv + 0.25f * (1.0f - fv) * 0.5f;
        float v1 = tv + 0.25f - 0.25f * (1.0f - fv) * 0.5f;
        vertex(pose, vc, light, m.c(), u0, v1, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, m.c(), u1, v1, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, m.c(), u1, v0, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, m.c(), u0, v0, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c, float u, float v,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
