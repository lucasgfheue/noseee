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
 * Dibuja el tanque «Umbra»: chasis intermedio de titanio negro con inserciones de cerámica oscura, placas que
 * flotan sobre soportes dejando ver líneas violetas, frontal inclinado, orugas medianas de segmentos angulares con
 * suspensión expuesta, torreta compacta y asimétrica (cañón energético a la derecha, sensores y disipadores a la
 * izquierda) y un reactor violeta con aro metálico y seis pétalos de refrigeración en la parte trasera.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -1.62 a 1.62 y X ±1.
 * El eje del cañón (pivotY 1.04, pivotForward 0.1, lateral 0.55, largo 1.8) debe coincidir con UmbraTankEntity.
 */
public class UmbraTankRenderer extends EntityRenderer<UmbraTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_umbra.png");

    private static final int T_PLATE = 0, T_CORNER = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_CERAMIC = 8, T_PETAL = 9;

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
    private static final Mat CERAMIC = new Mat(W, T_CERAMIC);
    private static final Mat PETAL = new Mat(W, T_PETAL);
    private static final Mat BOLT = new Mat(new int[]{110, 112, 125}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{14, 14, 18}, T_FLAT);
    private static final Mat GLASS = new Mat(new int[]{36, 30, 64}, T_FLAT);
    private static final Mat HOLO = new Mat(new int[]{150, 190, 255}, T_FLAT, true);
    private static final Mat GLOW_V = new Mat(new int[]{140, 70, 255}, T_FLAT, true);
    private static final Mat GLOW_DIM = new Mat(new int[]{96, 48, 190}, T_FLAT, true);

    private static final int WHEELS = 7;
    private static final float WHEEL_Y = 0.3f;
    private static final float WHEEL_X = 0.82f;
    private static final float WHEEL_SPIN = 0.34f / 0.24f;

    private static final float DECK = 0.82f;
    private static final float REACTOR_Y = 0.34f;
    private static final float REACTOR_Z = -0.64f;

    private float time;

    public UmbraTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.8f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(UmbraTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(UmbraTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(4.0, 2.0, 4.0));
    }

    @Override
    public void render(UmbraTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        float chargeFrac = tank.getCharge() > 0
                ? Mth.clamp((tank.getCharge() + partialTick) / UmbraTankEntity.CHARGE_TICKS, 0.0f, 1.0f) : 0.0f;
        float flash = Mth.lerp(partialTick, tank.flashO, tank.flash);
        float coreK = Math.max(chargeFrac, flash);
        float petals = Mth.lerp(partialTick, tank.petalsO, tank.petals);
        float heatK = Mth.clamp(tank.getHeat() / (float) UmbraTankEntity.HEAT_MAX, 0.0f, 1.0f);
        boolean overload = tank.isOverloaded();

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light, heatK);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, coreK, chargeFrac, heatK, petals, overload);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas y suspensión

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            // Cinta de la oruga: tramo inferior, tramo superior y dos rampas angulares en cada extremo
            mbox(ps, vc, light, s, 0.64f, 0.0f, -1.4f, 1.0f, 0.06f, 1.4f, TRACK);
            mbox(ps, vc, light, s, 0.64f, 0.57f, -1.1f, 1.0f, 0.63f, 1.1f, TRACK);
            float xc = s * 0.82f;
            slab(ps, vc, light, xc, 0.6f, 1.1f, 36.0f, 0.18f, 0.06f, 0.0f, 0.52f, TRACK);
            slab(ps, vc, light, xc, 0.294f, 1.52f, 114.0f, 0.18f, 0.06f, 0.0f, 0.29f, TRACK);
            slab(ps, vc, light, xc, 0.6f, -1.1f, -36.0f, 0.18f, 0.06f, -0.52f, 0.0f, TRACK);
            slab(ps, vc, light, xc, 0.294f, -1.52f, -114.0f, 0.18f, 0.06f, -0.29f, 0.0f, TRACK);
            // Zapatas angulares sobre los tramos rectos
            for (int i = 0; i < 12; i++) {
                float z = -1.32f + i * 0.22f;
                mbox(ps, vc, light, s, 0.625f, -0.012f, z, 1.015f, 0.04f, z + 0.12f, CORNER);
            }
            for (int i = 0; i < 9; i++) {
                float z = -1.06f + i * 0.24f;
                mbox(ps, vc, light, s, 0.625f, 0.59f, z, 1.015f, 0.64f, z + 0.12f, CORNER);
            }
            float angle = s > 0 ? angleL : angleR;
            for (int i = 0; i < WHEELS; i++) {
                float z = -1.2f + i * 0.4f;
                wheel(ps, vc, light, s * WHEEL_X, WHEEL_Y, z, angle);
                suspension(ps, vc, light, s, z);
            }
            drawSkirts(ps, vc, light, s);
        }
    }

    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.15f;
        final float r = 0.15f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.popPose();
        box(ps, vc, light, -h - 0.01f, -0.045f, -0.045f, h + 0.01f, 0.045f, 0.045f, GLOW_DIM);
        ps.popPose();
    }

    /** Suspensión expuesta en la cara exterior: brazo, amortiguador y muelle de cada rueda. */
    private void suspension(PoseStack ps, VertexConsumer vc, int light, int s, float z) {
        mbox(ps, vc, light, s, 1.0f, 0.28f, z - 0.04f, 1.045f, 0.34f, z + 0.2f, BLACK);
        mbox(ps, vc, light, s, 1.005f, 0.34f, z + 0.12f, 1.04f, 0.6f, z + 0.16f, CORNER);
        for (int k = 0; k < 3; k++) {
            float y = 0.4f + k * 0.065f;
            mbox(ps, vc, light, s, 1.0f, y, z + 0.105f, 1.05f, y + 0.03f, z + 0.175f, BOLT);
        }
    }

    /** Faldones: placas que flotan sobre la oruga en los extremos, con soportes y una línea violeta. */
    private void drawSkirts(PoseStack ps, VertexConsumer vc, int light, int s) {
        float[][] zr = {{-1.5f, -0.64f}, {0.64f, 1.5f}};
        for (float[] r : zr) {
            mbox(ps, vc, light, s, 1.07f, 0.12f, r[0], 1.13f, 0.7f, r[1], PLATE);
            mbox(ps, vc, light, s, 1.13f, 0.22f, r[0] + 0.1f, 1.145f, 0.6f, r[1] - 0.1f, CERAMIC);
            mbox(ps, vc, light, s, 1.07f, 0.7f, r[0], 1.13f, 0.725f, r[1], GLOW_V);
            mbox(ps, vc, light, s, 1.0f, 0.2f, r[0] + 0.05f, 1.07f, 0.28f, r[0] + 0.12f, BLACK);
            mbox(ps, vc, light, s, 1.0f, 0.56f, r[1] - 0.12f, 1.07f, 0.64f, r[1] - 0.05f, BLACK);
        }
    }

    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light, float heatK) {
        Mat glow = heatGlow(heatK, false);
        // Núcleo de cerámica y losa de luz violeta que se ve entre las placas flotantes
        box(ps, vc, light, -0.66f, 0.2f, -1.5f, 0.66f, 0.62f, 1.42f, CERAMIC);
        box(ps, vc, light, -0.82f, 0.62f, -1.5f, 0.82f, 0.66f, 1.1f, glow);
        // Placas de cubierta flotantes (titanio negro) con inserciones de cerámica
        box(ps, vc, light, -0.8f, 0.66f, -1.5f, 0.8f, DECK, -0.52f, PLATE);
        box(ps, vc, light, -0.8f, 0.66f, -0.46f, 0.8f, DECK, 0.46f, PLATE);
        box(ps, vc, light, -0.8f, 0.66f, 0.52f, 0.8f, DECK, 1.02f, PLATE);
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 0.1f : -0.62f;
            float xb = s > 0 ? 0.62f : -0.1f;
            box(ps, vc, light, xa, DECK, -1.4f, xb, DECK + 0.015f, -0.62f, CERAMIC);
            box(ps, vc, light, xa, DECK, 0.62f, xb, DECK + 0.015f, 0.92f, CERAMIC);
            // soportes mecánicos en las separaciones
            float sa = s > 0 ? 0.5f : -0.56f;
            float sb = s > 0 ? 0.56f : -0.5f;
            box(ps, vc, light, sa, 0.62f, -0.52f, sb, 0.66f, -0.46f, BLACK);
            box(ps, vc, light, sa, 0.62f, 0.46f, sb, 0.66f, 0.52f, BLACK);
        }
        // Frontal inclinado: dos placas separadas por una ranura violeta
        ps.pushPose();
        ps.translate(0.0, DECK, 1.02);
        ps.mulPose(Axis.XP.rotationDegrees(30.0f));
        box(ps, vc, light, 0.04f, -0.14f, 0.0f, 0.82f, 0.0f, 0.64f, PLATE);
        box(ps, vc, light, -0.82f, -0.14f, 0.0f, -0.04f, 0.0f, 0.64f, PLATE);
        box(ps, vc, light, 0.14f, 0.0f, 0.1f, 0.72f, 0.015f, 0.54f, CERAMIC);
        box(ps, vc, light, -0.72f, 0.0f, 0.1f, -0.14f, 0.015f, 0.54f, CERAMIC);
        box(ps, vc, light, -0.04f, -0.12f, 0.02f, 0.04f, -0.02f, 0.62f, glow);
        ps.popPose();
        box(ps, vc, light, -0.66f, 0.2f, 1.42f, 0.66f, 0.5f, 1.58f, PLATE);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.5f - 0.03f, 0.34f, 1.58f, s * 0.5f + 0.03f, 0.4f, 1.59f, GLOW_V);
        }
        // Placas laterales flotantes sobre las orugas
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.82f, 0.63f, -0.6f, 1.02f, 0.66f, 0.6f, glow);
            mbox(ps, vc, light, s, 0.82f, 0.66f, -0.6f, 1.04f, 0.8f, 0.6f, PLATE);
            mbox(ps, vc, light, s, 0.88f, 0.8f, -0.46f, 0.98f, 0.815f, 0.46f, CERAMIC);
            mbox(ps, vc, light, s, 1.04f, 0.68f, -0.5f, 1.055f, 0.78f, 0.5f, glow);
        }
        drawRear(ps, vc, light, glow);
        drawIndicators(ps, vc, light);
    }

    /** Parte trasera: módulos de potencia sobre las orugas, bloque de rejillas y luces violetas. */
    private void drawRear(PoseStack ps, VertexConsumer vc, int light, Mat glow) {
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.84f, 0.62f, -1.62f, 1.02f, 0.92f, -1.12f, PLATE);
            mbox(ps, vc, light, s, 1.02f, 0.7f, -1.52f, 1.035f, 0.86f, -1.22f, CERAMIC);
            mbox(ps, vc, light, s, 0.86f, 0.72f, -1.63f, 1.0f, 0.84f, -1.62f, glow);
            for (int i = 0; i < 3; i++) {
                float z = -1.56f + i * 0.14f;
                mbox(ps, vc, light, s, 0.88f, 0.92f, z, 0.98f, 0.935f, z + 0.07f, VENT);
            }
            // luz violeta en el borde del módulo
            mbox(ps, vc, light, s, 0.84f, 0.9f, -1.12f, 0.88f, 0.94f, -1.1f, GLOW_V);
        }
        // Rejilla de refrigeración del casco
        box(ps, vc, light, -0.52f, 0.24f, -1.6f, 0.52f, 0.62f, -1.5f, BLACK);
        box(ps, vc, light, -0.48f, 0.28f, -1.62f, 0.48f, 0.58f, -1.6f, VENT);
        box(ps, vc, light, -0.44f, 0.4f, -1.625f, 0.44f, 0.415f, -1.62f, glow);
        box(ps, vc, light, -0.44f, 0.46f, -1.625f, 0.44f, 0.475f, -1.62f, glow);
    }

    /** Indicadores holográficos sobre la cubierta y una antena fina con luz violeta. */
    private void drawIndicators(PoseStack ps, VertexConsumer vc, int light) {
        // emisor delantero izquierdo con barras flotantes que parpadean
        box(ps, vc, light, 0.44f, DECK, 0.62f, 0.62f, DECK + 0.04f, 0.74f, BLACK);
        for (int k = 0; k < 4; k++) {
            float h = 0.08f + 0.09f * (0.5f + 0.5f * Mth.sin(this.time * 0.09f + k * 1.7f));
            float x = 0.46f + k * 0.04f;
            box(ps, vc, light, x, DECK + 0.07f, 0.65f, x + 0.02f, DECK + 0.07f + h, 0.67f, HOLO);
        }
        // emisor trasero derecho con un anillo holográfico
        box(ps, vc, light, -0.62f, DECK, -1.1f, -0.46f, DECK + 0.04f, -0.98f, BLACK);
        float ry = DECK + 0.14f;
        box(ps, vc, light, -0.6f, ry, -1.08f, -0.48f, ry + 0.012f, -1.0f, HOLO);
        box(ps, vc, light, -0.58f, ry + 0.012f, -1.1f, -0.5f, ry + 0.024f, -0.98f, HOLO);
        // antena fina
        box(ps, vc, light, 0.6f, DECK, -1.3f, 0.63f, DECK + 0.4f, -1.27f, BLACK);
        box(ps, vc, light, 0.595f, DECK + 0.4f, -1.305f, 0.635f, DECK + 0.44f, -1.265f, GLOW_V);
        // pequeñas luces violetas en los bordes de la cubierta
        for (int i = 0; i < 4; i++) {
            float z = -1.3f + i * 0.35f;
            box(ps, vc, light, 0.79f, DECK - 0.07f, z, 0.81f, DECK - 0.04f, z + 0.04f, GLOW_V);
            box(ps, vc, light, -0.81f, DECK - 0.07f, z, -0.79f, DECK - 0.04f, z + 0.04f, GLOW_V);
        }
    }

    // ------------------------------------------------------------------ Torreta

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float coreK,
                            float chargeFrac, float heatK, float petals, boolean overload) {
        ps.pushPose();
        ps.translate(0.0, DECK, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        Mat hot = heatGlow(heatK, overload);

        // Corona de giro
        box(ps, vc, light, -0.5f, 0.0f, -0.5f, 0.5f, 0.05f, 0.5f, BLACK);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.5f, 0.0f, -0.5f, 0.5f, 0.05f, 0.5f, BLACK);
        ps.popPose();

        // Cuerpo compacto y formas geométricas irregulares
        box(ps, vc, light, -0.38f, 0.05f, -0.56f, 0.46f, 0.28f, 0.46f, PLATE);
        box(ps, vc, light, -0.76f, 0.05f, -0.5f, -0.34f, 0.36f, 0.62f, PLATE);              // alojamiento del cañón
        box(ps, vc, light, -0.7f, 0.36f, -0.3f, -0.4f, 0.375f, 0.5f, CERAMIC);
        ps.pushPose();                                                                     // cuña angulada frontal
        ps.translate(-0.62, 0.0, 0.3);
        ps.mulPose(Axis.YP.rotationDegrees(14.0f));
        box(ps, vc, light, -0.14f, 0.05f, -0.4f, 0.14f, 0.3f, 0.4f, CORNER);
        ps.popPose();
        ps.pushPose();                                                                     // placa superior inclinada
        ps.translate(-0.3, 0.28, 0.0);
        ps.mulPose(Axis.XP.rotationDegrees(-8.0f));
        box(ps, vc, light, 0.0f, 0.0f, 0.0f, 0.66f, 0.05f, 0.46f, PLATE);
        box(ps, vc, light, 0.08f, 0.05f, 0.08f, 0.58f, 0.062f, 0.4f, CERAMIC);
        ps.popPose();
        // Línea violeta entre el cuerpo y el alojamiento
        box(ps, vc, light, -0.345f, 0.08f, -0.4f, -0.335f, 0.3f, 0.5f, hot);

        drawSensors(ps, vc, light, hot);
        drawReactor(ps, vc, light, coreK, chargeFrac, heatK, petals, overload);

        // Cañón energético a la derecha: eje a (-0.55, 0.22, 0.1) de la torreta
        ps.translate(-0.55, 0.22, 0.1);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        drawCannon(ps, vc, light, chargeFrac, heatK, overload);
        ps.popPose();
    }

    /** Lado izquierdo: conjunto de sensores y disipadores de calor. */
    private void drawSensors(PoseStack ps, VertexConsumer vc, int light, Mat hot) {
        box(ps, vc, light, 0.3f, 0.05f, -0.2f, 0.58f, 0.3f, 0.5f, CERAMIC);
        box(ps, vc, light, 0.3f, 0.3f, -0.2f, 0.58f, 0.312f, 0.5f, CORNER);
        // cubierta que desciende hacia fuera
        ps.pushPose();
        ps.translate(0.3, 0.312, -0.2);
        ps.mulPose(Axis.ZP.rotationDegrees(-18.0f));
        box(ps, vc, light, 0.0f, 0.0f, 0.0f, 0.3f, 0.04f, 0.7f, PLATE);
        ps.popPose();
        // lentes y óptica
        box(ps, vc, light, 0.36f, 0.14f, 0.5f, 0.48f, 0.25f, 0.52f, GLASS);
        box(ps, vc, light, 0.39f, 0.17f, 0.52f, 0.45f, 0.22f, 0.53f, GLOW_V);
        box(ps, vc, light, 0.5f, 0.16f, 0.5f, 0.56f, 0.22f, 0.52f, GLASS);
        // mástil con plato sensor
        box(ps, vc, light, 0.43f, 0.31f, -0.1f, 0.46f, 0.52f, -0.07f, BLACK);
        box(ps, vc, light, 0.37f, 0.52f, -0.16f, 0.52f, 0.54f, -0.01f, PLATE);
        box(ps, vc, light, 0.425f, 0.54f, -0.105f, 0.465f, 0.56f, -0.065f, GLOW_V);
        // disipadores: aletas verticales con el borde brillante
        for (int i = 0; i < 5; i++) {
            float z = -0.52f + i * 0.075f;
            box(ps, vc, light, 0.32f, 0.3f, z, 0.56f, 0.46f, z + 0.025f, CORNER);
            box(ps, vc, light, 0.32f, 0.46f, z, 0.56f, 0.465f, z + 0.025f, hot);
        }
    }

    /** Reactor circular violeta con aro metálico y seis pétalos de refrigeración. */
    private void drawReactor(PoseStack ps, VertexConsumer vc, int light, float coreK, float chargeFrac,
                             float heatK, float petals, boolean overload) {
        float slow = 0.5f + 0.5f * Mth.sin(this.time * 0.09f);                 // pulso lento en reposo
        float k = Mth.clamp(0.35f + 0.45f * slow + 0.5f * chargeFrac, 0.0f, 1.0f);
        if (overload) {
            k = 0.7f + 0.3f * Mth.sin(this.time * 0.5f);
        }
        Mat outer = new Mat(new int[]{(int) Mth.lerp(k, 70, 200), (int) Mth.lerp(k, 25, 120), 255}, T_FLAT, true);
        Mat inner = new Mat(new int[]{(int) Mth.lerp(coreK, 205, 255), (int) Mth.lerp(coreK, 160, 255), 255},
                T_FLAT, true);
        Mat joint = overload
                ? new Mat(new int[]{255, (int) (40 + 30 * Mth.sin(this.time * 0.6f)), 200}, T_FLAT, true)
                : new Mat(new int[]{(int) Mth.lerp(heatK, 90, 200), 45, 220}, T_FLAT, true);

        // Placa trasera de la torreta (formas geométricas: dos cuadrados girados)
        ps.pushPose();
        ps.translate(0.0, REACTOR_Y, REACTOR_Z);
        box(ps, vc, light, -0.34f, -0.34f, -0.04f, 0.34f, 0.34f, 0.08f, PLATE);
        ps.mulPose(Axis.ZP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.34f, -0.34f, -0.04f, 0.34f, 0.34f, 0.08f, CORNER);
        ps.popPose();

        // Aro metálico (ocho segmentos)
        float zr = REACTOR_Z - 0.05f;
        for (int i = 0; i < 8; i++) {
            float a = i * 45.0f;
            ps.pushPose();
            ps.translate(0.235f * Mth.cos(a * Mth.DEG_TO_RAD), REACTOR_Y + 0.235f * Mth.sin(a * Mth.DEG_TO_RAD), zr);
            ps.mulPose(Axis.ZP.rotationDegrees(a + 90.0f));
            box(ps, vc, light, -0.1f, -0.03f, -0.05f, 0.1f, 0.03f, 0.05f, CORNER);
            ps.popPose();
        }

        // Núcleo violeta y centro que se vuelve blanco al acumular energía
        ps.pushPose();
        ps.translate(0.0, REACTOR_Y, REACTOR_Z - 0.02f);
        box(ps, vc, light, -0.14f, -0.14f, -0.03f, 0.14f, 0.14f, 0.03f, outer);
        ps.mulPose(Axis.ZP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.14f, -0.14f, -0.03f, 0.14f, 0.14f, 0.03f, outer);
        ps.popPose();
        ps.pushPose();
        ps.translate(0.0, REACTOR_Y, REACTOR_Z - 0.05f);
        box(ps, vc, light, -0.07f, -0.07f, -0.02f, 0.07f, 0.07f, 0.02f, inner);
        ps.mulPose(Axis.ZP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.07f, -0.07f, -0.02f, 0.07f, 0.07f, 0.02f, inner);
        ps.popPose();

        // Pétalos de refrigeración: se abren hacia atrás desde el aro
        float open = petals * 65.0f;
        for (int i = 0; i < 6; i++) {
            float a = 30.0f + i * 60.0f;
            ps.pushPose();
            ps.translate(0.0, REACTOR_Y, REACTOR_Z - 0.03f);
            ps.mulPose(Axis.ZP.rotationDegrees(a));
            ps.translate(0.27, 0.0, 0.0);
            box(ps, vc, light, -0.012f, -0.1f, -0.03f, 0.012f, 0.1f, 0.01f, joint);        // junta
            ps.mulPose(Axis.YP.rotationDegrees(open));
            box(ps, vc, light, 0.012f, -0.1f, -0.02f, 0.21f, 0.1f, 0.01f, PETAL);
            box(ps, vc, light, 0.012f, -0.014f, -0.03f, 0.21f, 0.014f, 0.015f, BLACK);
            ps.popPose();
        }
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float chargeFrac, float heatK,
                            boolean overload) {
        float k = Mth.clamp(0.3f + 0.7f * Math.max(chargeFrac, heatK), 0.0f, 1.0f);
        Mat line = overload
                ? new Mat(new int[]{255, 60, 200}, T_FLAT, true)
                : new Mat(new int[]{(int) (150 * k + 60), (int) (60 + 70 * k), 255}, T_FLAT, true);
        // Recámara y culata
        box(ps, vc, light, -0.14f, -0.14f, -0.5f, 0.14f, 0.14f, 0.0f, CORNER);
        box(ps, vc, light, -0.1f, 0.14f, -0.45f, 0.1f, 0.155f, -0.05f, CERAMIC);
        for (int i = 0; i < 3; i++) {
            float z = -0.42f + i * 0.1f;
            box(ps, vc, light, -0.15f, -0.1f, z, 0.15f, 0.1f, z + 0.03f, BLACK);
        }
        // Tubo interior y envolvente en dos tramos separados por anillos violeta
        box(ps, vc, light, -0.07f, -0.07f, 0.0f, 0.07f, 0.07f, 1.8f, BLACK);
        box(ps, vc, light, -0.11f, -0.11f, 0.02f, 0.11f, 0.11f, 0.6f, PLATE);
        box(ps, vc, light, -0.1f, -0.1f, 0.66f, 0.1f, 0.1f, 1.2f, PLATE);
        box(ps, vc, light, -0.125f, -0.125f, 0.6f, 0.125f, 0.125f, 0.66f, line);
        box(ps, vc, light, -0.115f, -0.115f, 1.2f, 0.115f, 0.115f, 1.26f, line);
        box(ps, vc, light, -0.015f, 0.11f, 0.1f, 0.015f, 0.125f, 0.58f, line);
        box(ps, vc, light, -0.015f, 0.1f, 0.68f, 0.015f, 0.115f, 1.18f, line);
        // Emisor: cuatro puntas con el interior brillante
        box(ps, vc, light, -0.1f, 0.075f, 1.26f, 0.1f, 0.115f, 1.7f, CORNER);
        box(ps, vc, light, -0.1f, -0.115f, 1.26f, 0.1f, -0.075f, 1.7f, CORNER);
        box(ps, vc, light, 0.075f, -0.1f, 1.26f, 0.115f, 0.1f, 1.7f, CORNER);
        box(ps, vc, light, -0.115f, -0.1f, 1.26f, -0.075f, 0.1f, 1.7f, CORNER);
        box(ps, vc, light, -0.05f, -0.05f, 1.74f, 0.05f, 0.05f, 1.8f, line);
    }

    /** Luz violeta de las juntas: más intensa con el calor; en la sobrecarga, magenta. */
    private Mat heatGlow(float heatK, boolean overload) {
        if (overload) {
            return new Mat(new int[]{255, (int) (40 + 40 * Mth.sin(this.time * 0.5f)), 205}, T_FLAT, true);
        }
        return new Mat(new int[]{(int) Mth.lerp(heatK, 95, 215), (int) Mth.lerp(heatK, 50, 70), 235}, T_FLAT, true);
    }

    // ------------------------------------------------------------------ Geometría

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
