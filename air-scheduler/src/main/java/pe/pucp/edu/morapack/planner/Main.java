package pe.pucp.edu.morapack.planner;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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
        var ORIGENES = java.util.List.of("LOWW", "EDDI", "LKPR"); // <- Lima, Berlin, Praga (por ahora)


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
        //Continuar con memético...
        //Te dejo unas consideraciones:
        /*
        Línea 85: cand es la lista de candidatos por pedido. Es decir, 'cand' representa todas las rutas candidatas para
        un ÚNICO pedido. Lo dejo así para que lo manejes como creas conveniente para el memético. Estas son las recomendaciones
        de GPT:

        Objetivo: usar las rutas CANDIDATAS generadas por RoutePlanner y armar un Plan global
// con un algoritmo memético. NO volver a buscar en el grafo: el memético trabaja SOLO
// con este pool de candidatas.
//
// En el Main dispones de:
//   - TEGraph G           : grafo temporal (nodos=eventos, arcos=vuelos/esperas)
//   - CapacityBook capBook: capacidades GLOBALES (solo reservar al final)
//   - SLAService sla      : 48h intra / 72h inter + pickup
//   - Map<Integer,Pedido> pedidosMap (lo armamos arriba)
//   - Map<Integer,List<CandidateRoute>> candPorPedido (lo armamos arriba)
//
// Reglas clave para el GA/Memético:
//   1) Individuo = elección de a lo sumo 1 candidata por pedido (o ninguna).
//   2) Fitness (maximizar):
//        - +#pedidos asignados (o suma de cantidades)
//        - -penalizaciones FUERTES si rompe capacidad (debe ser casi prohibitivo)
//        - desempatar con ETA más temprana y menos hops.
//      Sugerencia: usa un libro de capacidad LOCAL (delta) por individuo:
//        Map<String,Integer> delta;  // arcId -> carga adicional en el plan candidato
//        boolean canAssign(route,q): para cada arcId de route,
//             res = capBook.residual(arcId) - delta.getOrDefault(arcId,0)
//             si res < q => no asignar esa candidata
//        si asignas, delta.merge(arcId, q, Integer::sum)
//   3) Cruce y mutación SIEMPRE verifican `canAssign(...)` con el delta local.
//   4) Al terminar, COMMIT GLOBAL (reservar en capBook) SOLO para el mejor plan:
//        for (arcId : bestRoute.arcIds) capBook.reserve(G.arcsById.get(arcId), pedido.getCantidad());
//

        Long story short:

        - Usar `pedidosMap` y `candPorPedido` como inputs del GA/memético.
//  - NO reconstruir rutas desde el grafo; trabajar SOLO con este pool.
//  - Respetar capacidad con un "delta" local por individuo (no tocar capBook global).
//  - Al finalizar, reservar globalmente en capBook SOLO el mejor plan.


         */



    }
}
