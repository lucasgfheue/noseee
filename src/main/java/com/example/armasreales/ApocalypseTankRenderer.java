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
 * Dibuja el «Apocalipsis»: perfil escalonado (proa con el emblema X, cubierta principal, torre del reactor elevada y
 * escalón trasero con enormes rejillas térmicas), seis módulos de orugas independientes (tres por lado) unidos por
 * eslabones reforzados y cubiertos en parte por faldones, refuerzos rojos en las uniones, marcas de advertencia,
 * pasarelas, escaleras, conductos visibles, focos blancos, reactor central naranja y violeta protegido por arcos
 * metálicos y cubiertas móviles, y una torreta monumental con cañón de energía, corona de estabilización y dos
 * módulos secundarios. Con el Protocolo Apocalipsis las líneas luminosas recorren el casco en secuencia.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -3.8 a 3.8 y X ±1.75 (faldones a 2.08).
 * La torreta está a Z 0.3 (el eje del cañón: pivotY 2.2, pivotForward 1.2, largo 4.0) y debe coincidir con
 * ApocalypseTankEntity.
 */
public class ApocalypseTankRenderer extends EntityRenderer<ApocalypseTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_apocalipsis.png");

    private static final int T_PLATE = 0, T_TITAN = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_DENT = 8, T_EMBLEM = 9, T_CORNER = 10, T_MARK = 11, T_PIPE = 12,
            T_RED = 13, T_HAZARD = 14, T_CERAMIC = 15;

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
    private static final Mat RED = new Mat(W, T_RED);
    private static final Mat HAZARD = new Mat(W, T_HAZARD);
    private static final Mat CERAMIC = new Mat(W, T_CERAMIC);
    private static final Mat EMBLEM = new Mat(W, T_EMBLEM);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 132}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{12, 12, 15}, T_FLAT);
    private static final Mat LAMP = new Mat(new int[]{255, 255, 238}, T_FLAT, true);
    private static final Mat WHITE = new Mat(new int[]{255, 255, 255}, T_FLAT, true);

    private static final float WHEEL_Y = 0.38f;
    private static final float WHEEL_X = 1.73f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;
    private static final float[] UNITS = {2.6f, 0.0f, -2.6f};

    private static final float DECK = 1.5f;          // cubierta principal
    private static final float TOWER = 2.6f;         // techo de la torre del reactor
    private static final float TZ = 0.3f;            // centro de la torreta en Z
    private static final float RZ = -2.65f;          // centro del reactor en Z
    private static final float RY = 2.15f;           // altura del núcleo

    private float time;
    private int mode;          // 0 normal, 1 protocolo, 2 enfriamiento
    private float charge;      // 0..1
    private float open;        // 0..1 cubiertas del reactor

    public ApocalypseTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 3.2f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(ApocalypseTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ApocalypseTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(12.0, 5.0, 12.0));
    }

    @Override
    public void render(ApocalypseTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        this.charge = Mth.clamp(tank.getCharge() / (float) ApocalypseTankEntity.CHARGE_MAX, 0.0f, 1.0f);
        this.mode = tank.isActive() ? 1 : (tank.isCooling() ? 2 : 0);
        this.open = tank.openFraction(partialTick);
        float since = tank.ticksSinceShot(partialTick);
        float big = this.mode == 1 ? 1.0f : 0.0f;
        float recoil = since < 16.0f ? (0.42f - 0.12f * big) * (since < 2.5f ? since / 2.5f : 1.0f - (since - 2.5f) / 13.5f) : 0.0f;
        float flash = since < 6.0f ? 1.0f - since / 6.0f : 0.0f;

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);
        drawReactor(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Seis módulos de orugas independientes

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            float angle = s > 0 ? angleL : angleR;
            for (float zc : UNITS) {
                trackUnit(ps, vc, light, s, zc, angle);
            }
            // estructuras reforzadas que unen los módulos
            for (int g = 0; g < 2; g++) {
                float za = UNITS[g + 1] + 0.78f;
                float zb = UNITS[g] - 0.78f;
                mbox(ps, vc, light, s, 1.5f, 0.28f, za, 2.0f, 0.5f, zb, BLACK);
                mbox(ps, vc, light, s, 1.46f, 0.5f, za + 0.04f, 2.04f, 0.56f, zb - 0.04f, CORNER);
                mbox(ps, vc, light, s, 1.4f, 0.52f, za + 0.08f, 1.5f, 0.9f, zb - 0.08f, RED);
                mbox(ps, vc, light, s, 2.0f, 0.46f, za, 2.1f, 1.04f, zb, RED);
            }
        }
    }

    /** Un conjunto de oruga con tres ruedas grandes y su faldón blindado. zc = centro del conjunto en Z. */
    private void trackUnit(PoseStack ps, VertexConsumer vc, int light, int s, float zc, float angle) {
        float xc = s * WHEEL_X;
        mbox(ps, vc, light, s, 1.46f, 0.0f, zc - 0.45f, 2.0f, 0.08f, zc + 0.45f, TRACK);
        mbox(ps, vc, light, s, 1.46f, 0.82f, zc - 0.5f, 2.0f, 0.9f, zc + 0.5f, TRACK);
        slab(ps, vc, light, xc, 0.86f, zc + 0.5f, 45.0f, 0.27f, 0.08f, 0.0f, 0.4f, TRACK);
        slab(ps, vc, light, xc, 0.577f, zc + 0.783f, 135.0f, 0.27f, 0.08f, 0.0f, 0.5f, TRACK);
        slab(ps, vc, light, xc, 0.86f, zc - 0.5f, -45.0f, 0.27f, 0.08f, -0.4f, 0.0f, TRACK);
        slab(ps, vc, light, xc, 0.577f, zc - 0.783f, -135.0f, 0.27f, 0.08f, -0.5f, 0.0f, TRACK);
        for (int i = 0; i < 6; i++) {
            float z = zc - 0.45f + i * 0.15f;
            mbox(ps, vc, light, s, 1.44f, -0.012f, z, 2.02f, 0.05f, z + 0.1f, CORNER);
            float zt = zc - 0.5f + i * 0.17f;
            mbox(ps, vc, light, s, 1.44f, 0.88f, zt, 2.02f, 0.935f, zt + 0.1f, CORNER);
        }
        for (int k = -1; k <= 1; k++) {
            wheel(ps, vc, light, xc, WHEEL_Y, zc + k * 0.5f, angle);
        }
        // faldón blindado que cubre la mitad superior de las ruedas
        mbox(ps, vc, light, s, 2.0f, 0.44f, zc - 0.74f, 2.08f, 1.02f, zc + 0.74f, PLATE);
        mbox(ps, vc, light, s, 2.08f, 0.52f, zc - 0.64f, 2.092f, 0.94f, zc - 0.02f, DENT);
        mbox(ps, vc, light, s, 2.08f, 0.52f, zc + 0.04f, 2.092f, 0.94f, zc + 0.64f, MARK);
        mbox(ps, vc, light, s, 1.99f, 0.42f, zc - 0.76f, 2.105f, 1.04f, zc - 0.7f, CORNER);
        mbox(ps, vc, light, s, 1.99f, 0.42f, zc + 0.7f, 2.105f, 1.04f, zc + 0.76f, CORNER);
        mbox(ps, vc, light, s, 1.99f, 0.42f, zc - 0.76f, 2.105f, 0.48f, zc + 0.76f, CORNER);
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

    // ------------------------------------------------------------------ Casco escalonado

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Casco bajo continuo y cubierta principal
        box(ps, vc, light, -1.4f, 0.18f, -3.7f, 1.4f, 1.0f, 3.7f, PLATE);
        box(ps, vc, light, -1.3f, 0.1f, -3.6f, 1.3f, 0.18f, 3.6f, BLACK);
        box(ps, vc, light, -1.75f, 0.95f, -1.9f, 1.75f, DECK, 2.5f, TITAN);
        // Placas de la cubierta: capas de titanio y cerámica
        box(ps, vc, light, -1.7f, DECK, -1.85f, 1.7f, DECK + 0.05f, -1.2f, PLATE);
        box(ps, vc, light, -1.7f, DECK, 1.85f, 1.7f, DECK + 0.05f, 2.45f, PLATE);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.3f, DECK, -1.15f, 1.7f, DECK + 0.04f, 1.8f, CERAMIC);
            mbox(ps, vc, light, s, 1.3f, DECK + 0.04f, -0.9f, 1.68f, DECK + 0.045f, -0.3f, MARK);
        }
        drawFront(ps, vc, light);
        drawTowerBody(ps, vc, light);
        drawRearStep(ps, vc, light);
        drawJoints(ps, vc, light);
        drawLines(ps, vc, light);
        drawWalkways(ps, vc, light);
        drawLamps(ps, vc, light);
    }

    /** Proa escalonada con glacis y el emblema X en grande sobre un marco rojo. */
    private void drawFront(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.65f, 0.95f, 2.5f, 1.65f, 1.3f, 3.1f, PLATE);
        box(ps, vc, light, -1.55f, 0.95f, 3.1f, 1.55f, 1.15f, 3.8f, PLATE);
        box(ps, vc, light, -1.67f, 1.3f, 2.48f, 1.67f, 1.34f, 3.12f, CORNER);
        box(ps, vc, light, -1.57f, 1.15f, 3.08f, 1.57f, 1.19f, 3.82f, CORNER);
        // glacis
        slab(ps, vc, light, 0.0f, DECK, 2.5f, 28.0f, 1.72f, 0.08f, 0.0f, 0.6f, TITAN);
        // proa vertical con marco rojo y emblema X
        box(ps, vc, light, -1.5f, 0.2f, 3.55f, 1.5f, 1.65f, 3.8f, PLATE);
        box(ps, vc, light, -1.55f, 0.15f, 3.52f, 1.55f, 0.28f, 3.84f, RED);
        box(ps, vc, light, -1.55f, 1.55f, 3.52f, 1.55f, 1.7f, 3.84f, RED);
        box(ps, vc, light, -1.55f, 0.15f, 3.52f, -1.38f, 1.7f, 3.84f, RED);
        box(ps, vc, light, 1.38f, 0.15f, 3.52f, 1.55f, 1.7f, 3.84f, RED);
        box(ps, vc, light, -0.62f, 0.38f, 3.8f, 0.62f, 1.46f, 3.84f, TITAN);
        boxFull(ps, vc, light, -0.54f, 0.42f, 3.84f, 0.54f, 1.42f, 3.855f, EMBLEM);
        for (int s = -1; s <= 1; s += 2) {
            // rejillas térmicas laterales de la proa y marcas
            mbox(ps, vc, light, s, 0.75f, 0.35f, 3.8f, 1.3f, 1.4f, 3.815f, VENT);
            mbox(ps, vc, light, s, 0.7f, 0.3f, 3.8f, 0.76f, 1.45f, 3.82f, CORNER);
            // franjas de advertencia en el borde del escalón
            mbox(ps, vc, light, s, 0.4f, 1.34f, 2.6f, 1.6f, 1.36f, 3.05f, HAZARD);
            mbox(ps, vc, light, s, 0.4f, 1.19f, 3.2f, 1.5f, 1.21f, 3.6f, HAZARD);
        }
    }

    /** Torre elevada del reactor (el núcleo y sus arcos se dibujan en drawReactor). */
    private void drawTowerBody(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.5f, DECK, -3.4f, 1.5f, 1.85f, -1.9f, TITAN);
        box(ps, vc, light, -1.52f, 1.8f, -3.42f, 1.52f, 1.88f, -1.88f, CORNER);
        // paredes laterales de la torre con enormes rejillas térmicas
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.25f, 1.88f, -3.3f, 1.5f, TOWER - 0.15f, -2.0f, PLATE);
            mbox(ps, vc, light, s, 1.5f, 1.98f, -3.2f, 1.52f, TOWER - 0.25f, -2.1f, VENT);
            mbox(ps, vc, light, s, 1.5f, 1.95f, -3.24f, 1.53f, TOWER - 0.22f, -3.2f, RED);
            mbox(ps, vc, light, s, 1.5f, 1.95f, -2.1f, 1.53f, TOWER - 0.22f, -2.06f, RED);
        }
        // frente y trasera de la torre
        box(ps, vc, light, -1.3f, 1.88f, -1.95f, 1.3f, TOWER - 0.15f, -1.9f, PLATE);
        box(ps, vc, light, -1.2f, 1.95f, -1.9f, -0.2f, TOWER - 0.3f, -1.88f, VENT);
        box(ps, vc, light, 0.2f, 1.95f, -1.9f, 1.2f, TOWER - 0.3f, -1.88f, VENT);
        box(ps, vc, light, -1.3f, 1.88f, -3.4f, 1.3f, TOWER - 0.15f, -3.35f, PLATE);
        // marcos superiores
        box(ps, vc, light, -1.5f, TOWER - 0.15f, -3.42f, 1.5f, TOWER, -1.88f, CORNER);
        // conductos visibles que bajan de la torre y recorren la cubierta hacia la torreta
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.55f, DECK, -1.9f, 1.7f, 2.4f, -1.75f, PIPE);          // columna
            mbox(ps, vc, light, s, 1.55f, DECK + 0.05f, -1.75f, 1.7f, DECK + 0.2f, 2.3f, PIPE);   // conducto horizontal
            for (float z : new float[]{-1.4f, -0.6f, 0.2f, 1.0f, 1.8f}) {
                mbox(ps, vc, light, s, 1.52f, DECK, z, 1.73f, DECK + 0.25f, z + 0.07f, CORNER);
            }
            mbox(ps, vc, light, s, 1.55f, DECK, -3.4f, 1.7f, 2.2f, -3.25f, PIPE);
            mbox(ps, vc, light, s, 1.52f, 1.95f, -3.4f, 1.73f, 2.0f, -3.25f, CORNER);
        }
    }

    /** Escalón trasero con enormes rejillas térmicas en la cara trasera y en la cubierta. */
    private void drawRearStep(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -1.6f, 0.95f, -3.8f, 1.6f, 1.3f, -3.4f, PLATE);
        box(ps, vc, light, -1.62f, 1.3f, -3.82f, 1.62f, 1.34f, -3.38f, CORNER);
        for (int i = 0; i < 2; i++) {
            float x0 = -1.45f + i * 1.5f;
            box(ps, vc, light, x0, 0.3f, -3.84f, x0 + 1.4f, 1.25f, -3.8f, VENT);
            box(ps, vc, light, x0 + 0.05f, 0.3f, -3.83f, x0 + 1.35f, 1.25f, -3.81f, glowLine(-3.8f, i));
        }
        box(ps, vc, light, -0.06f, 0.3f, -3.85f, 0.06f, 1.25f, -3.8f, RED);
        box(ps, vc, light, -1.5f, 1.34f, -3.75f, 1.5f, 1.36f, -3.45f, VENT);
        box(ps, vc, light, -1.4f, 1.36f, -3.7f, 1.4f, 1.372f, -3.5f, glowLine(-3.6f, 1));
        for (int i = 0; i < 4; i++) {
            float x = -1.38f + i * 0.72f;
            box(ps, vc, light, x, 1.372f, -3.72f, x + 0.08f, 1.4f, -3.48f, BLACK);
        }
        box(ps, vc, light, -1.2f, 0.4f, -3.86f, -0.9f, 1.1f, -3.84f, HAZARD);
        box(ps, vc, light, 0.9f, 0.4f, -3.86f, 1.2f, 1.1f, -3.84f, HAZARD);
    }

    /** Refuerzos rojos en los puntos de unión entre secciones y en las esquinas. */
    private void drawJoints(PoseStack ps, VertexConsumer vc, int light) {
        float[] seams = {2.5f, -1.9f, -3.4f};
        float[] hs = {DECK - 0.2f, DECK - 0.1f, 1.3f};
        for (int i = 0; i < seams.length; i++) {
            float z = seams[i];
            box(ps, vc, light, -1.78f, 0.97f, z - 0.07f, 1.78f, hs[i] + 0.1f, z + 0.07f, RED);
            box(ps, vc, light, -1.8f, 0.95f, z - 0.1f, -1.7f, hs[i] + 0.14f, z + 0.1f, RED);
            box(ps, vc, light, 1.7f, 0.95f, z - 0.1f, 1.8f, hs[i] + 0.14f, z + 0.1f, RED);
        }
        // abrazaderas rojas sobre la cubierta
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.0f, DECK + 0.05f, 2.4f, 1.78f, DECK + 0.12f, 2.5f, RED);
            mbox(ps, vc, light, s, 1.0f, DECK + 0.05f, -1.95f, 1.78f, DECK + 0.12f, -1.85f, RED);
        }
    }

    /** Líneas luminosas del casco: con el protocolo activo una onda las recorre en secuencia desde el reactor. */
    private void drawLines(PoseStack ps, VertexConsumer vc, int light) {
        for (int s = -1; s <= 1; s += 2) {
            // costado de la cubierta (de proa a popa) y de la proa
            for (int i = 0; i < 9; i++) {
                float z = -1.7f + i * 0.45f;
                mbox(ps, vc, light, s, 1.75f, 1.2f, z, 1.768f, 1.26f, z + 0.38f, glowLine(z, i));
            }
            // en lo alto, en la cubierta
            for (int i = 0; i < 9; i++) {
                float z = -1.7f + i * 0.45f;
                mbox(ps, vc, light, s, 1.15f, DECK + 0.045f, z, 1.22f, DECK + 0.055f, z + 0.38f, glowLine(z, i + 1));
            }
            // proa y escalón
            for (int i = 0; i < 4; i++) {
                float z = 2.6f + i * 0.28f;
                mbox(ps, vc, light, s, 1.65f, 1.05f, z, 1.668f, 1.1f, z + 0.22f, glowLine(z, i));
            }
            // torre del reactor
            for (int i = 0; i < 4; i++) {
                float z = -3.25f + i * 0.38f;
                mbox(ps, vc, light, s, 1.5f, 1.9f, z, 1.52f, 1.94f, z + 0.3f, glowLine(z, i));
            }
            // marco rojo de proa con luz
            mbox(ps, vc, light, s, 0.65f, 0.3f, 3.84f, 0.7f, 1.45f, 3.855f, glowLine(3.8f, 0));
        }
    }

    /** Pasarelas de mantenimiento con barandillas, escaleras a la torre y escotillas reforzadas. */
    private void drawWalkways(PoseStack ps, VertexConsumer vc, int light) {
        float[] posts = {-1.8f, -1.0f, -0.2f, 0.6f, 1.4f, 2.2f};
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.75f, DECK, -1.8f, 2.12f, DECK + 0.035f, 2.4f, VENT);
            mbox(ps, vc, light, s, 2.1f, DECK + 0.035f, -1.8f, 2.12f, DECK + 0.07f, 2.4f, CORNER);
            for (float z : posts) {
                mbox(ps, vc, light, s, 2.06f, DECK + 0.035f, z, 2.12f, DECK + 0.32f, z + 0.06f, BLACK);
            }
            mbox(ps, vc, light, s, 2.06f, DECK + 0.3f, -1.8f, 2.12f, DECK + 0.34f, 2.4f, RED);
            mbox(ps, vc, light, s, 2.08f, DECK + 0.16f, -1.8f, 2.1f, DECK + 0.19f, 2.4f, BOLT);
            // escalera hacia la torre: sube hacia popa junto a la pared de la torre
            for (int i = 0; i < 6; i++) {
                float z0 = -1.9f - 0.12f * (i + 1);
                mbox(ps, vc, light, s, 1.55f, DECK, z0, 1.95f, DECK + 0.17f * (i + 1), z0 + 0.12f, i % 2 == 0 ? VENT : CORNER);
            }
            mbox(ps, vc, light, s, 1.92f, DECK + 0.1f, -2.7f, 1.97f, DECK + 1.0f, -2.66f, BLACK);
            // escotillas reforzadas
            mbox(ps, vc, light, s, 0.9f, DECK + 0.045f, 1.9f, 1.4f, DECK + 0.1f, 2.4f, CORNER);
            mbox(ps, vc, light, s, 0.95f, DECK + 0.1f, 1.95f, 1.35f, DECK + 0.13f, 2.35f, PANEL);
            mbox(ps, vc, light, s, 1.1f, DECK + 0.13f, 2.1f, 1.2f, DECK + 0.16f, 2.2f, BOLT);
            mbox(ps, vc, light, s, 0.88f, DECK + 0.045f, 1.88f, 1.42f, DECK + 0.07f, 1.93f, RED);
        }
    }

    /** Focos blancos de gran intensidad. */
    private void drawLamps(PoseStack ps, VertexConsumer vc, int light) {
        for (int s = -1; s <= 1; s += 2) {
            lamp(ps, vc, light, s * 1.3f, 1.7f, 3.55f, 0.26f);
            lamp(ps, vc, light, s * 1.6f, DECK + 0.05f, 2.15f, 0.26f);
            lamp(ps, vc, light, s * 1.3f, TOWER, -2.0f, 0.24f);
            lamp(ps, vc, light, s * 1.5f, 1.3f, -3.5f, 0.2f);
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

    // ------------------------------------------------------------------ Reactor central elevado

    private void drawReactor(PoseStack ps, VertexConsumer vc, int light) {
        float k = this.mode == 1 ? 1.0f : (this.mode == 2 ? 0.1f : 0.4f + 0.25f * (0.5f + 0.5f * Mth.sin(this.time * 0.07f)) + 0.35f * this.charge);
        float spin = this.time * (this.mode == 1 ? 7.0f : 1.4f);
        ps.pushPose();
        ps.translate(0.0, RY, RZ);
        // base y núcleo: naranja por fuera, violeta por dentro
        box(ps, vc, light, -0.5f, -0.35f, -0.5f, 0.5f, -0.25f, 0.5f, PLATE);
        box(ps, vc, light, -0.08f, -0.25f, -0.08f, 0.08f, -0.12f, 0.08f, BLACK);
        float ro = 0.28f + 0.06f * k;
        float ri = 0.17f + 0.04f * k;
        Mat orange = new Mat(new int[]{255, (int) (110 + 80 * k), (int) (20 + 40 * k * k)}, T_FLAT, true);
        Mat violet = new Mat(new int[]{(int) (130 + 70 * k), (int) (60 + 50 * k), 255}, T_FLAT, true);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        box(ps, vc, light, -ro, -ro, -ro, ro, ro, ro, orange);
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -ro, -ro, -ro, ro, ro, ro, orange);
        ps.popPose();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-spin * 1.6f));
        ps.mulPose(Axis.ZP.rotationDegrees(30.0f));
        box(ps, vc, light, -ri, -ri, -ri, ri, ri, ri, violet);
        ps.popPose();
        if (this.mode == 1) {
            int seed = (int) (this.time / 2.0f);
            for (int i = 0; i < 3; i++) {
                float ox = (hash(seed, i * 3) - 0.5f) * 0.6f;
                float oy = (hash(seed, i * 3 + 1) - 0.5f) * 0.6f;
                float oz = (hash(seed, i * 3 + 2) - 0.5f) * 0.6f;
                box(ps, vc, light, ox - 0.03f, oy - 0.03f, oz - 0.03f, ox + 0.03f, oy + 0.03f, oz + 0.03f, WHITE);
            }
        }
        ps.popPose();

        // Arcos metálicos que protegen el núcleo: tres arcos a lo largo de Z
        for (int a = 0; a < 3; a++) {
            float z = RZ - 0.8f + a * 0.8f;
            for (int s = -1; s <= 1; s += 2) {
                box(ps, vc, light, s * 1.15f - 0.07f, 1.88f, z - 0.07f, s * 1.15f + 0.07f, TOWER, z + 0.07f, CORNER);
                ps.pushPose();
                ps.translate(s * 1.15f, TOWER - 0.15f, z);
                ps.mulPose(Axis.ZP.rotationDegrees(s * -35.0f));
                box(ps, vc, light, -0.06f, 0.0f, -0.06f, 0.06f, 0.65f, 0.06f, TITAN);
                ps.popPose();
            }
            box(ps, vc, light, -0.62f, TOWER + 0.35f, z - 0.07f, 0.62f, TOWER + 0.45f, z + 0.07f, CORNER);
            box(ps, vc, light, -0.64f, TOWER + 0.3f, z - 0.09f, -0.5f, TOWER + 0.46f, z + 0.09f, RED);
            box(ps, vc, light, 0.5f, TOWER + 0.3f, z - 0.09f, 0.64f, TOWER + 0.46f, z + 0.09f, RED);
        }
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 0.05f, TOWER + 0.35f, RZ - 0.9f, 0.1f, TOWER + 0.45f, RZ + 0.9f, TITAN);
        }
        box(ps, vc, light, -1.2f, TOWER - 0.15f, -3.4f, 1.2f, TOWER - 0.05f, -3.35f, RED);

        // Cubiertas móviles: dos hojas sobre el núcleo que se abren hacia fuera con el protocolo
        float ang = 62.0f * this.open * this.open * (3.0f - 2.0f * this.open);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 1.1f, TOWER + 0.0f, RZ);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -ang));
            mbox(ps, vc, light, s, -1.0f, 0.0f, -0.95f, 0.0f, 0.08f, 0.95f, PLATE);
            mbox(ps, vc, light, s, -0.9f, 0.08f, -0.85f, -0.05f, 0.1f, 0.85f, CERAMIC);
            mbox(ps, vc, light, s, -0.95f, 0.08f, -0.9f, -0.9f, 0.12f, 0.9f, RED);
            ps.popPose();
        }
        // Ranuras luminosas en los costados de la torre (se ve el calor del núcleo desde fuera)
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 3; i++) {
                float y = 2.05f + i * 0.12f;
                mbox(ps, vc, light, s, 1.522f, y, -3.1f, 1.535f, y + 0.05f, -2.2f, glowLine(RZ - 0.4f + i * 0.3f, i));
            }
        }
        // Rejilla del reactor visible bajo las cubiertas, con luz naranja
        box(ps, vc, light, -0.9f, TOWER - 0.12f, RZ - 0.9f, 0.9f, TOWER - 0.1f, RZ + 0.9f, VENT);
        box(ps, vc, light, -0.8f, TOWER - 0.1f, RZ - 0.8f, 0.8f, TOWER - 0.098f, RZ + 0.8f, glowLine(RZ, 1));
    }

    // ------------------------------------------------------------------ Torreta monumental

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash) {
        ps.pushPose();
        ps.translate(0.0, DECK, TZ);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Corona de giro y cuerpo escalonado
        box(ps, vc, light, -1.5f, 0.0f, -1.5f, 1.5f, 0.07f, 1.5f, BLACK);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -1.45f, 0.0f, -1.45f, 1.45f, 0.07f, 1.45f, BLACK);
        ps.popPose();
        box(ps, vc, light, -1.15f, 0.07f, -1.2f, 1.15f, 0.95f, 1.05f, PLATE);
        box(ps, vc, light, -1.18f, 0.05f, -1.22f, 1.18f, 0.12f, 1.07f, RED);
        box(ps, vc, light, -1.18f, 0.88f, -1.22f, 1.18f, 0.95f, 1.07f, CORNER);
        box(ps, vc, light, -0.9f, 0.95f, -0.95f, 0.9f, 1.18f, 0.9f, TITAN);
        box(ps, vc, light, -0.93f, 1.14f, -0.98f, 0.93f, 1.2f, 0.93f, CORNER);
        box(ps, vc, light, -0.6f, 1.2f, -0.6f, 0.6f, 1.24f, 0.6f, CERAMIC);
        // facetas inclinadas delanteras y traseras
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.75f, 0.07f, 1.05f);
            ps.mulPose(Axis.YP.rotationDegrees(s * -30.0f));
            box(ps, vc, light, -0.45f, 0.0f, 0.0f, 0.45f, 0.88f, 0.1f, PLATE);
            box(ps, vc, light, -0.4f, 0.1f, 0.1f, 0.4f, 0.8f, 0.115f, DENT);
            ps.popPose();
            mbox(ps, vc, light, s, 0.0f, 0.1f, -1.25f, 1.1f, 0.85f, -1.2f, PLATE);
            mbox(ps, vc, light, s, 0.2f, 0.2f, -1.27f, 0.9f, 0.75f, -1.25f, VENT);
        }
        box(ps, vc, light, -0.15f, 0.2f, -1.27f, 0.15f, 0.75f, -1.25f, HAZARD);
        // cúpula del conductor
        box(ps, vc, light, -0.45f, 1.24f, -0.55f, 0.45f, 1.4f, 0.1f, CORNER);
        box(ps, vc, light, -0.38f, 1.4f, -0.48f, 0.38f, 1.44f, 0.03f, PANEL);
        for (int i = 0; i < 4; i++) {
            int v = (int) (205 + 50 * Mth.sin(this.time * 0.15f + i * 1.1f));
            Mat white = new Mat(new int[]{v, v, v}, T_FLAT, true);
            float px = (i % 2 == 0 ? -0.4f : 0.4f);
            float pz = (i / 2 == 0 ? -0.5f : 0.05f);
            box(ps, vc, light, px - 0.03f, 1.4f, pz - 0.03f, px + 0.03f, 1.44f, pz + 0.03f, white);
        }
        // focos de la torreta
        lamp(ps, vc, light, -0.8f, 1.2f, 0.4f, 0.24f);
        lamp(ps, vc, light, 0.8f, 1.2f, 0.4f, 0.24f);
        // refuerzos rojos en los puntos de unión de la torreta
        box(ps, vc, light, -1.2f, 0.4f, 1.0f, -1.12f, 0.9f, 1.12f, RED);
        box(ps, vc, light, 1.12f, 0.4f, 1.0f, 1.2f, 0.9f, 1.12f, RED);
        box(ps, vc, light, -0.95f, 0.95f, -0.97f, 0.95f, 1.0f, -0.9f, RED);
        // líneas luminosas de la torreta
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.15f, 0.5f, -0.9f, 1.165f, 0.55f, 0.9f, glowLine(0.0f, 2));
        }

        float rad = pitch * Mth.DEG_TO_RAD;
        // Módulos secundarios laterales: bloque blindado y cañón corto que cabecea con el principal
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.15f, 0.12f, -0.4f, 1.75f, 0.85f, 1.0f, PLATE);
            mbox(ps, vc, light, s, 1.15f, 0.8f, -0.4f, 1.75f, 0.88f, 1.0f, CORNER);
            mbox(ps, vc, light, s, 1.75f, 0.2f, -0.3f, 1.77f, 0.75f, 0.9f, DENT);
            mbox(ps, vc, light, s, 1.15f, 0.1f, -0.42f, 1.77f, 0.2f, 1.02f, RED);
            mbox(ps, vc, light, s, 1.35f, 0.88f, 0.1f, 1.55f, 0.94f, 0.4f, HAZARD);
            ps.pushPose();
            ps.translate(s * 1.45f, 0.55f, 1.0f);
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            box(ps, vc, light, -0.17f, -0.17f, -0.2f, 0.17f, 0.17f, 0.3f, CORNER);
            box(ps, vc, light, -0.1f, -0.1f, 0.3f, 0.1f, 0.1f, 2.1f, BLACK);
            for (int i = 0; i < 3; i++) {
                float z = 0.6f + i * 0.45f;
                box(ps, vc, light, -0.15f, -0.15f, z, 0.15f, 0.15f, z + 0.1f, i % 2 == 0 ? RED : CORNER);
            }
            box(ps, vc, light, -0.14f, -0.14f, 1.95f, 0.14f, 0.14f, 2.25f, PLATE);
            box(ps, vc, light, -0.06f, 0.14f, 2.0f, 0.06f, 0.16f, 2.2f, BLACK);
            ps.popPose();
        }

        // Cilindros hidráulicos de la torreta a la cuna del cañón
        float ay = 0.7f + 0.35f * Mth.cos(rad) - 0.6f * Mth.sin(rad);
        float az = 1.2f + 0.35f * Mth.sin(rad) + 0.6f * Mth.cos(rad);
        for (int s = -1; s <= 1; s += 2) {
            cylinder(ps, vc, light, s * 0.62f, 1.0f, 0.5f, ay, az);
        }
        // Cuna blindada con marco rojo
        box(ps, vc, light, -0.8f, 0.1f, 0.95f, 0.8f, 1.15f, 1.5f, PLATE);
        box(ps, vc, light, -0.84f, 0.1f, 0.93f, -0.74f, 1.15f, 1.52f, RED);
        box(ps, vc, light, 0.74f, 0.1f, 0.93f, 0.84f, 1.15f, 1.52f, RED);
        box(ps, vc, light, -0.84f, 1.08f, 0.93f, 0.84f, 1.18f, 1.52f, CORNER);

        // Cañón principal de energía: eje a (0, 0.7, 1.2) de la torreta
        ps.translate(0.0, 0.7, 1.2);
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
        box(ps, vc, light, -0.07f, -0.07f, 0.0f, 0.07f, 0.07f, body, PLATE);
        box(ps, vc, light, -0.1f, -0.1f, 0.04f, 0.1f, 0.1f, 0.12f, RED);
        box(ps, vc, light, -0.1f, -0.1f, body - 0.12f, 0.1f, 0.1f, body - 0.04f, CORNER);
        box(ps, vc, light, -0.04f, -0.04f, body, 0.04f, 0.04f, len, BOLT);
        box(ps, vc, light, -0.1f, -0.1f, len - 0.05f, 0.1f, 0.1f, len, BLACK);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash) {
        float k = this.mode == 1 ? 1.0f : (this.mode == 2 ? 0.1f : 0.35f + 0.65f * this.charge);
        // Recámara, tubo principal y refuerzos
        box(ps, vc, light, -0.5f, -0.46f, -0.7f, 0.5f, 0.46f, 0.3f, CORNER);
        box(ps, vc, light, -0.52f, -0.48f, -0.1f, 0.52f, 0.48f, 0.0f, RED);
        box(ps, vc, light, -0.3f, -0.3f, 0.3f, 0.3f, 0.3f, 3.4f, BLACK);
        box(ps, vc, light, -0.36f, -0.36f, 0.3f, 0.36f, 0.36f, 1.1f, TITAN);
        box(ps, vc, light, -0.28f, 0.3f, -0.5f, 0.28f, 0.34f, 0.1f, HAZARD);
        // Cámara principal: núcleo naranja con interior violeta
        Mat orange = new Mat(new int[]{255, (int) (110 + 90 * k), (int) (20 + 40 * k * k)}, T_FLAT, true);
        Mat violet = new Mat(new int[]{(int) (130 + 70 * k), (int) (60 + 50 * k), 255}, T_FLAT, true);
        float er = 0.26f + 0.05f * k;
        box(ps, vc, light, -er, -er, 1.45f, er, er, 2.1f, orange);
        box(ps, vc, light, -0.14f, -0.14f, 1.4f, 0.14f, 0.14f, 2.15f, violet);
        // Corona de estabilización: ocho segmentos que giran alrededor de la cámara
        for (int i = 0; i < 8; i++) {
            ps.pushPose();
            ps.translate(0.0f, 0.0f, 1.78f);
            ps.mulPose(Axis.ZP.rotationDegrees(this.time * (this.mode == 1 ? 6.0f : 1.2f) + i * 45.0f));
            box(ps, vc, light, 0.55f, -0.12f, -0.28f, 0.82f, 0.12f, 0.28f, i % 2 == 0 ? CORNER : RED);
            box(ps, vc, light, 0.5f, -0.04f, -0.2f, 0.56f, 0.04f, 0.2f, BLACK);
            ps.popPose();
        }
        // Cuatro puntales fijos que sujetan la corona al tubo
        for (int sx = -1; sx <= 1; sx += 2) {
            box(ps, vc, light, sx * 0.4f - 0.04f, -0.04f, 1.5f, sx * 0.4f + 0.04f, 0.04f, 2.06f, PLATE);
            box(ps, vc, light, -0.04f, sx * 0.4f - 0.04f, 1.5f, 0.04f, sx * 0.4f + 0.04f, 2.06f, PLATE);
        }
        // Anillos mecánicos y conductos de refrigeración a lo largo del tubo
        for (int i = 0; i < 4; i++) {
            float z = 2.3f + i * 0.2f;
            box(ps, vc, light, -0.4f, -0.4f, z, 0.4f, 0.4f, z + 0.1f, i % 2 == 0 ? CORNER : PLATE);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            box(ps, vc, light, sx * 0.34f - 0.04f, -0.04f, 0.4f, sx * 0.34f + 0.04f, 0.04f, 1.4f, PIPE);
        }
        // Boca pesada con aletas y puntas
        box(ps, vc, light, -0.45f, -0.45f, 3.1f, 0.45f, 0.45f, 3.8f, PLATE);
        box(ps, vc, light, -0.49f, -0.49f, 3.05f, 0.49f, 0.49f, 3.15f, RED);
        box(ps, vc, light, -0.3f, 0.45f, 3.3f, 0.3f, 0.5f, 3.7f, VENT);
        box(ps, vc, light, -0.3f, -0.5f, 3.3f, 0.3f, -0.45f, 3.7f, VENT);
        box(ps, vc, light, 0.45f, -0.3f, 3.3f, 0.5f, 0.3f, 3.7f, VENT);
        box(ps, vc, light, -0.5f, -0.3f, 3.3f, -0.45f, 0.3f, 3.7f, VENT);
        box(ps, vc, light, -0.1f, -0.1f, 3.8f, 0.1f, 0.1f, 3.84f, orange);
        if (flash > 0.0f) {
            float f = (this.mode == 1 ? 0.3f : 0.2f) + 0.3f * flash;
            box(ps, vc, light, -f, -f, 3.84f, f, f, 3.84f + (this.mode == 1 ? 1.2f : 0.7f) * flash, orange);
            box(ps, vc, light, -f * 0.5f, -f * 0.5f, 3.84f, f * 0.5f, f * 0.5f,
                    3.84f + (this.mode == 1 ? 1.6f : 1.0f) * flash, WHITE);
        }
    }

    // ------------------------------------------------------------------ Luz de las líneas

    /**
     * Material de una línea luminosa situada en la posición z del casco. Con el protocolo activo una onda naranja y
     * violeta la recorre en secuencia desde el reactor; durante el enfriamiento casi no brilla.
     */
    private Mat glowLine(float z, int index) {
        float k;
        if (this.mode == 1) {
            float d = Math.abs(z - RZ);
            float wave = (this.time * 0.045f - d * 0.16f) % 1.0f;
            if (wave < 0.0f) {
                wave += 1.0f;
            }
            k = Math.max(0.12f, 1.0f - 3.2f * wave);
            if (index % 2 == 1) {
                return new Mat(new int[]{(int) (140 + 90 * k), (int) (60 + 50 * k), 255}, T_FLAT, true);
            }
        } else if (this.mode == 2) {
            k = 0.12f;
            return new Mat(new int[]{(int) (90 + 60 * k), 14, 10}, T_FLAT, true);
        } else {
            k = 0.3f + 0.15f * Mth.sin(this.time * 0.08f - Math.abs(z - RZ) * 0.5f) + 0.3f * this.charge;
        }
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{255, (int) (90 + 100 * k), (int) (10 + 40 * k * k)}, T_FLAT, true);
    }

    private static float hash(int seed, int n) {
        float x = (float) Math.sin(seed * 12.9898 + n * 78.233) * 43758.5453f;
        return x - (float) Math.floor(x);
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
