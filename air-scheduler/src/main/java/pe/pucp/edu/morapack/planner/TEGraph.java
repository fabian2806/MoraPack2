package pe.pucp.edu.morapack.planner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static pe.pucp.edu.morapack.planner.SimulationConfig.SIM_DATE;

public class TEGraph {
    public class Node{
        private final String nodeId;
        private final Aeropuerto aeropuerto;
        private final LocalDateTime timestampUTC;
        private final NodeType nodeType;

        public Node(String nodeId, Aeropuerto aeropuerto, LocalDateTime timestampUTC, NodeType nodeType) {
            this.nodeId = nodeId;
            this.aeropuerto = aeropuerto;
            this.timestampUTC = timestampUTC;
            this.nodeType = nodeType;
        }

        public String getNodeId() {
            return nodeId;
        }

        public Aeropuerto getAeropuerto(){
            return aeropuerto;
        }

        public LocalDateTime getTimestampUTC(){
            return timestampUTC;
        }

        //public String getId() { return aeropuerto.getCodigo() + "@" + timestampUTC; }
    }

    public class Arc{
        private final String arcId;
        private final Node from, to;
        private final int capacity;
        private final Vuelo vuelo;
        private final ArcType arcType;

        public Arc(String arcId, Node from, Node to, int capacity, Vuelo vuelo, ArcType arcType) {
            this.arcId = arcId;
            this.from = from;
            this.to = to;
            this.capacity = capacity;
            this.vuelo = vuelo;
            this.arcType = arcType;
        }

        public String getArcId() {
            return arcId;
        }

        public Node getFrom() { return from; }
        public Node getTo() { return to; }
        public int getCapacity() { return capacity; }
        public Vuelo getVuelo() { return vuelo; }
        public ArcType getArcType() { return arcType; }
    }

    private int horizonDays;


    public Map<String, Node> nodesById = new HashMap<>();
    public Map<String, Arc> arcsById = new HashMap<>();

    public Map<String, List<Arc>> out = new HashMap<>();
    public Map<String, TreeSet<LocalDateTime>> eventsByAirport = new HashMap<>();

    public TEGraph(AeropuertosMap aeropuertos, VuelosMap vuelos, int horizonDays){
        this.horizonDays = Math.max(1, horizonDays);
        build(aeropuertos, vuelos);
    }

    public TEGraph(AeropuertosMap aeropuertos, VuelosMap vuelos){
        build(aeropuertos, vuelos);
    }

    private void build (AeropuertosMap aeropuertos, VuelosMap vuelos){
        Map<String, Aeropuerto> aps = aeropuertos.getAeropuertos();
        Map<String, List<Vuelo>> vuelosPorOrigen = vuelos.getVuelosPorOrigen();

        //Recuperamos la lista de vuelos que parten de un origen
        //Armamos Arcos de vuelo y Eventos por aeropuerto
        for (Map.Entry<String, List<Vuelo>> vs: vuelosPorOrigen.entrySet()){
            Aeropuerto aeropuertoActual = aps.get(vs.getKey());
            for (Vuelo v: vs.getValue()){
                Aeropuerto aeropuertoDestino = aeropuertos.obtener(v.getDestino());

                for (int d = 0; d < horizonDays; d++) {
                    LocalDate base = SIM_DATE.plusDays(d);

                    LocalDateTime depUTC = LocalDateTime.of(base, v.getHoraGMTOrigen());
                    LocalDateTime arrUTC = LocalDateTime.of(base, v.getHoraGMTDestino());
                    if (arrUTC.isBefore(depUTC)) arrUTC = arrUTC.plusDays(1);

                    Node n1 = getOrCreateNode(aeropuertoActual, depUTC, NodeType.SALIDA);
                    Node n2 = getOrCreateNode(aeropuertoDestino, arrUTC, NodeType.LLEGADA);

                    addArc(n1, n2, v, ArcType.VUELO);
                    addEventsByAirport(v.getOrigen(), v.getDestino(), depUTC, arrUTC);
                }
            }

        }

        //Armamos arcos de espera
        for (Map.Entry<String, TreeSet<LocalDateTime>> e: eventsByAirport.entrySet()){
            Aeropuerto aeropuertoActual = aps.get(e.getKey());
            if (aeropuertoActual == null) continue;
            //La capacidad se gestiona dentro de addArc
            //int capacidad = Math.max(1, aeropuertoActual.getCapacidad());

            TreeSet<LocalDateTime> eventos = e.getValue();

            //No pasa nada en el aeropuerto (0) o solo hay 1 evento por lo que no hay espera (1):
            if (eventos.size() < 2) continue;

            LocalDateTime prev = null;
            for (LocalDateTime t : eventos){
                if (prev != null){
                    Node a = getOrCreateNode(aeropuertoActual, prev, NodeType.ESPERA);
                    Node b = getOrCreateNode(aeropuertoActual, t, NodeType.ESPERA);
                    addArc(a, b, null, ArcType.ESPERA);
                }
                prev = t;
            }
        }

    }

