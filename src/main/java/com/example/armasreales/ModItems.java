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
}
