package pe.pucp.edu.morapack.planner;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class RutaPedido {
    private int idPedido;
    private double fraccion;
    private List<Vuelo> vuelos;
    private double costo;
    private LocalDateTime eta;

    public RutaPedido(int idPedido, double fraccion, List<Vuelo> vuelos) {
        this.idPedido = idPedido;
        this.fraccion = fraccion;
        this.vuelos = new ArrayList<>(vuelos);
        this.costo = calcularCosto();
        this.eta = calcularEta();
    }
    
    // Constructor que acepta un Pedido y una lista de Vuelos
    public RutaPedido(Pedido pedido, List<Vuelo> vuelos) {
        this.idPedido = pedido.getIdPedido();
        this.fraccion = 1.0; // Por defecto, la fracción es 1.0 (100% del pedido)
        this.vuelos = new ArrayList<>(vuelos);
        this.costo = calcularCosto();
        this.eta = calcularEta();
    }
    
    // Constructor de copia
    public RutaPedido(RutaPedido otra) {
        this.idPedido = otra.idPedido;
        this.fraccion = otra.fraccion;
        this.vuelos = new ArrayList<>(otra.vuelos);
        this.costo = otra.costo;
        this.eta = otra.eta;
    }
    
    public double calcularCosto() {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0.0;
        }
        // Asumimos un costo fijo por vuelo ya que no hay método getCosto()
        return vuelos.size() * 100.0; // Costo fijo de 100 por vuelo
    }

    public LocalDateTime calcularEta() {
        if (vuelos == null || vuelos.isEmpty()) {
            return null;
        }
        // Obtener la hora de llegada del último vuelo
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
        try {
            // Usamos la fecha de simulación + la hora de destino
            LocalTime horaDestino = ultimoVuelo.getHoraDestino();
            return LocalDateTime.of(LocalDate.now(), horaDestino);
        } catch (Exception e) {
            // En caso de error, devolver la hora actual más 2 horas
            return LocalDateTime.now().plusHours(2);
        }
    }

    public LocalDateTime calcularTiempoLlegada() {
        if (vuelos == null || vuelos.isEmpty()) {
            return null;
        }
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
        // Usamos la fecha actual + la hora de destino
        return LocalDateTime.of(LocalDate.now(), ultimoVuelo.getHoraDestino());
    }

    public long calcularTiempoViajeMinutos() {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0;
        }
        Vuelo primerVuelo = vuelos.get(0);
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
        
        // Usamos la fecha actual + las horas de los vuelos
        LocalDateTime inicio = LocalDateTime.of(LocalDate.now(), primerVuelo.getHoraOrigen());
        LocalDateTime fin = LocalDateTime.of(LocalDate.now(), ultimoVuelo.getHoraDestino());
        
        // Si el vuelo termina al día siguiente, sumamos un día
        if (fin.isBefore(inicio)) {
            fin = fin.plusDays(1);
        }
        
        return Duration.between(inicio, fin).toMinutes();
    }

    // Getters y setters
    public int getIdPedido() {
        return idPedido;
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public double getFraccion() {
        return fraccion;
    }

    public void setFraccion(double fraccion) {
        this.fraccion = fraccion;
    }

    public List<Vuelo> getVuelos() {
        return new ArrayList<>(vuelos);
    }

    public void setVuelos(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos);
        this.costo = calcularCosto();
        this.eta = calcularEta();
    }

    public double getCosto() {
        return costo;
    }

    public LocalDateTime getEta() {
        return eta;
    }

    public void agregarVuelo(Vuelo vuelo) {
        if (vuelo != null) {
            this.vuelos.add(vuelo);
            this.costo = calcularCosto();
            this.eta = calcularEta();
        }
    }
    
    @Override
    public String toString() {
        return String.format("Ruta{pedido=%d, fraccion=%.2f, vuelos=%d, costo=%.2f, eta=%s}",
            idPedido, fraccion, vuelos.size(), costo, eta);
    }
}
