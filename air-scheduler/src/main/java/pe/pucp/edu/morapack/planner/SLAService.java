package pe.pucp.edu.morapack.planner;

import java.time.Duration;
import java.time.LocalDateTime;

public class SLAService {
    private static final Duration INTRA_SLA = Duration.ofHours(48); //2 días intraCont
    private static final Duration INTER_SLA = Duration.ofHours(72); //3 días interCont
    private static final Duration pickup = Duration.ofHours(2); //2 horas se recoge

    public boolean isIntra (Aeropuerto a1, Aeropuerto a2){
        return a1.getContinente().equalsIgnoreCase(a2.getContinente());
    }

    public Duration sla (Aeropuerto a1, Aeropuerto a2){
        return isIntra(a1, a2) ? INTRA_SLA : INTER_SLA;
    }

    public Duration getPickup(){
        return pickup;
    }

    public LocalDateTime deadline(LocalDateTime readyTimeUTC, Duration sla){
        return readyTimeUTC.plus(sla);
    }
}
