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
import org.joml.Matrix4f;

/**
 * Dibuja el tanque «Sigilo»: casco extremadamente bajo y alargado con morro puntiagudo de nave furtiva, superficies
 * inclinadas de material compuesto negro casi liso, torreta estrecha y aplanada, cañón largo y delgado con cubierta,
 * visor cian en el frente, cámaras retráctiles laterales y orugas estrechas parcialmente cubiertas.
 * Durante el camuflaje se dibuja translúcido, con contornos azulados que se distorsionan.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Largo ~3 (-1.45 a 1.55), ancho 2.
 * El cañón gira desde el eje que fija StealthTankEntity (pivotY 0.46, pivotForward 0.25, largo 1.85).
 */
public class StealthTankRenderer extends EntityRenderer<StealthTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_sigilo.png");

    private static final int T_COMP = 0, T_COMP2 = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5, T_RUBBER = 6;

    private record Mat(int[] c, int tile, boolean glow) {
        Mat(int[] c, int tile) {
            this(c, tile, false);
        }
    }

    private static final int[] W = {255, 255, 255};
    private static final Mat COMP = new Mat(W, T_COMP);
    private static final Mat COMP2 = new Mat(W, T_COMP2);
    private static final Mat TRACK = new Mat(W, T_TRACK);
    private static final Mat HUB = new Mat(W, T_HUB);
    private static final Mat VENT = new Mat(W, T_VENT);
    private static final Mat RUBBER = new Mat(W, T_RUBBER);
    private static final Mat DARK = new Mat(new int[]{18, 19, 22}, T_FLAT);

    private static final float[] WHEEL_Z = {-1.2f, -0.65f, -0.1f, 0.45f, 0.95f};
    private static final float WHEEL_Y = 0.15f;
    private static final float WHEEL_X = 0.82f;
    private static final float WHEEL_SPIN = 0.34f / 0.14f;     // ruedas pequeñas: giran más rápido

    private static final float NOSE_Z0 = 0.5f;
    private static final float NOSE_Z1 = 1.55f;
    private static final float NOSE_HW = 0.95f;
    private static final int NOSE_STEPS = 8;

    /** Estado del dibujo actual (el render corre en un solo hilo). */
    private float fade;
    private int alpha = 255;

    public StealthTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.3f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(StealthTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(StealthTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(StealthTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.fade = Mth.lerp(partialTick, tank.stealthFadeO, tank.stealthFade);
        this.alpha = (int) (255 * (1.0f - 0.72f * this.fade));
        float time = tank.tickCount + partialTick;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(this.fade > 0.02f
                ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);
        drawCameras(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch);

        if (this.fade > 0.03f) {
            drawDistortion(ps, buffer.getBuffer(RenderType.lightning()), time, this.fade);
        }
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int side = 1; side >= -1; side -= 2) {
            float x1 = side > 0 ? 0.68f : -0.98f;
            float x2 = side > 0 ? 0.98f : -0.68f;
            box(ps, vc, light, x1, 0.0f, -1.45f, x2, 0.05f, 1.05f, TRACK);
            box(ps, vc, light, x1, 0.25f, -1.45f, x2, 0.3f, 1.05f, TRACK);
            box(ps, vc, light, x1 + 0.02f, 0.03f, -1.5f, x2 - 0.02f, 0.27f, -1.42f, TRACK);
            box(ps, vc, light, x1 + 0.02f, 0.03f, 1.02f, x2 - 0.02f, 0.27f, 1.1f, TRACK);
            float angle = side > 0 ? angleL : angleR;
            for (float z : WHEEL_Z) {
                wheel(ps, vc, light, side * WHEEL_X, WHEEL_Y, z, angle);
            }
        }
    }

    /** Rueda interna pequeña (octogonal): casi no se ve bajo el faldón. */
    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.11f;
        final float r = 0.1f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.popPose();
        ps.popPose();
    }

    // ------------------------------------------------------------------ Casco

    private float noseHalfWidth(float z) {
        return NOSE_HW * Mth.clamp((NOSE_Z1 - z) / (NOSE_Z1 - NOSE_Z0), 0.03f, 1.0f);
    }

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Cuerpo bajo y cubierta casi lisa
        box(ps, vc, light, -0.62f, 0.12f, -1.4f, 0.62f, 0.34f, NOSE_Z0, COMP);
        box(ps, vc, light, -0.56f, 0.34f, -1.36f, 0.56f, 0.4f, NOSE_Z0, COMP2);
        // Faldones inclinados sobre las orugas (la superficie lateral baja hacia afuera)
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.58f, 0.37f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(-s * 28.0f));
            box(ps, vc, light, s > 0 ? 0.0f : -0.42f, -0.035f, -1.42f, s > 0 ? 0.42f : 0.0f, 0.0f, NOSE_Z0, COMP2);
            ps.popPose();
        }
        // Parte trasera inclinada
        ps.pushPose();
        ps.translate(0.0f, 0.37f, -1.38f);
        ps.mulPose(Axis.XP.rotationDegrees(-32.0f));
        box(ps, vc, light, -0.6f, -0.035f, -0.3f, 0.6f, 0.0f, 0.0f, COMP2);
        ps.popPose();
        box(ps, vc, light, -0.62f, 0.12f, -1.46f, 0.62f, 0.26f, -1.4f, COMP);

        // Morro puntiagudo: capas que se estrechan y bajan, como la nariz de un avión furtivo
        float step = (NOSE_Z1 - NOSE_Z0) / NOSE_STEPS;
        for (int k = 0; k < NOSE_STEPS; k++) {
            float z0 = NOSE_Z0 + k * step;
            float z1 = z0 + step;
            float hw = noseHalfWidth(z1);
            float top = 0.4f - 0.2f * (k + 1) / NOSE_STEPS;
            box(ps, vc, light, -hw, 0.12f, z0, hw, Math.max(0.2f, top - 0.04f), z1, COMP);
            box(ps, vc, light, -hw * 0.82f, Math.max(0.2f, top - 0.04f), z0, hw * 0.82f, top, z1, COMP2);
        }
        // Visor cian: una franja que recorre el frontal (se atenúa durante el sigilo)
        float vk = 3;
        float vz0 = NOSE_Z0 + vk * step;
        float vhw = noseHalfWidth(vz0 + step) * 0.82f;
        float vtop = 0.4f - 0.2f * (vk + 1) / NOSE_STEPS;
        Mat visor = glow(120, 235, 255, 1.0f - 0.8f * this.fade);
        box(ps, vc, light, -vhw, vtop, vz0 + 0.02f, vhw, vtop + 0.012f, vz0 + step - 0.02f, visor);

        // Escapes ocultos: ranuras bajo el borde trasero
        box(ps, vc, light, -0.3f, 0.14f, -1.472f, 0.3f, 0.2f, -1.44f, VENT);

        // Luces (apagadas durante el sigilo)
        if (this.fade < 0.5f) {
            Mat lamp = glow(200, 235, 255, 1.0f - this.fade * 2.0f);
            float lhw = noseHalfWidth(NOSE_Z0 + 2 * step);
            box(ps, vc, light, lhw * 0.55f, 0.2f, NOSE_Z0 + 2 * step - 0.03f, lhw * 0.55f + 0.1f, 0.24f, NOSE_Z0 + 2 * step + 0.02f, lamp);
            box(ps, vc, light, -lhw * 0.55f - 0.1f, 0.2f, NOSE_Z0 + 2 * step - 0.03f, -lhw * 0.55f, 0.24f, NOSE_Z0 + 2 * step + 0.02f, lamp);
            Mat tail = glow(255, 40, 30, 1.0f - this.fade * 2.0f);
            box(ps, vc, light, -0.58f, 0.27f, -1.49f, -0.42f, 0.3f, -1.46f, tail);
            box(ps, vc, light, 0.42f, 0.27f, -1.49f, 0.58f, 0.3f, -1.46f, tail);
        }
    }

    private static Mat glow(int r, int g, int b, float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{(int) (r * k), (int) (g * k), (int) (b * k)}, T_FLAT, k > 0.5f);
    }

    /** Cámaras laterales retráctiles: salen del casco mientras el tanque está en sigilo, explorando. */
    private void drawCameras(PoseStack ps, VertexConsumer vc, int light) {
        float e = Mth.clamp((this.fade - 0.2f) / 0.8f, 0.0f, 1.0f);
        if (e <= 0.02f) {
            return;
        }
        float reach = 0.45f * e;
        Mat lens = glow(110, 230, 255, 0.35f + 0.65f * e);
        for (int s = -1; s <= 1; s += 2) {
            for (float z : new float[]{0.2f, -0.7f}) {
                float xa = s > 0 ? 0.55f : -0.55f - reach;
                float xb = s > 0 ? 0.55f + reach : -0.55f;
                box(ps, vc, light, xa, 0.26f, z - 0.015f, xb, 0.29f, z + 0.015f, COMP2);   // brazo
                float hx = s > 0 ? 0.55f + reach : -0.55f - reach - 0.1f;
                box(ps, vc, light, hx, 0.24f, z - 0.05f, hx + 0.1f, 0.34f, z + 0.05f, COMP);     // cabeza
                float lx = s > 0 ? hx + 0.1f : hx - 0.015f;
                box(ps, vc, light, lx, 0.27f, z - 0.025f, lx + 0.015f, 0.31f, z + 0.025f, lens);  // lente
            }
        }
    }

    // ------------------------------------------------------------------ Torreta

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch) {
        ps.pushPose();
        ps.translate(0.0, 0.36, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Torreta estrecha y aplanada, medio hundida en el casco
        box(ps, vc, light, -0.3f, 0.0f, -0.55f, 0.3f, 0.1f, 0.2f, COMP);
        box(ps, vc, light, -0.2f, 0.1f, -0.5f, 0.2f, 0.14f, 0.1f, COMP2);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.3f, 0.1f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(-s * 38.0f));
            box(ps, vc, light, s > 0 ? 0.0f : -0.14f, -0.03f, -0.52f, s > 0 ? 0.14f : 0.0f, 0.0f, 0.18f, COMP2);
            ps.popPose();
        }
        ps.pushPose();                                     // frente inclinado
        ps.translate(0.0f, 0.1f, 0.2f);
        ps.mulPose(Axis.XP.rotationDegrees(34.0f));
        box(ps, vc, light, -0.28f, -0.03f, 0.0f, 0.28f, 0.0f, 0.2f, COMP2);
        ps.popPose();
        ps.pushPose();                                     // popa inclinada
        ps.translate(0.0f, 0.1f, -0.55f);
        ps.mulPose(Axis.XP.rotationDegrees(-34.0f));
        box(ps, vc, light, -0.28f, -0.03f, -0.2f, 0.28f, 0.0f, 0.0f, COMP2);
        ps.popPose();
        box(ps, vc, light, -0.16f, 0.14f, -0.12f, 0.16f, 0.145f, 0.05f, DARK);   // escotilla al ras (casi invisible)

        // Cañón largo y delgado con cubierta que se funde con la estructura
        ps.translate(0.0, 0.1, 0.25);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        box(ps, vc, light, -0.09f, -0.07f, -0.05f, 0.09f, 0.07f, 0.2f, COMP);          // mantelete integrado
        box(ps, vc, light, -0.075f, -0.06f, 0.2f, 0.075f, 0.06f, 1.0f, COMP2);         // cubierta
        box(ps, vc, light, -0.04f, -0.04f, 1.0f, 0.04f, 0.04f, 1.8f, RUBBER);          // tubo delgado
        box(ps, vc, light, -0.05f, -0.05f, 1.72f, 0.05f, 0.05f, 1.85f, DARK);          // boca
        ps.popPose();
    }

    // ------------------------------------------------------------------ Distorsión del camuflaje

    private void drawDistortion(PoseStack ps, VertexConsumer vc, float time, float f) {
        Matrix4f m = ps.last().pose();
        // Contornos del casco que tiemblan y se desplazan, en tonos azulados
        for (int pass = 0; pass < 3; pass++) {
            float amp = 0.012f + 0.014f * pass;
            float ph = time * (0.55f + 0.2f * pass) + pass * 2.1f;
            float a = (0.30f - 0.07f * pass) * f;
            float y0 = 0.0f, y1 = 0.42f;
            float[][] poly = {{-0.97f, -1.46f}, {0.97f, -1.46f}, {0.97f, NOSE_Z0}, {0.0f, NOSE_Z1}, {-0.97f, NOSE_Z0}};
            for (int i = 0; i < poly.length; i++) {
                float[] p0 = poly[i];
                float[] p1 = poly[(i + 1) % poly.length];
                for (int level = 0; level < 2; level++) {
                    float y = level == 0 ? y0 : y1;
                    float j0 = Mth.sin(ph + p0[0] * 7.0f + p0[1] * 5.0f + level) * amp;
                    float j1 = Mth.sin(ph + p1[0] * 7.0f + p1[1] * 5.0f + level) * amp;
                    line(vc, m, p0[0] + j0, y + j0, p0[1], p1[0] + j1, y + j1, p1[1], 0.01f, 0.35f, 0.7f, 1.0f, a);
                }
                float jv = Mth.sin(ph + p0[0] * 3.0f + p0[1] * 4.0f) * amp;
                line(vc, m, p0[0] + jv, y0, p0[1], p0[0] + jv, y1, p0[1], 0.01f, 0.35f, 0.7f, 1.0f, a);
            }
        }
        // Bandas de distorsión que recorren el casco de atrás hacia delante
        for (int b = 0; b < 3; b++) {
            float t = ((time * 0.025f + b / 3.0f) % 1.0f);
            float z = -1.45f + t * 2.9f;
            float hw = z > NOSE_Z0 ? noseHalfWidth(z) : 0.95f;
            float wob = Mth.sin(time * 0.8f + b * 2.0f) * 0.02f;
            quad2(vc, m, -hw, 0.02f, z + wob, hw, 0.02f, z - wob, hw, 0.4f, z - wob, -hw, 0.4f, z + wob,
                    0.35f, 0.7f, 1.0f, 0.10f * f * (1.0f - Math.abs(t - 0.5f) * 1.2f));
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float cr, float cg, float cb, float ca) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5f) {
            return;
        }
        float px = -dz, pz = dx;
        float pl = Mth.sqrt(px * px + pz * pz);
        if (pl < 1.0E-4f) {
            px = 1.0f;
            pz = 0.0f;
            pl = 1.0f;
        }
        px = px / pl * r;
        pz = pz / pl * r;
        quad2(vc, m, x1 + px, y1, z1 + pz, x1 - px, y1, z1 - pz, x2 - px, y2, z2 - pz, x2 + px, y2, z2 + pz, cr, cg, cb, ca);
        quad2(vc, m, x1, y1 + r, z1, x1, y1 - r, z1, x2, y2 - r, z2, x2, y2 + r, z2, cr, cg, cb, ca);
    }

    private static void quad2(VertexConsumer vc, Matrix4f m,
                              float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz,
                              float r, float g, float b, float a) {
        v(vc, m, ax, ay, az, r, g, b, a); v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a); v(vc, m, dx, dy, dz, r, g, b, a);
        v(vc, m, dx, dy, dz, r, g, b, a); v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a); v(vc, m, ax, ay, az, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    // ------------------------------------------------------------------ Geometría

    private void box(PoseStack ps, VertexConsumer vc, int light,
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

    private void quad(PoseStack.Pose pose, VertexConsumer vc, int light, Mat m, float spanU, float spanV,
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
        // Durante el camuflaje el material se tiñe de azul frío (los que brillan conservan su color)
        int[] c = m.c();
        int r = c[0], g = c[1], b = c[2];
        if (!m.glow() && this.fade > 0.0f) {
            r = (int) (r * (1.0f - 0.45f * this.fade));
            g = (int) (g * (1.0f - 0.2f * this.fade));
        }
        vertex(pose, vc, light, r, g, b, u0, v1, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, r, g, b, u1, v1, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, r, g, b, u1, v0, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, r, g, b, u0, v0, nx, ny, nz, dx, dy, dz);
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int r, int g, int b, float u, float v,
                        float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, this.alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
