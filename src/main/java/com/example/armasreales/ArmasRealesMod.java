package com.example.armasreales;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(ArmasRealesMod.MOD_ID)
public class ArmasRealesMod {
    public static final String MOD_ID = "armasreales";

    public ArmasRealesMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModStructures.TYPES.register(modBus);
        ModStructures.PIECES.register(modBus);
        modBus.addListener(this::addToCreativeTabs);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerSpawns);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CampSpawner::onCommands);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TANK.get(), TankEntity.createAttributes().build());
        event.put(ModEntities.TWIN_TANK.get(), TankEntity.createAttributes().build());
        event.put(ModEntities.ZOMBI.get(), Zombie.createAttributes().build());
        event.put(ModEntities.HOMBRE_LOBO.get(), WerewolfEntity.createAttributes().build());
        event.put(ModEntities.CAMPISTA.get(), CampistaEntity.createAttributes().build());
        event.put(ModEntities.ANCIANO.get(), AncianoEntity.createAttributes().build());
    }

    private void registerSpawns(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.ZOMBI.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
        // Sin la restricción de oscuridad: también aparece de día, con forma humana
        event.register(ModEntities.HOMBRE_LOBO.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkAnyLightMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FirePayload.TYPE, FirePayload.STREAM_CODEC, FirePayload::handle);
    }

    private void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.ZOMBI_SPAWN_EGG);
            event.accept(ModItems.HOMBRE_LOBO_SPAWN_EGG);
            event.accept(ModItems.CAMPISTA_SPAWN_EGG);
            event.accept(ModItems.ANCIANO_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.PISTOLA);
            event.accept(ModItems.MUNICION_PISTOLA);
            event.accept(ModItems.RIFLE_LASER);
            event.accept(ModItems.CELDA_ENERGIA);
            event.accept(ModItems.LANZADOR_TNT);
            event.accept(ModItems.PISTOLA_BENGALAS);
            event.accept(ModItems.RIFLE_PLASMA);
            event.accept(ModItems.TASER);
            event.accept(ModItems.PISTOLA_ACIDA);
            event.accept(ModItems.RIFLE_VIOLETA);
            event.accept(ModItems.TANQUE);
            event.accept(ModItems.TANQUE_DOBLE);
            event.accept(ModItems.AK47);
            event.accept(ModItems.MUNICION_FUSIL);
            event.accept(ModItems.ESCOPETA);
            event.accept(ModItems.CARTUCHO_ESCOPETA);
            event.accept(ModItems.CUCHILLO);
            event.accept(ModItems.CANON_CALABAZAS);
            event.accept(ModItems.BOMBA);
            event.accept(ModItems.ZOMBI_SPAWN_EGG);
            event.accept(ModItems.HOMBRE_LOBO_SPAWN_EGG);
            event.accept(ModItems.CAMPISTA_SPAWN_EGG);
            event.accept(ModItems.ANCIANO_SPAWN_EGG);
        }
    }
}
