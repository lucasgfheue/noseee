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

    public static final DeferredItem<TankItem> TANQUE_DOBLE = ITEMS.registerItem(
            "tanque_doble",
            props -> new TankItem(props, () -> ModEntities.TWIN_TANK.get()),
            new Item.Properties().stacksTo(1));

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
}