    private Node getOrCreateNode(Aeropuerto aeropuerto, LocalDateTime tUTC, NodeType nodeType){
        String nodeId = aeropuerto.getCodigo() + "@" + tUTC; //ej: LIM@2025-09-07T10:00
        Node existing = nodesById.get(nodeId);
        if (existing != null) return existing;

        Node created = new Node (nodeId, aeropuerto, tUTC, nodeType);
        nodesById.put(nodeId, created);
        return created;
    }

    private void addArc(Node n1, Node n2, Vuelo v, ArcType arcType){
        String fromId = n1.aeropuerto.getCodigo() + "@" + n1.timestampUTC;
        String toId   = n2.aeropuerto.getCodigo() + "@" + n2.timestampUTC;
        String arcId = fromId + "→" + toId;
        int capacidad = (v != null ? v.getCapacidad() : Math.max(1, n1.aeropuerto.getCapacidad()));

        Arc arc = new Arc(arcId, n1, n2, capacidad, v, arcType);

        arcsById.put(arcId, arc);
        out.computeIfAbsent(fromId, k -> new ArrayList<>()).add(arc);
    }

    private void addEventsByAirport(String origen, String destino, LocalDateTime depUTC, LocalDateTime arrUTC){
        eventsByAirport.computeIfAbsent(origen,s -> new TreeSet<>()).add(depUTC);
        eventsByAirport.computeIfAbsent(destino,s -> new TreeSet<>()).add(arrUTC);
    }

    //CÓDIGO PARA HACER LAS PRUEBAS:
    // ---- Helpers de inspección (para pruebas) ----
    public List<Arc> getOutgoing(String nodeId) {
        return out.getOrDefault(nodeId, Collections.emptyList());
    }
    public Set<String> nodeIds() { return Collections.unmodifiableSet(nodesById.keySet()); }
    public Set<String> arcIds()  { return Collections.unmodifiableSet(arcsById.keySet()); }

    public void printOutgoing(String nodeId) {
        System.out.println("OUT from " + nodeId + ":");
        for (Arc a : getOutgoing(nodeId)) {
            String fromId = a.getFrom().getNodeId();
            String toId   = a.getTo().getNodeId();
            System.out.println("  " + a.getArcType() + " : " + fromId + " → " + toId + "  cap=" + a.getCapacity());
        }
    }

    public void printSomeSamples(int airportsToShow, int eventsPerAirport) {
        System.out.println("NODES=" + nodeIds().size() + "  ARCS=" + arcIds().size());
        int shownAirports = 0;
        for (Map.Entry<String, TreeSet<LocalDateTime>> e : eventsByAirport.entrySet()) {
            if (shownAirports++ >= airportsToShow) break;
            String iata = e.getKey();
            System.out.println("Aeropuerto " + iata + " eventos:");
            int shownEvents = 0;
            for (LocalDateTime t : e.getValue()) {
                if (shownEvents++ >= eventsPerAirport) break;
                String nodeId = iata + "@" + t;
                printOutgoing(nodeId);
            }
        }
    }

}
