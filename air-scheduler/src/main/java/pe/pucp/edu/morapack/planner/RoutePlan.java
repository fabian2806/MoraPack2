package pe.pucp.edu.morapack.planner;

import java.time.LocalDateTime;
import java.util.List;

public class RoutePlan {
    private final String origen;
    private final String destino;
    private final LocalDateTime depUTC; //salida del primer arco
    private final LocalDateTime arrUTC; //llegada del último arco
    private final List<TEGraph.Arc> arcs; //orden cronológico


    public RoutePlan(String origen, String destino, LocalDateTime depUTC,
                     LocalDateTime arrUTC, List<TEGraph.Arc> arcs){
        this.origen = origen;
        this.destino = destino;
        this.depUTC = depUTC;
        this.arrUTC = arrUTC;
        this.arcs = arcs;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public LocalDateTime getDepUTC() {
        return depUTC;
    }

    public LocalDateTime getArrUTC() {
        return arrUTC;
    }

    public List<TEGraph.Arc> getArcs() {
        return arcs;
    }
}
