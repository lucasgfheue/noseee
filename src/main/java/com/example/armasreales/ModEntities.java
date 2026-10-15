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

    public static final DeferredHolder<EntityType<?>, EntityType<PumpkinProjectile>> PUMPKIN_PROJECTILE =
            ENTITIES.register("proyectil_calabaza", () ->
                    EntityType.Builder.<PumpkinProjectile>of(PumpkinProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_calabaza"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownKnife>> KNIFE_PROJECTILE =
            ENTITIES.register("cuchillo_lanzado", () ->
                    EntityType.Builder.<ThrownKnife>of(ThrownKnife::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("cuchillo_lanzado"));

    public static final DeferredHolder<EntityType<?>, EntityType<BombProjectile>> BOMB_PROJECTILE =
            ENTITIES.register("proyectil_bomba", () ->
                    EntityType.Builder.<BombProjectile>of(BombProjectile::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("proyectil_bomba"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArmedZombie>> ZOMBI =
            ENTITIES.register("zombi", () ->
                    EntityType.Builder.<ArmedZombie>of(ArmedZombie::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .passengerAttachments(2.0125f)
                            .ridingOffset(-0.7f)
                            .clientTrackingRange(8)
                            .build("zombi"));

    public static final DeferredHolder<EntityType<?>, EntityType<WerewolfEntity>> HOMBRE_LOBO =
            ENTITIES.register("hombre_lobo", () ->
                    EntityType.Builder.<WerewolfEntity>of(WerewolfEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .clientTrackingRange(10)
                            .build("hombre_lobo"));

    public static final DeferredHolder<EntityType<?>, EntityType<BeamEntity>> BEAM =
            ENTITIES.register("rayo", () ->
                    EntityType.Builder.<BeamEntity>of(BeamEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("rayo"));

    public static final DeferredHolder<EntityType<?>, EntityType<CampistaEntity>> CAMPISTA =
            ENTITIES.register("campista", () ->
                    EntityType.Builder.<CampistaEntity>of(CampistaEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.62f)
                            .clientTrackingRange(10)
                            .build("campista"));

    public static final DeferredHolder<EntityType<?>, EntityType<AncianoEntity>> ANCIANO =
            ENTITIES.register("anciano", () ->
                    EntityType.Builder.<AncianoEntity>of(AncianoEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.62f)
                            .clientTrackingRange(10)
                            .build("anciano"));

    public static final DeferredHolder<EntityType<?>, EntityType<SoldadoEntity>> SOLDADO =
            ENTITIES.register("soldado", () ->
                    EntityType.Builder.<SoldadoEntity>of(SoldadoEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.62f)
                            .clientTrackingRange(10)
                            .build("soldado"));

    public static final DeferredHolder<EntityType<?>, EntityType<TurretEntity>> TURRET =
            ENTITIES.register("torreta", () ->
                    EntityType.Builder.<TurretEntity>of(TurretEntity::new, MobCategory.MISC)
                            .sized(1.1f, 1.2f)
                            // Asiento detrás del arma, para ver por encima del escudo
                            .passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 0.45, -0.55))
                            .clientTrackingRange(10)
                            .build("torreta"));

    public static final DeferredHolder<EntityType<?>, EntityType<PrisonZombie>> ZOMBI_PRISIONERO =
            ENTITIES.register("zombi_prisionero", () ->
                    EntityType.Builder.<PrisonZombie>of((t, l) -> new PrisonZombie(t, l, PrisonZombie.PRISONER), MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .passengerAttachments(2.0125f)
                            .ridingOffset(-0.7f)
                            .clientTrackingRange(8)
                            .build("zombi_prisionero"));

    public static final DeferredHolder<EntityType<?>, EntityType<PrisonZombie>> ZOMBI_POLICIA =
            ENTITIES.register("zombi_policia", () ->
                    EntityType.Builder.<PrisonZombie>of((t, l) -> new PrisonZombie(t, l, PrisonZombie.POLICE), MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .passengerAttachments(2.0125f)
                            .ridingOffset(-0.7f)
                            .clientTrackingRange(8)
                            .build("zombi_policia"));

    public static final DeferredHolder<EntityType<?>, EntityType<PrisonZombie>> ZOMBI_SWAT =
            ENTITIES.register("zombi_swat", () ->
                    EntityType.Builder.<PrisonZombie>of((t, l) -> new PrisonZombie(t, l, PrisonZombie.SWAT), MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .passengerAttachments(2.0125f)
                            .ridingOffset(-0.7f)
                            .clientTrackingRange(8)
                            .build("zombi_swat"));

    public static final DeferredHolder<EntityType<?>, EntityType<PrisonZombie>> ZOMBI_CAMINANTE =
            ENTITIES.register("zombi_caminante", () ->
                    EntityType.Builder.<PrisonZombie>of((t, l) -> new PrisonZombie(t, l, PrisonZombie.WALKER), MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .eyeHeight(1.74f)
                            .passengerAttachments(2.0125f)
                            .ridingOffset(-0.7f)
                            .clientTrackingRange(8)
                            .build("zombi_caminante"));

    public static final DeferredHolder<EntityType<?>, EntityType<SobrevivienteEntity>> SOBREVIVIENTE =
            ENTITIES.register("sobreviviente", () ->
                    EntityType.Builder.<SobrevivienteEntity>of(SobrevivienteEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .eyeHeight(1.62f)
                            .clientTrackingRange(10)
                            .build("sobreviviente"));

    public static final DeferredHolder<EntityType<?>, EntityType<ExplosiveShot>> EXPLOSIVE_SHOT =
            ENTITIES.register("proyectil_explosivo", () ->
                    EntityType.Builder.<ExplosiveShot>of(ExplosiveShot::new, MobCategory.MISC)
                            .sized(0.4f, 0.4f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("proyectil_explosivo"));

    public static final DeferredHolder<EntityType<?>, EntityType<ElectricShot>> ELECTRIC_SHOT =
            ENTITIES.register("disparo_electrico", () ->
                    EntityType.Builder.<ElectricShot>of(ElectricShot::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("disparo_electrico"));

    public static final DeferredHolder<EntityType<?>, EntityType<ShieldEntity>> SHIELD =
            ENTITIES.register("escudo_anillo", () ->
                    EntityType.Builder.<ShieldEntity>of(ShieldEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("escudo_anillo"));

    public static final DeferredHolder<EntityType<?>, EntityType<GravityZoneEntity>> GRAVITY_ZONE =
            ENTITIES.register("zona_horizonte", () ->
                    EntityType.Builder.<GravityZoneEntity>of(GravityZoneEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .noSave()
                            .noSummon()
                            .build("zona_horizonte"));
}
