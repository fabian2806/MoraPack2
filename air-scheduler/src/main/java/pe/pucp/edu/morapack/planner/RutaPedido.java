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
    
    private double calcularCosto() {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0.0;
        }
        
        double costoTotal = 0.0;
        
        for (Vuelo vuelo : vuelos) {
            try {
                // Obtener el costo del vuelo de forma segura
                Object costoObj = vuelo.getClass().getMethod("getCosto").invoke(vuelo);
                double costoVuelo = 0.0;
                
                // Convertir el costo a double de forma segura
                if (costoObj instanceof Number) {
                    costoVuelo = ((Number) costoObj).doubleValue();
                } else if (costoObj != null) {
                    try {
                        costoVuelo = Double.parseDouble(costoObj.toString());
                    } catch (NumberFormatException e) {
                        costoVuelo = 100.0; // Valor por defecto si no se puede convertir a número
                    }
                }
                
                // Validar que el costo sea un valor razonable
                if (costoVuelo < 0) {
                    costoVuelo = 0; // No permitir costos negativos
                } else if (costoVuelo > 1_000_000) { // Límite superior razonable
                    costoVuelo = 1_000_000;
                }
                
                costoTotal += costoVuelo;
                
                // Validar que el costo total no sea excesivo
                if (costoTotal > 10_000_000) { // Límite total razonable para una ruta
                    return 10_000_000;
                }
                
            } catch (Exception e) {
                // En caso de error, usar un costo base por vuelo
                costoTotal += 100.0;
            }
        }
        
        return costoTotal;
    }
    
    private LocalDateTime calcularEta() {
        if (vuelos == null || vuelos.isEmpty()) {
            return null;
        }
        
        // Obtener la hora de llegada del último vuelo
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
        try {
            // Intentar obtener la hora de destino como LocalDateTime
            Object horaDestino = ultimoVuelo.getClass().getMethod("getHoraDestino").invoke(ultimoVuelo);
            if (horaDestino instanceof LocalDateTime) {
                return (LocalDateTime) horaDestino;
            } else if (horaDestino instanceof LocalTime) {
                // Si es LocalTime, combinarlo con la fecha actual
                LocalTime time = (LocalTime) horaDestino;
                return LocalDateTime.of(LocalDate.now(), time);
            }
        } catch (Exception e) {
            // En caso de error, devolver la hora actual más 2 horas
            return LocalDateTime.now().plusHours(2);
        }
        
        return LocalDateTime.now().plusHours(2); // Valor por defecto
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
