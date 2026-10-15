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
        ModBlocks.BLOCKS.register(modBus);
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
        event.put(ModEntities.SOLDADO.get(), SoldadoEntity.createAttributes().build());
        event.put(ModEntities.SOBREVIVIENTE.get(), SobrevivienteEntity.createAttributes().build());
        event.put(ModEntities.TURRET.get(), TurretEntity.createAttributes().build());
        event.put(ModEntities.ZOMBI_PRISIONERO.get(), PrisonZombie.createAttributes(PrisonZombie.PRISONER).build());
        event.put(ModEntities.ZOMBI_POLICIA.get(), PrisonZombie.createAttributes(PrisonZombie.POLICE).build());
        event.put(ModEntities.ZOMBI_SWAT.get(), PrisonZombie.createAttributes(PrisonZombie.SWAT).build());
        event.put(ModEntities.ZOMBI_CAMINANTE.get(), PrisonZombie.createAttributes(PrisonZombie.WALKER).build());
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
            event.accept(ModItems.SOLDADO_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_PRISIONERO_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_POLICIA_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_SWAT_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_CAMINANTE_SPAWN_EGG);
            event.accept(ModItems.SOBREVIVIENTE_SPAWN_EGG);
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
            event.accept(ModItems.RIFLE_CRIOGENICO);
            event.accept(ModItems.REVOLVER_LATON);
            event.accept(ModItems.SUBFUSIL_TORMENTA);
            event.accept(ModItems.FUSIL_AMBAR);
            event.accept(ModItems.REVOLVER_COLMILLO);
            event.accept(ModItems.REVOLVER_TITAN);
            event.accept(ModItems.FUSIL_ANILLO);
            event.accept(ModItems.ARCO_DE_HIERRO);
            event.accept(ModItems.X32_OMEGA);
            event.accept(ModItems.X29_ECLIPSE);
            event.accept(ModItems.LAZARO);
            event.accept(ModItems.BOREAS);
            event.accept(ModItems.FROST_CELL);
            event.accept(ModItems.SENTINELA);
            event.accept(ModItems.SENTINEL_B);
            event.accept(ModItems.PRISMA);
            event.accept(ModItems.REFRACT_5);
            event.accept(ModItems.SOLARIS);
            event.accept(ModItems.SOL_3);
            event.accept(ModItems.ENJAMBRE);
            event.accept(ModItems.SWARM_6);
            event.accept(ModItems.MONOLITO);
            event.accept(ModItems.MASS_1);
            event.accept(ModItems.BIOCORE);
            event.accept(ModItems.ECL4);
            event.accept(ModItems.OMG3);
            event.accept(ModItems.FERRO9);
            event.accept(ModItems.CARGA_LAB10);
            event.accept(ModItems.REVOLVER_ECLIPSE);
            event.accept(ModItems.LANZAGRANADAS_CHISPA);
            event.accept(ModItems.BAZUCA_NOVA);
            event.accept(ModItems.LANZADOR_RACIMO);
            event.accept(ModItems.CANON_ADHESIVO);
            event.accept(ModItems.HACHA_VOLCANICA);
            event.accept(ModItems.KATANA_SOMBRIA);
            event.accept(ModItems.MUNICION_FUSIL);
            event.accept(ModItems.ESCOPETA);
            event.accept(ModItems.CARTUCHO_ESCOPETA);
            event.accept(ModItems.FRANCOTIRADOR);
            event.accept(ModItems.RADIO);
            event.accept(ModItems.TORRETA);
            event.accept(ModItems.TORRETA_PESADA);
            event.accept(ModItems.MUNICION_FRANCOTIRADOR);
            event.accept(ModItems.CELDA_CRIOGENICA);
            event.accept(ModItems.MUNICION_MAGNUM);
            event.accept(ModItems.CARGA_EXPLOSIVA);
            event.accept(ModItems.CUCHILLO);
            event.accept(ModItems.CANON_CALABAZAS);
            event.accept(ModItems.BOMBA);
            event.accept(ModItems.ZOMBI_SPAWN_EGG);
            event.accept(ModItems.HOMBRE_LOBO_SPAWN_EGG);
            event.accept(ModItems.CAMPISTA_SPAWN_EGG);
            event.accept(ModItems.ANCIANO_SPAWN_EGG);
            event.accept(ModItems.SOLDADO_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_PRISIONERO_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_POLICIA_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_SWAT_SPAWN_EGG);
            event.accept(ModItems.ZOMBI_CAMINANTE_SPAWN_EGG);
            event.accept(ModItems.SOBREVIVIENTE_SPAWN_EGG);
        }
    }
}
