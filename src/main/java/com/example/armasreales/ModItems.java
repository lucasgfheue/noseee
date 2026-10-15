package com.example.armasreales;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArmasRealesMod.MOD_ID);

    public static final DeferredItem<PistolItem> PISTOLA = ITEMS.registerItem(
            "pistola",
            PistolItem::new,
            new Item.Properties().stacksTo(1).durability(500));

    public static final DeferredItem<Item> MUNICION_PISTOLA = ITEMS.registerItem(
            "municion_pistola",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<LaserRifleItem> RIFLE_LASER = ITEMS.registerItem(
            "rifle_laser",
            LaserRifleItem::new,
            new Item.Properties().stacksTo(1).durability(400));

    public static final DeferredItem<Item> CELDA_ENERGIA = ITEMS.registerItem(
            "celda_energia",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<TntLauncherItem> LANZADOR_TNT = ITEMS.registerItem(
            "lanzador_tnt",
            TntLauncherItem::new,
            new Item.Properties().stacksTo(1).durability(150));

    public static final DeferredItem<FlareGunItem> PISTOLA_BENGALAS = ITEMS.registerItem(
            "pistola_bengalas",
            FlareGunItem::new,
            new Item.Properties().stacksTo(1).durability(120));

    public static final DeferredItem<PlasmaRifleItem> RIFLE_PLASMA = ITEMS.registerItem(
            "rifle_plasma",
            PlasmaRifleItem::new,
            new Item.Properties().stacksTo(1).durability(300));

    public static final DeferredItem<TaserItem> TASER = ITEMS.registerItem(
            "taser",
            TaserItem::new,
            new Item.Properties().stacksTo(1).durability(200));

    public static final DeferredItem<AcidPistolItem> PISTOLA_ACIDA = ITEMS.registerItem(
            "pistola_acida",
            AcidPistolItem::new,
            new Item.Properties().stacksTo(1).durability(200));

    public static final DeferredItem<VioletRifleItem> RIFLE_VIOLETA = ITEMS.registerItem(
            "rifle_violeta",
            VioletRifleItem::new,
            new Item.Properties().stacksTo(1).durability(250));

    public static final DeferredItem<TankItem> TANQUE = ITEMS.registerItem(
            "tanque",
            props -> new TankItem(props, () -> ModEntities.TANK.get()),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<TankItem> TANQUE_FORTALEZA = ITEMS.registerItem(
            "tanque_fortaleza",
            props -> new TankItem(props, () -> ModEntities.FORTRESS_TANK.get()),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<TankItem> TANQUE_CIUDADELA = ITEMS.registerItem(
            "tanque_ciudadela",
            props -> new TankItem(props, () -> ModEntities.CITADEL_TANK.get()),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<TankItem> TANQUE_UMBRA = ITEMS.registerItem(
            "tanque_umbra",
            props -> new TankItem(props, () -> ModEntities.UMBRA_TANK.get()),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<TankItem> TANQUE_DOBLE = ITEMS.registerItem(
            "tanque_doble",
            props -> new TankItem(props, () -> ModEntities.TWIN_TANK.get()),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<TitanRevolverItem> REVOLVER_TITAN = ITEMS.registerItem(
            "revolver_titan", TitanRevolverItem::new, new Item.Properties().stacksTo(1).durability(400));

    public static final DeferredItem<Item> CARGA_LAB10 = ITEMS.registerItem(
            "carga_lab10", Item::new, new Item.Properties());

    public static final DeferredItem<RingRifleItem> FUSIL_ANILLO = ITEMS.registerItem(
            "fusil_anillo", RingRifleItem::new, new Item.Properties().stacksTo(1).durability(450));

    public static final DeferredItem<Item> FERRO9 = ITEMS.registerItem(
            "ferro9", Item::new, new Item.Properties());

    public static final DeferredItem<IronBowItem> ARCO_DE_HIERRO = ITEMS.registerItem(
            "arco_de_hierro", IronBowItem::new, new Item.Properties().stacksTo(1).durability(400));

    public static final DeferredItem<Item> OMG3 = ITEMS.registerItem(
            "omg3", Item::new, new Item.Properties());

    public static final DeferredItem<OmegaCannonItem> X32_OMEGA = ITEMS.registerItem(
            "x32_omega", OmegaCannonItem::new, new Item.Properties().stacksTo(1).durability(300).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<Item> ECL4 = ITEMS.registerItem(
            "ecl4", Item::new, new Item.Properties());

    public static final DeferredItem<EclipseLauncherItem> X29_ECLIPSE = ITEMS.registerItem(
            "x29_eclipse", EclipseLauncherItem::new, new Item.Properties().stacksTo(1).durability(300).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<Item> BIOCORE = ITEMS.registerItem(
            "biocore", Item::new, new Item.Properties());

    public static final DeferredItem<LazaroRifleItem> LAZARO = ITEMS.registerItem(
            "lazaro", LazaroRifleItem::new, new Item.Properties().stacksTo(1).durability(450).rarity(net.minecraft.world.item.Rarity.RARE)
                    .component(net.minecraft.core.component.DataComponents.CUSTOM_DATA, LazaroRifleItem.initialData()));

    public static final DeferredItem<Item> FROST_CELL = ITEMS.registerItem(
            "frost_cell", Item::new, new Item.Properties());

    public static final DeferredItem<BorealPistolItem> BOREAS = ITEMS.registerItem(
            "boreas", BorealPistolItem::new, new Item.Properties().stacksTo(1).durability(400));

    public static final DeferredItem<Item> SENTINEL_B = ITEMS.registerItem(
            "sentinel_b", Item::new, new Item.Properties());

    public static final DeferredItem<SentinelGunItem> SENTINELA = ITEMS.registerItem(
            "sentinela", SentinelGunItem::new, new Item.Properties().stacksTo(1).durability(700).rarity(net.minecraft.world.item.Rarity.RARE)
                    .component(net.minecraft.core.component.DataComponents.CUSTOM_DATA, SentinelGunItem.initialData()));

    public static final DeferredItem<Item> REFRACT_5 = ITEMS.registerItem(
            "refract_5", Item::new, new Item.Properties());

    public static final DeferredItem<PrismRifleItem> PRISMA = ITEMS.registerItem(
            "prisma", PrismRifleItem::new, new Item.Properties().stacksTo(1).durability(450).rarity(net.minecraft.world.item.Rarity.RARE));

    public static final DeferredItem<Item> SOL_3 = ITEMS.registerItem(
            "sol_3", Item::new, new Item.Properties());

    public static final DeferredItem<SolarisLauncherItem> SOLARIS = ITEMS.registerItem(
            "solaris", SolarisLauncherItem::new, new Item.Properties().stacksTo(1).durability(300).rarity(net.minecraft.world.item.Rarity.RARE));

    public static final DeferredItem<Item> SWARM_6 = ITEMS.registerItem(
            "swarm_6", Item::new, new Item.Properties());

    public static final DeferredItem<SwarmLauncherItem> ENJAMBRE = ITEMS.registerItem(
            "enjambre", SwarmLauncherItem::new, new Item.Properties().stacksTo(1).durability(300).rarity(net.minecraft.world.item.Rarity.RARE));

    public static final DeferredItem<Item> MASS_1 = ITEMS.registerItem(
            "mass_1", Item::new, new Item.Properties());

    public static final DeferredItem<MonolithCannonItem> MONOLITO = ITEMS.registerItem(
            "monolito", MonolithCannonItem::new, new Item.Properties().stacksTo(1).durability(300).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<Item> MUNICION_FUSIL = ITEMS.registerItem(
            "municion_fusil", Item::new, new Item.Properties());

    public static final DeferredItem<AkItem> AK47 = ITEMS.registerItem(
            "ak47", AkItem::new, new Item.Properties().stacksTo(1).durability(600));

    public static final DeferredItem<Item> CARTUCHO_ESCOPETA = ITEMS.registerItem(
            "cartucho_escopeta", Item::new, new Item.Properties());

    public static final DeferredItem<ShotgunItem> ESCOPETA = ITEMS.registerItem(
            "escopeta", ShotgunItem::new, new Item.Properties().stacksTo(1).durability(300));

    /** Cuchillo: máximo 3 por casilla. */
    public static final DeferredItem<KnifeItem> CUCHILLO = ITEMS.registerItem(
            "cuchillo", KnifeItem::new, new Item.Properties().stacksTo(3).attributes(KnifeItem.attributes()));

    public static final DeferredItem<PumpkinCannonItem> CANON_CALABAZAS = ITEMS.registerItem(
            "canon_calabazas", PumpkinCannonItem::new, new Item.Properties().stacksTo(1).durability(150));

    public static final DeferredItem<BombItem> BOMBA = ITEMS.registerItem(
            "bomba", BombItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<SpawnEggItem> ZOMBI_SPAWN_EGG = ITEMS.registerItem(
            "zombi_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ZOMBI.get(), 0x5C7F4B, 0x3B4D8A, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> HOMBRE_LOBO_SPAWN_EGG = ITEMS.registerItem(
            "hombre_lobo_spawn_egg",
            props -> new SpawnEggItem(ModEntities.HOMBRE_LOBO.get(), 0x3A2F2A, 0xE8B923, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> CAMPISTA_SPAWN_EGG = ITEMS.registerItem(
            "campista_spawn_egg",
            props -> new SpawnEggItem(ModEntities.CAMPISTA.get(), 0x5B7A3A, 0xC9A66B, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> ANCIANO_SPAWN_EGG = ITEMS.registerItem(
            "anciano_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ANCIANO.get(), 0xE6E6E6, 0x6B6B6B, props),
            new Item.Properties());

    public static final DeferredItem<Item> MUNICION_FRANCOTIRADOR = ITEMS.registerItem(
            "municion_francotirador", Item::new, new Item.Properties());

    public static final DeferredItem<SniperItem> FRANCOTIRADOR = ITEMS.registerItem(
            "francotirador", SniperItem::new, new Item.Properties().stacksTo(1).durability(250));

    public static final DeferredItem<SpawnEggItem> SOLDADO_SPAWN_EGG = ITEMS.registerItem(
            "soldado_spawn_egg",
            props -> new SpawnEggItem(ModEntities.SOLDADO.get(), 0x4B5B3A, 0x2B2B2B, props),
            new Item.Properties());

    public static final DeferredItem<net.minecraft.world.item.BlockItem> RADIO = ITEMS.registerSimpleBlockItem(
            "radio", ModBlocks.RADIO);

    public static final DeferredItem<TurretItem> TORRETA = ITEMS.registerItem(
            "torreta", props -> new TurretItem(props, TurretEntity.MG), new Item.Properties().stacksTo(1));

    public static final DeferredItem<TurretItem> TORRETA_PESADA = ITEMS.registerItem(
            "torreta_pesada", props -> new TurretItem(props, TurretEntity.HEAVY), new Item.Properties().stacksTo(1));

    public static final DeferredItem<SpawnEggItem> ZOMBI_PRISIONERO_SPAWN_EGG = ITEMS.registerItem(
            "zombi_prisionero_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ZOMBI_PRISIONERO.get(), 0x5C7F4B, 0xE8731A, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> ZOMBI_POLICIA_SPAWN_EGG = ITEMS.registerItem(
            "zombi_policia_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ZOMBI_POLICIA.get(), 0x5C7F4B, 0x1E2A55, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> ZOMBI_SWAT_SPAWN_EGG = ITEMS.registerItem(
            "zombi_swat_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ZOMBI_SWAT.get(), 0x2A2A2E, 0x8A8A92, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> ZOMBI_CAMINANTE_SPAWN_EGG = ITEMS.registerItem(
            "zombi_caminante_spawn_egg",
            props -> new SpawnEggItem(ModEntities.ZOMBI_CAMINANTE.get(), 0x5C7F4B, 0x6B7C8A, props),
            new Item.Properties());

    public static final DeferredItem<SpawnEggItem> SOBREVIVIENTE_SPAWN_EGG = ITEMS.registerItem(
            "sobreviviente_spawn_egg",
            props -> new SpawnEggItem(ModEntities.SOBREVIVIENTE.get(), 0xE2761A, 0xC9A27A, props),
            new Item.Properties());

    public static final DeferredItem<Item> CELDA_CRIOGENICA = ITEMS.registerItem(
            "celda_criogenica", Item::new, new Item.Properties());

    public static final DeferredItem<CryoRifleItem> RIFLE_CRIOGENICO = ITEMS.registerItem(
            "rifle_criogenico", CryoRifleItem::new, new Item.Properties().stacksTo(1).durability(500));

    public static final DeferredItem<Item> MUNICION_MAGNUM = ITEMS.registerItem(
            "municion_magnum", Item::new, new Item.Properties());

    public static final DeferredItem<MagnumItem> REVOLVER_LATON = ITEMS.registerItem(
            "revolver_laton", MagnumItem::new, new Item.Properties().stacksTo(1).durability(300));

    public static final DeferredItem<Item> BOLA_ELECTRICA = ITEMS.registerItem(
            "bola_electrica", Item::new, new Item.Properties());
    public static final DeferredItem<Item> CHISPA = ITEMS.registerItem(
            "chispa", Item::new, new Item.Properties());
    public static final DeferredItem<Item> CARGA_EXPLOSIVA = ITEMS.registerItem(
            "carga_explosiva", Item::new, new Item.Properties());

    public static final DeferredItem<AutoGunItem> SUBFUSIL_TORMENTA = ITEMS.registerItem(
            "subfusil_tormenta", props -> new AutoGunItem(props, () -> MUNICION_PISTOLA.get(), 4.0f, 2, 0.06, 0.02, 0.035, 0.85f, AutoGunItem.Fx.NONE, 0.9f), new Item.Properties().stacksTo(1).durability(500));

    public static final DeferredItem<AutoGunItem> FUSIL_AMBAR = ITEMS.registerItem(
            "fusil_ambar", props -> new AutoGunItem(props, () -> MUNICION_FUSIL.get(), 7.0f, 3, 0.04, 0.01, 0.02, 0.7f, AutoGunItem.Fx.IGNITE, 0.5f), new Item.Properties().stacksTo(1).durability(450));

    public static final DeferredItem<RevolverItem> REVOLVER_COLMILLO = ITEMS.registerItem(
            "revolver_colmillo", props -> new RevolverItem(props, () -> MUNICION_PISTOLA.get(), 9.0f, 12, 80.0, 0.04, RevolverItem.Fx.SLOW, 0.8f, RevolverItem.Mode.ELECTRIC_BALL), new Item.Properties().stacksTo(1).durability(350));

    public static final DeferredItem<RevolverItem> REVOLVER_ECLIPSE = ITEMS.registerItem(
            "revolver_eclipse", props -> new RevolverItem(props, () -> MUNICION_MAGNUM.get(), 12.0f, 20, 100.0, 0.05, RevolverItem.Fx.WITHER, 0.45f, RevolverItem.Mode.PIERCE), new Item.Properties().stacksTo(1).durability(300));

    public static final DeferredItem<ExplosiveLauncherItem> LANZAGRANADAS_CHISPA = ITEMS.registerItem(
            "lanzagranadas_chispa", props -> new ExplosiveLauncherItem(props, () -> CARGA_EXPLOSIVA.get(), ExplosiveShot.GRENADE, 2.6f, 45, 1.4f, 25, 0.9f), new Item.Properties().stacksTo(1).durability(300));

    public static final DeferredItem<ExplosiveLauncherItem> BAZUCA_NOVA = ITEMS.registerItem(
            "bazuca_nova", props -> new ExplosiveLauncherItem(props, () -> CARGA_EXPLOSIVA.get(), ExplosiveShot.ROCKET, 3.2f, 0, 2.2f, 40, 0.6f), new Item.Properties().stacksTo(1).durability(250));

    public static final DeferredItem<ExplosiveLauncherItem> LANZADOR_RACIMO = ITEMS.registerItem(
            "lanzador_racimo", props -> new ExplosiveLauncherItem(props, () -> CARGA_EXPLOSIVA.get(), ExplosiveShot.CLUSTER, 1.8f, 0, 1.6f, 35, 0.8f), new Item.Properties().stacksTo(1).durability(250));

    public static final DeferredItem<ExplosiveLauncherItem> CANON_ADHESIVO = ITEMS.registerItem(
            "canon_adhesivo", props -> new ExplosiveLauncherItem(props, () -> CARGA_EXPLOSIVA.get(), ExplosiveShot.STICKY, 3.0f, 40, 1.5f, 30, 1.0f), new Item.Properties().stacksTo(1).durability(250));

    public static final DeferredItem<MeleeFxItem> HACHA_VOLCANICA = ITEMS.registerItem(
            "hacha_volcanica", props -> new MeleeFxItem(props, MeleeFxItem.Fx.VOLCANIC),
            new Item.Properties().stacksTo(1).durability(500).attributes(MeleeFxItem.attributes(8.0, -3.2)));

    public static final DeferredItem<MeleeFxItem> KATANA_SOMBRIA = ITEMS.registerItem(
            "katana_sombria", props -> new MeleeFxItem(props, MeleeFxItem.Fx.SHADOW),
            new Item.Properties().stacksTo(1).durability(450).attributes(MeleeFxItem.attributes(6.0, -1.2)));
}
