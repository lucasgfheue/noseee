package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
 * Dibuja la torreta con cajas (sin archivos de modelo), usando las casillas de la textura del tanque.
 * Eje local: +Z = hacia delante del arma, +Y = arriba. La base no gira; el arma sigue la mira del jugador.
 */
public class TurretRenderer extends EntityRenderer<TurretEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque.png");

    private record Mat(int[] c, int tile) {}

    private static final Mat STEEL = new Mat(new int[]{200, 204, 212}, 1);
    private static final Mat PLATE = new Mat(new int[]{190, 194, 200}, 0);
    private static final Mat RUBBER = new Mat(new int[]{110, 110, 114}, 6);
    private static final Mat GREEN = new Mat(new int[]{255, 255, 255}, 8);
    private static final Mat DARK_GREEN = new Mat(new int[]{150, 150, 156}, 8);
    private static final Mat BLACK_SKIN = new Mat(new int[]{255, 255, 255}, 9);

    public TurretRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.7f;
    }

    @Override
    public ResourceLocation getTextureLocation(TurretEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(TurretEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(2.0));
    }

    @Override
    public void render(TurretEntity t, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        boolean heavy = t.getTurretType() == TurretEntity.HEAVY;
        Mat body = heavy ? BLACK_SKIN : GREEN;
        Mat dark = heavy ? RUBBER : DARK_GREEN;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float yaw = entityYaw;
        float pitch = 0.0f;
        if (t.getFirstPassenger() instanceof LivingEntity rider) {
            yaw = rider.getViewYRot(partialTick);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), -70.0f, 45.0f);
        }

        ps.pushPose();
        // Base fija: placa, patas y columna central
        box(ps, vc, light, -0.5f, 0.0f, -0.5f, 0.5f, 0.12f, 0.5f, PLATE);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(ps, vc, light, sx * 0.42f - 0.08f, 0.0f, sz * 0.42f - 0.08f,
                        sx * 0.42f + 0.08f, 0.2f, sz * 0.42f + 0.08f, RUBBER);
            }
        }
        box(ps, vc, light, -0.16f, 0.12f, -0.16f, 0.16f, 0.85f, 0.16f, STEEL);
        box(ps, vc, light, -0.3f, 0.85f, -0.3f, 0.3f, 0.98f, 0.3f, dark);

        // Parte giratoria
        ps.pushPose();
        ps.translate(0.0, 0.98, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Escudo frontal con mirilla y soporte trasero
        float sw = heavy ? 0.6f : 0.5f;
        box(ps, vc, light, -sw, 0.0f, 0.3f, sw, 0.62f, 0.38f, body);
        box(ps, vc, light, -sw, 0.62f, 0.3f, sw, 0.68f, 0.38f, dark);
        box(ps, vc, light, -0.09f, 0.1f, 0.38f, 0.09f, 0.16f, 0.44f, STEEL);
        box(ps, vc, light, -0.2f, 0.0f, -0.35f, 0.2f, 0.1f, 0.3f, dark);

        // Arma con cabeceo
        ps.pushPose();
        ps.translate(0.0, 0.12, 0.0);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        if (heavy) {
            box(ps, vc, light, -0.3f, -0.25f, -0.45f, 0.3f, 0.25f, 0.45f, body);          // carcasa
            box(ps, vc, light, -0.12f, -0.12f, 0.45f, 0.12f, 0.12f, 1.6f, STEEL);          // cañón
            box(ps, vc, light, -0.18f, -0.18f, 1.5f, 0.18f, 0.18f, 1.7f, dark);            // freno de boca
            box(ps, vc, light, -0.2f, -0.3f, -0.6f, 0.2f, 0.0f, -0.45f, RUBBER);           // culata
        } else {
            box(ps, vc, light, -0.12f, -0.12f, -0.35f, 0.12f, 0.12f, 0.4f, dark);          // receptor
            box(ps, vc, light, -0.05f, -0.05f, 0.4f, 0.05f, 0.05f, 1.3f, STEEL);           // cañón
            box(ps, vc, light, -0.08f, -0.08f, 0.55f, 0.08f, 0.08f, 0.85f, RUBBER);        // funda
            box(ps, vc, light, -0.1f, -0.1f, 1.25f, 0.1f, 0.1f, 1.4f, dark);               // apagallamas
            box(ps, vc, light, 0.13f, -0.22f, -0.15f, 0.3f, 0.02f, 0.2f, body);            // caja de munición
            box(ps, vc, light, -0.2f, -0.05f, -0.5f, 0.2f, 0.05f, -0.35f, RUBBER);         // empuñaduras
        }
        ps.popPose();
        ps.popPose();
        ps.popPose();
        super.render(t, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Cajas (mismo método que el tanque)

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        PoseStack.Pose pose = ps.last();
        float xl = x2 - x1, yl = y2 - y1, zl = z2 - z1;
        quad(pose, vc, light, m, zl, xl, 0, 1, 0, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(pose, vc, light, m, xl, zl, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(pose, vc, light, m, yl, xl, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(pose, vc, light, m, xl, yl, 0, 0, 1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(pose, vc, light, m, zl, yl, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(pose, vc, light, m, yl, zl, 1, 0, 0, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
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
