package pe.pucp.edu.morapack.planner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class RouteEvaluator {
    private final SLAService sla;
    private final AeropuertosMap aeropuertos;

    public RouteEvaluator(SLAService sla, AeropuertosMap aeropuertos){
        this.sla = sla;
        this.aeropuertos = aeropuertos;
    }

    public LocalDateTime arrivalTime(RoutePlan r){
        return r.getArrUTC();
    }

    public LocalDateTime pickupTime(RoutePlan r){
        return arrivalTime(r).plus(sla.getPickup());
    }

    public boolean respectsReadyTime(RoutePlan r, Pedido p){
        return !r.getDepUTC().isBefore(p.getFecha());
    }

    //privado:
    //podría pasarse el aeropuerto como parametro del routeEvaluator tmb
    //para no crear un list de tegraph (quizas rompa encapsulamiento)
    private Aeropuerto originFromRoute(RoutePlan r){
        List<TEGraph.Arc> arcs = r.getArcs();
        if (arcs == null) return null;
        for (TEGraph.Arc a : arcs){
            if (a.getArcType() == ArcType.VUELO){
                return a.getFrom().getAeropuerto();
            }
        }
        return null;
    }

    //Como el pedido no tiene aeropuerto de salida, lo pasamos a parte
    public LocalDateTime deadline(RoutePlan r, Pedido p){
        Aeropuerto a1 = originFromRoute(r);
        Aeropuerto a2 = aeropuertos.obtener(p.getDestino());
        if (a1 == null || a2 == null) return null;
        Duration slaTime = sla.sla(a1, a2);
        return sla.deadline(p.getFecha(), slaTime);
    };

    public boolean meetsSLA(RoutePlan r, Pedido p){
        LocalDateTime dl = deadline(r, p);
        if (dl == null) return false;
        return !pickupTime(r).isAfter(dl);
    }
}
