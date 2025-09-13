package pe.pucp.edu.morapack.planner;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static pe.pucp.edu.morapack.planner.SimulationConfig.SIM_DATE;

public class TEGraph {
    private final Map<String, List<Vuelo>> vuelosPorOrigen;
    private final List<Vuelo> vuelos;
    private final Map<String, Aeropuerto> aeropuertos;
    
    public Map<String, Node> nodesById = new HashMap<>();
    public Map<String, Arc> arcsById = new HashMap<>();
    public Map<String, List<Arc>> out = new HashMap<>();
    public Map<String, TreeSet<LocalDateTime>> eventsByAirport = new HashMap<>();

    public TEGraph(AeropuertosMap aeropuertosMap, VuelosMap vuelosMap){
        this.vuelosPorOrigen = new HashMap<>();
        this.vuelos = new ArrayList<>();
        this.aeropuertos = aeropuertosMap.getAeropuertos();
        
        // Inicializar vuelos
        if (vuelosMap != null) {
            for (List<Vuelo> vuelos : vuelosMap.getVuelosPorOrigen().values()) {
                for (Vuelo vuelo : vuelos) {
                    agregarVuelo(vuelo);
                }
            }
        }
        
        build();
    }
    
    private void agregarVuelo(Vuelo vuelo) {
        vuelos.add(vuelo);
        vuelosPorOrigen.computeIfAbsent(vuelo.getOrigen(), k -> new ArrayList<>()).add(vuelo);
    }
    
    private void build(){
        //Recuperamos la lista de vuelos que parten de un origen
        //Armamos Arcos de vuelo y Eventos por aeropuerto
        for (Map.Entry<String, List<Vuelo>> vs: vuelosPorOrigen.entrySet()){
            Aeropuerto aeropuertoActual = aeropuertos.get(vs.getKey());
            for (Vuelo v: vs.getValue()){
                String origen = v.getOrigen();
                String destino = v.getDestino();
                Aeropuerto aeropuertoDestino = aeropuertos.get(destino);

                //El archivo del profe aun no tiene dia, solo horas:
                //SIM_DATE es una constante en SimulationConfig.java
                LocalDateTime depUTC = LocalDateTime.of(SIM_DATE, v.getHoraGMTOrigen());
                LocalDateTime arrUTC = LocalDateTime.of(SIM_DATE, v.getHoraGMTDestino());
                //Contemplar un posible adjust arrival por desfase del dia de vuelo
                if (arrUTC.isBefore(depUTC)) arrUTC = arrUTC.plusDays(1);

                //Node n1 = new Node(aeropuertoActual, depUTC, NodeType.SALIDA);
                //Node n2 = new Node(aeropuertoDestino, arrUTC, NodeType.LLEGADA);

                Node n1 = getOrCreateNode(aeropuertoActual, depUTC, NodeType.SALIDA);
                Node n2 = getOrCreateNode(aeropuertoDestino, arrUTC, NodeType.LLEGADA);

                //n1 es from, n2 es to:
                addArc(n1, n2, v, ArcType.VUELO);
                //Agregamos los eventos al aeropuerto:
                addEventsByAirport(origen, destino, depUTC, arrUTC);
            }
        }

        //Armamos arcos de espera
        for (Map.Entry<String, TreeSet<LocalDateTime>> e: eventsByAirport.entrySet()){
            Aeropuerto aeropuertoActual = aeropuertos.get(e.getKey());
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
        String fromId = n1.aeropuerto.getCodigo() + "@" + n1.time;
        String toId   = n2.aeropuerto.getCodigo() + "@" + n2.time;
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

    /**
     * Obtiene todos los vuelos que salen de un aeropuerto de origen dado.
     * @param origen Código IATA del aeropuerto de origen
     * @return Lista de vuelos que salen del aeropuerto especificado
     */
    public List<Vuelo> obtenerVuelosSalientes(String origen) {
        return new ArrayList<>(vuelosPorOrigen.getOrDefault(origen, Collections.emptyList()));
    }

    /**
     * Obtiene un vuelo por su ID.
     * @param idVuelo ID del vuelo a buscar
     * @return El vuelo con el ID especificado, o null si no se encuentra
     */
    public Vuelo obtenerVueloPorId(String idVuelo) {
        return vuelos.stream()
            .filter(v -> {
                try {
                    Object id = v.getClass().getMethod("getIdVuelo").invoke(v);
                    return id != null && id.toString().equals(idVuelo);
                } catch (Exception e) {
                    return false;
                }
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Obtiene una lista de todos los vuelos del grafo
     */
    public List<Vuelo> getVuelos() {
        return new ArrayList<>(vuelos);
    }
    
    /**
     * Verifica si un aeropuerto con el código especificado existe en el grafo
     * @param codigoAeropuerto Código IATA del aeropuerto a verificar
     * @return true si el aeropuerto existe, false en caso contrario
     */
    public boolean existeAeropuerto(String codigoAeropuerto) {
        return codigoAeropuerto != null && aeropuertos.containsKey(codigoAeropuerto);
    }
    
    /**
     * Obtiene una lista de todos los aeropuertos en el grafo
     * @return Lista de aeropuertos
     */
    public List<Aeropuerto> obtenerAeropuertos() {
        return new ArrayList<>(aeropuertos.values());
    }
    
    public Aeropuerto getAeropuerto(String codigo) {
        return aeropuertos.get(codigo);
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

    public class Node{
        private final String nodeId;
        private final Aeropuerto aeropuerto;
        private final LocalDateTime time;
        private final NodeType nodeType;

        public Node(String nodeId, Aeropuerto aeropuerto, LocalDateTime time, NodeType nodeType) {
            this.nodeId = nodeId;
            this.aeropuerto = aeropuerto;
            this.time = time;
            this.nodeType = nodeType;
        }

        public String getNodeId() {
            return nodeId;
        }

        public Aeropuerto getAeropuerto(){
            return aeropuerto;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public NodeType getNodeType() {
            return nodeType;
        }
    }

    public enum NodeType { SALIDA, LLEGADA, ESPERA }

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

    public enum ArcType { VUELO, ESPERA }
}
