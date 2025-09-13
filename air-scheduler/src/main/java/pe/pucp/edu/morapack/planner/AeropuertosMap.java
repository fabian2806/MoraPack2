package pe.pucp.edu.morapack.planner;

import java.io.File;
import java.util.*;

import pe.pucp.edu.morapack.planner.Aeropuerto;
public class AeropuertosMap {
    private final Map<String, Aeropuerto> aeropuertos;
    public AeropuertosMap() {
        aeropuertos = new HashMap<>();
    }

    public Map<String, Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void agregar(Aeropuerto ae, String key) {
        if (key ==null || ae == null) return; // Evitar claves o aeropuertos nulos
        aeropuertos.put(key, ae);
    }

    public Aeropuerto obtener(String key) {
        return aeropuertos.get(key);
    }

    public boolean existe(String key) {
        return aeropuertos.containsKey(key);
    }

    public void leerDatos(Scanner sc) {
        String continenteActual = null;

        while (sc.hasNext()) {
            // ¿Lo siguiente empieza con un entero (el índice de aeropuerto)?
            if (sc.hasNextInt()) {
                Aeropuerto ae = new Aeropuerto();
                String key = ae.leer(sc);     // <-- tu método0 existente, sin cambios
                if (key != null) {
                    ae.setContinente(continenteActual);   // asigna continente del bloque
                    agregar(ae, key);                     // guarda en el mapa
                }
            } else {
                // No es una línea de aeropuerto → lee la línea completa (encabezado)
                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;

                // Limpiamos por lo de "América del Sur. GMT CAPACIDAD" en el archivo
                continenteActual = limpiarEncabezadoContinente(linea);

            }
        }
    }

    private String limpiarEncabezadoContinente(String linea) {
        String s = linea.replace("*", "").trim();
        // Si viene con columnas ("GMT", "CAPACIDAD"), corta antes de "GMT"
        int idx = s.indexOf("GMT");
        if (idx > 0) s = s.substring(0, idx).trim();
        // Quita puntos finales y espacios dobles
        s = s.replaceAll("\\.+$", "").replaceAll("\\s{2,}", " ").trim();
        return s;
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

    public void imprimirAeropuertos() {
        for (Aeropuerto ae : aeropuertos.values()) {
            System.out.println(ae);
        }
    }

    /**
     * Verifica si dos aeropuertos están en el mismo continente
     * @param codigo1 Código IATA del primer aeropuerto
     * @param codigo2 Código IATA del segundo aeropuerto
     * @return true si están en el mismo continente, false en caso contrario
     */
    public boolean esMismoContinente(String codigo1, String codigo2) {
        Aeropuerto a1 = aeropuertos.get(codigo1);
        Aeropuerto a2 = aeropuertos.get(codigo2);
        
        if (a1 == null || a2 == null) {
            return false;
        }
        
        return a1.getContinente() != null && 
               a1.getContinente().equals(a2.getContinente());
    }
}
