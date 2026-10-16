package com.example.armasreales;

import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Quien va dentro de un tanque no recibe daño de fuera mientras el tanque siga vivo. */
@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID)
public final class TankSeatEvents {
    private TankSeatEvents() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof MechaEntity mecha
                && event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            mecha.noteAttacker(attacker);                  // el modo automático responderá a quien dañe al piloto
        }
        if (event.getEntity().getVehicle() instanceof TankEntity tank && tank.isAlive()
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }
}
