package com.example.armasreales;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mobiliario decorativo (sillas, mesas, cocinas, televisores...). Generado: ver DecoBlock para el comportamiento. */
public final class ModDeco {
    private ModDeco() {}

    public record Def(String id, int lum, boolean toggle, double[][] boxes) {}

    public static final List<Def> DEFS = List.of(
            new Def("silla", 0, false, new double[][] {{3.0, 7.0, 3.0, 13.0, 9.0, 13.0}, {3.0, 0.0, 3.0, 5.0, 7.0, 5.0}, {11.0, 0.0, 3.0, 13.0, 7.0, 5.0}, {3.0, 0.0, 11.0, 5.0, 7.0, 13.0}, {11.0, 0.0, 11.0, 13.0, 7.0, 13.0}, {3.0, 9.0, 11.0, 5.0, 16.0, 13.0}, {11.0, 9.0, 11.0, 13.0, 16.0, 13.0}, {5.0, 12.0, 11.0, 11.0, 15.0, 13.0}}),
            new Def("mesa", 0, false, new double[][] {{0.0, 13.0, 0.0, 16.0, 16.0, 16.0}, {1.0, 0.0, 1.0, 3.0, 13.0, 3.0}, {13.0, 0.0, 1.0, 15.0, 13.0, 3.0}, {1.0, 0.0, 13.0, 3.0, 13.0, 15.0}, {13.0, 0.0, 13.0, 15.0, 13.0, 15.0}}),
            new Def("escritorio", 0, false, new double[][] {{0.0, 13.0, 0.0, 16.0, 16.0, 16.0}, {0.0, 0.0, 2.0, 3.0, 13.0, 16.0}, {10.0, 0.0, 2.0, 16.0, 13.0, 16.0}, {3.0, 0.0, 14.0, 10.0, 13.0, 16.0}, {11.0, 8.0, 1.0, 15.0, 9.0, 2.0}, {11.0, 3.0, 1.0, 15.0, 4.0, 2.0}}),
            new Def("silla_oficina", 0, false, new double[][] {{4.0, 0.0, 4.0, 12.0, 1.0, 12.0}, {7.0, 1.0, 7.0, 9.0, 6.0, 9.0}, {3.0, 6.0, 3.0, 13.0, 8.0, 13.0}, {3.0, 8.0, 11.0, 13.0, 16.0, 13.0}, {2.0, 8.0, 5.0, 3.0, 10.0, 11.0}, {13.0, 8.0, 5.0, 14.0, 10.0, 11.0}}),
            new Def("sofa", 0, false, new double[][] {{0.0, 2.0, 0.0, 16.0, 8.0, 16.0}, {0.0, 8.0, 12.0, 16.0, 16.0, 16.0}, {0.0, 8.0, 0.0, 2.0, 12.0, 12.0}, {14.0, 8.0, 0.0, 16.0, 12.0, 12.0}, {1.0, 0.0, 1.0, 3.0, 2.0, 3.0}, {13.0, 0.0, 1.0, 15.0, 2.0, 3.0}, {1.0, 0.0, 13.0, 3.0, 2.0, 15.0}, {13.0, 0.0, 13.0, 15.0, 2.0, 15.0}, {3.0, 8.0, 3.0, 13.0, 10.0, 12.0}}),
            new Def("cama", 0, false, new double[][] {{0.0, 2.0, 0.0, 16.0, 6.0, 16.0}, {0.0, 0.0, 0.0, 2.0, 2.0, 2.0}, {14.0, 0.0, 0.0, 16.0, 2.0, 2.0}, {0.0, 0.0, 14.0, 2.0, 2.0, 16.0}, {14.0, 0.0, 14.0, 16.0, 2.0, 16.0}, {1.0, 6.0, 1.0, 15.0, 9.0, 15.0}, {0.0, 6.0, 14.0, 16.0, 16.0, 16.0}, {0.0, 2.0, 0.0, 16.0, 8.0, 1.0}, {3.0, 9.0, 9.0, 13.0, 11.0, 14.0}}),
            new Def("tv", 8, true, new double[][] {{5.0, 0.0, 6.0, 11.0, 1.0, 10.0}, {7.0, 1.0, 7.0, 9.0, 3.0, 9.0}, {0.0, 3.0, 7.0, 16.0, 15.0, 9.0}}),
            new Def("computadora", 6, true, new double[][] {{5.0, 0.0, 8.0, 11.0, 1.0, 12.0}, {7.0, 1.0, 9.0, 9.0, 3.0, 11.0}, {2.0, 3.0, 8.0, 14.0, 13.0, 11.0}, {3.0, 0.0, 2.0, 13.0, 1.0, 6.0}, {13.0, 0.0, 3.0, 15.0, 1.0, 5.0}}),
            new Def("cocina", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 14.0, 16.0}, {0.0, 14.0, 0.0, 16.0, 16.0, 16.0}, {2.0, 16.0, 12.0, 14.0, 17.0, 14.0}}),
            new Def("nevera", 0, false, new double[][] {{1.0, 0.0, 1.0, 15.0, 16.0, 15.0}, {12.0, 4.0, 0.0, 13.0, 12.0, 1.0}}),
            new Def("fregadero", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 14.0, 16.0}, {0.0, 14.0, 0.0, 16.0, 16.0, 2.0}, {0.0, 14.0, 14.0, 16.0, 16.0, 16.0}, {0.0, 14.0, 2.0, 2.0, 16.0, 14.0}, {14.0, 14.0, 2.0, 16.0, 16.0, 14.0}, {2.0, 14.0, 2.0, 14.0, 15.0, 14.0}, {7.0, 16.0, 13.0, 9.0, 19.0, 15.0}, {7.0, 18.0, 11.0, 9.0, 19.0, 14.0}}),
            new Def("mueble_cocina", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 14.0, 16.0}, {0.0, 14.0, 0.0, 16.0, 16.0, 16.0}}),
            new Def("estanteria", 0, false, new double[][] {{0.0, 0.0, 14.0, 16.0, 16.0, 16.0}, {0.0, 0.0, 8.0, 2.0, 16.0, 14.0}, {14.0, 0.0, 8.0, 16.0, 16.0, 14.0}, {2.0, 0.0, 8.0, 14.0, 1.0, 14.0}, {2.0, 5.0, 8.0, 14.0, 6.0, 14.0}, {2.0, 10.0, 8.0, 14.0, 11.0, 14.0}, {2.0, 15.0, 8.0, 14.0, 16.0, 14.0}, {3.0, 1.0, 9.0, 13.0, 5.0, 13.0}, {3.0, 6.0, 9.0, 13.0, 10.0, 13.0}, {3.0, 11.0, 9.0, 13.0, 15.0, 13.0}}),
            new Def("lampara", 14, true, new double[][] {{5.0, 0.0, 5.0, 11.0, 1.0, 11.0}, {7.0, 1.0, 7.0, 9.0, 11.0, 9.0}, {4.0, 11.0, 4.0, 12.0, 16.0, 12.0}}),
            new Def("inodoro", 0, false, new double[][] {{5.0, 0.0, 7.0, 11.0, 5.0, 13.0}, {3.0, 5.0, 2.0, 13.0, 8.0, 12.0}, {4.0, 8.0, 3.0, 12.0, 9.0, 11.0}, {4.0, 8.0, 10.0, 12.0, 15.0, 14.0}, {4.0, 15.0, 10.0, 12.0, 16.0, 14.0}, {7.0, 16.0, 11.0, 9.0, 17.0, 13.0}}),
            new Def("lavabo", 0, false, new double[][] {{6.0, 0.0, 8.0, 10.0, 9.0, 12.0}, {2.0, 9.0, 4.0, 14.0, 13.0, 14.0}, {3.0, 13.0, 5.0, 13.0, 14.0, 13.0}, {7.0, 13.0, 12.0, 9.0, 16.0, 14.0}, {7.0, 15.0, 10.0, 9.0, 16.0, 13.0}}),
            new Def("banera", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 8.0, 16.0}, {0.0, 8.0, 0.0, 16.0, 10.0, 2.0}, {0.0, 8.0, 14.0, 16.0, 10.0, 16.0}, {0.0, 8.0, 2.0, 2.0, 10.0, 14.0}, {14.0, 8.0, 2.0, 16.0, 10.0, 14.0}, {2.0, 8.0, 2.0, 14.0, 9.0, 14.0}, {7.0, 10.0, 14.0, 9.0, 13.0, 16.0}}),
            new Def("armario", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 16.0, 16.0}, {6.0, 6.0, -1.0, 7.0, 10.0, 0.0}, {9.0, 6.0, -1.0, 10.0, 10.0, 0.0}}),
            new Def("planta", 0, false, new double[][] {{4.0, 0.0, 4.0, 12.0, 6.0, 12.0}, {3.0, 5.0, 3.0, 13.0, 6.0, 13.0}, {5.0, 6.0, 5.0, 11.0, 7.0, 11.0}, {4.0, 7.0, 4.0, 12.0, 13.0, 12.0}, {6.0, 13.0, 6.0, 10.0, 16.0, 10.0}, {2.0, 8.0, 6.0, 4.0, 11.0, 10.0}, {12.0, 8.0, 6.0, 14.0, 11.0, 10.0}}),
            new Def("cuadro", 0, false, new double[][] {{1.0, 2.0, 14.0, 15.0, 14.0, 16.0}, {2.0, 3.0, 13.5, 14.0, 13.0, 14.0}}),
            new Def("microondas", 0, false, new double[][] {{2.0, 0.0, 3.0, 14.0, 8.0, 13.0}}),
            new Def("lavadora", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 16.0, 16.0}}),
            new Def("alfombra", 0, false, new double[][] {{0.0, 0.0, 0.0, 16.0, 1.0, 16.0}}),
            new Def("archivador", 0, false, new double[][] {{2.0, 0.0, 2.0, 14.0, 16.0, 14.0}}),
            new Def("mesita_noche", 0, false, new double[][] {{2.0, 0.0, 2.0, 14.0, 11.0, 14.0}, {1.0, 11.0, 1.0, 15.0, 12.0, 15.0}}),
            new Def("taburete", 0, false, new double[][] {{3.0, 10.0, 3.0, 13.0, 12.0, 13.0}, {3.0, 0.0, 3.0, 5.0, 10.0, 5.0}, {11.0, 0.0, 3.0, 13.0, 10.0, 5.0}, {3.0, 0.0, 11.0, 5.0, 10.0, 13.0}, {11.0, 0.0, 11.0, 13.0, 10.0, 13.0}}),
            new Def("papelera", 0, false, new double[][] {{4.0, 0.0, 4.0, 12.0, 10.0, 12.0}, {3.0, 10.0, 3.0, 13.0, 11.0, 13.0}}),
            new Def("reloj", 0, false, new double[][] {{3.0, 3.0, 14.0, 13.0, 13.0, 16.0}, {4.0, 4.0, 13.5, 12.0, 12.0, 14.0}})
    );

    public static final Map<String, DeferredBlock<Block>> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<BlockItem>> ITEMS = new LinkedHashMap<>();

    static {
        for (Def d : DEFS) {
            DeferredBlock<Block> b = ModBlocks.BLOCKS.registerBlock(d.id(), p -> new DecoBlock(p, d),
                    BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOD).noOcclusion()
                            .lightLevel(s -> s.getValue(DecoBlock.LIT) ? d.lum() : 0));
            BLOCKS.put(d.id(), b);
            ITEMS.put(d.id(), ModItems.ITEMS.registerSimpleBlockItem(d.id(), b));
        }
    }

    /** Fuerza la carga de la clase (registra los bloques antes de los eventos de registro). */
    public static void init() {
    }

    public static BlockState state(String id, Direction facing, boolean lit) {
        return BLOCKS.get(id).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing).setValue(DecoBlock.LIT, lit);
    }
}
