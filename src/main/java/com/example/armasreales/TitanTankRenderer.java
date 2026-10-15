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
 * Dibuja el «Titán Blindado»: fortaleza terrestre de proporciones monstruosas. Casco extremadamente ancho y alto con
 * sección frontal rectangular, capas de placas de titanio negro con refuerzos angulares en el frontal y cuatro bloques
 * laterales reemplazables, cuatro conjuntos de orugas independientes (dos por lado) con ruedas grandes parcialmente
 * cubiertas por faldones, pasarelas de mantenimiento con barandillas, escotillas reforzadas, torreta central elevada
 * con un cañón industrial rodeado de anillos y conductos de refrigeración, cilindros hidráulicos, indicadores blancos
 * alrededor de la cabina, líneas ámbar en el reactor trasero y el emblema X grabado en el blindaje frontal.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -2.2 a 1.95 y X ±1.7.
 * El eje del cañón (pivotY 2.1, pivotForward 1.0, largo 2.6) debe coincidir con TitanTankEntity.
 */
public class TitanTankRenderer extends EntityRenderer<TitanTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_titan.png");

    private static final int T_PLATE = 0, T_TITAN = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_DENT = 8, T_EMBLEM = 9, T_CORNER = 10, T_MARK = 11, T_PIPE = 12;

    private record Mat(int[] c, int tile, boolean glow) {
        Mat(int[] c, int tile) {
            this(c, tile, false);
        }
    }

    private static final int[] W = {255, 255, 255};
    private static final Mat PLATE = new Mat(W, T_PLATE);
    private static final Mat TITAN = new Mat(W, T_TITAN);
    private static final Mat TRACK = new Mat(W, T_TRACK);
    private static final Mat HUB = new Mat(W, T_HUB);
    private static final Mat VENT = new Mat(W, T_VENT);
    private static final Mat PANEL = new Mat(W, T_PANEL);
    private static final Mat DENT = new Mat(W, T_DENT);
    private static final Mat EMBLEM = new Mat(W, T_EMBLEM);
    private static final Mat CORNER = new Mat(W, T_CORNER);
    private static final Mat MARK = new Mat(W, T_MARK);
    private static final Mat PIPE = new Mat(W, T_PIPE);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 132}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{12, 12, 15}, T_FLAT);

    private static final float WHEEL_Y = 0.38f;
    private static final float WHEEL_X = 1.35f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;

    private static final float TOP = 1.65f;

    private float time;
    private Mat amber = BLACK;

    public TitanTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.4f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(TitanTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TitanTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(6.0, 3.0, 6.0));
    }

    @Override
    public void render(TitanTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        int blocks = tank.getBlocks();
        float heatK = Mth.clamp(tank.getHeat() / (float) TitanTankEntity.HEAT_MAX, 0.0f, 1.0f);
        float since = tank.ticksSinceShot(partialTick);
        float recoil = since < 16.0f ? 0.34f * (since < 2.5f ? since / 2.5f : 1.0f - (since - 2.5f) / 13.5f) : 0.0f;
        float flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;

        float k = 0.5f + 0.15f * Mth.sin(this.time * 0.08f) + 0.35f * heatK;
        this.amber = amberMat(k);

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light, blocks);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash);

        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Cuatro orugas independientes

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            float angle = s > 0 ? angleL : angleR;
            trackUnit(ps, vc, light, s, 0.985f, angle);
            trackUnit(ps, vc, light, s, -0.985f, angle);
            // eslabón articulado entre los dos conjuntos de cada lado
            mbox(ps, vc, light, s, 1.1f, 0.3f, -0.22f, 1.6f, 0.46f, 0.22f, BLACK);
            mbox(ps, vc, light, s, 1.1f, 0.46f, -0.2f, 1.6f, 0.5f, 0.2f, CORNER);
        }
    }

    /** Un conjunto de oruga con tres ruedas grandes y su faldón blindado. zc = centro del conjunto en Z. */
    private void trackUnit(PoseStack ps, VertexConsumer vc, int light, int s, float zc, float angle) {
        float xc = s * WHEEL_X;
        mbox(ps, vc, light, s, 1.08f, 0.0f, zc - 0.45f, 1.62f, 0.08f, zc + 0.45f, TRACK);
        mbox(ps, vc, light, s, 1.08f, 0.82f, zc - 0.5f, 1.62f, 0.9f, zc + 0.5f, TRACK);
        slab(ps, vc, light, xc, 0.86f, zc + 0.5f, 45.0f, 0.27f, 0.08f, 0.0f, 0.4f, TRACK);
        slab(ps, vc, light, xc, 0.577f, zc + 0.783f, 135.0f, 0.27f, 0.08f, 0.0f, 0.5f, TRACK);
        slab(ps, vc, light, xc, 0.86f, zc - 0.5f, -45.0f, 0.27f, 0.08f, -0.4f, 0.0f, TRACK);
        slab(ps, vc, light, xc, 0.577f, zc - 0.783f, -135.0f, 0.27f, 0.08f, -0.5f, 0.0f, TRACK);
        for (int i = 0; i < 6; i++) {
            float z = zc - 0.45f + i * 0.15f;
            mbox(ps, vc, light, s, 1.06f, -0.012f, z, 1.64f, 0.05f, z + 0.1f, CORNER);
            float zt = zc - 0.5f + i * 0.17f;
            mbox(ps, vc, light, s, 1.06f, 0.88f, zt, 1.64f, 0.935f, zt + 0.1f, CORNER);
        }
        for (int k = -1; k <= 1; k++) {
            wheel(ps, vc, light, xc, WHEEL_Y, zc + k * 0.5f, angle);
        }
        // faldón blindado que cubre la mitad superior de las ruedas
        mbox(ps, vc, light, s, 1.62f, 0.44f, zc - 0.74f, 1.7f, 1.02f, zc + 0.74f, PLATE);
        mbox(ps, vc, light, s, 1.7f, 0.52f, zc - 0.64f, 1.712f, 0.94f, zc - 0.02f, DENT);
        mbox(ps, vc, light, s, 1.7f, 0.52f, zc + 0.04f, 1.712f, 0.94f, zc + 0.64f, MARK);
        mbox(ps, vc, light, s, 1.61f, 0.42f, zc - 0.76f, 1.725f, 1.04f, zc - 0.7f, CORNER);
        mbox(ps, vc, light, s, 1.61f, 0.42f, zc + 0.7f, 1.725f, 1.04f, zc + 0.76f, CORNER);
        mbox(ps, vc, light, s, 1.61f, 0.42f, zc - 0.76f, 1.725f, 0.48f, zc + 0.76f, CORNER);
    }

    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.24f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -0.24f, -0.24f, h, 0.24f, 0.24f, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -0.18f, -0.18f, h, 0.18f, 0.18f, HUB);
        ps.popPose();
        box(ps, vc, light, -h - 0.02f, -0.08f, -0.08f, h + 0.02f, 0.08f, 0.08f, BOLT);
        ps.popPose();
    }

    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light, int blocks) {
        // Casco bajo y casco alto de titanio (estructura)
        box(ps, vc, light, -1.08f, 0.18f, -1.8f, 1.08f, 1.0f, 1.55f, PLATE);
        box(ps, vc, light, -1.0f, 0.1f, -1.7f, 1.0f, 0.18f, 1.5f, BLACK);
        box(ps, vc, light, -1.35f, 0.9f, -1.8f, 1.35f, TOP, 1.5f, TITAN);

        drawFront(ps, vc, light);
        drawSideBlocks(ps, vc, light, blocks);
        drawTop(ps, vc, light);
        drawWalkways(ps, vc, light);
        drawReactor(ps, vc, light);
    }

    /** Sección frontal rectangular con refuerzos angulares y el emblema X grabado en el blindaje. */
    private void drawFront(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.08f, 0.2f, 1.55f, 1.08f, 1.0f, 1.9f, PLATE);
        box(ps, vc, light, -1.7f, 1.0f, 1.4f, 1.7f, TOP, 1.9f, PLATE);
        box(ps, vc, light, -1.72f, TOP, 1.38f, 1.72f, TOP + 0.05f, 1.92f, CORNER);
        box(ps, vc, light, -1.72f, 0.96f, 1.38f, 1.72f, 1.0f, 1.92f, CORNER);
        // placa central con el emblema
        box(ps, vc, light, -0.55f, 1.02f, 1.9f, 0.55f, 1.63f, 1.94f, TITAN);
        boxFull(ps, vc, light, -0.28f, 1.045f, 1.94f, 0.28f, 1.605f, 1.952f, EMBLEM);
        // rejillas de ventilación en la proa baja
        box(ps, vc, light, -0.85f, 0.3f, 1.9f, -0.2f, 0.85f, 1.91f, VENT);
        box(ps, vc, light, 0.2f, 0.3f, 1.9f, 0.85f, 0.85f, 1.91f, VENT);
        for (int s = -1; s <= 1; s += 2) {
            // refuerzos angulares en el frontal (abrazaderas inclinadas)
            ps.pushPose();
            ps.translate(s * 1.0f, 1.33f, 1.9f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * 35.0f));
            box(ps, vc, light, -0.07f, -0.32f, 0.0f, 0.07f, 0.32f, 0.05f, CORNER);
            ps.popPose();
            ps.pushPose();
            ps.translate(s * 1.35f, 1.33f, 1.9f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -35.0f));
            box(ps, vc, light, -0.07f, -0.32f, 0.0f, 0.07f, 0.32f, 0.05f, CORNER);
            ps.popPose();
            // esquinas con refuerzo en cuña
            ps.pushPose();
            ps.translate(s * 1.64f, 1.33f, 1.82f);
            ps.mulPose(Axis.YP.rotationDegrees(s * 40.0f));
            box(ps, vc, light, -0.14f, -0.34f, -0.14f, 0.14f, 0.34f, 0.14f, CORNER);
            ps.popPose();
            // placas de proa baja con marcas de mantenimiento
            mbox(ps, vc, light, s, 0.9f, 0.3f, 1.9f, 1.05f, 0.9f, 1.91f, MARK);
            mbox(ps, vc, light, s, 1.0f, 1.05f, 1.9f, 1.65f, 1.58f, 1.915f, DENT);
        }
    }

    /** Cuatro bloques laterales de blindaje reemplazables (dos por lado). Si falta uno se ve su soporte. */
    private void drawSideBlocks(PoseStack ps, VertexConsumer vc, int light, int blocks) {
        for (int s = -1; s <= 1; s += 2) {
            for (int u = 0; u < 2; u++) {
                int index = (s > 0 ? 0 : 2) + u;
                float z0 = u == 0 ? 0.05f : -1.75f;
                float z1 = u == 0 ? 1.35f : -0.1f;
                // soporte (siempre presente): marco de rieles y pernos
                mbox(ps, vc, light, s, 1.35f, 0.95f, z0, 1.4f, 1.0f, z1, CORNER);
                mbox(ps, vc, light, s, 1.35f, 1.57f, z0, 1.4f, 1.62f, z1, CORNER);
                mbox(ps, vc, light, s, 1.35f, 0.95f, z0, 1.4f, 1.62f, z0 + 0.05f, CORNER);
                mbox(ps, vc, light, s, 1.35f, 0.95f, z1 - 0.05f, 1.4f, 1.62f, z1, CORNER);
                if ((blocks & (1 << index)) != 0) {
                    mbox(ps, vc, light, s, 1.4f, 0.95f, z0, 1.7f, 1.62f, z1, PLATE);
                    mbox(ps, vc, light, s, 1.7f, 1.0f, z0 + 0.1f, 1.712f, 1.57f, z0 + (z1 - z0) * 0.55f, DENT);
                    mbox(ps, vc, light, s, 1.7f, 1.0f, z0 + (z1 - z0) * 0.58f, 1.712f, 1.57f, z1 - 0.1f, MARK);
                    mbox(ps, vc, light, s, 1.39f, 0.93f, z0 - 0.01f, 1.725f, 1.64f, z0 + 0.06f, CORNER);
                    mbox(ps, vc, light, s, 1.39f, 0.93f, z1 - 0.06f, 1.725f, 1.64f, z1 + 0.01f, CORNER);
                    // asas de izado hundidas y pernos
                    float zm = (z0 + z1) * 0.5f;
                    mbox(ps, vc, light, s, 1.7f, 1.25f, zm - 0.12f, 1.73f, 1.35f, zm + 0.12f, BLACK);
                    for (int b = 0; b < 3; b++) {
                        float z = z0 + 0.18f + b * (z1 - z0 - 0.36f) / 2.0f;
                        mbox(ps, vc, light, s, 1.712f, 1.5f, z - 0.03f, 1.74f, 1.56f, z + 0.03f, BOLT);
                        mbox(ps, vc, light, s, 1.712f, 1.02f, z - 0.03f, 1.74f, 1.08f, z + 0.03f, BOLT);
                    }
                } else {
                    // soporte al descubierto: pared de titanio con los alojamientos de los pernos
                    for (int b = 0; b < 3; b++) {
                        float z = z0 + 0.18f + b * (z1 - z0 - 0.36f) / 2.0f;
                        mbox(ps, vc, light, s, 1.35f, 1.5f, z - 0.04f, 1.355f, 1.56f, z + 0.04f, BLACK);
                        mbox(ps, vc, light, s, 1.35f, 1.02f, z - 0.04f, 1.355f, 1.08f, z + 0.04f, BLACK);
                    }
                }
            }
        }
    }

    /** Placas de la cubierta, escotillas reforzadas y las uniones con el reactor. */
    private void drawTop(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.3f, TOP, 1.02f, 1.3f, TOP + 0.07f, 1.4f, PLATE);
        box(ps, vc, light, -1.3f, TOP, -1.75f, 1.3f, TOP + 0.07f, -1.12f, PLATE);
        box(ps, vc, light, -1.1f, TOP + 0.07f, 1.05f, -0.9f, TOP + 0.075f, 1.37f, MARK);
        // escotillas delanteras reforzadas
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.85f, TOP + 0.07f, 1.08f, 1.2f, TOP + 0.1f, 1.38f, CORNER);
            mbox(ps, vc, light, s, 0.9f, TOP + 0.1f, 1.12f, 1.15f, TOP + 0.12f, 1.34f, PANEL);
            mbox(ps, vc, light, s, 1.0f, TOP + 0.12f, 1.2f, 1.05f, TOP + 0.15f, 1.26f, BOLT);
            for (int c = 0; c < 4; c++) {
                float hx = (c % 2 == 0) ? 0.88f : 1.17f;
                float hz = (c / 2 == 0) ? 1.1f : 1.36f;
                mbox(ps, vc, light, s, hx - 0.02f, TOP + 0.1f, hz - 0.02f, hx + 0.02f, TOP + 0.13f, hz + 0.02f, BOLT);
            }
        }
        // gran escotilla trasera
        box(ps, vc, light, -0.5f, TOP + 0.07f, -1.72f, 0.5f, TOP + 0.1f, -1.45f, CORNER);
        box(ps, vc, light, -0.44f, TOP + 0.1f, -1.68f, 0.44f, TOP + 0.12f, -1.49f, PANEL);
        box(ps, vc, light, -0.1f, TOP + 0.12f, -1.62f, 0.1f, TOP + 0.15f, -1.56f, BOLT);
    }

    /** Pasarelas de mantenimiento con rejilla y barandillas a ambos lados sobre los bloques laterales. */
    private void drawWalkways(PoseStack ps, VertexConsumer vc, int light) {
        float[] posts = {-1.5f, -0.9f, -0.3f, 0.3f, 0.9f, 1.14f};
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.1f, TOP, -1.5f, 1.68f, TOP + 0.035f, 1.2f, VENT);
            mbox(ps, vc, light, s, 1.66f, TOP + 0.035f, -1.5f, 1.68f, TOP + 0.07f, 1.2f, CORNER);
            for (float z : posts) {
                mbox(ps, vc, light, s, 1.62f, TOP + 0.035f, z, 1.68f, TOP + 0.3f, z + 0.06f, BLACK);
            }
            mbox(ps, vc, light, s, 1.62f, TOP + 0.28f, -1.5f, 1.68f, TOP + 0.32f, 1.2f, CORNER);
            mbox(ps, vc, light, s, 1.64f, TOP + 0.15f, -1.5f, 1.66f, TOP + 0.18f, 1.2f, BOLT);
        }
    }

    /** Reactor trasero: líneas ámbar verticales y horizontales, rejilla superior y conductos hacia la torreta. */
    private void drawReactor(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.9f, 0.55f, -2.2f, 0.9f, 1.6f, -1.8f, CORNER);
        box(ps, vc, light, -0.95f, 0.5f, -2.22f, 0.95f, 0.56f, -1.78f, PLATE);
        box(ps, vc, light, -0.95f, 1.58f, -2.22f, 0.95f, 1.64f, -1.78f, PLATE);
        for (int i = 0; i < 7; i++) {
            float x = -0.72f + i * 0.24f;
            box(ps, vc, light, x, 0.7f, -2.212f, x + 0.06f, 1.48f, -2.2f, this.amber);
        }
        for (int s = -1; s <= 1; s += 2) {
            for (int j = 0; j < 3; j++) {
                float y = 0.78f + j * 0.25f;
                mbox(ps, vc, light, s, 0.9f, y, -2.1f, 0.912f, y + 0.04f, -1.9f, this.amber);
            }
            // conductos de refrigeración desde el reactor hacia la torreta
            mbox(ps, vc, light, s, 0.7f, 1.6f, -2.0f, 0.8f, TOP + 0.25f, -1.9f, PIPE);
            mbox(ps, vc, light, s, 0.7f, TOP + 0.15f, -2.0f, 0.8f, TOP + 0.25f, -1.42f, PIPE);
            for (float z : new float[]{-1.95f, -1.7f, -1.5f}) {
                mbox(ps, vc, light, s, 0.67f, TOP + 0.12f, z - 0.03f, 0.83f, TOP + 0.28f, z + 0.03f, CORNER);
            }
        }
        // rejilla superior con luz ámbar debajo
        box(ps, vc, light, -0.6f, 1.64f, -2.15f, 0.6f, 1.652f, -1.85f, this.amber);
        for (int i = 0; i < 4; i++) {
            float z = -2.14f + i * 0.08f;
            box(ps, vc, light, -0.62f, 1.652f, z, 0.62f, 1.67f, z + 0.04f, BLACK);
        }
    }

    // ------------------------------------------------------------------ Torreta elevada y cañón industrial

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash) {
        ps.pushPose();
        ps.translate(0.0, TOP, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Corona de giro y cuerpo enorme con la escotilla de la cabina
        box(ps, vc, light, -1.0f, 0.0f, -1.0f, 1.0f, 0.07f, 1.0f, BLACK);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.95f, 0.0f, -0.95f, 0.95f, 0.07f, 0.95f, BLACK);
        ps.popPose();
        box(ps, vc, light, -1.0f, 0.07f, -1.1f, 1.0f, 0.35f, 1.0f, PLATE);
        box(ps, vc, light, 0.4f, 0.35f, -1.1f, 1.0f, 0.7f, 1.0f, PLATE);
        box(ps, vc, light, -1.0f, 0.35f, -1.1f, -0.4f, 0.7f, 1.0f, PLATE);
        box(ps, vc, light, -0.4f, 0.35f, -1.1f, 0.4f, 0.7f, -0.5f, PLATE);
        box(ps, vc, light, -0.4f, 0.35f, 0.35f, 0.4f, 0.7f, 1.0f, PLATE);
        // Brocal reforzado de la cabina e indicadores blancos alrededor
        box(ps, vc, light, -0.43f, 0.7f, -0.53f, 0.43f, 0.78f, -0.4f, CORNER);
        box(ps, vc, light, -0.43f, 0.7f, 0.3f, 0.43f, 0.78f, 0.38f, CORNER);
        box(ps, vc, light, 0.33f, 0.7f, -0.53f, 0.43f, 0.78f, 0.38f, CORNER);
        box(ps, vc, light, -0.43f, 0.7f, -0.53f, -0.33f, 0.78f, 0.38f, CORNER);
        float[][] dots = {{0.38f, -0.47f}, {-0.38f, -0.47f}, {0.38f, 0.34f}, {-0.38f, 0.34f}, {0.0f, -0.47f},
                {0.0f, 0.34f}, {0.38f, -0.06f}, {-0.38f, -0.06f}};
        for (int i = 0; i < dots.length; i++) {
            int v = (int) (205 + 50 * Mth.sin(this.time * 0.15f + i * 0.8f));
            Mat white = new Mat(new int[]{v, v, v}, T_FLAT, true);
            box(ps, vc, light, dots[i][0] - 0.03f, 0.78f, dots[i][1] - 0.03f,
                    dots[i][0] + 0.03f, 0.82f, dots[i][1] + 0.03f, white);
        }
        // Planchas superiores con marcas, y laterales
        box(ps, vc, light, 0.45f, 0.7f, -1.0f, 0.95f, 0.72f, 0.9f, DENT);
        box(ps, vc, light, -0.95f, 0.7f, -1.0f, -0.45f, 0.72f, 0.9f, MARK);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.0f, 0.07f, -0.8f, 1.08f, 0.6f, 0.7f, PLATE);
            mbox(ps, vc, light, s, 1.08f, 0.15f, -0.65f, 1.092f, 0.52f, 0.55f, DENT);
        }
        // Compartimento trasero de la torreta con conductos
        box(ps, vc, light, -0.8f, 0.07f, -1.4f, 0.8f, 0.8f, -1.1f, PLATE);
        box(ps, vc, light, -0.82f, 0.74f, -1.42f, 0.82f, 0.82f, -1.08f, CORNER);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.55f - 0.05f, 0.1f, -1.5f, s * 0.55f + 0.05f, 0.74f, -1.4f, PIPE);
            box(ps, vc, light, s * 0.55f - 0.07f, 0.3f, -1.52f, s * 0.55f + 0.07f, 0.35f, -1.38f, CORNER);
        }
        box(ps, vc, light, -0.55f, 0.6f, -1.5f, 0.55f, 0.7f, -1.4f, PIPE);
        // Mantelete frontal
        box(ps, vc, light, -0.75f, 0.07f, 0.95f, 0.75f, 0.75f, 1.4f, PLATE);
        box(ps, vc, light, -0.77f, 0.7f, 0.93f, 0.77f, 0.78f, 1.4f, CORNER);
        box(ps, vc, light, -0.7f, 0.12f, 1.4f, -0.34f, 0.65f, 1.412f, DENT);
        box(ps, vc, light, 0.34f, 0.12f, 1.4f, 0.7f, 0.65f, 1.412f, MARK);

        // Cilindros hidráulicos visibles (de la torreta a la cuna del cañón)
        float rad = pitch * Mth.DEG_TO_RAD;
        float ay = 0.45f + 0.3f * Mth.cos(rad) - 0.5f * Mth.sin(rad);
        float az = 1.0f + 0.3f * Mth.sin(rad) + 0.5f * Mth.cos(rad);
        for (int s = -1; s <= 1; s += 2) {
            cylinder(ps, vc, light, s * 0.5f, 0.78f, 0.6f, ay, az);
        }

        // Cañón principal: eje a (0, 0.45, 1.0) de la torreta
        ps.translate(0.0, 0.45, 1.0);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0.0, 0.0, -recoil);
        drawCannon(ps, vc, light, flash);
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
        box(ps, vc, light, -0.06f, -0.06f, 0.0f, 0.06f, 0.06f, body, PLATE);
        box(ps, vc, light, -0.08f, -0.08f, 0.04f, 0.08f, 0.08f, 0.1f, CORNER);
        box(ps, vc, light, -0.08f, -0.08f, body - 0.1f, 0.08f, 0.08f, body - 0.04f, CORNER);
        box(ps, vc, light, -0.035f, -0.035f, body, 0.035f, 0.035f, len, BOLT);
        box(ps, vc, light, -0.08f, -0.08f, len - 0.04f, 0.08f, 0.08f, len, BLACK);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash) {
        // Recámara, cuna y apoyos de los cilindros
        box(ps, vc, light, -0.32f, -0.32f, -0.5f, 0.32f, 0.32f, 0.15f, CORNER);
        box(ps, vc, light, -0.28f, -0.28f, 0.15f, 0.28f, 0.28f, 0.8f, TITAN);
        box(ps, vc, light, -0.22f, 0.32f, -0.4f, 0.22f, 0.34f, 0.05f, MARK);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.28f, 0.24f, 0.44f, 0.5f, 0.36f, 0.56f, CORNER);
        }
        // Tubo y anillos mecánicos
        box(ps, vc, light, -0.17f, -0.17f, 0.15f, 0.17f, 0.17f, 2.55f, BLACK);
        for (int i = 0; i < 6; i++) {
            float z = 0.85f + i * 0.28f;
            box(ps, vc, light, -0.3f, -0.3f, z, 0.3f, 0.3f, z + 0.12f, i % 2 == 0 ? CORNER : TITAN);
            box(ps, vc, light, -0.32f, -0.32f, z + 0.04f, 0.32f, 0.32f, z + 0.08f, BLACK);
        }
        // Conductos de refrigeración a lo largo del cañón
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float px = sx * 0.22f;
                float py = sy * 0.22f;
                box(ps, vc, light, px - 0.03f, py - 0.03f, 0.8f, px + 0.03f, py + 0.03f, 2.35f, PIPE);
            }
        }
        // Freno de boca industrial
        box(ps, vc, light, -0.34f, -0.34f, 2.3f, 0.34f, 0.34f, 2.65f, PLATE);
        box(ps, vc, light, -0.37f, -0.37f, 2.27f, 0.37f, 0.37f, 2.34f, CORNER);
        box(ps, vc, light, -0.2f, 0.34f, 2.4f, 0.2f, 0.36f, 2.58f, BLACK);
        box(ps, vc, light, -0.2f, -0.36f, 2.4f, 0.2f, -0.34f, 2.58f, BLACK);
        box(ps, vc, light, 0.34f, -0.2f, 2.4f, 0.36f, 0.2f, 2.58f, BLACK);
        box(ps, vc, light, -0.36f, -0.2f, 2.4f, -0.34f, 0.2f, 2.58f, BLACK);
        if (flash > 0.0f) {
            float f = 0.14f + 0.2f * flash;
            box(ps, vc, light, -f, -f, 2.65f, f, f, 2.65f + 0.28f * flash, new Mat(new int[]{255, 225, 150}, T_FLAT, true));
        }
    }

    // ------------------------------------------------------------------ Luz ámbar

    private static Mat amberMat(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{255, (int) (110 + 70 * k), (int) (10 + 40 * k * k)}, T_FLAT, true);
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
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, false);
    }

    /** Igual que box, pero cada cara usa la casilla de textura completa (para el emblema). */
    private static void boxFull(PoseStack ps, VertexConsumer vc, int light,
                                float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, true);
    }

    private static void boxImpl(PoseStack ps, VertexConsumer vc, int light,
                                float x1, float y1, float z1, float x2, float y2, float z2, Mat m, boolean full) {
        PoseStack.Pose pose = ps.last();
        int l = m.glow() ? LightTexture.FULL_BRIGHT : light;
        float xl = full ? 1.0f : x2 - x1;
        float yl = full ? 1.0f : y2 - y1;
        float zl = full ? 1.0f : z2 - z1;
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
