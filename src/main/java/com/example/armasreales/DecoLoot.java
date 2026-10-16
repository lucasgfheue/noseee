package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Muebles que se pueden registrar (clic derecho): nevera, cajones, caja fuerte... Dan comida, munición, oro y más,
 * una sola vez por mueble. Si hay un ciudadano armado cerca, te ataca de día o de noche.
 */
public final class DecoLoot {
    private DecoLoot() {}

    enum Kind { FOOD, DRAWER, VALUABLE, JUNK }

    static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("nevera", Kind.FOOD), Map.entry("cocina", Kind.FOOD), Map.entry("horno_empotrado", Kind.FOOD),
            Map.entry("microondas", Kind.FOOD), Map.entry("mueble_cocina", Kind.FOOD), Map.entry("tostadora", Kind.FOOD),
            Map.entry("cafetera", Kind.FOOD),
            Map.entry("mesa", Kind.DRAWER), Map.entry("escritorio", Kind.DRAWER), Map.entry("mesita_noche", Kind.DRAWER),
            Map.entry("archivador", Kind.DRAWER), Map.entry("armario", Kind.DRAWER), Map.entry("tocador", Kind.DRAWER),
            Map.entry("zapatero", Kind.DRAWER), Map.entry("estanteria", Kind.DRAWER), Map.entry("vitrina", Kind.DRAWER),
            Map.entry("mostrador", Kind.DRAWER), Map.entry("botiquin", Kind.DRAWER), Map.entry("mesa_redonda", Kind.DRAWER),
            Map.entry("caja_fuerte", Kind.VALUABLE), Map.entry("caja_registradora", Kind.VALUABLE),
            Map.entry("estante_super", Kind.FOOD), Map.entry("nevera_expositor", Kind.FOOD), Map.entry("congelador", Kind.FOOD),
            Map.entry("puesto_frutas", Kind.FOOD), Map.entry("cesta_compras", Kind.FOOD), Map.entry("caja_pago", Kind.VALUABLE),
            Map.entry("libreria", Kind.DRAWER), Map.entry("cajas_apiladas", Kind.JUNK), Map.entry("palet", Kind.JUNK),
            Map.entry("cubo_basura", Kind.JUNK), Map.entry("contenedor", Kind.JUNK),
            Map.entry("contenedor_reciclaje", Kind.JUNK), Map.entry("papelera", Kind.JUNK),
            Map.entry("taquilla", Kind.DRAWER), Map.entry("armero", Kind.VALUABLE), Map.entry("cocina_industrial", Kind.FOOD),
            Map.entry("barra_bar", Kind.FOOD), Map.entry("estante_botellas", Kind.FOOD), Map.entry("mesa_mantel", Kind.FOOD),
            Map.entry("horno_pizza", Kind.FOOD), Map.entry("palomitas", Kind.FOOD), Map.entry("ventanilla_banco", Kind.VALUABLE),
            Map.entry("banco_lab", Kind.DRAWER), Map.entry("pupitre", Kind.DRAWER), Map.entry("puesto_gamer", Kind.DRAWER),
            Map.entry("mesa_quirofano", Kind.DRAWER), Map.entry("cama_hospital", Kind.JUNK), Map.entry("camilla", Kind.JUNK),
            Map.entry("butaca_cine", Kind.JUNK), Map.entry("manguera_carrete", Kind.JUNK));

    static boolean isSearchable(String id) {
        return KINDS.containsKey(id);
    }

    // ------------------------------------------------------------------ Registro de ya saqueados

    public static final class Looted extends SavedData {
        private final Set<Long> set = new HashSet<>();

        static SavedData.Factory<Looted> factory() {
            return new SavedData.Factory<>(Looted::new, Looted::load, null);
        }

        static Looted load(CompoundTag tag, HolderLookup.Provider provider) {
            Looted l = new Looted();
            for (long v : tag.getLongArray("p")) {
                l.set.add(v);
            }
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            long[] arr = new long[this.set.size()];
            int i = 0;
            for (long v : this.set) {
                arr[i++] = v;
            }
            tag.putLongArray("p", arr);
            return tag;
        }

        static Looted get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(factory(), "armasreales_looted");
        }

        boolean has(BlockPos p) {
            return this.set.contains(p.asLong());
        }

        void add(BlockPos p) {
            if (this.set.add(p.asLong())) {
                this.setDirty();
            }
        }

        void remove(BlockPos p) {
            if (this.set.remove(p.asLong())) {
                this.setDirty();
            }
        }
    }

    /** Se llama al romper/quitar el mueble: vuelve a poder registrarse si se coloca otro. */
    static void forget(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            Looted.get(sl).remove(pos);
        }
    }

    // ------------------------------------------------------------------ Botín

    private record Entry(int weight, Supplier<ItemStack> item) {}

    private static Entry e(int w, Supplier<Item> item, int min, int max) {
        return new Entry(w, () -> {
            RandomSource r = RandomSource.create();
            ItemStack st = new ItemStack(item.get(), min + r.nextInt(max - min + 1));
            st.setCount(Math.min(st.getCount(), st.getMaxStackSize()));
            return st;
        });
    }

    private static List<Entry> table(Kind k) {
        List<Entry> t = new ArrayList<>();
        switch (k) {
            case FOOD -> {
                t.add(e(10, () -> Items.BREAD, 1, 3));
                t.add(e(10, () -> Items.APPLE, 1, 3));
                t.add(e(8, () -> Items.COOKED_BEEF, 1, 2));
                t.add(e(8, () -> Items.COOKED_PORKCHOP, 1, 2));
                t.add(e(8, () -> Items.COOKED_CHICKEN, 1, 2));
                t.add(e(7, () -> Items.CARROT, 2, 4));
                t.add(e(7, () -> Items.BAKED_POTATO, 1, 3));
                t.add(e(6, () -> Items.COOKIE, 2, 6));
                t.add(e(4, () -> Items.PUMPKIN_PIE, 1, 1));
                t.add(e(3, () -> Items.CAKE, 1, 1));
                t.add(e(2, () -> Items.GOLDEN_CARROT, 1, 2));
                t.add(e(3, () -> Items.GOLD_NUGGET, 1, 4));
                t.add(e(3, () -> ModItems.MUNICION_PISTOLA.get(), 4, 10));
            }
            case DRAWER -> {
                t.add(e(10, () -> Items.GOLD_NUGGET, 1, 5));
                t.add(e(7, () -> Items.GOLD_INGOT, 1, 2));
                t.add(e(8, () -> ModItems.MUNICION_PISTOLA.get(), 4, 12));
                t.add(e(5, () -> ModItems.CARTUCHO_ESCOPETA.get(), 2, 6));
                t.add(e(5, () -> ModItems.MUNICION_FUSIL.get(), 4, 12));
                t.add(e(2, () -> ModItems.MUNICION_MAGNUM.get(), 2, 6));
                t.add(e(1, () -> ModItems.MUNICION_FRANCOTIRADOR.get(), 1, 4));
                t.add(e(6, () -> Items.PAPER, 1, 3));
                t.add(e(4, () -> Items.IRON_NUGGET, 2, 6));
                t.add(e(4, () -> Items.BREAD, 1, 2));
                t.add(e(3, () -> Items.CLOCK, 1, 1));
                t.add(e(3, () -> Items.EMERALD, 1, 1));
            }
            case VALUABLE -> {
                t.add(e(12, () -> Items.GOLD_INGOT, 1, 4));
                t.add(e(8, () -> Items.GOLD_NUGGET, 3, 9));
                t.add(e(7, () -> Items.EMERALD, 1, 3));
                t.add(e(4, () -> Items.DIAMOND, 1, 1));
                t.add(e(8, () -> ModItems.MUNICION_PISTOLA.get(), 6, 16));
                t.add(e(5, () -> ModItems.MUNICION_FUSIL.get(), 6, 16));
                t.add(e(4, () -> ModItems.CARTUCHO_ESCOPETA.get(), 4, 8));
                t.add(e(3, () -> ModItems.MUNICION_MAGNUM.get(), 3, 8));
                t.add(e(2, () -> ModItems.MUNICION_FRANCOTIRADOR.get(), 2, 5));
            }
            case JUNK -> {
                t.add(e(10, () -> Items.ROTTEN_FLESH, 1, 2));
                t.add(e(8, () -> Items.BONE, 1, 2));
                t.add(e(8, () -> Items.STRING, 1, 3));
                t.add(e(8, () -> Items.IRON_NUGGET, 1, 4));
                t.add(e(6, () -> Items.STICK, 1, 4));
                t.add(e(4, () -> Items.GOLD_NUGGET, 1, 3));
                t.add(e(4, () -> Items.BREAD, 1, 1));
                t.add(e(3, () -> ModItems.MUNICION_PISTOLA.get(), 2, 6));
                t.add(e(2, () -> Items.IRON_INGOT, 1, 1));
            }
        }
        return t;
    }

    private static ItemStack roll(Kind k, RandomSource r) {
        List<Entry> t = table(k);
        int total = 0;
        for (Entry en : t) {
            total += en.weight();
        }
        int v = r.nextInt(total);
        for (Entry en : t) {
            v -= en.weight();
            if (v < 0) {
                return en.item().get();
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ Registrar

    static void search(Level level, BlockPos pos, BlockState state, Player player, String id) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        Looted looted = Looted.get(sl);
        if (looted.has(pos)) {
            player.displayClientMessage(Component.translatable("msg.armasreales.vacio"), true);
            return;
        }
        // marca también las demás partes del mismo mueble
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (sl.getBlockState(p).is(state.getBlock())) {
                looted.add(p.immutable());
            }
        }
        looted.add(pos);

        Kind kind = KINDS.get(id);
        SoundEvent open = "caja_fuerte".equals(id) ? SoundEvents.IRON_DOOR_OPEN
                : kind == Kind.FOOD ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN;
        sl.playSound(null, pos, open, SoundSource.BLOCKS, 0.7f, 1.0f);

        RandomSource r = sl.getRandom();
        int rolls = kind == Kind.VALUABLE ? 2 + r.nextInt(2) : 1 + r.nextInt(2);
        boolean empty = r.nextInt(100) < (kind == Kind.VALUABLE ? 8 : 22);
        List<ItemStack> found = new ArrayList<>();
        if (!empty) {
            for (int i = 0; i < rolls; i++) {
                ItemStack st = roll(kind, r);
                if (!st.isEmpty()) {
                    found.add(st);
                }
            }
        }
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.armasreales.nada"), true);
        } else {
            Component names = Component.empty();
            boolean first = true;
            for (ItemStack st : found) {
                names = ((net.minecraft.network.chat.MutableComponent) names)
                        .append(first ? Component.empty() : Component.literal(", "))
                        .append(Component.literal(st.getCount() + "× ")).append(st.getHoverName());
                first = false;
                if (!player.getInventory().add(st)) {
                    player.drop(st, false);
                }
            }
            player.displayClientMessage(Component.translatable("msg.armasreales.encontrado", names), true);
        }

        // Un armado cerca te ve rebuscando: te ataca, de día o de noche
        AABB box = new AABB(pos).inflate(14.0, 7.0, 14.0);
        for (CiudadanoEntity c : sl.getEntitiesOfClass(CiudadanoEntity.class, box, q -> q.isAlive() && q.carriesGun())) {
            c.provoke(player);
            c.provoked = 1500;
        }
    }
}
