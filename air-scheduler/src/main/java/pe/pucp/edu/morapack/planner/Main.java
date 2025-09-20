package pe.pucp.edu.morapack.planner;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;

public class Main {
    public static void main(String[] args) {
        //Aeropuertos
        AeropuertosMap aeropuertosMap = new AeropuertosMap();
        try (Scanner sc = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.Aeropuerto.husos.v1.20250818__estudiantes.txt")) {
            if (sc != null) {
                aeropuertosMap.leerDatos(sc);
            } else return;
        }
        aeropuertosMap.imprimirAeropuertos();

        System.out.println("holi");

        //Vuelos
        VuelosMap mapa = new VuelosMap(aeropuertosMap);
        try (Scanner sc = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.planes_vuelo.v4.20250818.txt")) {
            if (sc != null) {
                mapa.leerDatos(sc);
            } else return;
        }
        mapa.imprimirVuelos();


       /*Map<String, List<Vuelo>> vuelosPorOrigen = mapa.getVuelosPorOrigen();
       System.out.println("Vuelos desde SPIM:");
        List<Vuelo> skboVuelos = vuelosPorOrigen.get("SPIM");
        if (skboVuelos != null) {
            for (Vuelo v : skboVuelos) {
                System.out.println(v);
            }
        }
        */


        //Pedidos
        CargarPedidos pedidos = new CargarPedidos();
        try (Scanner sc = ArchivoUtils.getScannerFromResource("pedidos.txt")) {
            if (sc != null) {
                pedidos.leerDatos(sc);
            } else return;
        }
        //pedidos.mostrar();

        TEGraph G = new TEGraph(aeropuertosMap, mapa, 3);

        G.printSomeSamples(3, 2);

        //*****
        //**********
        //Construcción de rutas CANDIDATAS (no memético yet)
        //**********
        //*****

        SLAService sla = new SLAService();
        CapacityBook capBook = new CapacityBook(); // usa tu ctor real
        RoutePlanner planner = new RoutePlanner(G, aeropuertosMap, sla, capBook);

        //String ORIGEN = "LOWW";
        //var ORIGENES = java.util.List.of("SKBO","SEQM","SVMI","SBBR","SPIM","SLLP","SCEL","SABE","SGAS","SUAA","LATI","EDDI","LOWW","EBCI","UMMS","LBSF","LKPR","LDZA","EKCH","EHAM","VIDP","OSDI","OERK","OMDB","OAKB","OOMS","OYSN","OPKC","UBBB","OJAI"); // <- Lima, Berlin, Praga (por ahora)

        var ORIGENES = java.util.List.of("SPIM", "EBCI", "UBBB");


        java.util.List<Pedido> listaPedidos = new java.util.ArrayList<>(pedidos.getColaPedidos());
        int k = 3; // top-k candidatas por pedido

        //Mapa de pedidos (usarlo en el memético):
        java.util.Map<Integer, Pedido> pedidosMap = new java.util.HashMap<>();
        for (Pedido p : listaPedidos) pedidosMap.put(p.getIdPedido(), p);

        java.util.Map<Integer, java.util.List<CandidateRoute>> candPorPedido = new java.util.HashMap<>();
        for (Pedido p : listaPedidos) {
            var cand = planner.generarCandidatasMultiOrigen(p, ORIGENES, k);
            // nunca dejes null
            candPorPedido.put(p.getIdPedido(), (cand == null) ? java.util.List.of() : cand);
        }

        int maxPedidos = Math.min(10, listaPedidos.size());

        //Para depuración
        System.out.println("EVENTOS en LOWW = " + G.eventsByAirport.get("LOWW").size());
        System.out.println("Primer evento: " + G.eventsByAirport.get("LOWW").first());
        System.out.println("Último evento:  " + G.eventsByAirport.get("LOWW").last());
        //

        System.out.println("\n=== CANDIDATAS (primeros " + maxPedidos + " pedidos) ===");

        //Esto es solo para la impresión:
        for (int i = 0; i < maxPedidos; i++) {
            Pedido p = listaPedidos.get(i);
            var cand = candPorPedido.get(p.getIdPedido());

            System.out.println("\nPedido " + p.getIdPedido() + " → " + p.getDestino() +
                    " (ready: " + p.getFecha() + ")");

            if (cand == null || cand.isEmpty()) {
                System.out.println("  - Sin candidatas factibles");
                continue;
            }

            int idx = 0; // <-- contador correcto
            for (CandidateRoute c : cand) {
                idx++;
                System.out.printf("  %d) ETA=%s | hops=%d | maxAsignable=%d%n",
                        idx, c.arrUTC, c.hops, c.minResidual);

                // reconstruye sólo tramos de vuelo
                java.util.List<String> legs = new java.util.ArrayList<>();
                for (String arcId : c.arcIds) {
                    TEGraph.Arc a = G.arcsById.get(arcId);
                    if (a == null) continue;
                    if (a.getArcType() == ArcType.VUELO) {
                        String from = a.getFrom().getAeropuerto().getCodigo();
                        String to   = a.getTo().getAeropuerto().getCodigo();
                        if (legs.isEmpty()) legs.add(from);
                        legs.add(to);
                    }
                }
                System.out.println("     Ruta: " + (legs.isEmpty() ? "(solo esperas)" : String.join(" -> ", legs)));

                // Diagnóstico sólo para las 3 primeras candidatas de este pedido
                if (idx <= 3) {
                    System.out.printf("     [Diagnóstico candidata #%d]%n", idx);
                    planner.printDiagnosticoRuta(c, p);
                }
            }
        }
        //Fin de la impresión

        //
        // Ejecutar el algoritmo memético para asignar rutas a los pedidos
        //
        System.out.println("\n=== EJECUTANDO ALGORITMO MEMÉTICO ===");
        
        // Crear e inicializar el algoritmo memético
        AlgoritmoMemetico memetico = new AlgoritmoMemetico(
            G,          // Grafo temporal
            capBook,    // Libro de capacidades global
            pedidosMap, // Mapa de pedidos
            candPorPedido // Rutas candidatas por pedido
        );
        
        // Ejecutar el algoritmo
        System.out.println("Iniciando búsqueda de solución óptima...");
        memetico.ejecutar();

        System.out.println("\n=== OCUPACIÓN DE ALMACENES ===");

        //Para imprimir bien las horas:
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        for (String origen : ORIGENES){
            System.out.println("\nOrigen: " + origen + "\n");

            TreeSet<LocalDateTime> eventos = G.eventsByAirport.get(origen);
            if (eventos == null || eventos.size() < 2) continue;

            int capacidad = -1;
            LocalDateTime prev = null;

            for (LocalDateTime t : eventos){
                if (prev == null){
                    prev = t;
                    continue;
                }
                String fromId = origen + "@" + prev;
                String toId   = origen + "@" + t;
                String arcId = fromId + "→" + toId;

                TEGraph.Arc arc = G.arcsById.get(arcId);

                if (capacidad == -1){
                    capacidad = arc.getCapacity();
                }

                LocalDateTime dep = arc.getFrom().getTimestampUTC();
                LocalDateTime arr = arc.getTo().getTimestampUTC();
                int ocupacion = capBook.used(arcId);
                String saturacion = obtenerSaturacion(capacidad, ocupacion);

                System.out.printf("(%s - %s)  Ocupación: %d/%d (Estado: %s) %n", dep.format(fmt), arr.format(fmt), ocupacion, capacidad, saturacion);

                prev = t;

            }

            capacidad = -1;
            System.out.println("\n" + "-".repeat(50));
        }


        System.out.println("\n=== FIN DEL PROGRAMA ===");
    }

     public static String obtenerSaturacion (int capacidad, int ocupacion){

        double saturacion = (double) ocupacion / capacidad;
        saturacion = Math.round(saturacion * 100.0);
        String mensaje;

        if (saturacion < 33.33){
            mensaje = "Disponible ✅" + saturacion + "%";
        }
        else if (saturacion < 66.66){
            mensaje = "Limitado ⚠\uFE0F" + saturacion + "%";
        }
        else {
            mensaje = "Saturado ❌" + saturacion + "%";
        }

        return mensaje;
    }

}


