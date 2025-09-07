package pe.pucp.edu.morapack.planner;

import java.io.File;
import java.util.*;

import pe.pucp.edu.morapack.planner.Aeropuerto;
public class AeropuertosMap {
    private final Map<String, Aeropuerto> aeropuertos;
    public AeropuertosMap() {
        aeropuertos = new HashMap<>();
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
        while (sc.hasNextLine()) {
            Aeropuerto ae = new Aeropuerto();
            String key = ae.leer(sc); // key = origen
            if(key!=null) agregar(ae, key);
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

    public void imprimirAeropuertos() {
        for (Aeropuerto ae : aeropuertos.values()) {
            System.out.println(ae);
        }
    }



}

