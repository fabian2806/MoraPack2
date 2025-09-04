package pe.pucp.edu.morapack.planner.GA;

import java.util.ArrayList;
import java.util.List;

public class Cromosoma {
    private final ArrayList<int[]> asignacion;
    private double fitness;

    public Cromosoma(int numPedidos) {
        this.asignacion = new ArrayList<>(numPedidos);
        for (int i = 0; i < numPedidos; i++) asignacion.add(new int[0]);
    }

    public ArrayList<int[]> getAsignacion() {
        return asignacion;
    }

    public int[] getRuta(int pedido) {
        return asignacion.get(pedido);
    }

    public void setRuta(int pedido, int[] ruta) {
        asignacion.set(pedido, ruta);
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public Cromosoma clonar(){
        Cromosoma c = new Cromosoma(asignacion.size());
        for (int i = 0; i < asignacion.size(); i++) c.setRuta(i, asignacion.get(i).clone());
        c.setFitness(this.fitness);
        return c;
    }
}
