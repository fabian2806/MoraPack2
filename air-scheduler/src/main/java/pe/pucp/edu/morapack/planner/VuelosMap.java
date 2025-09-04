package pe.pucp.edu.morapack.planner;

import java.io.File;
import java.util.*;

public class VuelosMap {

    private final Map<String, List<Vuelo>> vuelosPorOrigen;

    public VuelosMap() {
        vuelosPorOrigen = new HashMap<>();
    }

    // Agrega un vuelo al HashMap
    public void agregar(Vuelo vue, String key) {
        if (key == null) return; // Evitar claves nulas
        vuelosPorOrigen.computeIfAbsent(key, k -> new ArrayList<>()).add(vue);
    }

    // Leer vuelos desde un Scanner
    public void leerDatos(Scanner sc) {
        int i = 1;
        while (sc.hasNextLine()) {
            Vuelo vue = new Vuelo();
            String key = vue.leer(sc, i); // key = origen
            agregar(vue, key);
            i++;
        }
    }

    // Leer datos desde System.in
    public void leerDatos() {
        leerDatos(new Scanner(System.in));
    }

    // Leer datos desde archivo
    public void leerDatos(String nomArch) throws Exception {
        File file = new File(nomArch);
        try (Scanner sc = new Scanner(file)) {
            leerDatos(sc);
        }
    }

    // Obtener el Map completo
    public Map<String, List<Vuelo>> getVuelosPorOrigen() {
        return vuelosPorOrigen;
    }

    // Imprimir todos los vuelos (opcional)
    public void imprimirVuelos() {
        for (String origen : vuelosPorOrigen.keySet()) {
            System.out.println("Vuelos desde " + origen + ":");
            for (Vuelo v : vuelosPorOrigen.get(origen)) {
                System.out.println(v);
            }
            System.out.println();
        }
    }
}
