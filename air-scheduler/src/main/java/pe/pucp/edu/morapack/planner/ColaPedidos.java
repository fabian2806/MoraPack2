package pe.pucp.edu.morapack.planner;

import java.util.LinkedList;
import java.util.Queue;

public class ColaPedidos {
    private Queue<Pedido> cola = new LinkedList<>();

    public void agregarPedido(Pedido p) {
        cola.add(p);
    }

    public Pedido obtenerPedido() {
        return cola.poll();
    }

    public boolean estaVacia() {
        return cola.isEmpty();
    }
}

