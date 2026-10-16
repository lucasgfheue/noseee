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
 * Dibuja el «Reactor Viviente»: cuerpo de anchura media con un bloque trasero elevado que lleva sobre su base una
 * esfera violeta dentro de una jaula metálica abierta por delante, cubiertas que flotan alrededor del reactor sujetas
 * por soportes, placas de titanio negro separadas por líneas violetas, paneles de refrigeración laterales que se abren
 * mostrando disipadores y conductos luminosos, y una torreta angular asimétrica (a la derecha y adelantada) con un
 * cañón de energía y un aro mecánico alrededor de su cámara de emisión. Hologramas de diagnóstico, luces magenta de
 * advertencia y placas con símbolos de contención energética. El centro de la esfera se vuelve blanco con las
 * habilidades.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Torreta en X -0.75, Z 0.85 (eje del cañón a 1.6 de alto
 * y 0.9 por delante de su eje, largo 2.3); reactor en Z -1.4. Debe coincidir con ReactorTankEntity.
 */
public class ReactorTankRenderer extends EntityRenderer<ReactorTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_reactor.png");

    private static final int T_PLATE = 0, T_CORNER = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_CERAMIC = 8, T_PETAL = 9, T_INSUL = 10;

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
    private static final Mat INSUL = new Mat(W, T_INSUL);
    private static final Mat BOLT = new Mat(new int[]{110, 112, 125}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{14, 14, 18}, T_FLAT);
    private static final Mat HOLO = new Mat(new int[]{150, 200, 255}, T_FLAT, true);
    private static final Mat HOLO_DIM = new Mat(new int[]{70, 120, 210}, T_FLAT, true);
    private static final Mat WHITE = new Mat(new int[]{255, 255, 255}, T_FLAT, true);

    private static final float WHEEL_Y = 0.38f;
    private static final float WHEEL_X = 1.1f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;

    private static final float DECK = 1.05f;        // cubierta frontal
    private static final float BLOCK = 1.75f;       // techo del bloque trasero
    private static final float RZ = -1.4f;          // centro del reactor en Z
    private static final float TURRET_X = -0.75f;
    private static final float TURRET_Z = 0.85f;

    private float time;
    private boolean distort;
    private float charge;
    private float open;

    public ReactorTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.9f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(ReactorTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ReactorTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(5.0, 3.0, 5.0));
    }

    @Override
    public void render(ReactorTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        this.charge = Mth.clamp(tank.getCharge() / (float) ReactorTankEntity.CHARGE_MAX, 0.0f, 1.0f);
        this.distort = tank.isDistorting();
        this.open = tank.openFraction(partialTick);
        float since = tank.ticksSinceShot(partialTick);
        float recoil = since < 12.0f ? 0.18f * (since < 2.0f ? since / 2.0f : 1.0f - (since - 2.0f) / 10.0f) : 0.0f;
        float flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);
        drawReactor(ps, vc, light, flash);

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

    // ------------------------------------------------------------------ Orugas bajo faldones

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            float angle = s > 0 ? angleL : angleR;
            float xc = s * WHEEL_X;
            mbox(ps, vc, light, s, 0.85f, 0.0f, -1.85f, 1.35f, 0.08f, 1.85f, TRACK);
            mbox(ps, vc, light, s, 0.85f, 0.82f, -1.9f, 1.35f, 0.9f, 1.9f, TRACK);
            slab(ps, vc, light, xc, 0.86f, 1.9f, 45.0f, 0.25f, 0.08f, 0.0f, 0.4f, TRACK);
            slab(ps, vc, light, xc, 0.577f, 2.183f, 135.0f, 0.25f, 0.08f, 0.0f, 0.5f, TRACK);
            slab(ps, vc, light, xc, 0.86f, -1.9f, -45.0f, 0.25f, 0.08f, -0.4f, 0.0f, TRACK);
            slab(ps, vc, light, xc, 0.577f, -2.183f, -135.0f, 0.25f, 0.08f, -0.5f, 0.0f, TRACK);
            for (int i = 0; i < 14; i++) {
                float z = -1.85f + i * 0.27f;
                mbox(ps, vc, light, s, 0.83f, -0.012f, z, 1.37f, 0.05f, z + 0.12f, CORNER);
                mbox(ps, vc, light, s, 0.83f, 0.88f, z, 1.37f, 0.935f, z + 0.12f, CORNER);
            }
            for (int i = 0; i < 6; i++) {
                wheel(ps, vc, light, xc, WHEEL_Y, -1.5f + i * 0.6f, angle);
            }
            // faldón blindado con insertos cerámicos separados por líneas violetas
            mbox(ps, vc, light, s, 1.35f, 0.46f, -2.0f, 1.45f, 1.02f, 2.0f, PLATE);
            for (int i = 0; i < 4; i++) {
                float z0 = -1.95f + i * 0.98f;
                mbox(ps, vc, light, s, 1.45f, 0.52f, z0, 1.462f, 0.96f, z0 + 0.88f, CERAMIC);
                mbox(ps, vc, light, s, 1.445f, 0.5f, z0 + 0.88f, 1.465f, 0.98f, z0 + 0.96f, violet(0.55f, i));
            }
            mbox(ps, vc, light, s, 1.34f, 0.44f, -2.02f, 1.47f, 0.5f, 2.02f, CORNER);
            mbox(ps, vc, light, s, 1.34f, 1.0f, -2.02f, 1.47f, 1.05f, 2.02f, CORNER);
            mbox(ps, vc, light, s, 1.462f, 0.62f, -0.25f, 1.475f, 0.86f, 0.25f, INSUL);
        }
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
        box(ps, vc, light, -h - 0.02f, -0.07f, -0.07f, h + 0.02f, 0.07f, 0.07f, BOLT);
        ps.popPose();
    }


    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Vientre y bloque frontal bajo
        box(ps, vc, light, -0.85f, 0.2f, -2.3f, 0.85f, 0.85f, 2.3f, PLATE);
        box(ps, vc, light, -0.8f, 0.12f, -2.2f, 0.8f, 0.2f, 2.2f, BLACK);
        box(ps, vc, light, -1.4f, 0.85f, -0.5f, 1.4f, DECK, 2.2f, PLATE);
        box(ps, vc, light, -1.3f, 0.5f, 2.2f, 1.3f, 0.85f, 2.45f, PLATE);
        slab(ps, vc, light, 0.0f, DECK + 0.02f, 1.35f, 12.0f, 1.42f, 0.09f, 0.0f, 0.9f, PLATE);
        box(ps, vc, light, -1.32f, 0.5f, 2.45f, 1.32f, 0.72f, 2.5f, CORNER);
        box(ps, vc, light, -0.8f, 0.55f, 2.5f, -0.3f, 0.7f, 2.51f, VENT);
        box(ps, vc, light, 0.3f, 0.55f, 2.5f, 0.8f, 0.7f, 2.51f, VENT);
        box(ps, vc, light, -0.2f, 0.55f, 2.5f, 0.2f, 0.68f, 2.51f, INSUL);
        // Bloque trasero elevado: placas de titanio separadas por líneas violetas
        box(ps, vc, light, -1.2f, 0.85f, -2.3f, 1.2f, BLOCK, -0.5f, PLATE);
        box(ps, vc, light, -1.22f, BLOCK - 0.06f, -2.32f, 1.22f, BLOCK, -0.48f, CORNER);
        box(ps, vc, light, -1.22f, 0.83f, -2.32f, 1.22f, 0.9f, -0.48f, CORNER);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 3; i++) {
                float z0 = -2.2f + i * 0.58f;
                mbox(ps, vc, light, s, 1.2f, 1.0f, z0, 1.212f, 1.7f, z0 + 0.5f, i == 1 ? INSUL : CERAMIC);
                mbox(ps, vc, light, s, 1.198f, 0.95f, z0 + 0.5f, 1.215f, 1.72f, z0 + 0.58f, violet(0.6f, i));
            }
        }
        // caras trasera y delantera del bloque
        box(ps, vc, light, -1.1f, 1.0f, -2.34f, 1.1f, 1.7f, -2.3f, VENT);
        box(ps, vc, light, -0.8f, 1.2f, -2.35f, 0.8f, 1.5f, -2.34f, violet(0.5f, 1));
        box(ps, vc, light, -1.1f, 0.9f, -0.5f, 1.1f, 1.7f, -0.48f, CERAMIC);
        box(ps, vc, light, -0.06f, 0.9f, -0.49f, 0.06f, 1.7f, -0.47f, violet(0.6f, 2));
        // banco de capacitores a la izquierda (asimetría) con líneas violetas
        box(ps, vc, light, 0.7f, DECK, 0.1f, 1.3f, 1.5f, 1.6f, PLATE);
        box(ps, vc, light, 0.68f, 1.46f, 0.08f, 1.32f, 1.52f, 1.62f, CORNER);
        for (int i = 0; i < 3; i++) {
            float z0 = 0.2f + i * 0.5f;
            box(ps, vc, light, 1.3f, 1.1f, z0, 1.312f, 1.42f, z0 + 0.4f, CERAMIC);
            box(ps, vc, light, 1.298f, 1.08f, z0 + 0.4f, 1.315f, 1.44f, z0 + 0.48f, violet(0.6f, i + 1));
        }
        // marcas de contención en la cubierta derecha y líneas violetas en las aristas
        box(ps, vc, light, -1.4f, DECK, -0.4f, -1.2f, DECK + 0.04f, 0.1f, INSUL);
        box(ps, vc, light, -1.41f, DECK, -0.5f, -1.38f, DECK + 0.03f, 2.2f, violet(0.5f, 3));
        box(ps, vc, light, 1.38f, DECK, -0.5f, 1.41f, DECK + 0.03f, 2.2f, violet(0.5f, 0));
        for (int i = 0; i < 4; i++) {
            float z = -0.4f + i * 0.65f;
            box(ps, vc, light, -1.4f, DECK, z + 0.55f, 1.4f, DECK + 0.012f, z + 0.6f, violet(0.45f, i));
        }
        drawCockpit(ps, vc, light);
        drawCooling(ps, vc, light);
        box(ps, vc, light, -1.1f, BLOCK, -2.2f, 1.1f, BLOCK + 0.02f, -2.0f, INSUL);
        holo(ps, vc, light, 1.0f, 1.52f, 0.95f, 90.0f);
        holo(ps, vc, light, -0.7f, BLOCK, -0.62f, 0.0f);
        drawIndicators(ps, vc, light);
    }

    /** Cabina central entre la torreta y el bloque trasero (el conductor va en el centro): marco abierto. */
    private void drawCockpit(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.3f, DECK, -0.45f, 0.3f, DECK + 0.07f, 0.45f, CORNER);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(ps, vc, light, sx * 0.26f - 0.03f, DECK + 0.07f, sz * 0.4f - 0.03f,
                        sx * 0.26f + 0.03f, DECK + 0.5f, sz * 0.4f + 0.03f, BLACK);
            }
        }
        box(ps, vc, light, -0.3f, DECK + 0.48f, -0.45f, 0.3f, DECK + 0.54f, 0.45f, CORNER);
        box(ps, vc, light, -0.04f, DECK + 0.54f, -0.4f, 0.04f, DECK + 0.56f, 0.4f, violet(0.6f, 2));
        box(ps, vc, light, -0.3f, DECK + 0.07f, -0.47f, 0.3f, DECK + 0.3f, -0.43f, PLATE);
    }

    /** Paneles laterales de refrigeración del bloque trasero: se abren hacia fuera mostrando disipadores y conductos. */
    private void drawCooling(PoseStack ps, VertexConsumer vc, int light) {
        float theta = 62.0f * this.open * this.open * (3.0f - 2.0f * this.open);
        for (int s = -1; s <= 1; s += 2) {
            mbox(ps, vc, light, s, 1.2f, 0.95f, -2.1f, 1.215f, 1.65f, -0.7f, BLACK);
            for (int i = 0; i < 7; i++) {
                float z = -2.05f + i * 0.2f;
                mbox(ps, vc, light, s, 1.215f, 0.97f, z, 1.3f, 1.63f, z + 0.04f, VENT);
                mbox(ps, vc, light, s, 1.215f, 0.97f, z + 0.07f, 1.235f, 1.63f, z + 0.12f,
                        violet(0.35f + 0.6f * this.open, i));
            }
            mbox(ps, vc, light, s, 1.2f, 0.93f, -2.12f, 1.32f, 0.97f, -0.68f, CORNER);
            // hoja con bisagra en el borde superior
            ps.pushPose();
            ps.translate(s * 1.25f, 1.67f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * theta));
            mbox(ps, vc, light, s, 0.0f, -0.74f, -2.12f, 0.06f, 0.0f, -0.68f, PLATE);
            mbox(ps, vc, light, s, 0.06f, -0.66f, -2.05f, 0.075f, -0.08f, -0.75f, CERAMIC);
            mbox(ps, vc, light, s, 0.06f, -0.74f, -2.12f, 0.09f, -0.7f, -0.68f, CORNER);
            mbox(ps, vc, light, s, 0.06f, -0.4f, -1.5f, 0.09f, -0.34f, -1.3f, violet(0.7f, 4));
            ps.popPose();
        }
    }

    /** Luces magenta de advertencia; parpadean mucho más rápido con el pozo activo. */
    private void drawIndicators(PoseStack ps, VertexConsumer vc, int light) {
        float[][] pts = {{-1.35f, DECK + 0.03f, 2.1f}, {1.35f, DECK + 0.03f, 2.1f}, {-1.15f, BLOCK + 0.02f, -2.2f},
                {1.15f, BLOCK + 0.02f, -2.2f}, {1.15f, BLOCK + 0.02f, -0.6f}, {1.25f, 1.55f, 1.55f},
                {-1.3f, DECK + 0.03f, -0.4f}, {0.0f, 0.82f, 2.46f}};
        float rate = this.distort ? 3.0f : 12.0f;
        for (int i = 0; i < pts.length; i++) {
            boolean on = ((int) (this.time / rate) + i) % 2 == 0;
            Mat m = on ? new Mat(new int[]{255, 50, 200}, T_FLAT, true) : new Mat(new int[]{80, 18, 70}, T_FLAT, true);
            box(ps, vc, light, pts[i][0] - 0.04f, pts[i][1], pts[i][2] - 0.04f,
                    pts[i][0] + 0.04f, pts[i][1] + 0.07f, pts[i][2] + 0.04f, m);
        }
    }

    private void holo(PoseStack ps, VertexConsumer vc, int light, float x, float y, float z, float yaw) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        box(ps, vc, light, -0.28f, 0.0f, -0.07f, 0.28f, 0.05f, 0.07f, BLACK);
        box(ps, vc, light, -0.04f, 0.05f, -0.03f, 0.04f, 0.1f, 0.03f, CORNER);
        float flick = 0.85f + 0.15f * Mth.sin(this.time * 0.9f + x * 7.0f);
        Mat bg = flick > 0.93f ? HOLO_DIM : new Mat(new int[]{50, 90, 170}, T_FLAT, true);
        box(ps, vc, light, -0.26f, 0.2f, -0.008f, 0.26f, 0.5f, 0.008f, bg);
        for (int i = 0; i < 4; i++) {
            float len = 0.1f + 0.13f * (0.5f + 0.5f * Mth.sin(this.time * 0.12f + i * 1.9f + x));
            box(ps, vc, light, -0.22f, 0.24f + i * 0.065f, 0.008f, -0.22f + len, 0.27f + i * 0.065f, 0.012f, HOLO);
        }
        box(ps, vc, light, 0.12f, 0.24f, 0.008f, 0.22f, 0.45f, 0.012f, HOLO_DIM);
        box(ps, vc, light, -0.27f, 0.19f, -0.012f, 0.27f, 0.2f, 0.012f, HOLO);
        box(ps, vc, light, -0.27f, 0.5f, -0.012f, 0.27f, 0.51f, 0.012f, HOLO);
        ps.popPose();
    }



    // ------------------------------------------------------------------ Reactor: esfera en jaula, cubiertas suspendidas

    private void drawReactor(PoseStack ps, VertexConsumer vc, int light, float flash) {
        float pulse = 0.5f + 0.5f * Mth.sin(this.time * 0.06f);                   // pulso lento
        boolean white = this.distort || flash > 0.0f;
        float k = this.distort ? 1.0f : 0.35f + 0.35f * pulse + 0.3f * this.charge;
        ps.pushPose();
        ps.translate(0.0f, BLOCK, RZ);

        // Base: anillo con soportes y marcas de contención
        box(ps, vc, light, -0.78f, 0.0f, -0.78f, 0.78f, 0.12f, 0.78f, PLATE);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.74f, 0.0f, -0.74f, 0.74f, 0.12f, 0.74f, PLATE);
        ps.popPose();
        box(ps, vc, light, -0.8f, 0.1f, -0.8f, 0.8f, 0.15f, 0.8f, CORNER);
        box(ps, vc, light, -0.25f, 0.02f, 0.78f, 0.25f, 0.1f, 0.8f, INSUL);
        box(ps, vc, light, -0.25f, 0.02f, -0.8f, 0.25f, 0.1f, -0.78f, INSUL);
        box(ps, vc, light, -0.1f, 0.15f, -0.1f, 0.1f, 0.3f, 0.1f, BLACK);

        // Esfera violeta (facetada) que pulsa; su centro se vuelve blanco con las habilidades
        float r = (0.4f + 0.04f * pulse + (this.distort ? 0.05f : 0.0f)) * 0.82f;
        float cy = 0.7f;
        Mat sphere = violet(k, 0);
        ps.pushPose();
        ps.translate(0.0f, cy, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(this.time * (this.distort ? 4.0f : 0.8f)));
        box(ps, vc, light, -r, -r, -r, r, r, r, sphere);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -r, -r, -r, r, r, r, sphere);
        ps.popPose();
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -r, -r, -r, r, r, r, sphere);
        ps.popPose();
        ps.popPose();
        float c = white ? 0.2f : 0.12f;
        Mat core = white ? WHITE : new Mat(new int[]{(int) (170 + 60 * pulse), (int) (110 + 40 * pulse), 255}, T_FLAT, true);
        ps.pushPose();
        ps.translate(0.0f, cy, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(-this.time * 3.0f));
        ps.mulPose(Axis.ZP.rotationDegrees(35.0f));
        box(ps, vc, light, -c, -c, -c, c, c, c, core);
        ps.popPose();
        if (white) {
            int seed = (int) (this.time / 2.0f);
            for (int i = 0; i < 3; i++) {
                float ox = (hash(seed, i * 3) - 0.5f) * 0.5f;
                float oy = (hash(seed, i * 3 + 1) - 0.5f) * 0.5f;
                float oz = (hash(seed, i * 3 + 2) - 0.5f) * 0.5f;
                box(ps, vc, light, ox - 0.03f, cy + oy - 0.03f, oz - 0.03f, ox + 0.03f, cy + oy + 0.03f, oz + 0.03f, WHITE);
            }
        }

        // Jaula metálica abierta por delante: tres aros y costillas
        ring(ps, vc, light, 0.15f, 0.66f, 0.0f);
        ring(ps, vc, light, 0.7f, 0.74f, 45.0f);
        ring(ps, vc, light, 1.25f, 0.6f, 0.0f);
        for (int i = 0; i < 8; i++) {
            float a = i * 45.0f;
            float dz = (float) Math.cos(Math.toRadians(a));
            if (dz > 0.5f) {
                continue;                                  // abierta hacia delante
            }
            float rx = (float) Math.sin(Math.toRadians(a)) * 0.66f;
            float rz = dz * 0.66f;
            box(ps, vc, light, rx - 0.035f, 0.12f, rz - 0.035f, rx + 0.035f, 1.3f, rz + 0.035f, i % 2 == 0 ? CORNER : PANEL);
            box(ps, vc, light, rx * 1.1f - 0.045f, 0.62f, rz * 1.1f - 0.045f, rx * 1.1f + 0.045f, 0.8f, rz * 1.1f + 0.045f, CORNER);
        }
        box(ps, vc, light, -0.62f, 1.3f, -0.04f, 0.62f, 1.36f, 0.04f, CORNER);
        box(ps, vc, light, -0.04f, 1.3f, -0.62f, 0.04f, 1.36f, 0.62f, CORNER);
        box(ps, vc, light, -0.03f, 1.36f, -0.03f, 0.03f, 1.6f, 0.03f, BLACK);
        box(ps, vc, light, -0.04f, 1.6f, -0.04f, 0.04f, 1.66f, 0.04f, new Mat(new int[]{255, 50, 200}, T_FLAT, true));

        // Cubiertas suspendidas alrededor del reactor mediante soportes
        float speed = this.distort ? 1.2f : 0.35f;
        for (int i = 0; i < 4; i++) {
            float ang = this.time * speed + i * 90.0f;
            float bob = 0.07f * Mth.sin(this.time * 0.05f + i * 1.9f);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(ang));
            float py = 0.75f + bob;
            float pr = 1.1f + (this.distort ? 0.1f : 0.0f);
            rod(ps, vc, light, 0.72f, 0.12f, pr - 0.02f, py - 0.25f);
            rod(ps, vc, light, 0.72f, 0.16f, pr - 0.02f, py + 0.05f);
            ps.translate(pr, py, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(-12.0f));
            box(ps, vc, light, -0.035f, -0.3f, -0.4f, 0.035f, 0.3f, 0.4f, i % 2 == 0 ? PLATE : CORNER);
            box(ps, vc, light, 0.035f, -0.2f, -0.28f, 0.045f, 0.2f, 0.28f, i % 2 == 0 ? CERAMIC : INSUL);
            box(ps, vc, light, 0.03f, 0.28f, -0.4f, 0.05f, 0.3f, 0.4f, violet(0.7f, i));
            box(ps, vc, light, 0.03f, -0.3f, -0.4f, 0.05f, -0.28f, 0.4f, violet(0.7f, i + 2));
            ps.popPose();
        }
        ps.popPose();
    }

    /** Aro cuadrado de la jaula a la altura y; rot gira el aro alrededor del eje vertical. */
    private void ring(PoseStack ps, VertexConsumer vc, int light, float y, float h, float rot) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        float t = 0.045f;
        box(ps, vc, light, -h, y, -h, h, y + 0.07f, -h + 2 * t, CORNER);
        box(ps, vc, light, -h, y, h - 2 * t, h, y + 0.07f, h, CORNER);
        box(ps, vc, light, -h, y, -h, -h + 2 * t, y + 0.07f, h, CORNER);
        box(ps, vc, light, h - 2 * t, y, -h, h, y + 0.07f, h, CORNER);
        ps.popPose();
    }

    /** Soporte delgado en el plano XY (radio, altura) entre dos puntos. */
    private void rod(PoseStack ps, VertexConsumer vc, int light, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
        ps.pushPose();
        ps.translate(x1, y1, 0.0f);
        ps.mulPose(Axis.ZP.rotationDegrees(ang));
        box(ps, vc, light, 0.0f, -0.03f, -0.03f, len, 0.03f, 0.03f, BLACK);
        box(ps, vc, light, len * 0.4f, -0.045f, -0.045f, len * 0.5f, 0.045f, 0.045f, CORNER);
        ps.popPose();
    }

    // ------------------------------------------------------------------ Torreta angular asimétrica

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash) {
        ps.pushPose();
        ps.translate(TURRET_X, DECK, TURRET_Z);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        box(ps, vc, light, -0.72f, 0.0f, -0.72f, 0.72f, 0.06f, 0.72f, BLACK);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.7f, 0.0f, -0.7f, 0.7f, 0.06f, 0.7f, BLACK);
        ps.popPose();
        box(ps, vc, light, -0.55f, 0.06f, -0.6f, 0.55f, 0.34f, 0.6f, PLATE);
        box(ps, vc, light, -0.58f, 0.32f, -0.62f, 0.58f, 0.36f, 0.62f, CORNER);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.5f, 0.34f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -30.0f));
            box(ps, vc, light, s > 0 ? -0.02f : -0.46f, 0.0f, -0.5f, s > 0 ? 0.46f : 0.02f, 0.07f, 0.5f, CERAMIC);
            ps.popPose();
            ps.pushPose();
            ps.translate(s * 0.3f, 0.06f, 0.6f);
            ps.mulPose(Axis.YP.rotationDegrees(s * -28.0f));
            box(ps, vc, light, -0.3f, 0.0f, 0.0f, 0.3f, 0.3f, 0.1f, PLATE);
            box(ps, vc, light, -0.28f, 0.2f, 0.1f, 0.28f, 0.23f, 0.115f, violet(0.6f, s + 2));
            ps.popPose();
        }
        slab(ps, vc, light, 0.0f, 0.52f, -0.1f, -8.0f, 0.46f, 0.1f, -0.55f, 0.55f, PLATE);
        box(ps, vc, light, -0.35f, 0.56f, -0.45f, 0.35f, 0.59f, 0.4f, CERAMIC);
        box(ps, vc, light, -0.03f, 0.59f, -0.42f, 0.03f, 0.595f, 0.38f, violet(0.6f, 1));
        box(ps, vc, light, -0.4f, 0.1f, -0.7f, 0.4f, 0.45f, -0.6f, PLATE);
        box(ps, vc, light, -0.25f, 0.15f, -0.715f, 0.25f, 0.4f, -0.7f, INSUL);
        // cuna blindada del cañón
        box(ps, vc, light, -0.32f, 0.3f, 0.5f, 0.32f, 0.82f, 0.95f, PLATE);
        box(ps, vc, light, -0.34f, 0.78f, 0.5f, 0.34f, 0.84f, 0.95f, CORNER);
        box(ps, vc, light, -0.36f, 0.3f, 0.5f, -0.32f, 0.82f, 0.95f, violet(0.5f, 2));
        box(ps, vc, light, 0.32f, 0.3f, 0.5f, 0.36f, 0.82f, 0.95f, violet(0.5f, 3));
        boolean on = ((int) (this.time / (this.distort ? 3.0f : 12.0f))) % 2 == 0;
        box(ps, vc, light, -0.04f, 0.59f, 0.42f, 0.04f, 0.65f, 0.5f,
                on ? new Mat(new int[]{255, 50, 200}, T_FLAT, true) : new Mat(new int[]{80, 18, 70}, T_FLAT, true));

        // Cilindros hidráulicos de la torreta a la cuna del cañón
        float rad = pitch * Mth.DEG_TO_RAD;
        float ay = 0.6f + 0.3f * Mth.cos(rad) - 0.55f * Mth.sin(rad);
        float az = 0.9f + 0.3f * Mth.sin(rad) + 0.55f * Mth.cos(rad);
        for (int s = -1; s <= 1; s += 2) {
            cylinder(ps, vc, light, s * 0.42f, 0.4f, 0.4f, ay, az);
        }

        // Cañón de energía: eje a (0, 0.6, 0.9) de la torreta
        ps.translate(0.0, 0.6, 0.9);
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
        box(ps, vc, light, -0.1f, -0.1f, 0.04f, 0.1f, 0.1f, 0.12f, CORNER);
        box(ps, vc, light, -0.1f, -0.1f, body - 0.12f, 0.1f, 0.1f, body - 0.04f, CORNER);
        box(ps, vc, light, -0.04f, -0.04f, body, 0.04f, 0.04f, len, BOLT);
        box(ps, vc, light, -0.1f, -0.1f, len - 0.05f, 0.1f, 0.1f, len, BLACK);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash) {
        float k = this.distort ? 1.0f : 0.3f + 0.7f * this.charge;
        box(ps, vc, light, -0.22f, -0.22f, -0.4f, 0.22f, 0.22f, 0.15f, CORNER);
        box(ps, vc, light, -0.17f, -0.17f, 0.15f, 0.17f, 0.17f, 0.9f, BLACK);
        // Púas guía que convergen hacia la boca
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.17f - 0.04f, -0.07f, 0.6f, s * 0.17f + 0.04f, 0.07f, 1.95f, PETAL);
            box(ps, vc, light, s * 0.1f - 0.03f, -0.05f, 1.85f, s * 0.1f + 0.03f, 0.05f, 2.15f, PETAL);
        }
        // Cámara de emisión
        float er = 0.07f + 0.05f * k;
        box(ps, vc, light, -er, -er, 0.2f, er, er, 1.95f, violet(k, 1));
        Mat chamber = this.distort ? WHITE : violet(Math.min(1.0f, k + 0.2f), 3);
        box(ps, vc, light, -0.12f, -0.12f, 1.3f, 0.12f, 0.12f, 1.7f, chamber);
        // Aro mecánico que rodea la cámara: anillo exterior y anillo interior que giran en sentidos opuestos
        ps.pushPose();
        ps.translate(0.0f, 0.0f, 1.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(this.time * (this.distort ? 8.0f : 2.0f)));
        box(ps, vc, light, -0.36f, 0.22f, -0.12f, 0.36f, 0.32f, 0.12f, CORNER);
        box(ps, vc, light, -0.36f, -0.32f, -0.12f, 0.36f, -0.22f, 0.12f, CORNER);
        box(ps, vc, light, 0.22f, -0.32f, -0.12f, 0.36f, 0.32f, 0.12f, CORNER);
        box(ps, vc, light, -0.36f, -0.32f, -0.12f, -0.22f, 0.32f, 0.12f, CORNER);
        for (int i = 0; i < 4; i++) {
            box(ps, vc, light, 0.3f, -0.05f, -0.06f, 0.4f, 0.05f, 0.06f, i % 2 == 0 ? BOLT : PLATE);
            ps.mulPose(Axis.ZP.rotationDegrees(90.0f));
        }
        ps.popPose();
        ps.pushPose();
        ps.translate(0.0f, 0.0f, 1.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(-this.time * (this.distort ? 5.0f : 1.2f)));
        box(ps, vc, light, -0.24f, 0.17f, -0.05f, 0.24f, 0.21f, 0.05f, violet(0.7f, 0));
        box(ps, vc, light, -0.24f, -0.21f, -0.05f, 0.24f, -0.17f, 0.05f, violet(0.7f, 2));
        ps.popPose();
        box(ps, vc, light, -0.2f, -0.2f, 0.95f, 0.2f, 0.2f, 1.05f, PLATE);
        box(ps, vc, light, -0.2f, -0.2f, 1.85f, 0.2f, 0.2f, 1.92f, PLATE);
        box(ps, vc, light, -0.16f, -0.24f, 1.0f, 0.16f, -0.2f, 1.1f, INSUL);
        if (flash > 0.0f) {
            float f = 0.1f + 0.2f * flash;
            box(ps, vc, light, -f, -f, 2.15f, f, f, 2.15f + 0.5f * flash, violet(1.0f, 0));
            box(ps, vc, light, -f * 0.5f, -f * 0.5f, 2.15f, f * 0.5f, f * 0.5f, 2.15f + 0.7f * flash, WHITE);
        }
    }

    // ------------------------------------------------------------------ Luz violeta / magenta

    /** Material violeta luminoso (vira a magenta con la energía); phase desplaza el pulso entre segmentos. */
    private Mat violet(float k, int phase) {
        float v = Mth.clamp(k + 0.12f * Mth.sin(this.time * 0.1f + phase * 1.3f), 0.0f, 1.0f);
        return new Mat(new int[]{(int) (90 + 120 * v), (int) (30 + 60 * v), (int) (170 + 85 * v)}, T_FLAT, true);
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
