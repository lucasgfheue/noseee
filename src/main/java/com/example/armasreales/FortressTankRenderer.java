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
 * Dibuja el tanque «Fortaleza»: casco ancho y bajo con grandes placas hexagonales de acero negro sobre
 * estructura de titanio, seis ruedas con faldones, torreta angular con cañón pesado y sensores, cuatro emisores
 * de escudo en las esquinas y el símbolo del Proyecto X en la torreta.
 *
 * Coordenadas del casco: X lateral (+X izquierda), Y arriba, Z adelante. Mide 3 de largo (±1.5), 2 de ancho (±1).
 * El cañón gira desde el mismo eje que el tanque base (TankEntity.PIVOT_*).
 */
public class FortressTankRenderer extends EntityRenderer<FortressTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_fortaleza.png");

    /** Casillas del atlas 4x4 (64 px): cada una trae su propio color, por eso el tinte es blanco. */
    private static final int T_PLATE = 0, T_TITAN = 1, T_TRACK = 2, T_HUB = 3, T_GRILLE = 4, T_FLAT = 5,
            T_RUBBER = 6, T_EMBLEM = 7;

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
    private static final Mat GRILLE = new Mat(W, T_GRILLE);
    private static final Mat RUBBER = new Mat(W, T_RUBBER);
    private static final Mat BOLT = new Mat(new int[]{170, 175, 185}, T_FLAT);
    private static final Mat DARKFLAT = new Mat(new int[]{26, 28, 32}, T_FLAT);
    private static final Mat BLUE = new Mat(new int[]{130, 215, 255}, T_FLAT, true);
    private static final Mat BLUEDIM = new Mat(new int[]{60, 150, 230}, T_FLAT, true);
    private static final Mat EMBLEM = new Mat(W, T_EMBLEM);

    private static final float[] WHEEL_Z = {-1.25f, -0.75f, -0.25f, 0.25f, 0.75f, 1.25f};
    private static final float WHEEL_Y = 0.3f;
    private static final float WHEEL_X = 0.83f;
    /** TankEntity anima con el radio de las ruedas del tanque base (0,34); estas son más pequeñas. */
    private static final float WHEEL_SPIN = 0.34f / 0.24f;

    public FortressTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.3f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(FortressTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FortressTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(FortressTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch);

        int shield = tank.shieldRemaining();
        if (shield > 0) {
            drawShield(ps, buffer.getBuffer(RenderType.lightning()), shield, tank.tickCount + partialTick);
        }

        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas y ruedas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int side = 1; side >= -1; side -= 2) {
            float x1 = side > 0 ? 0.66f : -1.0f;
            float x2 = side > 0 ? 1.0f : -0.66f;
            box(ps, vc, light, x1, 0.0f, -1.5f, x2, 0.06f, 1.5f, TRACK);          // oruga inferior
            box(ps, vc, light, x1, 0.54f, -1.5f, x2, 0.6f, 1.5f, TRACK);          // oruga superior
            box(ps, vc, light, x1 + 0.02f, 0.04f, -1.52f, x2 - 0.02f, 0.56f, -1.44f, TRACK);   // extremos
            box(ps, vc, light, x1 + 0.02f, 0.04f, 1.44f, x2 - 0.02f, 0.56f, 1.52f, TRACK);
            // Segmentos metálicos reforzados
            for (int i = 0; i < 12; i++) {
                float z = -1.46f + i * 0.25f;
                box(ps, vc, light, x1 - 0.015f, -0.01f, z, x2 + 0.015f, 0.04f, z + 0.14f, PLATE);
                box(ps, vc, light, x1 - 0.015f, 0.56f, z, x2 + 0.015f, 0.61f, z + 0.14f, PLATE);
            }
            float angle = side > 0 ? angleL : angleR;
            for (float z : WHEEL_Z) {
                wheel(ps, vc, light, side * WHEEL_X, WHEEL_Y, z, angle);
            }
            // Faldón blindado por fuera, en placas separadas por juntas
            float fx1 = side > 0 ? 0.99f : -1.05f;
            float fx2 = side > 0 ? 1.05f : -0.99f;
            for (int i = 0; i < 5; i++) {
                float z1 = -1.42f + i * 0.56f;
                box(ps, vc, light, fx1, 0.26f, z1, fx2, 0.66f, z1 + 0.52f, TITAN);
                box(ps, vc, light, fx1 - (side > 0 ? 0.0f : 0.0f), 0.24f, z1 + 0.02f, fx2, 0.26f, z1 + 0.5f, DARKFLAT);
                float px = side > 0 ? 1.05f : -1.05f;
                hex(ps, vc, light, 'x', px, z1 + 0.26f, 0.46f, 0.15f, side, PLATE);
            }
        }
    }

    /** Rueda octogonal con buje y tornillos: se nota cuando gira. */
    private void wheel(PoseStack ps, VertexConsumer vc, int light, float cx, float cy, float cz, float angle) {
        final float h = 0.17f;
        final float r = 0.17f;
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -h, -r, -r, h, r, r, HUB);
        ps.popPose();
        box(ps, vc, light, -h - 0.01f, -0.05f, -0.05f, h + 0.01f, 0.05f, 0.05f, BOLT);
        box(ps, vc, light, -h - 0.015f, 0.09f, -0.025f, h + 0.015f, 0.15f, 0.025f, BOLT);
        box(ps, vc, light, -h - 0.015f, -0.15f, -0.025f, h + 0.015f, -0.09f, 0.025f, BOLT);
        ps.popPose();
    }

    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.7f, 0.18f, -1.4f, 0.7f, 0.64f, 1.4f, TITAN);               // estructura de titanio
        box(ps, vc, light, -1.0f, 0.62f, -1.42f, 1.0f, 0.9f, 1.4f, TITAN);               // cubierta ancha y voluminosa
        box(ps, vc, light, -1.0f, 0.6f, -1.44f, 1.0f, 0.64f, 1.44f, DARKFLAT);           // junta bajo la cubierta
        // Frente casi vertical con un ligero escalón
        box(ps, vc, light, -0.7f, 0.18f, 1.4f, 0.7f, 0.64f, 1.46f, TITAN);
        box(ps, vc, light, -1.0f, 0.62f, 1.4f, 1.0f, 0.9f, 1.47f, PLATE);
        // Ganchos de remolque
        box(ps, vc, light, -0.4f, 0.22f, 1.46f, -0.28f, 0.32f, 1.54f, BOLT);
        box(ps, vc, light, 0.28f, 0.22f, 1.46f, 0.4f, 0.32f, 1.54f, BOLT);

        // Placas hexagonales de acero negro: frente, cubierta y laterales
        for (int i = -1; i <= 1; i++) {
            hex(ps, vc, light, 'z', 1.47f, i * 0.5f, 0.73f, 0.24f, 1, PLATE);
        }
        for (int i = -2; i <= 1; i++) {
            hex(ps, vc, light, 'z', 1.47f, i * 0.5f + 0.25f, 0.4f, 0.22f, 1, PLATE);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int j = 0; j < 4; j++) {
                hex(ps, vc, light, 'y', 0.9f, sx * 0.8f, -0.7f + j * 0.5f, 0.2f, 1, PLATE);
            }
            for (int j = 0; j < 8; j++) {
                hex(ps, vc, light, 'x', sx * 1.0f, -1.12f + j * 0.32f, 0.76f, 0.13f, sx, PLATE);
            }
        }
        for (int i = -2; i <= 2; i++) {
            hex(ps, vc, light, 'y', 0.9f, i * 0.34f, 1.15f, 0.2f, 1, PLATE);
        }

        // Rejillas de refrigeración en la parte trasera (cubierta y panel trasero)
        box(ps, vc, light, -0.55f, 0.9f, -1.38f, 0.55f, 0.94f, -0.8f, GRILLE);
        box(ps, vc, light, -0.58f, 0.9f, -1.4f, -0.55f, 0.97f, -0.78f, DARKFLAT);
        box(ps, vc, light, 0.55f, 0.9f, -1.4f, 0.58f, 0.97f, -0.78f, DARKFLAT);
        box(ps, vc, light, -0.58f, 0.9f, -1.4f, 0.58f, 0.97f, -1.37f, DARKFLAT);
        box(ps, vc, light, -0.58f, 0.9f, -0.81f, 0.58f, 0.97f, -0.78f, DARKFLAT);
        for (int side = -1; side <= 1; side += 2) {
            float a = side > 0 ? 0.08f : -0.62f;
            box(ps, vc, light, a, 0.3f, -1.47f, a + 0.54f, 0.84f, -1.4f, GRILLE);
            box(ps, vc, light, a - 0.03f, 0.27f, -1.48f, a + 0.57f, 0.3f, -1.4f, DARKFLAT);
            box(ps, vc, light, a - 0.03f, 0.84f, -1.48f, a + 0.57f, 0.87f, -1.4f, DARKFLAT);
        }
        // Escapes
        box(ps, vc, light, -0.1f, 0.3f, -1.52f, 0.1f, 0.5f, -1.4f, BOLT);

        // Luces azules estrechas
        box(ps, vc, light, -0.62f, 0.84f, 1.47f, -0.08f, 0.875f, 1.5f, BLUE);
        box(ps, vc, light, 0.08f, 0.84f, 1.47f, 0.62f, 0.875f, 1.5f, BLUE);
        box(ps, vc, light, -0.5f, 0.88f, -1.46f, 0.5f, 0.91f, -1.43f, BLUEDIM);
        for (int side = -1; side <= 1; side += 2) {
            float xa = side > 0 ? 1.0f : -1.03f;
            float xb = side > 0 ? 1.03f : -1.0f;
            box(ps, vc, light, xa, 0.7f, -1.3f, xb, 0.725f, 1.3f, BLUEDIM);
        }

        // Cuatro emisores de escudo en las esquinas
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                emitter(ps, vc, light, sx * 0.9f, 0.9f, sz * 1.28f);
            }
        }
    }

    private void emitter(PoseStack ps, VertexConsumer vc, int light, float x, float y, float z) {
        box(ps, vc, light, x - 0.1f, y, z - 0.1f, x + 0.1f, y + 0.06f, z + 0.1f, DARKFLAT);   // base
        box(ps, vc, light, x - 0.065f, y + 0.06f, z - 0.065f, x + 0.065f, y + 0.24f, z + 0.065f, TITAN);
        box(ps, vc, light, x - 0.08f, y + 0.14f, z - 0.08f, x + 0.08f, y + 0.17f, z + 0.08f, PLATE);
        box(ps, vc, light, x - 0.04f, y + 0.24f, z - 0.04f, x + 0.04f, y + 0.34f, z + 0.04f, BLUE);   // punta luminosa
        box(ps, vc, light, x - 0.075f, y + 0.24f, z - 0.075f, x + 0.075f, y + 0.27f, z + 0.075f, BOLT);
    }

    // ------------------------------------------------------------------ Torreta

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch) {
        ps.pushPose();
        ps.translate(0.0, 0.9, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        box(ps, vc, light, -0.64f, 0.0f, -0.72f, 0.64f, 0.07f, 0.66f, DARKFLAT);          // anillo de la torreta
        box(ps, vc, light, -0.6f, 0.07f, -0.74f, 0.6f, 0.38f, 0.5f, TITAN);               // cuerpo
        box(ps, vc, light, -0.5f, 0.38f, -0.7f, 0.5f, 0.5f, 0.42f, TITAN);                // cubierta superior
        box(ps, vc, light, -0.42f, 0.5f, -0.62f, 0.42f, 0.55f, 0.3f, PLATE);              // escalón del techo
        // Frente angular en escalones
        box(ps, vc, light, -0.5f, 0.07f, 0.5f, 0.5f, 0.3f, 0.62f, TITAN);
        box(ps, vc, light, -0.4f, 0.3f, 0.42f, 0.4f, 0.4f, 0.56f, PLATE);
        box(ps, vc, light, -0.3f, 0.07f, 0.62f, 0.3f, 0.26f, 0.66f, PLATE);
        // Flancos inclinados (placas angulares)
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(side * 0.58f, 0.34f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(-side * 32.0f));
            box(ps, vc, light, side > 0 ? 0.0f : -0.1f, 0.0f, -0.7f, side > 0 ? 0.1f : 0.0f, 0.2f, 0.38f, PLATE);
            ps.popPose();
        }
        // Caja trasera con rejilla
        box(ps, vc, light, -0.45f, 0.1f, -0.98f, 0.45f, 0.42f, -0.74f, TITAN);
        box(ps, vc, light, -0.32f, 0.18f, -1.0f, 0.32f, 0.34f, -0.98f, GRILLE);
        // Placas hexagonales en la torreta
        for (int sx = -1; sx <= 1; sx += 2) {
            hex(ps, vc, light, 'x', sx * 0.6f, 0.28f, 0.22f, 0.15f, sx, PLATE);
            hex(ps, vc, light, 'x', sx * 0.6f, -0.62f, 0.2f, 0.12f, sx, PLATE);
        }
        hex(ps, vc, light, 'y', 0.55f, -0.18f, -0.22f, 0.15f, 1, PLATE);
        hex(ps, vc, light, 'y', 0.55f, 0.18f, -0.22f, 0.15f, 1, PLATE);
        hex(ps, vc, light, 'y', 0.5f, 0.0f, 0.34f, 0.0f, 1, PLATE);
        // Símbolo del Proyecto X grabado en la torreta (flancos y parte trasera)
        for (int sx = -1; sx <= 1; sx += 2) {
            float xa = sx > 0 ? 0.606f : -0.63f;
            float xb = sx > 0 ? 0.63f : -0.606f;
            boxFull(ps, vc, light, xa, 0.07f, -0.42f, xb, 0.37f, -0.12f, EMBLEM);
        }
        boxFull(ps, vc, light, -0.16f, 0.18f, -1.012f, 0.16f, 0.4f, -0.99f, EMBLEM);
        // Módulos laterales de sensores
        for (int sx = -1; sx <= 1; sx += 2) {
            float xa = sx > 0 ? 0.6f : -0.84f;
            float xb = sx > 0 ? 0.84f : -0.6f;
            box(ps, vc, light, xa, 0.14f, 0.1f, xb, 0.34f, 0.46f, TITAN);
            box(ps, vc, light, xa, 0.12f, 0.08f, xb, 0.14f, 0.48f, DARKFLAT);
            box(ps, vc, light, xa + (sx > 0 ? 0.04f : 0.0f), 0.18f, 0.46f, xb - (sx > 0 ? 0.0f : 0.04f), 0.3f, 0.49f, BLUE);   // lente
            float tx = sx > 0 ? 0.72f : -0.76f;
            box(ps, vc, light, tx, 0.34f, 0.18f, tx + 0.04f, 0.5f, 0.22f, BOLT);                          // antenita
            box(ps, vc, light, tx - 0.005f, 0.5f, 0.175f, tx + 0.045f, 0.54f, 0.225f, BLUEDIM);
        }
        // Escotilla del conductor
        box(ps, vc, light, -0.3f, 0.55f, 0.04f, 0.3f, 0.66f, 0.1f, DARKFLAT);
        box(ps, vc, light, -0.3f, 0.55f, -0.46f, 0.3f, 0.66f, -0.4f, DARKFLAT);
        box(ps, vc, light, 0.24f, 0.55f, -0.4f, 0.3f, 0.66f, 0.04f, DARKFLAT);
        box(ps, vc, light, -0.3f, 0.55f, -0.4f, -0.24f, 0.66f, 0.04f, DARKFLAT);
        box(ps, vc, light, -0.24f, 0.55f, -0.4f, 0.24f, 0.58f, 0.04f, RUBBER);

        // Mantelete pesado
        box(ps, vc, light, -0.36f, 0.1f, 0.62f, 0.36f, 0.58f, 0.76f, PLATE);
        box(ps, vc, light, -0.28f, 0.14f, 0.76f, 0.28f, 0.54f, 0.8f, DARKFLAT);

        // Cañón principal: gira alrededor del mismo eje que el tanque base
        ps.translate(0.0, 0.34, 0.72);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        box(ps, vc, light, -0.14f, -0.14f, 0.0f, 0.14f, 0.14f, 0.3f, RUBBER);             // culata
        box(ps, vc, light, -0.12f, -0.12f, 0.3f, 0.12f, 0.12f, 0.8f, TITAN);               // camisa
        box(ps, vc, light, -0.135f, -0.135f, 0.78f, 0.135f, 0.135f, 0.82f, BLUEDIM);       // anillo azul
        box(ps, vc, light, -0.09f, -0.09f, 0.8f, 0.09f, 0.09f, 1.4f, TITAN);               // tubo
        box(ps, vc, light, -0.115f, -0.115f, 0.95f, 0.115f, 0.115f, 1.12f, PLATE);         // evacuador de humos
        box(ps, vc, light, -0.15f, -0.15f, 1.12f, 0.15f, 0.15f, 1.4f, DARKFLAT);           // freno de boca
        box(ps, vc, light, -0.155f, -0.05f, 1.18f, 0.155f, 0.05f, 1.21f, RUBBER);
        box(ps, vc, light, -0.155f, -0.05f, 1.28f, 0.155f, 0.05f, 1.31f, RUBBER);
        box(ps, vc, light, -0.05f, -0.05f, 1.4f, 0.05f, 0.05f, 1.41f, RUBBER);
        ps.popPose();
    }

    // ------------------------------------------------------------------ Escudo

    /** Segmentos luminosos azul celeste alrededor del tanque, unidos a los cuatro emisores. */
    private void drawShield(PoseStack ps, VertexConsumer vc, int remaining, float time) {
        float appear = Mth.clamp((FortressTankEntity.SHIELD_TICKS - remaining) / 6.0f, 0.0f, 1.0f);
        if (remaining > FortressTankEntity.SHIELD_TICKS) {
            appear = 1.0f;                       // activación larga (otra duración): ya está encendido
        }
        float fade = Mth.clamp(remaining / 10.0f, 0.0f, 1.0f);
        float f = Math.min(appear, fade);
        Matrix4f m = ps.last().pose();
        float ex = 1.12f;
        float ez = 1.62f;
        float y0 = 0.05f;
        float y1 = 1.95f;
        int rows = 4;
        // Cuatro muros (frente, atrás, izquierda, derecha)
        wall(vc, m, -ex, -ez, ex, -ez, y0, y1, 5, rows, time, f, 0);
        wall(vc, m, ex, -ez, ex, ez, y0, y1, 7, rows, time, f, 1);
        wall(vc, m, ex, ez, -ex, ez, y0, y1, 5, rows, time, f, 2);
        wall(vc, m, -ex, ez, -ex, -ez, y0, y1, 7, rows, time, f, 3);
        // Techo en segmentos
        int cx = 4;
        int cz = 6;
        for (int i = 0; i < cx; i++) {
            for (int j = 0; j < cz; j++) {
                float xa = -ex + (2 * ex) * i / cx + 0.03f;
                float xb = -ex + (2 * ex) * (i + 1) / cx - 0.03f;
                float za = -ez + (2 * ez) * j / cz + 0.03f;
                float zb = -ez + (2 * ez) * (j + 1) / cz - 0.03f;
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f + i * 1.1f + j * 0.8f);
                float a = (0.10f + 0.10f * pulse) * f;
                quad2(vc, m, xa, y1, za, xb, y1, za, xb, y1, zb, xa, y1, zb, 0.45f, 0.82f, 1.0f, a);
            }
        }
        // Líneas de energía desde las puntas de los emisores a las esquinas del escudo
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x0 = sx * 0.9f;
                float z0 = sz * 1.28f;
                line(vc, m, x0, 1.24f, z0, sx * ex, y1, sz * ez, 0.012f, 0.6f, 0.9f, 1.0f, 0.7f * f);
            }
        }
    }

    private void wall(VertexConsumer vc, Matrix4f m, float xa, float za, float xb, float zb,
                      float y0, float y1, int cols, int rows, float time, float f, int idx) {
        float gap = 0.035f;
        for (int c = 0; c < cols; c++) {
            float t0 = (float) c / cols;
            float t1 = (float) (c + 1) / cols;
            float ax = Mth.lerp(t0, xa, xb), az = Mth.lerp(t0, za, zb);
            float bx = Mth.lerp(t1, xa, xb), bz = Mth.lerp(t1, za, zb);
            float dx = bx - ax, dz = bz - az;
            float len = Mth.sqrt(dx * dx + dz * dz);
            float ux = dx / len * gap, uz = dz / len * gap;
            for (int r = 0; r < rows; r++) {
                float ya = Mth.lerp((float) r / rows, y0, y1) + gap;
                float yb = Mth.lerp((float) (r + 1) / rows, y0, y1) - gap;
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f + c * 0.9f + r * 0.7f + idx * 1.7f);
                float a = (0.12f + 0.16f * pulse) * f;
                float x1 = ax + ux, z1 = az + uz, x2 = bx - ux, z2 = bz - uz;
                quad2(vc, m, x1, ya, z1, x2, ya, z2, x2, yb, z2, x1, yb, z1, 0.45f, 0.82f, 1.0f, a);
                // Bordes brillantes (arriba y a la izquierda de cada segmento)
                line(vc, m, x1, yb, z1, x2, yb, z2, 0.012f, 0.7f, 0.95f, 1.0f, 0.55f * f);
                line(vc, m, x1, ya, z1, x1, yb, z1, 0.012f, 0.7f, 0.95f, 1.0f, 0.4f * f);
            }
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float cr, float cg, float cb, float ca) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5f) {
            return;
        }
        // Vector perpendicular al segmento (en horizontal; si es casi vertical, en X)
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

    /** Cuadrilátero visible por ambas caras. */
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

    // ------------------------------------------------------------------ Placas hexagonales

    /**
     * Placa hexagonal con tornillos industriales. eje 'z' = plano XY (frente), 'x' = plano ZY (laterales),
     * 'y' = plano XZ (cubierta). c es la posición del plano, (u, w) el centro sobre él y s el sentido hacia fuera.
     */
    private void hex(PoseStack ps, VertexConsumer vc, int light, char axis, float c, float u, float w,
                     float r, int s, Mat m) {
        if (r <= 0.0f) {
            return;
        }
        float t = 0.03f * s;
        float[][] bands = {{0.0f, 0.3f, 0.92f}, {0.3f, 0.86f, 0.56f}, {-0.86f, -0.3f, 0.56f}};
        // cada banda: v desde, v hasta, semiancho (en múltiplos de r)
        for (float[] b : bands) {
            float v0 = w + b[0] * r;
            float v1 = w + b[1] * r;
            if (b[0] == 0.0f) {
                v0 = w - b[1] * r;                    // banda central: simétrica
            }
            float hu = b[2] * r;
            plate(ps, vc, light, axis, c, c + t, u - hu, u + hu, Math.min(v0, v1), Math.max(v0, v1), m);
        }
        // Tornillos en los seis vértices
        float[][] bolts = {{-0.9f, 0.0f}, {0.9f, 0.0f}, {-0.5f, 0.78f}, {0.5f, 0.78f}, {-0.5f, -0.78f}, {0.5f, -0.78f}};
        for (float[] b : bolts) {
            float bu = u + b[0] * r;
            float bw = w + b[1] * r;
            plate(ps, vc, light, axis, c + t, c + t + 0.015f * s, bu - 0.018f, bu + 0.018f, bw - 0.018f, bw + 0.018f, BOLT);
        }
    }

    /** Caja fina sobre un plano: d0..d1 profundidad (eje 'axis'), u0..u1 y v0..v1 el área. */
    private void plate(PoseStack ps, VertexConsumer vc, int light, char axis, float d0, float d1,
                       float u0, float u1, float v0, float v1, Mat m) {
        float da = Math.min(d0, d1);
        float db = Math.max(d0, d1);
        switch (axis) {
            case 'z' -> box(ps, vc, light, u0, v0, da, u1, v1, db, m);       // u = X, v = Y
            case 'x' -> box(ps, vc, light, da, v0, u0, db, v1, u1, m);       // u = Z, v = Y
            default -> box(ps, vc, light, u0, da, v0, u1, db, v1, m);        // 'y': u = X, v = Z
        }
    }

    // ------------------------------------------------------------------ Geometría

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, false);
    }

    /** Igual que box, pero cada cara muestra la casilla completa (para el emblema). */
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
