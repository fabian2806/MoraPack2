package pe.pucp.edu.morapack.planner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class RoutePlanner {
    private final TEGraph g;
    private final AeropuertosMap aps;
    private final SLAService sla;
    private final CapacityBook capBook; //EL capBook ES SOLO LECTURA, ACA NO HACEMOS RESERVA GLOBAL

    private static final int MAX_ESCALAS = 3;
    private static final int MAX_LABELS_POR_NODO = 8;
    private static final Duration MIN_LAYOVER = Duration.ZERO;

    public RoutePlanner(TEGraph g, AeropuertosMap aps, SLAService sla, CapacityBook capBook) {
        this.g = g;
        this.aps = aps;
        this.sla = sla;
        this.capBook = capBook;
    }

    //Recordar que la llega es si o si antes del deadline:
    //restarrrrrr:
    private LocalDateTime latestArrival(LocalDateTime deadline){
        return deadline.minus(sla.getPickup());
    }

    //Multi-origen:
    public List<CandidateRoute> generarCandidatasMultiOrigen(
            Pedido p, Collection<String> origenes, int kTotal) {

        if (p == null || origenes == null || origenes.isEmpty() || kTotal <= 0)
            return java.util.Collections.emptyList();

        java.util.Map<String, CandidateRoute> uniq = new java.util.HashMap<>(); // firma -> cand

        for (String origen : origenes) {
            // Llamamos al mono-origen
            java.util.List<CandidateRoute> parciales = generarCandidatas(p, origen, kTotal);

            for (CandidateRoute c : parciales) {
                // firma para deduplicar: secuencia exacta de arcos
                String sig = String.join(">", c.arcIds);
                // conserva la “mejor” si hay choque (ETA↑, minResidual↓, hops↑)
                CandidateRoute prev = uniq.get(sig);
                if (prev == null
                        || c.arrUTC.isBefore(prev.arrUTC)
                        || (c.arrUTC.equals(prev.arrUTC) && c.minResidual > prev.minResidual)
                        || (c.arrUTC.equals(prev.arrUTC) && c.minResidual == prev.minResidual && c.hops < prev.hops)) {
                    uniq.put(sig, c);
                }
            }
        }

        java.util.List<CandidateRoute> all = new java.util.ArrayList<>(uniq.values());

        // MISMO criterio de orden que ya usas
        all.sort(java.util.Comparator
                .comparing((CandidateRoute c) -> c.arrUTC)
                .thenComparing((CandidateRoute c) -> -c.minResidual)
                .thenComparingInt(c -> c.hops));

        if (all.size() > kTotal) return all.subList(0, kTotal);
        return all;
    }


    //Mono-origen:
    public List<CandidateRoute> generarCandidatas(Pedido p, String origen, int k) {
        if (p == null || origen == null || origen.isEmpty() || k <= 0) return Collections.emptyList();

        LocalDateTime earliest = p.getFecha(); //está en el mismo día que el grafo
        LocalDateTime deadline = sla.deadline(earliest, sla.sla(aps.obtener(origen), aps.obtener(p.getDestino())));
        LocalDateTime latestArr = latestArrival(deadline);


        TreeSet<LocalDateTime> eventos = g.eventsByAirport.get(origen);
        if (eventos == null) return Collections.emptyList();

        LocalDateTime startT = eventos.ceiling(earliest);
        if (startT == null) return Collections.emptyList();

        //Recordar que esta es la estructura del idNodo
        final String startNodeId = origen + "@" + startT;

        //Estamos haciendo label-setting con poda por dominancia

        class Label {
            String nodeId;
            LocalDateTime t;
            int hops;
            int minResidual;
            List<String> path;

            Label(String nodeId, LocalDateTime t) {
                this.nodeId = nodeId;
                this.t = t;
                this.hops = 0;
                this.minResidual = Integer.MAX_VALUE;
                this.path = new ArrayList<>();
            }

            Label extend(TEGraph.Arc a){
                Label nx = new Label(a.getTo().getNodeId(), a.getTo().getTimestampUTC());
                nx.hops = this.hops + ( (a.getArcType() == ArcType.VUELO ? 1 : 0));

                int residual = capBook.residual(a);
                nx.minResidual = Math.min(this.minResidual, residual);

                nx.path = new ArrayList<>(this.path);
                nx.path.add(a.getArcId());

                return nx;
            }

        }

        //System.out.println("Pedido " + p.getIdPedido() + " earliest=" + earliest);
        //System.out.println("deadline=" + deadline + " latestArr=" + latestArr);
        //System.out.println("startT=" + startT + " startNodeId=" + startNodeId);

        //Esto expande por llegada más temprana:
        PriorityQueue<Label> pq = new PriorityQueue<>(Comparator.comparing(l -> l.t));
        Map<String,List<Label>> best = new HashMap<>();
        List<CandidateRoute> out = new ArrayList<>();

        //Inicial:
        pq.add(new Label(startNodeId, startT));

        while (!pq.isEmpty() && out.size() < k) {
            Label cur = pq.poll();

            //Si llegué al destino y a tiempo:
            if (cur.nodeId.startsWith(p.getDestino()+"@") && !cur.t.isAfter(latestArr)){

                LocalDateTime depUTC = startT;
                if (!cur.path.isEmpty()){
                    TEGraph.Arc first = g.arcsById.get(cur.path.get(0));
                    depUTC = first.getFrom().getTimestampUTC();
                }

                int maxAsignable = (cur.minResidual == Integer.MAX_VALUE) ? 0 : cur.minResidual;

                out.add( new CandidateRoute(p.getIdPedido(), cur.path, depUTC, cur.t, 0, cur.hops,
                        maxAsignable));
                continue;
            }

            //Poda por tiempo
            if (cur.t.isAfter(latestArr)) continue;

            //Expandimos los arcos salientes de dicho nodo
            List<TEGraph.Arc> salientes = g.out.get(cur.nodeId);
            if (salientes == null) continue;

            for (TEGraph.Arc a : salientes){

                LocalDateTime dep = a.getFrom().getTimestampUTC();
                LocalDateTime arr = a.getTo().getTimestampUTC();

                if (dep.isBefore(cur.t)) continue;        // no puedo salir antes de llegar
                if (arr.isAfter(latestArr)) continue;     // llegaría tarde

                int nextHops = cur.hops + (a.getArcType() == ArcType.VUELO ? 1 : 0);
                if (nextHops > MAX_ESCALAS) continue;

                if (capBook.residual(a) <= 0) continue;

                // Layover mínimo *******************
                if (a.getArcType() == ArcType.VUELO && MIN_LAYOVER.compareTo(Duration.ZERO) > 0){
                    // si quieres exigir conexión mínima, verifica dep - cur.t >= MIN_LAYOVER
                    if (Duration.between(cur.t, dep).compareTo(MIN_LAYOVER) < 0) continue;
                }

                Label nx = cur.extend(a);

                List<Label> pool = best.computeIfAbsent(a.getTo().getNodeId(), __ -> new ArrayList<>());
                boolean dominado = false;

                for (Label b : pool){
                    boolean bDominaNx =
                            !b.t.isAfter(nx.t) &&        // b llega antes o igual
                            b.hops <= nx.hops &&         // b usa <= escalas
                            b.minResidual >= nx.minResidual && // b tiene >= capacidad mínima
                            ( b.t.isBefore(nx.t) || b.hops < nx.hops || b.minResidual > nx.minResidual ); // al menos una estricta

                    if (bDominaNx) { dominado = true; break; }
                }
                if (dominado) continue;

                pool.add(nx);
                pool.sort(Comparator
                        .comparing((Label l) -> l.t)
                        .thenComparingInt(l -> l.hops)
                        .thenComparing((Label l) -> -l.minResidual)
                );
                if (pool.size() > MAX_LABELS_POR_NODO) {
                    pool.remove(pool.size() - 1);
                }

                pq.add(nx);


            }


        }

        // Orden final: ETA ↑, minResidual ↓(negativo para priorizar alto), hops ↑
        out.sort(Comparator
                .comparing((CandidateRoute c) -> c.arrUTC)
                .thenComparing((CandidateRoute c) -> -c.minResidual)
                .thenComparingInt(c -> c.hops));

        return out;
    }

    //Helper de impresión:
    public void printDiagnosticoRuta(CandidateRoute c, Pedido p) {
        System.out.printf("     pedido.q=%d  |  minResidual=%d  => %s%n",
                p.getCantidad(), c.minResidual, (c.minResidual >= p.getCantidad() ? "OK" : "NO FIT"));

        System.out.println("     Detalle por arco:");
        for (String arcId : c.arcIds) {
            TEGraph.Arc a = g.arcsById.get(arcId);
            if (a == null) continue;
            int cap = a.getCapacity();
            int used = capBook.used(arcId);
            int res = Math.max(0, cap - used);
            int resPost = res - p.getCantidad(); // hipotético PORQUE ACA NO ASIGNAMOS REAL, SOLO ARMAMOS RUTAS CANDIDATAS
            System.out.printf("       %s  cap=%d  used=%d  res=%d  -> res(si asigno q=%d)=%d%n",
                    arcId, cap, used, res, p.getCantidad(), resPost);
        }
    }


}