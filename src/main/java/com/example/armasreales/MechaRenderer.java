package com.example.armasreales;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
 * Dibuja el Mecha de Hierro con cajas (sin archivos de modelo).
 * Ejes del modelo: +X = izquierda, +Y = arriba, +Z = delante. Mide unos 3,3 de alto (el doble en el juego).
 */
public class MechaRenderer extends EntityRenderer<MechaEntity> {
    /** Una textura por skin: 0 = hierro y 1..16 = los 16 tintes (en orden de DyeColor). */
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[17];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath(ArmasRealesMod.MOD_ID,
                    i == 0 ? "textures/entity/mecha_hierro.png" : "textures/entity/mecha_hierro_" + i + ".png");
        }
    }

    /** Skin que se está dibujando (el render corre en un solo hilo). */
    private int skin;
    private ResourceLocation tex = TEXTURES[0];

    private record Mat(int[] c, int tile, boolean glow, int alpha) {
        Mat(int[] c, int tile) {
            this(c, tile, false, 255);
        }

        Mat(int[] c, int tile, boolean glow) {
            this(c, tile, glow, 255);
        }
    }

    private static final int[] W = {255, 255, 255};
    private static final Mat IRON = new Mat(W, 0);
    private static final Mat DARK = new Mat(W, 1);
    private static final Mat RIVET = new Mat(W, 2);
    private static final Mat VISOR = new Mat(new int[]{150, 235, 255}, 3, false, 90);
    private static final Mat VENT = new Mat(W, 4);
    private static final Mat JOINT = new Mat(W, 5);
    private static final Mat HAZARD = new Mat(W, 6);
    private static final Mat EMBER = new Mat(new int[]{255, 140, 40}, 7, true);

    public MechaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.6f * TankEntity.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(MechaEntity entity) {
        return TEXTURES[Mth.clamp(entity.getSkin(), 0, TEXTURES.length - 1)];
    }

    @Override
    public boolean shouldRender(MechaEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(3.0));
    }

    @Override
    public void render(MechaEntity mecha, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffer, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        ps.scale(TankEntity.SCALE, TankEntity.SCALE, TankEntity.SCALE);
        this.skin = Mth.clamp(mecha.getSkin(), 0, TEXTURES.length - 1);
        this.tex = TEXTURES[this.skin];
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(this.tex));

        // En primera persona, el piloto no ve el cuerpo del mecha (sólo los brazos que apuntan)
        Minecraft mc = Minecraft.getInstance();
        boolean inside = mc.options.getCameraType().isFirstPerson() && mc.player != null && mecha.hasPassenger(mc.player);

        // Marcha: cada pierna tiene su propia fase (una va adelante cuando la otra va atrás)
        float phase = Mth.lerp(partialTick, mecha.walkPhaseO, mecha.walkPhase);
        float amp = Mth.lerp(partialTick, mecha.walkAmpO, mecha.walkAmp);
        boolean air = !mecha.onGround();
        float bob = air ? 0.0f : Math.abs(Mth.cos((float) Math.toRadians(phase))) * 0.06f * amp;
        for (int side = 1; side >= -1; side -= 2) {
            float ph = (float) Math.toRadians(side > 0 ? phase : phase + 180.0f);
            float hip = -Mth.sin(ph) * amp * 34.0f;
            float knee = Math.max(0.0f, Mth.cos(ph)) * amp * 55.0f;
            if (air) {
                hip = -22.0f;
                knee = 45.0f;
            }
            if (!inside) {
                drawLeg(ps, vc, light, side, hip, knee);
            }
        }

        ps.pushPose();
        ps.translate(0.0f, bob, 0.0f);
        if (!inside) {
            drawPelvis(ps, vc, light);
        }

        float relYaw = 0.0f;
        float pitch = 0.0f;
        LivingEntity rider = mecha.getControllingPassenger();
        if (rider != null) {
            relYaw = Mth.wrapDegrees(rider.getViewYRot(partialTick) - entityYaw);
            pitch = Mth.clamp(rider.getViewXRot(partialTick), -MechaEntity.ARM_AIM_LIMIT, MechaEntity.ARM_AIM_LIMIT);
        }
        float since = mecha.ticksSinceShot(partialTick);
        float recoil = since < 10.0f ? 0.25f * (1.0f - since / 10.0f) : 0.0f;
        boolean rightFired = mecha.lastShotRight();

        ps.pushPose();
        ps.translate(0.0f, 1.75f, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(-relYaw));
        if (!inside) {
            drawTorso(ps, vc, light, mecha.isFlying(), mecha.tickCount + partialTick);
            drawBoosters(ps, vc, light, mecha.isFlying(), mecha.isBoosting(), mecha.tickCount + partialTick);
        }
        drawArm(ps, vc, light, -1, pitch, rightFired ? recoil : 0.0f);
        drawArm(ps, vc, light, 1, pitch, rightFired ? 0.0f : recoil);
        if (!inside) {
            drawHead(ps, vc, buffer, light, pitch, false);
        }
        ps.popPose();
        ps.popPose();

        ps.popPose();
        super.render(mecha, entityYaw, partialTick, ps, buffer, light);
    }

    // ------------------------------------------------------------------ Piezas

    /** side: +1 = izquierda, -1 = derecha. El origen es la cadera. hip: balanceo del muslo; knee: flexión de la rodilla. */
    private void drawLeg(PoseStack ps, VertexConsumer vc, int light, int side, float hip, float knee) {
        ps.pushPose();
        ps.translate(side * 0.62f, 1.55f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(hip));
        legExtras(ps, vc, light, side);
        box(ps, vc, light, -0.3f, -0.15f, -0.3f, 0.3f, 0.12f, 0.3f, JOINT);               // cadera
        box(ps, vc, light, -0.27f, -0.8f, -0.27f, 0.27f, -0.12f, 0.27f, IRON);            // muslo
        box(ps, vc, light, -0.3f, -0.8f, 0.27f, 0.3f, -0.3f, 0.34f, RIVET);               // placa frontal
        box(ps, vc, light, -0.22f, -0.95f, -0.22f, 0.22f, -0.8f, 0.22f, JOINT);           // rodilla
        box(ps, vc, light, -0.3f, -1.0f, 0.2f, 0.3f, -0.85f, 0.36f, DARK);                // rótula
        ps.pushPose();
        ps.translate(0.0f, -0.9f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(knee - hip * 0.4f));                            // la espinilla se dobla hacia atrás
        box(ps, vc, light, -0.25f, -0.55f, -0.25f, 0.25f, 0.0f, 0.25f, DARK);             // espinilla
        box(ps, vc, light, -0.28f, -0.5f, 0.25f, 0.28f, -0.05f, 0.3f, RIVET);
        box(ps, vc, light, -0.2f, -0.5f, -0.3f, 0.2f, -0.1f, -0.25f, VENT);               // radiador
        box(ps, vc, light, -0.36f, -0.78f, -0.55f, 0.36f, -0.55f, 0.7f, IRON);            // pie
        box(ps, vc, light, -0.36f, -0.78f, 0.55f, 0.36f, -0.7f, 0.7f, HAZARD);            // punta con rayas
        ps.popPose();
        ps.popPose();
    }

    private void drawPelvis(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, light, -0.8f, 1.35f, -0.4f, 0.8f, 1.8f, 0.4f, IRON);
        box(ps, vc, light, -0.45f, 1.3f, -0.45f, 0.45f, 1.4f, 0.45f, JOINT);
        box(ps, vc, light, -0.3f, 1.45f, 0.4f, 0.3f, 1.7f, 0.46f, HAZARD);
    }

    /** Origen: base del torso. */
    private void drawTorso(PoseStack ps, VertexConsumer vc, int light, boolean flying, float time) {
        box(ps, vc, light, -0.9f, 0.0f, -0.55f, 0.9f, 0.9f, 0.55f, IRON);                  // pecho
        box(ps, vc, light, -0.8f, 0.9f, -0.5f, 0.8f, 1.0f, 0.5f, DARK);                    // cuello
        box(ps, vc, light, -0.6f, 0.15f, 0.55f, 0.6f, 0.75f, 0.62f, RIVET);                // peto
        box(ps, vc, light, -0.1f, 0.15f, 0.62f, 0.1f, 0.75f, 0.65f, DARK);
        box(ps, vc, light, -0.35f, 0.3f, 0.62f, -0.15f, 0.6f, 0.66f, EMBER);               // luces del pecho
        box(ps, vc, light, 0.15f, 0.3f, 0.62f, 0.35f, 0.6f, 0.66f, EMBER);
        // Mochila trasera con escapes
        box(ps, vc, light, -0.7f, 0.1f, -0.9f, 0.7f, 0.95f, -0.55f, DARK);
        box(ps, vc, light, -0.6f, 0.2f, -0.93f, -0.1f, 0.85f, -0.9f, VENT);
        box(ps, vc, light, 0.1f, 0.2f, -0.93f, 0.6f, 0.85f, -0.9f, VENT);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.45f - 0.1f, 0.95f, -0.85f, s * 0.45f + 0.1f, 1.45f, -0.65f, JOINT);   // chimeneas
            box(ps, vc, light, s * 0.45f - 0.13f, 1.45f, -0.88f, s * 0.45f + 0.13f, 1.5f, -0.62f, DARK);
        }
        // Toberas del jetpack (llamas cuando vuela)
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.35f - 0.14f, -0.12f, -0.88f, s * 0.35f + 0.14f, 0.1f, -0.6f, JOINT);
            if (flying) {
                float len = 0.55f + 0.2f * Mth.sin(time * 1.7f + s);
                box(ps, vc, light, s * 0.35f - 0.1f, -0.12f - len, -0.84f, s * 0.35f + 0.1f, -0.12f, -0.64f, EMBER);
            }
        }
        // Hombreras
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.95f - 0.02f, 0.62f, -0.5f, s * 1.45f, 1.0f, 0.5f, IRON);
            box(ps, vc, light, s * 1.0f - 0.02f, 1.0f, -0.4f, s * 1.4f, 1.08f, 0.4f, RIVET);
            box(ps, vc, light, s * 1.43f - 0.02f, 0.66f, -0.3f, s * 1.47f, 0.96f, 0.3f, HAZARD);
        }
        torsoExtras(ps, vc, light, time);
    }

    /** Propulsores traseros: aparecen al volar y echan llamas largas con Ctrl + W (origen: base del torso). */
    private void drawBoosters(PoseStack ps, VertexConsumer vc, int light, boolean flying, boolean boosting, float time) {
        if (!flying) {
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            mb(ps, vc, light, s, 0.6f, -0.3f, -0.8f, 0.9f, 0.05f, -0.4f, DARK);
            mb(ps, vc, light, s, 0.65f, -0.25f, -0.86f, 0.85f, 0.0f, -0.8f, JOINT);
            float len = boosting ? 1.5f + 0.35f * Mth.sin(time * 2.1f + s) : 0.3f;
            mb(ps, vc, light, s, 0.68f, -0.22f, -0.86f - len, 0.82f, -0.03f, -0.86f, EMBER);
        }
    }

    // ------------------------------------------------------------------ Variantes por tinte

    /** Caja reflejada: side +1 = izquierda, -1 = derecha; xa..xb se miden hacia fuera desde el centro. */
    private static void mb(PoseStack ps, VertexConsumer vc, int light, int side,
                           float xa, float y1, float z1, float xb, float y2, float z2, Mat m) {
        if (side > 0) {
            box(ps, vc, light, xa, y1, z1, xb, y2, z2, m);
        } else {
            box(ps, vc, light, -xb, y1, z1, -xa, y2, z2, m);
        }
    }

    /** Caja girada alrededor de un punto (axis: 0 = X, 1 = Y, 2 = Z). */
    private static void rb(PoseStack ps, VertexConsumer vc, int light, float tx, float ty, float tz, int axis, float deg,
                           float x1, float y1, float z1, float x2, float y2, float z2, Mat m) {
        ps.pushPose();
        ps.translate(tx, ty, tz);
        ps.mulPose((axis == 0 ? Axis.XP : axis == 1 ? Axis.YP : Axis.ZP).rotationDegrees(deg));
        box(ps, vc, light, x1, y1, z1, x2, y2, z2, m);
        ps.popPose();
    }

    /** Piezas extra del torso y la mochila (origen: base del torso). */
    private void torsoExtras(PoseStack ps, VertexConsumer vc, int light, float time) {
        for (int s = -1; s <= 1; s += 2) {
            switch (this.skin) {
                case 1 -> {                                           // blanco: alas de placas
                    for (int i = 0; i < 4; i++) {
                        mb(ps, vc, light, s, 0.5f + i * 0.4f, 0.95f - i * 0.15f, -1.0f, 0.88f + i * 0.4f, 1.75f - i * 0.1f, -0.9f, IRON);
                        mb(ps, vc, light, s, 0.5f + i * 0.4f, 0.9f - i * 0.15f, -1.0f, 0.88f + i * 0.4f, 0.95f - i * 0.15f, -0.9f, EMBER);
                    }
                }
                case 2 -> {                                           // naranja: faros y rack de herramientas
                    mb(ps, vc, light, s, 1.0f, 1.08f, 0.1f, 1.3f, 1.3f, 0.4f, EMBER);
                    mb(ps, vc, light, s, 0.2f, 0.3f, -1.15f, 0.3f, 1.2f, -0.95f, JOINT);
                    mb(ps, vc, light, s, 0.5f, 0.3f, -1.15f, 0.6f, 1.2f, -0.95f, JOINT);
                }
                case 3 -> {                                           // magenta: cristales
                    rb(ps, vc, light, s * 1.2f, 1.05f, 0.0f, 2, -s * 18.0f, -0.12f, 0.0f, -0.12f, 0.12f, 1.0f, 0.12f, EMBER);
                    rb(ps, vc, light, s * 1.35f, 1.0f, 0.25f, 2, -s * 38.0f, -0.09f, 0.0f, -0.09f, 0.09f, 0.7f, 0.09f, EMBER);
                    rb(ps, vc, light, s * 1.35f, 1.0f, -0.25f, 2, -s * 38.0f, -0.09f, 0.0f, -0.09f, 0.09f, 0.7f, 0.09f, EMBER);
                }
                case 4 -> {                                           // celeste: carámbanos
                    for (int i = 0; i < 3; i++) {
                        rb(ps, vc, light, s * (1.05f + i * 0.14f), 1.05f, -0.3f + i * 0.3f, 2, -s * (10.0f + i * 12.0f),
                                -0.07f, 0.0f, -0.07f, 0.07f, 0.8f - i * 0.12f, 0.07f, RIVET);
                    }
                    mb(ps, vc, light, s, 0.15f, 0.95f, -0.9f, 0.3f, 1.7f, -0.7f, RIVET);
                }
                case 5 -> {                                           // amarillo: aletas de rayo y baterías
                    mb(ps, vc, light, s, 0.7f, 0.2f, -1.0f, 1.0f, 0.55f, -0.9f, EMBER);
                    mb(ps, vc, light, s, 0.95f, 0.5f, -1.0f, 1.25f, 0.85f, -0.9f, EMBER);
                    mb(ps, vc, light, s, 1.2f, 0.8f, -1.0f, 1.5f, 1.15f, -0.9f, EMBER);
                    mb(ps, vc, light, s, 0.95f, -0.4f, -0.1f, 1.25f, 0.1f, 0.3f, DARK);
                }
                case 6 -> {                                           // lima: tubos del reactor y goteo
                    mb(ps, vc, light, s, 0.25f, 0.1f, 0.6f, 0.4f, 0.85f, 0.7f, EMBER);
                    mb(ps, vc, light, s, 0.25f, -0.35f, 0.62f, 0.32f, 0.1f, 0.68f, JOINT);
                    mb(ps, vc, light, s, 1.0f, -0.1f, -0.2f, 1.18f, 0.6f, 0.0f, EMBER);
                }
                case 7 -> {                                           // rosa: lazo en la mochila
                    mb(ps, vc, light, s, 0.0f, 0.55f, -1.02f, 0.45f, 0.95f, -0.9f, EMBER);
                    box(ps, vc, light, -0.1f, 0.6f, -1.06f, 0.1f, 0.9f, -0.9f, IRON);
                }
                case 8 -> {                                           // gris: coraza reforzada y hombreras dobles
                    mb(ps, vc, light, s, 0.0f, 0.0f, 0.62f, 0.95f, 0.8f, 0.8f, DARK);
                    mb(ps, vc, light, s, 0.0f, 0.1f, 0.8f, 0.9f, 0.7f, 0.85f, RIVET);
                    mb(ps, vc, light, s, 0.95f, 1.0f, -0.55f, 1.55f, 1.2f, 0.55f, DARK);
                }
                case 9 -> {                                           // gris claro: antenas largas
                    mb(ps, vc, light, s, 0.5f, 0.95f, -0.8f, 0.56f, 2.1f, -0.74f, JOINT);
                    mb(ps, vc, light, s, 0.5f, 2.1f, -0.82f, 0.58f, 2.2f, -0.72f, EMBER);
                }
                case 10 -> {                                          // cian: aletas y propulsores laterales
                    box(ps, vc, light, -0.05f, 0.9f, -1.3f, 0.05f, 1.9f, -0.55f, IRON);
                    mb(ps, vc, light, s, 0.95f, -0.3f, -0.5f, 1.3f, 0.2f, 0.1f, DARK);
                    mb(ps, vc, light, s, 1.0f, -0.3f, -0.55f, 1.25f, -0.2f, -0.5f, EMBER);
                }
                case 11 -> {                                          // morado: cuchillas en los hombros y capa
                    rb(ps, vc, light, s * 1.25f, 1.05f, 0.0f, 2, -s * 25.0f, -0.08f, 0.0f, -0.35f, 0.08f, 1.3f, 0.35f, DARK);
                    box(ps, vc, light, -0.7f, -0.8f, -1.0f, 0.7f, 0.95f, -0.94f, DARK);
                }
                case 12 -> {                                          // azul: lanzamisiles en los hombros
                    for (int a = 0; a < 2; a++) {
                        for (int b = 0; b < 2; b++) {
                            mb(ps, vc, light, s, 1.0f + a * 0.22f, 1.08f, -0.3f + b * 0.3f, 1.2f + a * 0.22f, 1.5f, -0.06f + b * 0.3f, DARK);
                            mb(ps, vc, light, s, 1.04f + a * 0.22f, 1.5f, -0.26f + b * 0.3f, 1.16f + a * 0.22f, 1.56f, -0.1f + b * 0.3f, EMBER);
                        }
                    }
                }
                case 13 -> {                                          // marrón: chimeneas altas
                    mb(ps, vc, light, s, 0.15f, 0.95f, -0.95f, 0.45f, 2.0f, -0.65f, JOINT);
                    mb(ps, vc, light, s, 0.1f, 2.0f, -1.0f, 0.5f, 2.1f, -0.6f, DARK);
                }
                case 14 -> {                                          // verde: placas de hojas
                    for (int i = 0; i < 3; i++) {
                        mb(ps, vc, light, s, 0.95f + i * 0.0f, 1.0f + i * 0.12f, -0.5f + i * 0.35f, 1.5f - i * 0.1f, 1.12f + i * 0.12f, -0.15f + i * 0.35f, IRON);
                    }
                    mb(ps, vc, light, s, 0.3f, 0.2f, -1.0f, 0.4f, 1.0f, -0.9f, EMBER);
                }
                case 15 -> {                                          // rojo: pinchos y respiraderos de llama
                    rb(ps, vc, light, s * 1.2f, 1.05f, 0.0f, 2, -s * 30.0f, -0.12f, 0.0f, -0.12f, 0.12f, 0.9f, 0.12f, DARK);
                    rb(ps, vc, light, s * 1.35f, 0.95f, 0.3f, 2, -s * 50.0f, -0.1f, 0.0f, -0.1f, 0.1f, 0.7f, 0.1f, DARK);
                    mb(ps, vc, light, s, 0.2f, 0.95f, -0.9f, 0.5f, 1.6f + 0.1f * Mth.sin(time * 0.6f + s), -0.7f, EMBER);
                }
                case 16 -> {                                          // negro: cuchillas dorsales
                    rb(ps, vc, light, s * 0.5f, 0.9f, -0.8f, 2, -s * 12.0f, -0.05f, 0.0f, -0.25f, 0.05f, 1.6f, 0.25f, DARK);
                    rb(ps, vc, light, s * 0.85f, 0.7f, -0.8f, 2, -s * 28.0f, -0.04f, 0.0f, -0.2f, 0.04f, 1.2f, 0.2f, DARK);
                }
                default -> { }
            }
        }
        switch (this.skin) {
            case 6 -> box(ps, vc, light, -0.2f, 0.1f, 0.62f, 0.2f, 0.8f, 0.68f, EMBER);
            case 13 -> {                                              // pala excavadora
                box(ps, vc, light, -1.15f, -0.45f, 0.75f, 1.15f, 0.55f, 0.85f, DARK);
                box(ps, vc, light, -1.15f, -0.45f, 0.85f, 1.15f, -0.3f, 1.05f, IRON);
                box(ps, vc, light, -0.1f, 0.0f, 0.55f, 0.1f, 0.4f, 0.75f, JOINT);
            }
            case 15 -> box(ps, vc, light, -0.35f, 0.25f, 0.62f, 0.35f, 0.65f, 0.7f, EMBER);
            default -> { }
        }
    }

    /** Piezas extra de la cabeza (origen: base de la cabeza). */
    private void headExtras(PoseStack ps, VertexConsumer vc, int light) {
        switch (this.skin) {
            case 1 -> {                                               // halo
                box(ps, vc, light, -0.45f, 1.1f, -0.45f, 0.45f, 1.16f, 0.45f, EMBER);
                box(ps, vc, light, -0.3f, 1.1f, -0.3f, 0.3f, 1.16f, 0.3f, DARK);
            }
            case 2 -> {                                               // jaula antivuelco
                for (int sx = -1; sx <= 1; sx += 2) {
                    box(ps, vc, light, sx * 0.55f - 0.04f, 0.6f, -0.5f, sx * 0.55f + 0.04f, 0.9f, 0.5f, JOINT);
                }
                box(ps, vc, light, -0.55f, 0.86f, -0.5f, 0.55f, 0.94f, 0.5f, JOINT);
                box(ps, vc, light, -0.2f, 0.7f, 0.5f, 0.2f, 0.82f, 0.6f, EMBER);
            }
            case 3 -> {                                               // corona de cristal
                for (int i = -1; i <= 1; i++) {
                    box(ps, vc, light, i * 0.25f - 0.07f, 0.68f, -0.07f, i * 0.25f + 0.07f, 0.95f - Math.abs(i) * 0.2f, 0.07f, EMBER);
                }
            }
            case 4 -> {                                               // carámbanos en el casco
                for (int i = -2; i <= 2; i++) {
                    box(ps, vc, light, i * 0.18f - 0.05f, 0.68f, 0.1f, i * 0.18f + 0.05f, 0.9f - Math.abs(i) * 0.06f, 0.2f, RIVET);
                }
            }
            case 5 -> {                                               // doble antena de rayo
                box(ps, vc, light, -0.3f, 0.68f, -0.1f, -0.25f, 1.2f, -0.05f, JOINT);
                box(ps, vc, light, 0.25f, 0.68f, -0.1f, 0.3f, 1.2f, -0.05f, JOINT);
                box(ps, vc, light, -0.33f, 1.2f, -0.13f, -0.22f, 1.3f, -0.02f, EMBER);
                box(ps, vc, light, 0.22f, 1.2f, -0.13f, 0.33f, 1.3f, -0.02f, EMBER);
            }
            case 6 -> {                                               // cresta
                for (int i = 0; i < 5; i++) {
                    box(ps, vc, light, -0.05f, 0.68f, -0.45f + i * 0.2f, 0.05f, 0.9f - Math.abs(i - 2) * 0.07f, -0.3f + i * 0.2f, EMBER);
                }
            }
            case 7 -> {                                               // orejas de gato
                for (int sx = -1; sx <= 1; sx += 2) {
                    box(ps, vc, light, sx * 0.3f - 0.14f, 0.68f, -0.1f, sx * 0.3f + 0.14f, 0.82f, 0.1f, IRON);
                    box(ps, vc, light, sx * 0.3f - 0.08f, 0.82f, -0.06f, sx * 0.3f + 0.08f, 0.95f, 0.06f, EMBER);
                }
            }
            case 8 -> box(ps, vc, light, -0.55f, 0.55f, -0.55f, 0.55f, 0.65f, 0.55f, DARK);   // visera pesada
            case 9 -> {                                               // radar
                box(ps, vc, light, -0.04f, 0.68f, -0.04f, 0.04f, 0.95f, 0.04f, JOINT);
                box(ps, vc, light, -0.4f, 0.95f, -0.4f, 0.4f, 1.0f, 0.4f, IRON);
                box(ps, vc, light, -0.4f, 1.0f, -0.4f, 0.4f, 1.1f, -0.3f, IRON);
                box(ps, vc, light, -0.06f, 1.0f, -0.04f, 0.06f, 1.2f, 0.06f, EMBER);
            }
            case 10 -> box(ps, vc, light, -0.05f, 0.68f, -0.5f, 0.05f, 1.15f, 0.0f, IRON);   // aleta
            case 11 -> {                                              // cuernos
                for (int sx = -1; sx <= 1; sx += 2) {
                    rb(ps, vc, light, sx * 0.4f, 0.62f, 0.0f, 2, -sx * 20.0f, -0.07f, 0.0f, -0.07f, 0.07f, 0.55f, 0.07f, DARK);
                    rb(ps, vc, light, sx * 0.55f, 1.12f, 0.0f, 2, sx * 40.0f, -0.05f, 0.0f, -0.05f, 0.05f, 0.35f, 0.05f, DARK);
                }
            }
            case 12 -> {                                              // mástil y bandera
                box(ps, vc, light, -0.03f, 0.68f, -0.3f, 0.03f, 1.2f, -0.24f, JOINT);
                box(ps, vc, light, 0.03f, 0.95f, -0.3f, 0.4f, 1.15f, -0.26f, EMBER);
            }
            case 13 -> box(ps, vc, light, -0.3f, 0.68f, 0.1f, 0.3f, 0.8f, 0.4f, EMBER);      // faro de obras
            case 14 -> {                                              // cornamenta
                for (int sx = -1; sx <= 1; sx += 2) {
                    rb(ps, vc, light, sx * 0.3f, 0.62f, 0.0f, 2, -sx * 25.0f, -0.05f, 0.0f, -0.05f, 0.05f, 0.7f, 0.05f, IRON);
                    rb(ps, vc, light, sx * 0.5f, 1.2f, 0.0f, 2, sx * 35.0f, -0.04f, 0.0f, -0.04f, 0.04f, 0.4f, 0.04f, IRON);
                    rb(ps, vc, light, sx * 0.43f, 0.95f, 0.0f, 2, -sx * 70.0f, -0.04f, 0.0f, -0.04f, 0.04f, 0.35f, 0.04f, IRON);
                }
            }
            case 15 -> {                                              // grandes cuernos
                for (int sx = -1; sx <= 1; sx += 2) {
                    rb(ps, vc, light, sx * 0.4f, 0.6f, 0.1f, 2, -sx * 15.0f, -0.09f, 0.0f, -0.09f, 0.09f, 0.7f, 0.09f, DARK);
                    rb(ps, vc, light, sx * 0.55f, 1.28f, 0.1f, 2, sx * 30.0f, -0.07f, 0.0f, -0.07f, 0.07f, 0.45f, 0.07f, EMBER);
                }
            }
            case 16 -> {                                              // cuchillas laterales
                for (int sx = -1; sx <= 1; sx += 2) {
                    rb(ps, vc, light, sx * 0.5f, 0.45f, -0.1f, 2, -sx * 55.0f, -0.03f, 0.0f, -0.25f, 0.03f, 0.7f, 0.25f, DARK);
                }
            }
            default -> { }
        }
    }

    /** Piezas extra del antebrazo (origen: antebrazo-cañón; el cañón apunta a +Z). */
    private void armExtras(PoseStack ps, VertexConsumer vc, int light, int side) {
        switch (this.skin) {
            case 3 -> box(ps, vc, light, -0.1f, 0.1f, 0.0f, 0.1f, 0.55f, 0.35f, EMBER);                // cristal
            case 6 -> box(ps, vc, light, side * 0.3f - 0.04f, -0.5f, 0.0f, side * 0.3f + 0.04f, 0.1f, 0.6f, EMBER);   // tubo
            case 8 -> mb(ps, vc, light, side, 0.3f, -0.45f, -0.3f, 0.4f, 0.15f, 0.7f, DARK);          // escudo lateral
            case 10 -> box(ps, vc, light, -0.04f, 0.1f, -0.2f, 0.04f, 0.45f, 0.3f, IRON);              // aleta
            case 12 -> box(ps, vc, light, -0.28f, -0.42f, 0.65f, 0.28f, -0.3f, 1.4f, DARK);            // segundo cañón
            case 14 -> box(ps, vc, light, -0.32f, -0.2f, 0.0f, 0.32f, -0.12f, 0.6f, EMBER);            // enredadera
            case 15 -> box(ps, vc, light, -0.04f, 0.1f, 0.6f, 0.04f, 0.4f, 1.1f, DARK);               // cuchilla
            case 16 -> box(ps, vc, light, -0.03f, -0.36f, 0.3f, 0.03f, -0.28f, 1.6f, DARK);            // hoja
            case 1 -> box(ps, vc, light, -0.34f, -0.1f, 0.0f, 0.34f, 0.0f, 0.5f, EMBER);
            default -> { }
        }
    }

    /** Piezas extra de las piernas (origen: cadera). */
    private void legExtras(PoseStack ps, VertexConsumer vc, int light, int side) {
        switch (this.skin) {
            case 11, 15, 16 -> box(ps, vc, light, -0.06f, -1.05f, 0.34f, 0.06f, -0.8f, 0.6f, DARK);   // pincho en la rodilla
            case 2, 13 -> box(ps, vc, light, -0.3f, -0.65f, -0.34f, 0.3f, -0.3f, -0.27f, HAZARD);
            case 8 -> mb(ps, vc, light, side, 0.27f, -0.8f, -0.3f, 0.36f, -0.12f, 0.3f, DARK);
            default -> { }
        }
    }

    /** side: +1 = izquierda, -1 = derecha. Cuelga del hombro (origen en el torso, a 0,8 de altura). */
    private void drawArm(PoseStack ps, VertexConsumer vc, int light, int side, float pitch, float recoil) {
        ps.pushPose();
        ps.translate(side * 1.25f, 0.75f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        box(ps, vc, light, -0.2f, -0.2f, -0.2f, 0.2f, 0.2f, 0.2f, JOINT);                  // hombro
        box(ps, vc, light, -0.25f, -0.45f, -0.25f, 0.25f, 0.0f, 0.25f, DARK);              // brazo
        ps.translate(0.0f, -0.4f, -recoil);
        box(ps, vc, light, -0.3f, -0.4f, -0.3f, 0.3f, 0.1f, 0.65f, IRON);                  // antebrazo-cañón
        box(ps, vc, light, -0.33f, -0.34f, 0.3f, 0.33f, 0.04f, 0.4f, RIVET);
        box(ps, vc, light, -0.17f, -0.27f, 0.65f, 0.17f, 0.0f, 1.45f, DARK);               // cañón
        box(ps, vc, light, -0.22f, -0.32f, 1.3f, 0.22f, 0.05f, 1.5f, JOINT);               // freno de boca
        box(ps, vc, light, -0.12f, -0.22f, 1.5f, 0.12f, -0.05f, 1.51f, EMBER);
        armExtras(ps, vc, light, side);
        ps.popPose();
    }

    /**
     * Cabeza-cabina: sigue la mirada del piloto. Origen: base de la cabeza (sobre el cuello).
     * La ventana es un hueco real cubierto por un cristal translúcido. En primera persona, sólo se dibuja el marco
     * para que el piloto vea hacia fuera.
     */
    private void drawHead(PoseStack ps, VertexConsumer vc, MultiBufferSource buffer, int light, float pitch, boolean inside) {
        ps.pushPose();
        ps.translate(0.0f, 1.0f, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(pitch * 0.6f));
        if (this.skin == 7 && !inside) {
            ps.scale(1.35f, 1.35f, 1.35f);                                                 // cabezón
        }
        if (inside) {
            box(ps, vc, light, -0.5f, 0.0f, 0.4f, 0.5f, 0.08f, 0.52f, DARK);               // salpicadero
            for (int sx = -1; sx <= 1; sx += 2) {
                box(ps, vc, light, sx * 0.5f - 0.04f, 0.0f, 0.44f, sx * 0.5f + 0.04f, 0.7f, 0.52f, DARK);   // montantes
            }
            ps.popPose();
            return;
        }
        box(ps, vc, light, -0.5f, 0.0f, -0.5f, 0.5f, 0.6f, 0.3f, IRON);                    // casco (parte trasera)
        box(ps, vc, light, -0.5f, 0.0f, 0.3f, 0.5f, 0.2f, 0.5f, IRON);                     // bajo la ventana
        box(ps, vc, light, -0.5f, 0.48f, 0.3f, 0.5f, 0.6f, 0.5f, IRON);                    // sobre la ventana
        box(ps, vc, light, -0.5f, 0.2f, 0.3f, -0.42f, 0.48f, 0.5f, DARK);                  // laterales de la ventana
        box(ps, vc, light, 0.42f, 0.2f, 0.3f, 0.5f, 0.48f, 0.5f, DARK);
        box(ps, vc, light, -0.45f, 0.6f, -0.45f, 0.45f, 0.68f, 0.4f, RIVET);               // techo
        box(ps, vc, light, -0.04f, 0.68f, -0.3f, 0.04f, 1.0f, -0.26f, JOINT);              // antena
        box(ps, vc, light, -0.06f, 1.0f, -0.32f, 0.06f, 1.06f, -0.24f, EMBER);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, vc, light, s * 0.5f - 0.02f, 0.15f, -0.2f, s * 0.58f, 0.45f, 0.2f, VENT);   // oídos
        }
        headExtras(ps, vc, light);
        // El cristal va lo último: pedir otro tipo de buffer cierra el anterior, así que ya no se dibuja nada opaco después
        VertexConsumer glass = buffer.getBuffer(RenderType.entityTranslucent(this.tex));
        box(ps, glass, light, -0.42f, 0.2f, 0.46f, 0.42f, 0.48f, 0.5f, VISOR);             // cristal translúcido
        ps.popPose();
    }

    // ------------------------------------------------------------------ Geometría

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
        vertex(pose, vc, light, m.c(), m.alpha(), u0, v1, nx, ny, nz, ax, ay, az);
        vertex(pose, vc, light, m.c(), m.alpha(), u1, v1, nx, ny, nz, bx, by, bz);
        vertex(pose, vc, light, m.c(), m.alpha(), u1, v0, nx, ny, nz, cx, cy, cz);
        vertex(pose, vc, light, m.c(), m.alpha(), u0, v0, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, int[] c, int alpha, float u, float v,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(pose, x, y, z)
                .setColor(c[0], c[1], c[2], alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
