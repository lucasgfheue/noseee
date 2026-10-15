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
 * Dibuja el tanque «Ciudadela»: fortaleza larguísima sobre orugas (4,4 de largo del modelo, el doble en bloques),
 * módulos energéticos atrás, estructura blindada delante, cabina de mando en el centro, plataforma elevada giratoria
 * con un cañón electromagnético de segmentos separados por anillos ámbar, bobinas que se iluminan en secuencia
 * al cargar y cuatro estabilizadores que se despliegan al disparar.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Casco de Z -2.2 a 2.2 y X ±1.
 * El eje del cañón (pivotY 2.3, pivotForward 0.6, largo 3.4) debe coincidir con CitadelTankEntity.
 */
public class CitadelTankRenderer extends EntityRenderer<CitadelTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_ciudadela.png");

    private static final int T_PLATE = 0, T_CORNER = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_HAZARD = 8;

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
    private static final Mat RUBBER = new Mat(W, T_RUBBER);
    private static final Mat PANEL = new Mat(W, T_PANEL);
    private static final Mat HAZARD = new Mat(W, T_HAZARD);
    private static final Mat BOLT = new Mat(new int[]{120, 122, 130}, T_FLAT);
    private static final Mat BLACK = new Mat(new int[]{16, 17, 20}, T_FLAT);
    private static final Mat GLASS = new Mat(new int[]{40, 70, 80}, T_FLAT);
    private static final Mat LAMP = new Mat(new int[]{255, 250, 225}, T_FLAT, true);
    private static final Mat RED = new Mat(new int[]{255, 50, 40}, T_FLAT, true);

    private static final int WHEELS = 10;
    private static final float WHEEL_Y = 0.3f;
    private static final float WHEEL_X = 0.82f;
    private static final float WHEEL_SPIN = 0.34f / 0.24f;

    private static final int SEGMENTS = 9;
    private static final float SEG_STEP = 0.37f;

    private float time;

    public CitadelTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.2f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(CitadelTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(CitadelTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(6.0, 3.0, 6.0));
    }

    @Override
    public void render(CitadelTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        float deploy = Mth.lerp(partialTick, tank.deployO, tank.deploy);
        float chargeFrac = tank.getCharge() > 0 ? Mth.clamp((tank.getCharge() + partialTick) / CitadelTankEntity.CHARGE_TICKS, 0.0f, 1.0f) : 0.0f;
        float afterglow = Mth.lerp(partialTick, tank.coilGlowO, tank.coilGlow);

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light, chargeFrac, afterglow);
        drawStabilizers(ps, vc, light, deploy);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawPlatform(ps, vc, light, relYaw, pitch, chargeFrac, afterglow);

        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int side = 1; side >= -1; side -= 2) {
            float x1 = side > 0 ? 0.64f : -1.0f;
            float x2 = side > 0 ? 1.0f : -0.64f;
            box(ps, vc, light, x1, 0.0f, -2.2f, x2, 0.06f, 2.2f, TRACK);
            box(ps, vc, light, x1, 0.56f, -2.2f, x2, 0.62f, 2.2f, TRACK);
            box(ps, vc, light, x1 + 0.02f, 0.04f, -2.26f, x2 - 0.02f, 0.58f, -2.16f, TRACK);
            box(ps, vc, light, x1 + 0.02f, 0.04f, 2.16f, x2 - 0.02f, 0.58f, 2.26f, TRACK);
            // Zapatas y uniones articuladas entre tramos
            for (int i = 0; i < 22; i++) {
                float z = -2.15f + i * 0.2f;
                box(ps, vc, light, x1 - 0.015f, -0.01f, z, x2 + 0.015f, 0.04f, z + 0.1f, CORNER);
                box(ps, vc, light, x1 - 0.015f, 0.58f, z, x2 + 0.015f, 0.63f, z + 0.1f, CORNER);
            }
            for (float jz : new float[]{-0.8f, 0.8f}) {
                box(ps, vc, light, x1 - 0.02f, 0.0f, jz - 0.03f, x2 + 0.02f, 0.64f, jz + 0.03f, BLACK);
            }
            float angle = side > 0 ? angleL : angleR;
            for (int i = 0; i < WHEELS; i++) {
                float z = -1.98f + i * 0.44f;
                wheel(ps, vc, light, side * WHEEL_X, WHEEL_Y, z, angle);
            }
            // Gruesa cubierta blindada sobre la oruga, en placas separadas por juntas
            float cx1 = side > 0 ? 0.6f : -1.08f;
            float cx2 = side > 0 ? 1.08f : -0.6f;
            for (int i = 0; i < 5; i++) {
                float z1 = -2.1f + i * 0.84f;
                box(ps, vc, light, cx1, 0.5f, z1, cx2, 0.88f, z1 + 0.8f, PLATE);
                // esquinas reforzadas de cada placa
                float ox1 = side > 0 ? 1.04f : -1.12f;
                float ox2 = side > 0 ? 1.12f : -1.04f;
                box(ps, vc, light, ox1, 0.48f, z1 - 0.02f, ox2, 0.9f, z1 + 0.06f, CORNER);
                box(ps, vc, light, ox1, 0.48f, z1 + 0.74f, ox2, 0.9f, z1 + 0.82f, CORNER);
                float fx = side > 0 ? 1.08f : -1.08f;
                // remaches en dos filas
                for (int r = 0; r < 4; r++) {
                    float z = z1 + 0.14f + r * 0.18f;
                    rivet(ps, vc, light, 'x', fx, 0.56f, z, side);
                    rivet(ps, vc, light, 'x', fx, 0.82f, z, side);
                }
                // panel de mantenimiento en algunas placas
                if (i % 2 == 0) {
                    float pa = side > 0 ? 1.08f : -1.092f;
                    float pb = side > 0 ? 1.092f : -1.08f;
                    boxFull(ps, vc, light, pa, 0.6f, z1 + 0.2f, pb, 0.78f, z1 + 0.6f, PANEL);
                }
            }
        }
    }

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
        ps.popPose();
    }

    private void rivet(PoseStack ps, VertexConsumer vc, int light, char axis, float c, float u, float w, int s) {
        float t = 0.025f * s;
        if (axis == 'x') {                                  // cara lateral: u = Y, w = Z
            float a = Math.min(c, c + t), b = Math.max(c, c + t);
            box(ps, vc, light, a, u - 0.02f, w - 0.02f, b, u + 0.02f, w + 0.02f, BOLT);
        } else {                                            // cara superior: u = X, w = Z
            box(ps, vc, light, u - 0.02f, c, w - 0.02f, u + 0.02f, c + 0.025f, w + 0.02f, BOLT);
        }
    }

    // ------------------------------------------------------------------ Casco

    private void drawHull(PoseStack ps, VertexConsumer vc, int light, float chargeFrac, float afterglow) {
        // Estructura y cubierta
        box(ps, vc, light, -0.62f, 0.2f, -2.2f, 0.62f, 0.88f, 2.2f, CORNER);
        box(ps, vc, light, -0.66f, 0.84f, -2.2f, 0.66f, 0.94f, 2.2f, PLATE);
        // Esquinas reforzadas (postes gruesos)
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x1 = sx > 0 ? 0.52f : -0.72f;
                float x2 = sx > 0 ? 0.72f : -0.52f;
                float z1 = sz > 0 ? 2.0f : -2.24f;
                float z2 = sz > 0 ? 2.24f : -2.0f;
                box(ps, vc, light, x1, 0.18f, z1, x2, 1.0f, z2, CORNER);
                box(ps, vc, light, x1 - 0.02f, 0.96f, z1 - 0.02f, x2 + 0.02f, 1.02f, z2 + 0.02f, BLACK);
            }
        }
        // Placas superpuestas en el costado del casco entre el tren de rodaje y la cubierta
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 0.62f : -0.66f;
            float xb = s > 0 ? 0.66f : -0.62f;
            box(ps, vc, light, xa, 0.88f, -2.0f, xb, 0.96f, 2.0f, PLATE);
        }
        // Remaches de cubierta
        for (int i = 0; i < 20; i++) {
            float z = -2.0f + i * 0.21f;
            rivet(ps, vc, light, 'y', 0.94f, -0.6f, z, 1);
            rivet(ps, vc, light, 'y', 0.94f, 0.6f, z, 1);
        }

        drawRear(ps, vc, light, chargeFrac, afterglow);
        drawBastion(ps, vc, light);
        drawCabin(ps, vc, light);
        drawDetails(ps, vc, light);
    }

    /** Parte trasera: grandes módulos energéticos con franjas ámbar, conductos, rejillas y antenas. */
    private void drawRear(PoseStack ps, VertexConsumer vc, int light, float chargeFrac, float afterglow) {
        float pulse = 0.55f + 0.2f * Mth.sin(this.time * 0.12f) + 0.35f * Math.max(chargeFrac, afterglow);
        Mat amber = amber(pulse);
        // Módulo central alto
        box(ps, vc, light, -0.38f, 0.94f, -2.12f, 0.38f, 1.6f, -1.45f, PLATE);
        box(ps, vc, light, -0.4f, 0.94f, -2.14f, 0.4f, 1.0f, -1.43f, CORNER);
        box(ps, vc, light, -0.4f, 1.56f, -2.14f, 0.4f, 1.64f, -1.43f, CORNER);
        // Módulos laterales
        for (int s = -1; s <= 1; s += 2) {
            float x1 = s > 0 ? 0.4f : -0.86f;
            float x2 = s > 0 ? 0.86f : -0.4f;
            box(ps, vc, light, x1, 0.94f, -2.1f, x2, 1.38f, -1.0f, PLATE);
            box(ps, vc, light, x1 - 0.02f, 0.94f, -2.12f, x2 + 0.02f, 1.0f, -0.98f, CORNER);
            box(ps, vc, light, x1 - 0.02f, 1.34f, -2.12f, x2 + 0.02f, 1.4f, -0.98f, CORNER);
            // franjas energéticas ámbar (verticales) en los costados exteriores
            float fa = s > 0 ? 0.86f : -0.874f;
            float fb = s > 0 ? 0.874f : -0.86f;
            for (int i = 0; i < 5; i++) {
                float z = -2.0f + i * 0.2f;
                box(ps, vc, light, fa, 1.0f, z, fb, 1.32f, z + 0.06f, amber);
            }
            // aletas de refrigeración sobre cada módulo
            for (int i = 0; i < 5; i++) {
                float z = -2.0f + i * 0.2f;
                box(ps, vc, light, x1 + 0.04f, 1.4f, z, x2 - 0.04f, 1.46f, z + 0.1f, VENT);
            }
        }
        // Módulo delantero bajo y conductos hacia la cabina
        box(ps, vc, light, -0.3f, 0.94f, -1.42f, 0.3f, 1.2f, -0.98f, PLATE);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.15f - 0.04f, 0.94f, -0.98f, s * 0.15f + 0.04f, 1.04f, -0.5f, CORNER);
            box(ps, vc, light, s * 0.15f - 0.015f, 1.04f, -0.98f, s * 0.15f + 0.015f, 1.06f, -0.5f, amber);
        }
        // Franjas ámbar del módulo central
        for (int i = 0; i < 3; i++) {
            float x = -0.24f + i * 0.24f;
            box(ps, vc, light, x, 1.0f, -2.145f, x + 0.08f, 1.54f, -2.13f, amber);
        }
        // Rejillas traseras de refrigeración
        box(ps, vc, light, -0.55f, 0.3f, -2.24f, 0.55f, 0.8f, -2.2f, VENT);
        box(ps, vc, light, -0.58f, 0.27f, -2.25f, 0.58f, 0.3f, -2.2f, BLACK);
        box(ps, vc, light, -0.58f, 0.8f, -2.25f, 0.58f, 0.83f, -2.2f, BLACK);
        // Antenas de comunicación
        mast(ps, vc, light, -0.62f, -1.9f, 1.9f);
        mast(ps, vc, light, 0.62f, -1.7f, 1.6f);
        mast(ps, vc, light, 0.0f, -2.0f, 2.1f);
        // Plato de comunicaciones
        box(ps, vc, light, 0.0f, 1.64f, -1.9f, 0.04f, 1.9f, -1.86f, CORNER);
        box(ps, vc, light, -0.17f, 1.84f, -1.88f, 0.21f, 2.06f, -1.84f, PLATE);
        box(ps, vc, light, 0.0f, 1.93f, -1.84f, 0.04f, 1.97f, -1.76f, BOLT);
    }

    private void mast(PoseStack ps, VertexConsumer vc, int light, float x, float z, float top) {
        box(ps, vc, light, x - 0.03f, 0.94f, z - 0.03f, x + 0.03f, top, z + 0.03f, BLACK);
        box(ps, vc, light, x - 0.06f, 0.94f, z - 0.06f, x + 0.06f, 1.02f, z + 0.06f, CORNER);
        box(ps, vc, light, x - 0.04f, top, z - 0.04f, x + 0.04f, top + 0.06f, z + 0.04f, RED);
    }

    /** Parte delantera: gran estructura blindada escalonada, con placas, remaches y focos de trabajo. */
    private void drawBastion(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.74f, 0.94f, 0.9f, 0.74f, 1.28f, 2.02f, PLATE);
        box(ps, vc, light, -0.58f, 1.28f, 1.05f, 0.58f, 1.42f, 1.9f, CORNER);
        // Glacis escalonado y casi vertical
        box(ps, vc, light, -0.74f, 0.94f, 2.02f, 0.74f, 1.12f, 2.12f, PLATE);
        box(ps, vc, light, -0.66f, 0.88f, 2.12f, 0.66f, 1.04f, 2.22f, CORNER);
        box(ps, vc, light, -0.74f, 1.12f, 1.96f, 0.74f, 1.2f, 2.02f, CORNER);
        // Placas superpuestas laterales
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 0.74f : -0.8f;
            float xb = s > 0 ? 0.8f : -0.74f;
            for (int i = 0; i < 3; i++) {
                float z = 0.95f + i * 0.36f;
                box(ps, vc, light, xa, 0.98f, z, xb, 1.24f, z + 0.33f, PLATE);
                float fx = s > 0 ? 0.8f : -0.8f;
                rivet(ps, vc, light, 'x', fx, 1.02f, z + 0.06f, s);
                rivet(ps, vc, light, 'x', fx, 1.02f, z + 0.27f, s);
                rivet(ps, vc, light, 'x', fx, 1.2f, z + 0.06f, s);
                rivet(ps, vc, light, 'x', fx, 1.2f, z + 0.27f, s);
            }
            float pa = s > 0 ? 0.8f : -0.812f;
            float pb = s > 0 ? 0.812f : -0.8f;
            boxFull(ps, vc, light, pa, 1.0f, 1.0f, pb, 1.22f, 1.28f, PANEL);
            // esquinas reforzadas
            float cxa = s > 0 ? 0.68f : -0.84f;
            float cxb = s > 0 ? 0.84f : -0.68f;
            box(ps, vc, light, cxa, 0.94f, 1.92f, cxb, 1.3f, 2.1f, CORNER);
        }
        // Placas en el techo de la estructura
        for (int i = 0; i < 3; i++) {
            for (int s = -1; s <= 1; s += 2) {
                float z = 1.12f + i * 0.28f;
                float xa = s > 0 ? 0.1f : -0.52f;
                float xb = s > 0 ? 0.52f : -0.1f;
                box(ps, vc, light, xa, 1.42f, z, xb, 1.45f, z + 0.25f, PLATE);
            }
        }
        // Escotillas de mantenimiento
        boxFull(ps, vc, light, -0.35f, 1.28f - 0.0f, 1.2f, 0.35f, 1.285f, 1.75f, PANEL);
    }

    /** Cabina de mando protegida dentro del casco, con vista panorámica a la altura de los ojos. */
    private void drawCabin(PoseStack ps, VertexConsumer vc, int light) {
        // Muros blindados hasta el pecho, con ranuras sobre ellos
        box(ps, vc, light, -0.52f, 0.94f, -0.52f, -0.42f, 1.5f, 0.5f, PLATE);          // derecha
        box(ps, vc, light, 0.42f, 0.94f, -0.52f, 0.52f, 1.5f, 0.5f, PLATE);            // izquierda
        box(ps, vc, light, -0.52f, 0.94f, -0.52f, 0.52f, 1.5f, -0.42f, PLATE);         // trasero
        box(ps, vc, light, -0.52f, 0.94f, 0.42f, 0.52f, 1.2f, 0.52f, PLATE);           // frontal bajo
        box(ps, vc, light, -0.54f, 1.46f, -0.54f, 0.54f, 1.52f, 0.54f, CORNER);        // cornisa
        // Pilares que sostienen la plataforma elevada
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x1 = sx > 0 ? 0.43f : -0.53f;
                float x2 = sx > 0 ? 0.53f : -0.43f;
                float z1 = sz > 0 ? 0.42f : -0.53f;
                float z2 = sz > 0 ? 0.53f : -0.42f;
                box(ps, vc, light, x1, 1.52f, z1, x2, 1.95f, z2, CORNER);
            }
        }
        // Cuadro de mandos con pequeños indicadores
        box(ps, vc, light, -0.35f, 1.2f, 0.34f, 0.35f, 1.26f, 0.42f, BLACK);
        box(ps, vc, light, -0.25f, 1.26f, 0.37f, -0.15f, 1.29f, 0.41f, amber(0.9f));
        box(ps, vc, light, 0.15f, 1.26f, 0.37f, 0.25f, 1.29f, 0.41f, new Mat(new int[]{120, 230, 160}, T_FLAT, true));
        // Visores blindados laterales (cristal oscuro)
        box(ps, vc, light, 0.52f, 1.2f, -0.2f, 0.54f, 1.42f, 0.2f, GLASS);
        box(ps, vc, light, -0.54f, 1.2f, -0.2f, -0.52f, 1.42f, 0.2f, GLASS);
    }

    /** Escaleras de mantenimiento, focos de trabajo y paneles. */
    private void drawDetails(PoseStack ps, VertexConsumer vc, int light) {
        // Escaleras a cada lado (sobre la cubierta de la oruga)
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 1.1f : -1.16f;
            float xb = s > 0 ? 1.16f : -1.1f;
            float z = 0.05f;
            box(ps, vc, light, xa, 0.05f, z - 0.2f, xb, 0.92f, z - 0.17f, BLACK);
            box(ps, vc, light, xa, 0.05f, z + 0.17f, xb, 0.92f, z + 0.2f, BLACK);
            for (int i = 0; i < 7; i++) {
                float y = 0.12f + i * 0.12f;
                box(ps, vc, light, xa + 0.015f * s, y, z - 0.18f, xb - 0.015f * s, y + 0.025f, z + 0.18f, BOLT);
            }
        }
        // Focos de trabajo (adelante y atrás)
        float[][] lamps = {{0.62f, 1.45f, 1.88f}, {-0.62f, 1.45f, 1.88f}, {0.86f, 1.4f, -1.2f}, {-0.86f, 1.4f, -1.2f}};
        for (float[] l : lamps) {
            work(ps, vc, light, l[0], l[1], l[2], l[2] > 0 ? 1 : -1);
        }
    }

    private void work(PoseStack ps, VertexConsumer vc, int light, float x, float y, float z, int dir) {
        box(ps, vc, light, x - 0.03f, y - 0.2f, z - 0.03f, x + 0.03f, y, z + 0.03f, BLACK);
        box(ps, vc, light, x - 0.1f, y, z - 0.07f, x + 0.1f, y + 0.12f, z + 0.07f, CORNER);
        float zf1 = dir > 0 ? z + 0.07f : z - 0.08f;
        float zf2 = dir > 0 ? z + 0.08f : z - 0.07f;
        box(ps, vc, light, x - 0.085f, y + 0.015f, zf1, x + 0.085f, y + 0.105f, zf2, LAMP);
    }

    // ------------------------------------------------------------------ Estabilizadores

    private void drawStabilizers(PoseStack ps, VertexConsumer vc, int light, float e) {
        float[] zs = {-1.55f, 1.4f};
        for (int s = -1; s <= 1; s += 2) {
            for (float z : zs) {
                float hx1 = s > 0 ? 1.08f : -1.22f;
                float hx2 = s > 0 ? 1.22f : -1.08f;
                box(ps, vc, light, hx1, 0.52f, z - 0.2f, hx2, 0.86f, z + 0.2f, CORNER);           // alojamiento
                box(ps, vc, light, hx1, 0.56f, z - 0.16f, hx2, 0.6f, z + 0.16f, HAZARD);
                if (e <= 0.02f) {
                    continue;
                }
                float reach = 0.8f * e;
                float xs = s > 0 ? 1.22f : -1.22f - reach;
                float xe = s > 0 ? 1.22f + reach : -1.22f;
                box(ps, vc, light, xs, 0.66f, z - 0.1f, xe, 0.76f, z + 0.1f, PLATE);              // brazo telescópico
                float px = s > 0 ? 1.22f + reach : -1.22f - reach;
                float footY = Mth.lerp(e, 0.62f, 0.0f);
                box(ps, vc, light, px - 0.06f, footY + 0.05f, z - 0.06f, px + 0.06f, 0.7f, z + 0.06f, BOLT);   // pistón
                box(ps, vc, light, px - 0.22f, footY, z - 0.22f, px + 0.22f, footY + 0.07f, z + 0.22f, HAZARD);  // pie
                box(ps, vc, light, px - 0.24f, footY - 0.005f, z - 0.24f, px + 0.24f, footY + 0.01f, z + 0.24f, BLACK);
            }
        }
    }

    // ------------------------------------------------------------------ Plataforma y cañón

    private void drawPlatform(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch,
                              float chargeFrac, float afterglow) {
        ps.pushPose();
        ps.translate(0.0, 1.95, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Plataforma elevada giratoria
        box(ps, vc, light, -0.64f, 0.0f, -0.64f, 0.64f, 0.05f, 0.64f, BLACK);               // corona de giro
        box(ps, vc, light, -0.74f, 0.05f, -0.95f, 0.74f, 0.18f, 0.9f, PLATE);
        box(ps, vc, light, -0.78f, 0.15f, -0.99f, 0.78f, 0.21f, 0.94f, CORNER);             // reborde reforzado
        for (int i = 0; i < 8; i++) {
            float z = -0.85f + i * 0.22f;
            rivet(ps, vc, light, 'y', 0.21f, -0.7f, z, 1);
            rivet(ps, vc, light, 'y', 0.21f, 0.7f, z, 1);
        }
        // Cuna del cañón
        box(ps, vc, light, -0.4f, 0.21f, 0.35f, 0.4f, 0.5f, 0.9f, PLATE);
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 0.26f : -0.42f;
            float xb = s > 0 ? 0.42f : -0.26f;
            box(ps, vc, light, xa, 0.21f, 0.35f, xb, 0.6f, 0.92f, CORNER);
            box(ps, vc, light, xa - 0.02f * s, 0.44f, 0.55f, xb + 0.02f * s, 0.5f, 0.65f, BOLT);   // muñones
        }
        // Contrapeso trasero y bancos de condensadores a los lados
        box(ps, vc, light, -0.5f, 0.21f, -0.95f, 0.5f, 0.56f, -0.42f, CORNER);
        box(ps, vc, light, -0.46f, 0.56f, -0.92f, 0.46f, 0.6f, -0.45f, PLATE);
        float pulse = 0.5f + 0.2f * Mth.sin(this.time * 0.15f) + 0.3f * Math.max(chargeFrac, afterglow);
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 0.5f : -0.74f;
            float xb = s > 0 ? 0.74f : -0.5f;
            box(ps, vc, light, xa, 0.21f, -0.4f, xb, 0.42f, 0.3f, PLATE);
            float fa = s > 0 ? 0.74f : -0.754f;
            float fb = s > 0 ? 0.754f : -0.74f;
            for (int i = 0; i < 3; i++) {
                float z = -0.3f + i * 0.2f;
                box(ps, vc, light, fa, 0.25f, z, fb, 0.38f, z + 0.06f, amber(pulse));
            }
            boxFull(ps, vc, light, s > 0 ? 0.74f : -0.752f, 0.23f, -0.38f, s > 0 ? 0.752f : -0.74f, 0.4f, -0.1f, PANEL);
        }
        // Antenas y foco sobre la plataforma
        mast(ps, vc, light, 0.6f, -0.85f, 0.75f);
        mast(ps, vc, light, -0.6f, -0.85f, 0.55f);
        work(ps, vc, light, 0.55f, 0.4f, 0.8f, 1);
        work(ps, vc, light, -0.55f, 0.4f, 0.8f, 1);

        // Cañón electromagnético: eje a (0, 0.35, 0.6) de la plataforma
        ps.translate(0.0, 0.35, 0.6);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        drawCannon(ps, vc, light, chargeFrac, afterglow);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float chargeFrac, float afterglow) {
        // Recámara y culata
        box(ps, vc, light, -0.2f, -0.2f, -0.8f, 0.2f, 0.2f, 0.0f, CORNER);
        box(ps, vc, light, -0.22f, -0.22f, -0.5f, 0.22f, 0.22f, -0.42f, PLATE);
        box(ps, vc, light, -0.16f, -0.16f, -0.86f, 0.16f, 0.16f, -0.8f, BLACK);
        // Segmentos metálicos separados por anillos ámbar
        for (int i = 0; i < SEGMENTS; i++) {
            float z0 = 0.02f + i * SEG_STEP;
            float lit = coilLevel(i, chargeFrac, afterglow);
            box(ps, vc, light, -0.13f, -0.13f, z0, 0.13f, 0.13f, z0 + 0.32f, i % 2 == 0 ? PLATE : CORNER);
            box(ps, vc, light, -0.155f, -0.155f, z0 + 0.32f, 0.155f, 0.155f, z0 + SEG_STEP, amber(0.3f + 0.7f * lit));
            // Bobina sobre el tubo (se enciende en secuencia)
            box(ps, vc, light, -0.1f, 0.13f, z0 + 0.04f, 0.1f, 0.21f, z0 + 0.3f, coil(lit));
            // Conductos laterales de cada segmento
            box(ps, vc, light, 0.11f, 0.03f, z0 + 0.02f, 0.15f, 0.07f, z0 + 0.3f, coil(lit));
            box(ps, vc, light, -0.15f, 0.03f, z0 + 0.02f, -0.11f, 0.07f, z0 + 0.3f, coil(lit));
        }
        // Conducto principal superior que recorre el cañón
        box(ps, vc, light, -0.03f, 0.21f, -0.4f, 0.03f, 0.25f, SEGMENTS * SEG_STEP, BLACK);
        box(ps, vc, light, -0.02f, 0.25f, -0.4f, 0.02f, 0.27f, SEGMENTS * SEG_STEP, CORNER);
        // Boca
        float zEnd = SEGMENTS * SEG_STEP;
        float lastLit = coilLevel(SEGMENTS - 1, chargeFrac, afterglow);
        box(ps, vc, light, -0.17f, -0.17f, zEnd, 0.17f, 0.17f, zEnd + 0.1f, BLACK);
        box(ps, vc, light, -0.19f, -0.19f, zEnd + 0.04f, 0.19f, 0.19f, zEnd + 0.07f, amber(0.3f + 0.7f * lastLit));
        box(ps, vc, light, -0.08f, -0.08f, zEnd + 0.1f, 0.08f, 0.08f, zEnd + 0.11f, coil(lastLit));
    }

    /** Resplandor de la bobina i: en carga se encienden una a una desde la base hasta la boca. */
    private float coilLevel(int i, float chargeFrac, float afterglow) {
        if (chargeFrac > 0.0f) {
            return Mth.clamp(chargeFrac * (SEGMENTS + 2) - i, 0.0f, 1.0f);
        }
        return afterglow;
    }

    private static Mat coil(float lit) {
        if (lit <= 0.02f) {
            return new Mat(new int[]{62, 44, 30}, T_FLAT);               // cobre apagado
        }
        int r = (int) Mth.lerp(lit, 90, 255);
        int g = (int) Mth.lerp(lit, 60, lit > 0.85f ? 235 : 185);
        int b = (int) Mth.lerp(lit, 30, lit > 0.85f ? 150 : 70);
        return new Mat(new int[]{r, g, b}, T_FLAT, lit > 0.3f);
    }

    private static Mat amber(float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        return new Mat(new int[]{(int) (255 * Math.max(0.35f, k)), (int) (170 * Math.max(0.3f, k)), (int) (50 * k)},
                T_FLAT, k > 0.45f);
    }

    // ------------------------------------------------------------------ Geometría

    private static void box(PoseStack ps, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        boxImpl(ps, vc, light, x1, y1, z1, x2, y2, z2, m, false);
    }

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
