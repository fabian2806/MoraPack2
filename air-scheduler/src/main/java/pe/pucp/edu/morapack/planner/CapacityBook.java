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

    //Helpers para impresión:
    public int residualPath(java.util.List<String> arcIds, pe.pucp.edu.morapack.planner.TEGraph g) {
        int min = Integer.MAX_VALUE;
        for (String arcId : arcIds) {
            pe.pucp.edu.morapack.planner.TEGraph.Arc a = g.arcsById.get(arcId);
            if (a == null) continue;
            int res = residual(a); // = max(0, a.getCapacity() - used(arcId))
            if (res < min) min = res;
        }
        return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    public boolean canFitPath(java.util.List<String> arcIds, pe.pucp.edu.morapack.planner.TEGraph g, int q) {
        return residualPath(arcIds, g) >= q;
    }


}
