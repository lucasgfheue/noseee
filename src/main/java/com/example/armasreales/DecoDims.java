package com.example.armasreales;

import java.util.Map;

/** Tamaño (ancho, alto, fondo) y máscara de celdas con geometría de los muebles multi-bloque. Generado. */
final class DecoDims {
    private DecoDims() {}

    static final Map<String, int[]> M = Map.ofEntries(
            Map.entry("cama", new int[] {1, 1, 2, 3}),
            Map.entry("litera", new int[] {1, 2, 2, 15}),
            Map.entry("nevera", new int[] {1, 2, 1, 3}),
            Map.entry("ducha", new int[] {1, 2, 1, 3}),
            Map.entry("cabina_telefonica", new int[] {1, 2, 1, 3}),
            Map.entry("parada_autobus", new int[] {3, 2, 1, 63}),
            Map.entry("mesa_billar", new int[] {2, 1, 3, 63}),
            Map.entry("columpio", new int[] {3, 3, 1, 511}),
            Map.entry("tobogan", new int[] {1, 2, 3, 61}),
            Map.entry("subibaja", new int[] {1, 1, 3, 7}),
            Map.entry("poste_electrico", new int[] {1, 8, 1, 255}),
            Map.entry("puesto_comida", new int[] {3, 2, 2, 4095}),
            Map.entry("mesa_terraza", new int[] {1, 2, 1, 3}),
            Map.entry("estatua", new int[] {1, 3, 1, 7}),
            Map.entry("tocador", new int[] {2, 2, 1, 15}),
            Map.entry("contenedor", new int[] {2, 1, 1, 3})
    );
}
