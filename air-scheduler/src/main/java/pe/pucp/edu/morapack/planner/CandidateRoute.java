package pe.pucp.edu.morapack.planner;

import java.time.LocalDateTime;
import java.util.List;

public class CandidateRoute {
    public final int pedidoId;
    public final List<String> arcIds;
    public final LocalDateTime depUTC;
    public final LocalDateTime arrUTC;
    public final double cost;
    public final int hops;
    public final int minResidual; //esto representa el cuello de botella

    public CandidateRoute(int pedidoId, List<String> arcIds,
                          LocalDateTime depUTC, LocalDateTime arrUTC,
                          double cost, int hops, int minResidual) {
        this.pedidoId = pedidoId;
        this.arcIds   = arcIds;
        this.depUTC   = depUTC;
        this.arrUTC   = arrUTC;
        this.cost     = cost;
        this.hops     = hops;
        this.minResidual = minResidual;
    }
}
