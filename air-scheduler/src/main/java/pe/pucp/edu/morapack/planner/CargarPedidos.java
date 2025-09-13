package pe.pucp.edu.morapack.planner;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

public class CargarPedidos {
    private final Queue<Pedido> colaPedidos;
    private List<Pedido> pedidos;

    public CargarPedidos() {
        colaPedidos = new LinkedList<>();
        this.pedidos = new ArrayList<>();
    }

    public Queue<Pedido> getColaPedidos() {
        return colaPedidos;
    }

    public List<Pedido> getPedidos() {
        return new ArrayList<>(pedidos); 
    }

    public void agregar(Pedido pe) {
        colaPedidos.add(pe);
        pedidos.add(pe);
    }

    public void leerDatos(Scanner sc) {
        while (sc.hasNextLine()) {
            String linea = sc.nextLine().trim();
            if (!linea.isEmpty()) {
                try {
                    String[] partes = linea.split(",");
                    if (partes.length >= 5) {
                        int idPedido = Integer.parseInt(partes[0].trim());
                        int idCliente = Integer.parseInt(partes[1].trim());
                        String destino = partes[2].trim();
                        
                        // Parsear la fecha, eliminando los milisegundos si existen
                        String fechaStr = partes[3].trim();
                        LocalDateTime fecha = LocalDateTime.parse(fechaStr.split("\\.")[0]);
                        
                        int cantidad = Integer.parseInt(partes[4].trim());
                        
                        Pedido pedido = new Pedido(idPedido, idCliente, destino, fecha, cantidad);
                        agregar(pedido);
                    }
                } catch (Exception e) {
                    System.err.println("Error al procesar línea de pedido: " + linea);
                    e.printStackTrace();
                }
            }
        }
    }

    public void mostrar() {
        for (Pedido p : colaPedidos) {
            System.out.println(p);
        }
    }

}
