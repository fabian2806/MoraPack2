package pe.pucp.edu.morapack.planner;

import pe.pucp.edu.morapack.planner.GA.AlgoritmoGenetico;
import pe.pucp.edu.morapack.planner.Vuelo;
import pe.pucp.edu.morapack.planner.Pedido;
import pe.pucp.edu.morapack.planner.GA.AlgoritmoGenetico;
import pe.pucp.edu.morapack.planner.ArchivoUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        AeropuertosMap aeropuertosMap = new AeropuertosMap();
        try (Scanner sc = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.Aeropuerto.husos.v1.20250818__estudiantes.txt")) {
            if (sc != null) {
                aeropuertosMap.leerDatos(sc);
            } else return;
        }
        VuelosMap mapa = new VuelosMap(aeropuertosMap);
        try (Scanner sc = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.planes_vuelo.v4.20250818.txt")) {
            if (sc != null) {
                mapa.leerDatos(sc);
            } else return;
        }
       Map<String, List<Vuelo>> vuelosPorOrigen = mapa.getVuelosPorOrigen();
       System.out.println("Vuelos desde SPIM:");
        List<Vuelo> skboVuelos = vuelosPorOrigen.get("SPIM");
        if (skboVuelos != null) {
            for (Vuelo v : skboVuelos) {
                System.out.println(v);
            }
        }
/*
        // Definir pedidos
        List<Pedido> pedidos = Arrays.asList(
                new Pedido("P1", "SPIM", "SKBO", 100),
                new Pedido("P2", "SPIM", "SKBO", 50),
                new Pedido("P3", "SPIM", "SEQM", 30),
                new Pedido("P4", "SPIM", "SVMI", 20),
                new Pedido("P5", "SPIM", "SCEL", 60)
        );
/*
        // Resolver
     /*   AlgoritmoGenetico ga = new AlgoritmoGenetico(vuelos);
        ga.resolver(pedidos);

        */
    }
}
