package com.example.armasreales;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ArmasRealesMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TntProjectile>> TNT_PROJECTILE =
            ENTITIES.register("proyectil_tnt", () ->
                    EntityType.Builder.<TntProjectile>of(TntProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("proyectil_tnt"));

    public static final DeferredHolder<EntityType<?>, EntityType<FlareProjectile>> FLARE_PROJECTILE =
            ENTITIES.register("proyectil_bengala", () ->
                    EntityType.Builder.<FlareProjectile>of(FlareProjectile::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_bengala"));

    public static final DeferredHolder<EntityType<?>, EntityType<TaserProjectile>> TASER_PROJECTILE =
            ENTITIES.register("proyectil_taser", () ->
                    EntityType.Builder.<TaserProjectile>of(TaserProjectile::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("proyectil_taser"));

    public static final DeferredHolder<EntityType<?>, EntityType<AcidProjectile>> ACID_PROJECTILE =
            ENTITIES.register("proyectil_acido", () ->
                    EntityType.Builder.<AcidProjectile>of(AcidProjectile::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_acido"));

    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK =
            ENTITIES.register("tanque", () ->
                    EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                            // La hitbox de Minecraft es siempre cuadrada: 4x4 de base y 4 de alto.
                            // El casco mide 6 de largo, así que sobresale 1 por delante y por detrás.
                            .sized(2.0f * TankEntity.SCALE, 2.0f * TankEntity.SCALE)
                            // Asiento del conductor: la cadera a 1.4 x escala (asoma por la escotilla)
                            .passengerAttachments(1.4f * TankEntity.SCALE)
                            .fireImmune()
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("tanque"));

    public static final DeferredHolder<EntityType<?>, EntityType<TwinTankEntity>> TWIN_TANK =
            ENTITIES.register("tanque_doble", () ->
                    EntityType.Builder.<TwinTankEntity>of(TwinTankEntity::new, MobCategory.MISC)
                            .sized(2.0f * TankEntity.SCALE, 2.0f * TankEntity.SCALE)
                            .passengerAttachments(1.4f * TankEntity.SCALE)
                            .fireImmune()
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build("tanque_doble"));

    public static final DeferredHolder<EntityType<?>, EntityType<BeamEntity>> BEAM =
            ENTITIES.register("rayo", () ->
                    EntityType.Builder.<BeamEntity>of(BeamEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("rayo"));
}
