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
 * Dibuja el «Leviatán Mk II»: casco larguísimo dividido en secciones (frontal escalonado, cubierta central con la
 * torreta de asedio y sección trasera elevada con el reactor), placas hexagonales y rectangulares con módulos
 * laterales reemplazables, seis conjuntos de orugas independientes (tres por lado), conductos ámbar que recorren el
 * casco del reactor a la torreta, escaleras, plataformas con barandillas, antenas, sensores laterales, focos blancos y
 * un cañón electromagnético de raíles cuyas bobinas se iluminan según la carga del núcleo de asedio.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -3.5 a 3.5 y X ±1.3.
 * El eje del cañón (pivotY 1.9, pivotForward 1.45, largo 3.2) debe coincidir con LeviathanTankEntity.
 */
public class LeviathanTankRenderer extends EntityRenderer<LeviathanTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_leviatan.png");

    private static final int T_PLATE = 0, T_TITAN = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_DENT = 8, T_EMBLEM = 9, T_CORNER = 10, T_MARK = 11, T_PIPE = 12,
            T_HEX = 13;

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
    private static final Mat CORNER = new Mat(W, T_CORNER);
    private static final Mat MARK = new Mat(W, T_MARK);
    private static final Mat PIPE = new Mat(W, T_PIPE);
    private static final Mat HEX = new Mat(W, T_HEX);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 132}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{12, 12, 15}, T_FLAT);
    private static final Mat LAMP = new Mat(new int[]{255, 255, 238}, T_FLAT, true);

    private static final float WHEEL_Y = 0.38f;
    private static final float WHEEL_X = 1.35f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;

    private static final float TOP = 1.4f;          // cubierta principal
    private static final float REAR_TOP = 2.3f;     // cubierta de la sección trasera elevada
    private static final float TZ = 0.3f;           // centro de la torreta en Z

    private float time;
    private float heat;      // 0..1, intensidad de la luz ámbar
    private boolean hot;     // sobrecalentado

    public LeviathanTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 3.0f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(LeviathanTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(LeviathanTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(9.0, 4.0, 9.0));
    }

    @Override
    public void render(LeviathanTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        float charge = Mth.clamp(tank.getCharge() / (float) LeviathanTankEntity.CHARGE_MAX, 0.0f, 1.0f);
        this.hot = tank.isOverheated();
        this.heat = this.hot ? 0.15f : 0.55f + 0.25f * charge;
        float since = tank.ticksSinceShot(partialTick);
        float sinceSiege = tank.ticksSinceSiege(partialTick);
        boolean siege = sinceSiege < 40.0f;
        float recoil;
        float flash;
        if (siege) {
            recoil = sinceSiege < 3.0f ? 0.6f * sinceSiege / 3.0f : 0.6f * Math.max(0.0f, 1.0f - (sinceSiege - 3.0f) / 30.0f);
            flash = sinceSiege < 8.0f ? 1.0f - sinceSiege / 8.0f : 0.0f;
        } else {
            recoil = since < 16.0f ? 0.3f * (since < 2.5f ? since / 2.5f : 1.0f - (since - 2.5f) / 13.5f) : 0.0f;
            flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;
        }

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash, siege, charge);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Seis orugas independientes

    private static final float[] UNITS = {2.55f, 0.0f, -2.55f};

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            float angle = s > 0 ? angleL : angleR;
            for (float zc : UNITS) {
                trackUnit(ps, vc, light, s, zc, angle);
            }
            // eslabones articulados entre conjuntos
            for (int g = 0; g < 2; g++) {
                float za = UNITS[g + 1] + 0.78f;
                float zb = UNITS[g] - 0.78f;
                mbox(ps, vc, light, s, 1.1f, 0.3f, za, 1.6f, 0.46f, zb, BLACK);
                mbox(ps, vc, light, s, 1.1f, 0.46f, za + 0.05f, 1.6f, 0.5f, zb - 0.05f, CORNER);
            }
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

    // ------------------------------------------------------------------ Casco por secciones

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Casco bajo continuo
        box(ps, vc, light, -1.08f, 0.18f, -3.4f, 1.08f, 0.95f, 3.4f, PLATE);
        box(ps, vc, light, -1.0f, 0.1f, -3.3f, 1.0f, 0.18f, 3.3f, BLACK);
        // Cubierta central
        box(ps, vc, light, -1.3f, 0.9f, -1.9f, 1.3f, TOP, 1.7f, TITAN);
        // Cubierta de combate: placas de la superficie
        box(ps, vc, light, -1.28f, TOP, -1.85f, 1.28f, TOP + 0.05f, -0.9f, HEX);
        box(ps, vc, light, -1.28f, TOP, 1.5f, 1.28f, TOP + 0.05f, 1.68f, PLATE);
        box(ps, vc, light, -1.3f, TOP + 0.05f, 1.45f, 1.3f, TOP + 0.09f, 1.72f, CORNER);
        drawFront(ps, vc, light);
        drawRear(ps, vc, light);
        drawModules(ps, vc, light);
        drawConduits(ps, vc, light);
        drawWalkways(ps, vc, light);
        drawLamps(ps, vc, light);
    }

    /** Frontal escalonado: dos escalones con glacis de placas hexagonales y cubiertas gruesas. */
    private void drawFront(PoseStack ps, VertexConsumer vc, int light) {
        // escalón A (z 1.7..2.6) y escalón B (z 2.6..3.5)
        box(ps, vc, light, -1.22f, 0.9f, 1.7f, 1.22f, 1.2f, 2.6f, PLATE);
        box(ps, vc, light, -1.1f, 0.85f, 2.6f, 1.1f, 1.0f, 3.5f, PLATE);
        box(ps, vc, light, -1.24f, 1.2f, 1.7f, 1.24f, 1.26f, 2.62f, HEX);
        box(ps, vc, light, -1.12f, 1.0f, 2.6f, 1.12f, 1.06f, 3.5f, HEX);
        // contrahuellas y cubierta gruesa de la proa
        box(ps, vc, light, -1.14f, 0.85f, 3.5f, 1.14f, 1.1f, 3.56f, CORNER);
        box(ps, vc, light, -1.26f, 1.26f, 1.68f, 1.26f, 1.3f, 1.8f, CORNER);
        box(ps, vc, light, -1.14f, 1.06f, 2.58f, 1.14f, 1.1f, 2.7f, CORNER);
        // placas verticales inclinadas (glacis) en las caras de los escalones
        slab(ps, vc, light, 0.0f, 1.2f, 2.62f, -40.0f, 1.2f, 0.07f, 0.0f, 0.0f, PLATE);
        slab(ps, vc, light, 0.0f, 0.95f, 3.5f, -35.0f, 1.08f, 0.07f, 0.0f, 0.2f, PLATE);
        // rejillas y marcas
        box(ps, vc, light, -0.85f, 0.35f, 3.4f, -0.25f, 0.8f, 3.41f, VENT);
        box(ps, vc, light, 0.25f, 0.35f, 3.4f, 0.85f, 0.8f, 3.41f, VENT);
        box(ps, vc, light, -0.2f, 0.4f, 3.4f, 0.2f, 0.75f, 3.41f, MARK);
        // escotillas del escalón A
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.5f, 1.26f, 1.95f, 0.95f, 1.29f, 2.4f, CORNER);
            mbox(ps, vc, light, s, 0.55f, 1.29f, 2.0f, 0.9f, 1.31f, 2.35f, PANEL);
            mbox(ps, vc, light, s, 0.68f, 1.31f, 2.12f, 0.76f, 1.34f, 2.2f, BOLT);
        }
    }

    /** Sección trasera elevada con el reactor ficticio. */
    private void drawRear(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.3f, 0.9f, -3.5f, 1.3f, REAR_TOP, -1.9f, TITAN);
        box(ps, vc, light, -1.32f, REAR_TOP, -3.52f, 1.32f, REAR_TOP + 0.07f, -1.88f, HEX);
        box(ps, vc, light, -1.34f, REAR_TOP + 0.07f, -3.54f, 1.34f, REAR_TOP + 0.11f, -1.86f, CORNER);
        // cara trasera con rejillas de refrigeración y cubierta gruesa
        box(ps, vc, light, -1.2f, 1.1f, -3.52f, 1.2f, 2.2f, -3.5f, PLATE);
        for (int i = 0; i < 2; i++) {
            float x0 = -1.1f + i * 1.15f;
            box(ps, vc, light, x0, 1.2f, -3.53f, x0 + 1.05f, 2.1f, -3.51f, VENT);
        }
        // reactor: núcleo elevado con líneas ámbar
        box(ps, vc, light, -0.75f, REAR_TOP + 0.11f, -3.25f, 0.75f, REAR_TOP + 0.65f, -2.15f, PLATE);
        box(ps, vc, light, -0.8f, REAR_TOP + 0.6f, -3.3f, 0.8f, REAR_TOP + 0.68f, -2.1f, CORNER);
        box(ps, vc, light, -0.8f, REAR_TOP + 0.11f, -3.3f, 0.8f, REAR_TOP + 0.17f, -2.1f, CORNER);
        Mat amber = amberAt(0.0f);
        for (int i = 0; i < 6; i++) {
            float x = -0.6f + i * 0.24f;
            box(ps, vc, light, x, REAR_TOP + 0.2f, -3.262f, x + 0.07f, REAR_TOP + 0.58f, -3.25f, amber);
            box(ps, vc, light, x, REAR_TOP + 0.2f, -2.15f, x + 0.07f, REAR_TOP + 0.58f, -2.138f, amber);
        }
        for (int s = -1; s <= 1; s += 2) {
            for (int j = 0; j < 3; j++) {
                float y = REAR_TOP + 0.22f + j * 0.14f;
                mbox(ps, vc, light, s, 0.75f, y, -3.0f, 0.762f, y + 0.05f, -2.4f, amberAt(j * 0.4f));
            }
        }
        // radiadores en la cubierta trasera (se abren si está sobrecalentado)
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 1.05f, REAR_TOP + 0.11f, -2.7f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * (this.hot ? -50.0f : -4.0f)));
            box(ps, vc, light, -0.28f, 0.0f, -0.55f, 0.28f, 0.05f, 0.55f, VENT);
            ps.popPose();
        }
        // escaleras laterales desde la cubierta a la sección trasera
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 6; i++) {
                float z0 = -1.9f + 0.15f * (5 - i);
                mbox(ps, vc, light, s, 0.85f, TOP, z0, 1.2f, TOP + 0.15f * (i + 1), z0 + 0.15f, i % 2 == 0 ? VENT : CORNER);
            }
            mbox(ps, vc, light, s, 1.18f, TOP + 0.1f, -1.9f, 1.22f, REAR_TOP + 0.3f, -1.86f, BLACK);
            mbox(ps, vc, light, s, 0.84f, TOP + 0.1f, -1.9f, 0.88f, REAR_TOP + 0.3f, -1.86f, BLACK);
        }
        // plataforma de mantenimiento a media altura de la sección trasera (ambos lados)
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.3f, 1.9f, -3.4f, 1.72f, 1.94f, -2.0f, VENT);
            mbox(ps, vc, light, s, 1.7f, 1.94f, -3.4f, 1.72f, 2.3f, -2.0f, BLACK);
            mbox(ps, vc, light, s, 1.7f, 2.26f, -3.4f, 1.74f, 2.3f, -2.0f, CORNER);
            for (float z : new float[]{-3.4f, -2.9f, -2.4f, -2.0f}) {
                mbox(ps, vc, light, s, 1.68f, 1.94f, z, 1.74f, 2.28f, z + 0.05f, BLACK);
            }
        }
        // antenas de comunicación
        antenna(ps, vc, light, -1.0f, -3.2f, 1.1f, 0.0f);
        antenna(ps, vc, light, 1.0f, -3.2f, 0.8f, 0.0f);
        antenna(ps, vc, light, 0.0f, -3.35f, 0.55f, 0.0f);
        antenna(ps, vc, light, 1.15f, -2.2f, 0.9f, 22.0f);
        // plato de comunicaciones
        box(ps, vc, light, -1.15f, REAR_TOP + 0.11f, -2.2f, -0.95f, REAR_TOP + 0.4f, -2.05f, CORNER);
        ps.pushPose();
        ps.translate(-1.05f, REAR_TOP + 0.55f, -2.12f);
        ps.mulPose(Axis.YP.rotationDegrees(this.time * 1.2f));
        box(ps, vc, light, -0.22f, -0.15f, -0.02f, 0.22f, 0.15f, 0.02f, HUB);
        box(ps, vc, light, -0.02f, -0.02f, 0.0f, 0.02f, 0.02f, 0.15f, BLACK);
        ps.popPose();
    }

    private void antenna(PoseStack ps, VertexConsumer vc, int light, float x, float z, float len, float tilt) {
        ps.pushPose();
        ps.translate(x, REAR_TOP + 0.11f, z);
        ps.mulPose(Axis.ZP.rotationDegrees(tilt));
        box(ps, vc, light, -0.045f, 0.0f, -0.045f, 0.045f, 0.08f, 0.045f, CORNER);
        box(ps, vc, light, -0.02f, 0.08f, -0.02f, 0.02f, len, 0.02f, BLACK);
        int v = (int) (200 + 55 * Mth.sin(this.time * 0.2f + x * 3.0f));
        box(ps, vc, light, -0.03f, len, -0.03f, 0.03f, len + 0.06f, 0.03f,
                new Mat(new int[]{v, v, v}, T_FLAT, true));
        ps.popPose();
    }

    /** Módulos laterales reemplazables sobre una ranura de rieles; uno de ellos es una pieza nueva. */
    private void drawModules(PoseStack ps, VertexConsumer vc, int light) {
        float[][] slots = {{-1.8f, -0.7f}, {-0.6f, 0.7f}, {0.8f, 1.65f}};
        for (int s = -1; s <= 1; s += 2) {
            for (int u = 0; u < slots.length; u++) {
                float z0 = slots[u][0];
                float z1 = slots[u][1];
                mbox(ps, vc, light, s, 1.3f, 0.95f, z0, 1.35f, 1.0f, z1, CORNER);
                mbox(ps, vc, light, s, 1.3f, 1.35f, z0, 1.35f, 1.4f, z1, CORNER);
                boolean fresh = s > 0 && u == 1;           // un módulo recién sustituido
                mbox(ps, vc, light, s, 1.35f, 0.98f, z0 + 0.02f, 1.55f, 1.38f, z1 - 0.02f, fresh ? PLATE : HEX);
                mbox(ps, vc, light, s, 1.55f, 1.02f, z0 + 0.1f, 1.565f, 1.34f, z1 - 0.1f, fresh ? MARK : DENT);
                mbox(ps, vc, light, s, 1.34f, 0.96f, z0, 1.57f, 1.4f, z0 + 0.05f, CORNER);
                mbox(ps, vc, light, s, 1.34f, 0.96f, z1 - 0.05f, 1.57f, 1.4f, z1, CORNER);
                float zm = (z0 + z1) * 0.5f;
                mbox(ps, vc, light, s, 1.565f, 1.14f, zm - 0.1f, 1.6f, 1.22f, zm + 0.1f, BLACK);    // asa de izado
                mbox(ps, vc, light, s, 1.565f, 1.3f, z0 + 0.12f, 1.59f, 1.34f, z0 + 0.18f, BOLT);
                mbox(ps, vc, light, s, 1.565f, 1.3f, z1 - 0.18f, 1.59f, 1.34f, z1 - 0.12f, BOLT);
            }
            // paneles hexagonales en la sección trasera
            mbox(ps, vc, light, s, 1.3f, 1.0f, -3.3f, 1.42f, 1.85f, -2.7f, HEX);
            mbox(ps, vc, light, s, 1.3f, 1.0f, -2.6f, 1.42f, 1.85f, -2.0f, DENT);
        }
    }

    /** Conductos ámbar del reactor a la torreta y a lo largo de los costados, con pulso viajando hacia delante. */
    private void drawConduits(PoseStack ps, VertexConsumer vc, int light) {
        for (int s = -1; s <= 1; s += 2) {
            // del reactor sube por la cubierta hasta el anillo de la torreta
            mbox(ps, vc, light, s, 0.55f, REAR_TOP + 0.11f, -2.2f, 0.67f, REAR_TOP + 0.23f, -2.0f, PIPE);
            // bajada de la sección trasera a la cubierta central
            mbox(ps, vc, light, s, 0.55f, TOP, -2.0f, 0.67f, REAR_TOP + 0.23f, -1.88f, PIPE);
            // tramo de cubierta hacia la torreta (segmentos iluminados con pulso)
            for (int i = 0; i < 6; i++) {
                float z = -1.85f + i * 0.2f;
                mbox(ps, vc, light, s, 0.55f, TOP + 0.05f, z, 0.67f, TOP + 0.13f, z + 0.17f,
                        amberAt(-z * 2.5f));
            }
            // refuerzos cada tanto sobre el conducto
            for (float z : new float[]{-1.8f, -1.3f}) {
                mbox(ps, vc, light, s, 0.52f, TOP + 0.05f, z, 0.7f, TOP + 0.17f, z + 0.05f, CORNER);
            }
            // conducto lateral entre módulos
            for (int i = 0; i < 12; i++) {
                float z = -1.7f + i * 0.28f;
                mbox(ps, vc, light, s, 1.3f, 0.92f, z, 1.36f, 0.97f, z + 0.22f, amberAt(-z * 2.0f));
            }
        }
    }

    /** Pasarelas de mantenimiento con rejilla y barandillas sobre los laterales de la cubierta. */
    private void drawWalkways(PoseStack ps, VertexConsumer vc, int light) {
        float[] posts = {-1.2f, -0.5f, 0.2f, 0.9f, 1.55f};
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.3f, TOP, -1.2f, 1.7f, TOP + 0.035f, 1.6f, VENT);
            mbox(ps, vc, light, s, 1.68f, TOP + 0.035f, -1.2f, 1.7f, TOP + 0.07f, 1.6f, CORNER);
            for (float z : posts) {
                mbox(ps, vc, light, s, 1.64f, TOP + 0.035f, z, 1.7f, TOP + 0.3f, z + 0.06f, BLACK);
            }
            mbox(ps, vc, light, s, 1.64f, TOP + 0.28f, -1.2f, 1.7f, TOP + 0.32f, 1.6f, CORNER);
            mbox(ps, vc, light, s, 1.66f, TOP + 0.15f, -1.2f, 1.68f, TOP + 0.18f, 1.6f, BOLT);
        }
    }

    /** Focos blancos de gran intensidad: proa, escalón A, torreta y sección trasera. */
    private void drawLamps(PoseStack ps, VertexConsumer vc, int light) {
        for (int s = -1; s <= 1; s += 2) {
            lamp(ps, vc, light, s * 0.9f, 1.06f, 3.3f, 0.2f);
            lamp(ps, vc, light, s * 1.1f, 1.26f, 2.1f, 0.22f);
            lamp(ps, vc, light, s * 1.15f, REAR_TOP + 0.11f, -1.95f, 0.2f);
        }
    }

    private void lamp(PoseStack ps, VertexConsumer vc, int light, float x, float y, float z, float size) {
        float h = size * 0.5f;
        box(ps, vc, light, x - h - 0.04f, y, z - h - 0.04f, x + h + 0.04f, y + 0.05f, z + h + 0.04f, BLACK);
        box(ps, vc, light, x - h - 0.03f, y + 0.05f, z - h - 0.03f, x - h, y + size * 0.9f, z + h + 0.03f, CORNER);
        box(ps, vc, light, x + h, y + 0.05f, z - h - 0.03f, x + h + 0.03f, y + size * 0.9f, z + h + 0.03f, CORNER);
        box(ps, vc, light, x - h, y + 0.05f, z - h - 0.03f, x + h, y + size * 0.9f, z - h, CORNER);
        box(ps, vc, light, x - h, y + 0.05f, z + h, x + h, y + size * 0.9f, z + h + 0.03f, LAMP);
        box(ps, vc, light, x - h, y + size * 0.9f, z - h - 0.03f, x + h, y + size * 0.9f + 0.03f, z + h + 0.03f, CORNER);
    }

    // ------------------------------------------------------------------ Torreta de asedio y cañón electromagnético

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash, boolean siege, float charge) {
        ps.pushPose();
        ps.translate(0.0, TOP, TZ);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Corona de giro y plataforma de asedio
        box(ps, vc, light, -1.15f, 0.0f, -1.15f, 1.15f, 0.07f, 1.15f, BLACK);
        box(ps, vc, light, -1.25f, 0.07f, -1.3f, 1.25f, 0.33f, 1.25f, HEX);
        box(ps, vc, light, -1.27f, 0.3f, -1.32f, 1.27f, 0.36f, 1.27f, CORNER);
        // Cuerpo central con cubiertas gruesas
        box(ps, vc, light, -0.9f, 0.36f, -1.05f, 0.9f, 0.75f, 1.0f, PLATE);
        box(ps, vc, light, -0.92f, 0.7f, -1.07f, 0.92f, 0.78f, 1.02f, CORNER);
        box(ps, vc, light, -0.55f, 0.78f, -0.3f, 0.55f, 0.84f, 0.8f, TITAN);
        box(ps, vc, light, -0.5f, 0.78f, -0.95f, 0.5f, 0.82f, -0.35f, DENT);
        // Cabina del conductor (atrás) con escotilla y brocal
        box(ps, vc, light, -0.45f, 0.78f, -1.0f, 0.45f, 0.86f, -0.55f, CORNER);
        box(ps, vc, light, -0.35f, 0.86f, -0.95f, 0.35f, 0.88f, -0.6f, PANEL);
        // Mantelete frontal
        box(ps, vc, light, -0.8f, 0.36f, 1.0f, 0.8f, 0.78f, 1.3f, PLATE);
        box(ps, vc, light, -0.82f, 0.74f, 0.98f, 0.82f, 0.8f, 1.3f, CORNER);
        // Radiadores laterales (se abren como alas al sobrecalentarse)
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.9f, 0.7f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * (this.hot ? -55.0f : -5.0f)));
            box(ps, vc, light, -0.04f, -0.34f, -0.7f, 0.04f, 0.0f, 0.7f, VENT);
            ps.popPose();
        }
        // Sensores montados en los laterales: mástil, cabezal con lentes y plato giratorio
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.0f, 0.33f, -0.9f, 1.2f, 0.6f, -0.6f, PLATE);
            mbox(ps, vc, light, s, 1.05f, 0.6f, -0.85f, 1.15f, 0.95f, -0.65f, CORNER);
            mbox(ps, vc, light, s, 1.0f, 0.9f, -0.95f, 1.22f, 1.12f, -0.55f, TITAN);
            mbox(ps, vc, light, s, 1.0f, 0.95f, -0.56f, 1.22f, 1.07f, -0.54f, BLACK);
            mbox(ps, vc, light, s, 1.08f, 0.97f, -0.55f, 1.14f, 1.03f, -0.53f, LAMP);
            mbox(ps, vc, light, s, 1.0f, 0.33f, 0.2f, 1.2f, 0.55f, 0.55f, PLATE);
            ps.pushPose();
            ps.translate(s * 1.1f, 0.8f, 0.38f);
            ps.mulPose(Axis.YP.rotationDegrees(this.time * 2.0f * s));
            box(ps, vc, light, -0.2f, -0.02f, -0.02f, 0.2f, 0.12f, 0.02f, HUB);
            box(ps, vc, light, -0.03f, -0.1f, -0.03f, 0.03f, 0.0f, 0.03f, BLACK);
            ps.popPose();
        }
        // Focos de la torreta
        lamp(ps, vc, light, -0.65f, 0.78f, 0.6f, 0.2f);
        lamp(ps, vc, light, 0.65f, 0.78f, 0.6f, 0.2f);
        // Cilindros hidráulicos de la torreta a la cuna del cañón
        float rad = pitch * Mth.DEG_TO_RAD;
        float ay = 0.5f + 0.3f * Mth.cos(rad) - 0.55f * Mth.sin(rad);
        float az = 1.15f + 0.3f * Mth.sin(rad) + 0.55f * Mth.cos(rad);
        for (int s = -1; s <= 1; s += 2) {
            cylinder(ps, vc, light, s * 0.55f, 0.78f, 0.65f, ay, az);
        }

        // Cañón electromagnético: eje a (0, 0.5, 1.15) de la torreta
        ps.translate(0.0, 0.5, 1.15);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0.0, 0.0, -recoil);
        drawCannon(ps, vc, light, flash, siege, charge);
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

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash, boolean siege, float charge) {
        // Recámara y cuna
        box(ps, vc, light, -0.38f, -0.36f, -0.55f, 0.38f, 0.36f, 0.2f, CORNER);
        box(ps, vc, light, -0.3f, -0.3f, 0.2f, 0.3f, 0.3f, 0.7f, TITAN);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.3f, 0.26f, 0.45f, 0.52f, 0.38f, 0.57f, CORNER);
        }
        // Dos raíles paralelos (superior e inferior) con brillo según la carga
        float k = this.hot ? 0.0f : charge;
        Mat rail = new Mat(new int[]{(int) (20 + 90 * k), (int) (40 + 170 * k), (int) (60 + 195 * k)}, T_FLAT, k > 0.05f);
        for (int sy = -1; sy <= 1; sy += 2) {
            float y0 = sy > 0 ? 0.12f : -0.3f;
            box(ps, vc, light, -0.2f, y0, 0.25f, 0.2f, y0 + 0.18f, 3.2f, BLACK);
            box(ps, vc, light, -0.1f, sy > 0 ? y0 + 0.18f : y0 - 0.012f, 0.4f,
                    0.1f, sy > 0 ? y0 + 0.19f : y0, 3.1f, rail);
        }
        // Bobinas electromagnéticas: se iluminan una a una al subir la carga
        int coils = 8;
        for (int i = 0; i < coils; i++) {
            float z = 0.8f + i * 0.3f;
            boolean lit = !this.hot && charge >= (i + 1) / (float) coils;
            box(ps, vc, light, -0.36f, -0.36f, z, 0.36f, 0.36f, z + 0.14f, i % 2 == 0 ? CORNER : TITAN);
            Mat glow = lit ? new Mat(new int[]{120, 215, 255}, T_FLAT, true) : BLACK;
            box(ps, vc, light, -0.38f, -0.38f, z + 0.04f, 0.38f, 0.38f, z + 0.1f, glow);
        }
        // Conductos de energía ámbar a lo largo del cañón
        for (int sx = -1; sx <= 1; sx += 2) {
            box(ps, vc, light, sx * 0.26f - 0.03f, -0.03f, 0.7f, sx * 0.26f + 0.03f, 0.03f, 3.0f, PIPE);
        }
        // Boca: refuerzo y deflector
        box(ps, vc, light, -0.4f, -0.4f, 3.0f, 0.4f, 0.4f, 3.2f, PLATE);
        box(ps, vc, light, -0.43f, -0.43f, 2.96f, 0.43f, 0.43f, 3.04f, CORNER);
        box(ps, vc, light, -0.25f, -0.28f, 3.2f, 0.25f, -0.22f, 3.3f, BLACK);
        box(ps, vc, light, -0.25f, 0.22f, 3.2f, 0.25f, 0.28f, 3.3f, BLACK);
        if (flash > 0.0f) {
            float f = (siege ? 0.3f : 0.14f) + (siege ? 0.45f : 0.2f) * flash;
            float len = (siege ? 0.9f : 0.3f) * flash;
            box(ps, vc, light, -f, -f, 3.3f, f, f, 3.3f + len, new Mat(new int[]{190, 235, 255}, T_FLAT, true));
            if (siege) {
                float g = f * 0.5f;
                box(ps, vc, light, -g, -g, 3.3f, g, g, 3.3f + len * 1.6f, LAMP);
            }
        }
    }

    // ------------------------------------------------------------------ Luz ámbar

    /** Material ámbar; phase desplaza el pulso para que la energía parezca viajar por los conductos. */
    private Mat amberAt(float phase) {
        float k = Mth.clamp(this.heat + 0.25f * Mth.sin(this.time * 0.18f - phase * 2.0f), 0.0f, 1.0f);
        if (this.hot) {
            return new Mat(new int[]{(int) (90 + 80 * k), (int) (25 + 25 * k), 8}, T_FLAT, true);
        }
        return new Mat(new int[]{255, (int) (100 + 90 * k), (int) (10 + 40 * k * k)}, T_FLAT, true);
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
