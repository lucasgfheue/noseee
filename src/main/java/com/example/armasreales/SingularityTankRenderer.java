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
 * Dibuja la «Singularidad»: tanque asimétrico montado alrededor de un reactor violeta experimental. Casco con un
 * bloque alto a la izquierda y cubierta baja a la derecha, frontal bajo, torreta angular desplazada a la derecha con
 * un cañón de energía dirigida y un aro mecánico alrededor de su cámara de emisión, placas que flotan alrededor del
 * reactor, cubierta circular parcialmente transparente, paneles de refrigeración que se abren como alas, orugas
 * medianas ocultas bajo los faldones, módulos giroscópicos de estabilización, pantallas holográficas, indicadores
 * violetas y marcas de aislamiento energético.
 *
 * Coordenadas: X lateral (+X izquierda), Y arriba, Z adelante. Torreta en X -0.85; reactor en X 0.85, Z -1.1.
 * El eje del cañón (pivotY 1.57, pivotForward 0.9, largo 2.2, torreta a 0,85 a la derecha) debe coincidir con
 * SingularityTankEntity.
 */
public class SingularityTankRenderer extends EntityRenderer<SingularityTankEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID, "textures/entity/tanque_singularidad.png");

    private static final int T_PLATE = 0, T_CORNER = 1, T_TRACK = 2, T_HUB = 3, T_VENT = 4, T_FLAT = 5,
            T_RUBBER = 6, T_PANEL = 7, T_CERAMIC = 8, T_PETAL = 9, T_INSUL = 10;

    private record Mat(int[] c, int tile, boolean glow, int a) {
        Mat(int[] c, int tile) {
            this(c, tile, false, 255);
        }

        Mat(int[] c, int tile, boolean glow) {
            this(c, tile, glow, 255);
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
    private static final float WHEEL_X = 1.25f;
    private static final float WHEEL_SPIN = 0.34f / 0.3f;

    private static final float DECK_R = 0.95f;      // cubierta derecha (baja)
    private static final float DECK_L = 1.25f;      // bloque izquierdo (alto)
    private static final float TURRET_X = -0.85f;
    private static final float REACT_X = 0.85f;
    private static final float REACT_Z = -1.1f;

    private float time;
    private boolean overload;
    private float coreK;     // 0..1 intensidad del reactor
    private float open;      // 0..1 apertura de los paneles

    public SingularityTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.0f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(SingularityTankEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(SingularityTankEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(5.0, 3.0, 5.0));
    }

    @Override
    public void render(SingularityTankEntity tank, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        this.time = tank.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float angleL = Mth.lerp(partialTick, tank.wheelAngleLO, tank.wheelAngleL) * WHEEL_SPIN;
        float angleR = Mth.lerp(partialTick, tank.wheelAngleRO, tank.wheelAngleR) * WHEEL_SPIN;
        float charge = Mth.clamp(tank.getCharge() / (float) SingularityTankEntity.CHARGE_MAX, 0.0f, 1.0f);
        this.overload = tank.isOverloaded();
        this.open = tank.openFraction(partialTick);
        float pulse = 0.5f + 0.5f * Mth.sin(this.time * 0.07f);              // pulso lento
        this.coreK = this.overload ? 1.0f : 0.35f + 0.25f * pulse + 0.4f * charge;
        float since = tank.ticksSinceShot(partialTick);
        float recoil = since < 12.0f ? 0.18f * (since < 2.0f ? since / 2.0f : 1.0f - (since - 2.0f) / 10.0f) : 0.0f;
        float flash = since < 5.0f ? 1.0f - since / 5.0f : 0.0f;

        drawTracks(ps, vc, light, angleL, angleR);
        drawHull(ps, vc, light);
        drawReactorBase(ps, vc, light);
        drawFloatingPlates(ps, vc, light);

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = tank.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), TankEntity.MIN_PITCH, TankEntity.MAX_PITCH);
        }
        drawTurret(ps, vc, light, relYaw, pitch, recoil, flash, charge);

        // Elementos translúcidos al final: cubierta del reactor y cúpula de la cabina
        VertexConsumer glass = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        drawGlass(ps, glass, light);

        TankInterior.draw(ps, buffer, light, tank);
        ps.popPose();
        super.render(tank, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Orugas medianas bajo faldones

    private void drawTracks(PoseStack ps, VertexConsumer vc, int light, float angleL, float angleR) {
        for (int s = 1; s >= -1; s -= 2) {
            float angle = s > 0 ? angleL : angleR;
            float xc = s * WHEEL_X;
            mbox(ps, vc, light, s, 1.0f, 0.0f, -1.2f, 1.5f, 0.08f, 1.2f, TRACK);
            mbox(ps, vc, light, s, 1.0f, 0.82f, -1.25f, 1.5f, 0.9f, 1.25f, TRACK);
            slab(ps, vc, light, xc, 0.86f, 1.25f, 45.0f, 0.25f, 0.08f, 0.0f, 0.4f, TRACK);
            slab(ps, vc, light, xc, 0.577f, 1.533f, 135.0f, 0.25f, 0.08f, 0.0f, 0.5f, TRACK);
            slab(ps, vc, light, xc, 0.86f, -1.25f, -45.0f, 0.25f, 0.08f, -0.4f, 0.0f, TRACK);
            slab(ps, vc, light, xc, 0.577f, -1.533f, -135.0f, 0.25f, 0.08f, -0.5f, 0.0f, TRACK);
            for (int i = 0; i < 10; i++) {
                float z = -1.2f + i * 0.24f;
                mbox(ps, vc, light, s, 0.98f, -0.012f, z, 1.52f, 0.05f, z + 0.12f, CORNER);
                mbox(ps, vc, light, s, 0.98f, 0.88f, z, 1.52f, 0.935f, z + 0.12f, CORNER);
            }
            for (int k = -2; k <= 2; k++) {
                wheel(ps, vc, light, xc, WHEEL_Y, k * 0.6f, angle);
            }
            // faldón blindado: oculta la mitad superior de las ruedas; insertos cerámicos separados por líneas violetas
            mbox(ps, vc, light, s, 1.46f, 0.46f, -1.5f, 1.56f, 1.02f, 1.5f, PLATE);
            for (int i = 0; i < 3; i++) {
                float z0 = -1.4f + i * 0.95f;
                mbox(ps, vc, light, s, 1.56f, 0.52f, z0, 1.572f, 0.96f, z0 + 0.85f, CERAMIC);
                if (i > 0) {
                    mbox(ps, vc, light, s, 1.56f, 0.5f, z0 - 0.07f, 1.575f, 0.98f, z0 - 0.04f, violet(0.5f, i));
                }
            }
            mbox(ps, vc, light, s, 1.45f, 0.44f, -1.52f, 1.58f, 0.5f, 1.52f, CORNER);
            mbox(ps, vc, light, s, 1.45f, 1.0f, -1.52f, 1.58f, 1.05f, 1.52f, CORNER);
            // marca de aislamiento energético en el faldón
            mbox(ps, vc, light, s, 1.572f, 0.62f, -0.25f, 1.585f, 0.86f, 0.25f, INSUL);
        }
        // módulos de estabilización giroscópicos fuera del faldón
        for (int s = 1; s >= -1; s -= 2) {
            for (float z : new float[]{-1.3f, 1.3f}) {
                gyro(ps, vc, light, s, z);
            }
        }
    }

    private void gyro(PoseStack ps, VertexConsumer vc, int light, int s, float z) {
        mbox(ps, vc, light, s, 1.56f, 0.55f, z - 0.05f, 1.72f, 0.65f, z + 0.05f, BLACK);       // brazo
        mbox(ps, vc, light, s, 1.7f, 0.42f, z - 0.2f, 1.9f, 0.74f, z + 0.2f, PLATE);          // carcasa
        mbox(ps, vc, light, s, 1.88f, 0.46f, z - 0.16f, 1.905f, 0.7f, z + 0.16f, INSUL);
        mbox(ps, vc, light, s, 1.7f, 0.72f, z - 0.2f, 1.9f, 0.76f, z + 0.2f, CORNER);
        // disco giratorio violeta
        ps.pushPose();
        ps.translate(s * 1.8f, 0.58f, z);
        ps.mulPose(Axis.YP.rotationDegrees(this.time * 12.0f * s));
        box(ps, vc, light, -0.15f, 0.12f, -0.015f, 0.15f, 0.15f, 0.015f, violet(0.6f, 0));
        box(ps, vc, light, -0.015f, 0.12f, -0.15f, 0.015f, 0.15f, 0.15f, violet(0.6f, 0));
        ps.popPose();
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

    // ------------------------------------------------------------------ Casco asimétrico

    private void drawHull(PoseStack ps, VertexConsumer vc, int light) {
        // Vientre, espina central, cubierta derecha baja y bloque izquierdo alto
        box(ps, vc, light, -1.0f, 0.2f, -1.9f, 1.0f, 0.8f, 1.9f, PLATE);
        box(ps, vc, light, -0.95f, 0.12f, -1.8f, 0.95f, 0.2f, 1.8f, BLACK);
        box(ps, vc, light, -0.25f, 0.8f, -1.9f, 0.25f, 1.1f, 0.9f, CORNER);
        box(ps, vc, light, -1.45f, 0.8f, -1.85f, -0.25f, DECK_R, 0.9f, PLATE);
        box(ps, vc, light, 0.25f, 0.8f, -1.9f, 1.45f, DECK_L, 0.9f, PLATE);
        // Frontal bajo con glacis inclinado
        box(ps, vc, light, -1.4f, 0.5f, 0.9f, 1.4f, 0.8f, 2.1f, PLATE);
        slab(ps, vc, light, 0.0f, 0.97f, 0.88f, 14.0f, 1.42f, 0.09f, 0.0f, 1.25f, PLATE);
        slab(ps, vc, light, 0.0f, 0.985f, 1.0f, 14.0f, 0.9f, 0.1f, 0.0f, 0.9f, CERAMIC);
        box(ps, vc, light, -1.42f, 0.5f, 2.1f, 1.42f, 0.72f, 2.14f, CORNER);
        box(ps, vc, light, -0.9f, 0.55f, 2.14f, -0.3f, 0.7f, 2.15f, VENT);
        box(ps, vc, light, 0.3f, 0.55f, 2.14f, 0.9f, 0.7f, 2.15f, VENT);
        box(ps, vc, light, -0.2f, 0.55f, 2.14f, 0.2f, 0.68f, 2.15f, INSUL);
        // Cara trasera con marcas de aislamiento
        box(ps, vc, light, -1.2f, 0.85f, -1.95f, -0.45f, 1.2f, -1.9f, INSUL);
        box(ps, vc, light, 0.3f, 0.85f, -1.95f, 1.4f, 1.2f, -1.9f, PLATE);
        box(ps, vc, light, -0.4f, 0.85f, -1.93f, 0.25f, 1.05f, -1.9f, VENT);
        // Insertos cerámicos separados por líneas violetas luminosas: cubierta derecha
        for (int i = 0; i < 4; i++) {
            float z0 = -1.75f + i * 0.62f;
            box(ps, vc, light, -1.4f, DECK_R, z0, -0.32f, DECK_R + 0.02f, z0 + 0.55f, CERAMIC);
            box(ps, vc, light, -1.42f, DECK_R, z0 + 0.55f, -0.3f, DECK_R + 0.012f, z0 + 0.62f, violet(0.55f, i));
        }
        // Bloque izquierdo: insertos y líneas en la cara superior (alrededor de los paneles de refrigeración) y flanco
        box(ps, vc, light, 0.3f, DECK_L, 0.78f, 1.4f, DECK_L + 0.02f, 0.88f, CERAMIC);
        box(ps, vc, light, 0.3f, DECK_L, 0.74f, 1.4f, DECK_L + 0.012f, 0.78f, violet(0.6f, 2));
        box(ps, vc, light, 0.3f, DECK_L, -0.55f, 1.4f, DECK_L + 0.012f, -0.5f, violet(0.6f, 1));
        for (int i = 0; i < 3; i++) {
            float z0 = -1.75f + i * 0.9f;
            box(ps, vc, light, 1.45f, 1.05f, z0, 1.462f, 1.22f, z0 + 0.8f, CERAMIC);
            box(ps, vc, light, 1.445f, 1.03f, z0 + 0.8f, 1.465f, 1.24f, z0 + 0.86f, violet(0.6f, i));
        }
        box(ps, vc, light, 0.26f, 0.95f, -1.7f, 0.262f, DECK_L, 0.8f, violet(0.5f, 3));     // junta con la espina
        box(ps, vc, light, -0.26f, 0.95f, -1.8f, -0.258f, 1.08f, 0.8f, violet(0.5f, 0));
        // Aristas
        box(ps, vc, light, 0.24f, DECK_L, -1.9f, 1.46f, DECK_L + 0.04f, -1.8f, CORNER);
        box(ps, vc, light, -1.46f, DECK_R, -1.87f, -0.24f, DECK_R + 0.04f, -1.8f, CORNER);

        drawCooling(ps, vc, light);
        drawCockpit(ps, vc, light);
        drawHolos(ps, vc, light);
        drawIndicators(ps, vc, light);
    }

    /** Paneles de refrigeración móviles sobre el bloque izquierdo: se abren hacia fuera mostrando conductos. */
    private void drawCooling(PoseStack ps, VertexConsumer vc, int light) {
        // Cavidad con conductos luminosos y disipadores
        box(ps, vc, light, 0.35f, DECK_L, -0.5f, 1.4f, DECK_L + 0.02f, 0.75f, BLACK);
        for (int i = 0; i < 6; i++) {
            float x = 0.42f + i * 0.17f;
            box(ps, vc, light, x, DECK_L + 0.02f, -0.45f, x + 0.04f, DECK_L + 0.2f, 0.7f, VENT);
            box(ps, vc, light, x + 0.06f, DECK_L + 0.02f, -0.4f, x + 0.14f, DECK_L + 0.06f, 0.65f,
                    violet(0.4f + 0.5f * this.open, i));
        }
        for (float z : new float[]{-0.45f, 0.65f}) {
            box(ps, vc, light, 0.38f, DECK_L, z, 1.38f, DECK_L + 0.26f, z + 0.06f, CORNER);
        }
        // Dos hojas con bisagra en los bordes exteriores
        float theta = 70.0f * this.open * this.open * (3.0f - 2.0f * this.open);
        ps.pushPose();
        ps.translate(0.35f, DECK_L + 0.28f, 0.0f);
        ps.mulPose(Axis.ZP.rotationDegrees(theta));
        box(ps, vc, light, 0.0f, 0.0f, -0.5f, 0.55f, 0.06f, 0.78f, PLATE);
        box(ps, vc, light, 0.04f, 0.06f, -0.44f, 0.5f, 0.075f, 0.72f, CERAMIC);
        box(ps, vc, light, 0.0f, 0.06f, -0.5f, 0.06f, 0.09f, 0.78f, CORNER);
        box(ps, vc, light, 0.5f, 0.0f, -0.4f, 0.55f, 0.01f, 0.68f, violet(0.6f, 4));
        ps.popPose();
        ps.pushPose();
        ps.translate(1.4f, DECK_L + 0.28f, 0.0f);
        ps.mulPose(Axis.ZP.rotationDegrees(-theta));
        box(ps, vc, light, -0.55f, 0.0f, -0.5f, 0.0f, 0.06f, 0.78f, PLATE);
        box(ps, vc, light, -0.5f, 0.06f, -0.44f, -0.04f, 0.075f, 0.72f, CERAMIC);
        box(ps, vc, light, -0.06f, 0.06f, -0.5f, 0.0f, 0.09f, 0.78f, CORNER);
        box(ps, vc, light, -0.55f, 0.0f, -0.4f, -0.5f, 0.01f, 0.68f, violet(0.6f, 5));
        ps.popPose();
    }

    /** Cabina central a caballo entre la cubierta baja y el bloque alto (el conductor va en el centro). */
    private void drawCockpit(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.3f, 1.1f, -0.5f, 0.3f, 1.18f, 0.5f, CORNER);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(ps, vc, light, sx * 0.27f - 0.03f, 1.18f, sz * 0.42f - 0.03f,
                        sx * 0.27f + 0.03f, 1.6f, sz * 0.42f + 0.03f, BLACK);
            }
        }
        box(ps, vc, light, -0.3f, 1.58f, -0.47f, 0.3f, 1.64f, 0.47f, CORNER);
        box(ps, vc, light, -0.26f, 1.64f, -0.4f, 0.26f, 1.66f, 0.4f, CERAMIC);
        box(ps, vc, light, -0.04f, 1.66f, -0.38f, 0.04f, 1.67f, 0.38f, violet(0.6f, 2));
    }

    /** Pequeñas pantallas holográficas sobre emisores. */
    private void drawHolos(PoseStack ps, VertexConsumer vc, int light) {
        holo(ps, vc, light, -1.0f, DECK_R, -1.2f, 0.0f);
        holo(ps, vc, light, 1.0f, DECK_L, 0.82f, 90.0f);
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

    /** Indicadores de advertencia violetas; parpadean mucho más rápido durante la sobrecarga. */
    private void drawIndicators(PoseStack ps, VertexConsumer vc, int light) {
        float[][] pts = {{-1.35f, DECK_R + 0.03f, 0.75f}, {1.35f, DECK_L + 0.03f, 0.75f}, {-1.3f, 0.82f, 2.05f},
                {1.3f, 0.82f, 2.05f}, {1.35f, DECK_L + 0.03f, -1.75f}, {-1.35f, DECK_R + 0.03f, -1.75f},
                {0.0f, 1.12f, 0.85f}, {0.0f, 1.12f, -1.85f}};
        float rate = this.overload ? 3.0f : 12.0f;
        for (int i = 0; i < pts.length; i++) {
            boolean on = ((int) (this.time / rate) + i) % 2 == 0;
            Mat m = on ? new Mat(new int[]{190, 110, 255}, T_FLAT, true) : new Mat(new int[]{60, 30, 100}, T_FLAT, true);
            box(ps, vc, light, pts[i][0] - 0.04f, pts[i][1], pts[i][2] - 0.04f,
                    pts[i][0] + 0.04f, pts[i][1] + 0.06f, pts[i][2] + 0.04f, m);
        }
    }

    // ------------------------------------------------------------------ Reactor

    /** Base del reactor, núcleo violeta que pulsa y destellos blancos durante la sobrecarga. */
    private void drawReactorBase(PoseStack ps, VertexConsumer vc, int light) {
        ps.pushPose();
        ps.translate(REACT_X, DECK_L, REACT_Z);
        // anillo base octogonal con marcas de aislamiento
        box(ps, vc, light, -0.58f, 0.0f, -0.58f, 0.58f, 0.12f, 0.58f, PLATE);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.58f, 0.0f, -0.58f, 0.58f, 0.12f, 0.58f, PLATE);
        ps.popPose();
        box(ps, vc, light, -0.6f, 0.1f, -0.6f, 0.6f, 0.14f, 0.6f, CORNER);
        box(ps, vc, light, -0.2f, 0.02f, 0.575f, 0.2f, 0.1f, 0.6f, INSUL);
        box(ps, vc, light, -0.2f, 0.02f, -0.6f, 0.2f, 0.1f, -0.575f, INSUL);
        // columna y núcleo
        box(ps, vc, light, -0.07f, 0.14f, -0.07f, 0.07f, 0.28f, 0.07f, BLACK);
        float r = 0.13f + 0.04f * this.coreK;
        float cy = 0.4f;
        Mat core = violet(this.coreK, 0);
        ps.pushPose();
        ps.translate(0.0f, cy, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(this.time * (this.overload ? 6.0f : 1.2f)));
        box(ps, vc, light, -r, -r, -r, r, r, r, core);
        ps.mulPose(Axis.XP.rotationDegrees(45.0f));
        box(ps, vc, light, -r, -r, -r, r, r, r, core);
        ps.popPose();
        if (this.overload) {
            int seed = (int) (this.time / 2.0f);
            for (int i = 0; i < 4; i++) {
                float ox = (hash(seed, i * 3) - 0.5f) * 0.42f;
                float oy = (hash(seed, i * 3 + 1) - 0.5f) * 0.42f;
                float oz = (hash(seed, i * 3 + 2) - 0.5f) * 0.42f;
                float sz = 0.025f + 0.04f * hash(seed, i + 40);
                box(ps, vc, light, ox - sz, cy + oy - sz, oz - sz, ox + sz, cy + oy + sz, oz + sz, WHITE);
            }
            box(ps, vc, light, -0.06f, cy - 0.06f, -0.06f, 0.06f, cy + 0.06f, 0.06f, WHITE);
        }
        // cubierta circular: postes, aro superior y mitad trasera opaca (la delantera es transparente)
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4.0 + Math.PI / 8.0;
            float px = (float) Math.cos(a) * 0.5f;
            float pz = (float) Math.sin(a) * 0.5f;
            box(ps, vc, light, px - 0.035f, 0.14f, pz - 0.035f, px + 0.035f, 0.66f, pz + 0.035f, BLACK);
        }
        box(ps, vc, light, -0.56f, 0.64f, -0.56f, 0.56f, 0.7f, 0.56f, CORNER);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.56f, 0.64f, -0.56f, 0.56f, 0.7f, 0.56f, CORNER);
        ps.popPose();
        box(ps, vc, light, -0.3f, 0.7f, -0.3f, 0.3f, 0.74f, 0.3f, CERAMIC);
        box(ps, vc, light, -0.4f, 0.14f, -0.52f, 0.4f, 0.64f, -0.44f, PLATE);
        box(ps, vc, light, -0.3f, 0.2f, -0.545f, 0.3f, 0.58f, -0.52f, INSUL);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(s * 45.0f));
            box(ps, vc, light, -0.3f, 0.14f, -0.52f, 0.3f, 0.64f, -0.44f, PLATE);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Placas blindadas que flotan alrededor del reactor, con bordes violetas luminosos. */
    private void drawFloatingPlates(PoseStack ps, VertexConsumer vc, int light) {
        ps.pushPose();
        ps.translate(REACT_X, DECK_L + 0.55f, REACT_Z);
        float speed = this.overload ? 1.6f : 0.5f;
        for (int i = 0; i < 4; i++) {
            float ang = this.time * speed + i * 90.0f;
            float bob = 0.09f * Mth.sin(this.time * 0.05f + i * 1.7f);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(ang));
            ps.translate(0.78f + (this.overload ? 0.08f : 0.0f), 0.1f + bob, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(14.0f));
            box(ps, vc, light, -0.035f, -0.24f, -0.3f, 0.035f, 0.24f, 0.3f, i % 2 == 0 ? PLATE : CORNER);
            box(ps, vc, light, 0.035f, -0.16f, -0.2f, 0.045f, 0.16f, 0.2f, CERAMIC);
            box(ps, vc, light, 0.03f, 0.22f, -0.3f, 0.05f, 0.24f, 0.3f, violet(0.7f, i));
            box(ps, vc, light, 0.03f, -0.24f, -0.3f, 0.05f, -0.22f, 0.3f, violet(0.7f, i + 2));
            box(ps, vc, light, 0.03f, -0.24f, -0.3f, 0.05f, 0.24f, -0.28f, violet(0.7f, i + 1));
            box(ps, vc, light, 0.03f, -0.24f, 0.28f, 0.05f, 0.24f, 0.3f, violet(0.7f, i + 3));
            ps.popPose();
        }
        ps.popPose();
    }

    /** Elementos translúcidos: cubierta del reactor y cúpula de la cabina. */
    private void drawGlass(PoseStack ps, VertexConsumer vc, int light) {
        Mat dome = new Mat(new int[]{150, 90, 255}, T_FLAT, true, this.overload ? 105 : 70);
        ps.pushPose();
        ps.translate(REACT_X, DECK_L, REACT_Z);
        box(ps, vc, light, -0.46f, 0.15f, -0.46f, 0.46f, 0.65f, 0.46f, dome);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.46f, 0.15f, -0.46f, 0.46f, 0.65f, 0.46f, dome);
        ps.popPose();
        ps.popPose();
        Mat canopy = new Mat(new int[]{40, 30, 80}, T_FLAT, false, 140);
        box(ps, vc, light, -0.25f, 1.18f, -0.41f, 0.25f, 1.58f, 0.41f, canopy);
    }

    // ------------------------------------------------------------------ Torreta angular desplazada y cañón de energía

    private void drawTurret(PoseStack ps, VertexConsumer vc, int light, float relYaw, float pitch, float recoil,
                            float flash, float charge) {
        ps.pushPose();
        ps.translate(TURRET_X, DECK_R, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));

        // Aro de giro y cuerpo angular
        box(ps, vc, light, -0.72f, 0.0f, -0.72f, 0.72f, 0.06f, 0.72f, BLACK);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(45.0f));
        box(ps, vc, light, -0.7f, 0.0f, -0.7f, 0.7f, 0.06f, 0.7f, BLACK);
        ps.popPose();
        box(ps, vc, light, -0.55f, 0.06f, -0.6f, 0.55f, 0.32f, 0.6f, PLATE);
        box(ps, vc, light, -0.58f, 0.3f, -0.62f, 0.58f, 0.34f, 0.62f, CORNER);
        // facetas inclinadas laterales y delanteras
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.5f, 0.32f, 0.0f);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -32.0f));
            box(ps, vc, light, s > 0 ? -0.02f : -0.46f, 0.0f, -0.5f, s > 0 ? 0.46f : 0.02f, 0.07f, 0.5f, CERAMIC);
            ps.popPose();
            ps.pushPose();
            ps.translate(s * 0.3f, 0.06f, 0.6f);
            ps.mulPose(Axis.YP.rotationDegrees(s * -28.0f));
            box(ps, vc, light, -0.3f, 0.0f, 0.0f, 0.3f, 0.3f, 0.1f, PLATE);
            ps.popPose();
        }
        slab(ps, vc, light, 0.0f, 0.5f, -0.1f, -8.0f, 0.46f, 0.1f, -0.55f, 0.55f, PLATE);
        box(ps, vc, light, -0.35f, 0.54f, -0.45f, 0.35f, 0.57f, 0.4f, CERAMIC);
        box(ps, vc, light, -0.03f, 0.57f, -0.42f, 0.03f, 0.575f, 0.38f, violet(0.6f, 1));
        // parte trasera con marcas de aislamiento energético
        box(ps, vc, light, -0.4f, 0.1f, -0.7f, 0.4f, 0.45f, -0.6f, PLATE);
        box(ps, vc, light, -0.25f, 0.15f, -0.715f, 0.25f, 0.4f, -0.7f, INSUL);
        // cuna blindada del cañón
        box(ps, vc, light, -0.32f, 0.3f, 0.5f, 0.32f, 0.8f, 0.95f, PLATE);
        box(ps, vc, light, -0.34f, 0.76f, 0.5f, 0.34f, 0.82f, 0.95f, CORNER);
        box(ps, vc, light, -0.36f, 0.3f, 0.5f, -0.32f, 0.8f, 0.95f, violet(0.5f, 2));
        box(ps, vc, light, 0.32f, 0.3f, 0.5f, 0.36f, 0.8f, 0.95f, violet(0.5f, 3));
        // indicador violeta de la torreta
        boolean on = ((int) (this.time / (this.overload ? 3.0f : 12.0f))) % 2 == 0;
        box(ps, vc, light, -0.04f, 0.57f, 0.42f, 0.04f, 0.63f, 0.5f,
                on ? new Mat(new int[]{190, 110, 255}, T_FLAT, true) : new Mat(new int[]{60, 30, 100}, T_FLAT, true));

        // Cañón de energía dirigida: eje a (0, 0.62, 0.9) de la torreta
        ps.translate(0.0, 0.62, 0.9);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0.0, 0.0, -recoil);
        drawCannon(ps, vc, light, flash, charge);
        ps.popPose();
    }

    private void drawCannon(PoseStack ps, VertexConsumer vc, int light, float flash, float charge) {
        // Recámara y dos púas guía que convergen hacia la boca
        box(ps, vc, light, -0.22f, -0.22f, -0.4f, 0.22f, 0.22f, 0.15f, CORNER);
        box(ps, vc, light, -0.17f, -0.17f, 0.15f, 0.17f, 0.17f, 0.9f, BLACK);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.17f - 0.04f, -0.07f, 0.6f, s * 0.17f + 0.04f, 0.07f, 2.0f, PETAL);
            box(ps, vc, light, s * 0.1f - 0.03f, -0.05f, 1.9f, s * 0.1f + 0.03f, 0.05f, 2.2f, PETAL);
        }
        // Núcleo de emisión
        float k = this.overload ? 1.0f : 0.3f + 0.7f * charge;
        float er = 0.07f + 0.05f * k;
        box(ps, vc, light, -er, -er, 0.2f, er, er, 2.0f, violet(k, 1));
        box(ps, vc, light, -0.13f, -0.13f, 1.3f, 0.13f, 0.13f, 1.72f, violet(Math.min(1.0f, k + 0.2f), 3));
        // Aro mecánico alrededor de la cámara de emisión (gira)
        ps.pushPose();
        ps.translate(0.0f, 0.0f, 1.51f);
        ps.mulPose(Axis.ZP.rotationDegrees(this.time * (this.overload ? 9.0f : 2.0f)));
        box(ps, vc, light, -0.34f, 0.2f, -0.13f, 0.34f, 0.3f, 0.13f, CORNER);
        box(ps, vc, light, -0.34f, -0.3f, -0.13f, 0.34f, -0.2f, 0.13f, CORNER);
        box(ps, vc, light, 0.2f, -0.3f, -0.13f, 0.34f, 0.3f, 0.13f, CORNER);
        box(ps, vc, light, -0.34f, -0.3f, -0.13f, -0.2f, 0.3f, 0.13f, CORNER);
        box(ps, vc, light, -0.28f, 0.3f, -0.05f, 0.28f, 0.32f, 0.05f, violet(0.7f, 0));
        box(ps, vc, light, -0.28f, -0.32f, -0.05f, 0.28f, -0.3f, 0.05f, violet(0.7f, 2));
        ps.popPose();
        box(ps, vc, light, -0.2f, -0.2f, 0.95f, 0.2f, 0.2f, 1.05f, PLATE);
        box(ps, vc, light, -0.2f, -0.2f, 1.85f, 0.2f, 0.2f, 1.92f, PLATE);
        box(ps, vc, light, -0.16f, -0.24f, 1.0f, 0.16f, -0.2f, 1.1f, INSUL);
        if (flash > 0.0f) {
            float f = 0.1f + 0.22f * flash;
            box(ps, vc, light, -f, -f, 2.2f, f, f, 2.2f + 0.5f * flash, violet(1.0f, 0));
            box(ps, vc, light, -f * 0.5f, -f * 0.5f, 2.2f, f * 0.5f, f * 0.5f, 2.2f + 0.7f * flash, WHITE);
        }
    }

    // ------------------------------------------------------------------ Luz violeta

    /** Material violeta luminoso; phase desplaza ligeramente el pulso entre segmentos. */
    private Mat violet(float k, int phase) {
        float v = Mth.clamp(k + 0.12f * Mth.sin(this.time * 0.1f + phase * 1.3f), 0.0f, 1.0f);
        return new Mat(new int[]{(int) (80 + 110 * v), (int) (30 + 70 * v), (int) (170 + 85 * v)}, T_FLAT, true);
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
        vertex(pose, vc, light, m.c(), m.a(), u0, v1, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, m.c(), m.a(), u1, v1, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, m.c(), m.a(), u1, v0, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, m.c(), m.a(), u0, v0, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c, int a, float u, float v,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
