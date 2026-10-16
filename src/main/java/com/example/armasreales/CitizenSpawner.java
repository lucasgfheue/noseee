package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Hace aparecer ciudadanos alrededor de los jugadores dentro de la ciudad, en calles e interiores. */
public final class CitizenSpawner {
    private CitizenSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 0) {
            return;
        }
        if (!(level.getChunkSource().getGenerator() instanceof CityChunkGenerator)) {
            return;
        }
        boolean night = CiudadanoEntity.isNight(level);
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        int capCivil = night ? 0 : 26;
        int capArmed = night && !peaceful ? 8 : 4;
        int capThief = night && !peaceful ? 6 : 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || Math.abs(p.getX()) > CityPlan.HALF || Math.abs(p.getZ()) > CityPlan.HALF) {
                continue;
            }
            var box = p.getBoundingBox().inflate(48.0, 24.0, 48.0);
            int civil = 0, armed = 0, thief = 0;
            for (CiudadanoEntity c : level.getEntitiesOfClass(CiudadanoEntity.class, box)) {
                if (c.isCivil()) civil++;
                else if (c.isThief()) thief++;
                else armed++;
            }
            for (int n = 0; n < 3; n++) {
                int type;
                if (civil < capCivil && (n != 2 || level.random.nextBoolean())) {
                    type = 0;
                    civil++;
                } else if (armed < capArmed && level.random.nextInt(3) != 0) {
                    type = 1;
                    armed++;
                } else if (thief < capThief) {
                    type = 2;
                    thief++;
                } else {
                    break;
                }
                spawn(level, p, type);
            }
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player, int type) {
        boolean night = CiudadanoEntity.isNight(level);
        // De noche, la mayoría de los armados esperan dentro de edificios y casas (emboscada)
        boolean indoor = type == 1 && night ? level.random.nextFloat() < 0.7f : level.random.nextBoolean();
        for (int attempt = 0; attempt < 14; attempt++) {
            double ang = level.random.nextDouble() * Math.PI * 2;
            double dist = 14 + level.random.nextDouble() * 26;
            int x = (int) Math.floor(player.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(ang) * dist);
            if (Math.abs(x) >= CityPlan.HALF - 4 || Math.abs(z) >= CityPlan.HALF - 4) {
                continue;
            }
            int y = CityPlan.GROUND + 1 + (indoor ? 4 * level.random.nextInt(5) : 0);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState below = level.getBlockState(pos.below());
            if (below.isAir() || !below.getFluidState().isEmpty() || !below.isSolidRender(level, pos.below())
                    && !below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                continue;
            }
            if (level.canSeeSky(pos) == indoor) {
                continue;
            }
            CiudadanoEntity e = (type == 2 ? ModEntities.LADRON.get() : ModEntities.CIUDADANO.get()).create(level);
            if (e == null) {
                return;
            }
            e.setRole(type);
            e.ambush = type == 1 && night && indoor;
            e.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            if (!level.noCollision(e) || level.getNearestPlayer(e, 12.0) != null) {
                e.discard();
                continue;
            }
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            level.addFreshEntity(e);
            return;
        }
    }
}
