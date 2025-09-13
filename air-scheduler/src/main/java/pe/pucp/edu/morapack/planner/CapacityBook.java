package pe.pucp.edu.morapack.planner;

import java.util.HashMap;
import java.util.Map;

public class CapacityBook {
    private final Map<String, Integer> usedByArc = new HashMap<>();

    public int used (String arcId){
        return usedByArc.getOrDefault(arcId, 0);
    }

    public int residual(TEGraph.Arc arc){
        return Math.max(0, arc.getCapacity() - used(arc.getArcId()));
    }

    public boolean canFit(TEGraph.Arc arc, int q){
        return residual(arc) >= q;
    }

    public void reserve(TEGraph.Arc arc, int q){
        usedByArc.merge(arc.getArcId(), q, Integer::sum);
    }

    public void release(TEGraph.Arc arc, int q){
        usedByArc.merge(arc.getArcId(), -q, Integer::sum);
        if (used(arc.getArcId()) <= 0) usedByArc.remove(arc.getArcId());
    }

}
