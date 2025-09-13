package pe.pucp.edu.morapack.planner;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class Plan {
    private Map<Integer, List<RutaPedido>> asignaciones;
    private Map<Integer, Pedido> pedidos;
    private TEGraph grafo;
    private double fitness;
    
    public Plan() {
        this.asignaciones = new HashMap<>();
        this.pedidos = new HashMap<>();
        this.fitness = Double.MAX_VALUE / 1000.0;
    }
    
    // Constructor de copia
    public Plan(Plan otro) {
        this.grafo = otro.grafo;
        this.pedidos = new HashMap<>(otro.pedidos);
        this.asignaciones = new HashMap<>();
        
        // Realizar una copia profunda de las asignaciones
        for (Map.Entry<Integer, List<RutaPedido>> entry : otro.asignaciones.entrySet()) {
            List<RutaPedido> rutas = new ArrayList<>();
            for (RutaPedido ruta : entry.getValue()) {
                rutas.add(new RutaPedido(ruta));
            }
            this.asignaciones.put(entry.getKey(), rutas);
        }
        
        this.fitness = otro.fitness;
    }
    
    public void evaluar() {
        if (grafo == null || pedidos == null || asignaciones == null) {
            this.fitness = Double.MAX_VALUE / 1000.0;
            return;
        }
        
        System.out.println("\n=== INICIO EVALUACIÓN ===");
        
        double costoTotal = 0;
        double penalizacionTardanza = 0;
        double penalizacionCapacidad = 0;
        
        // Mapa para rastrear la capacidad utilizada en cada vuelo
        Map<String, Double> capacidadPorVuelo = new HashMap<>();
        
        try {
            // Evaluar cada pedido y sus rutas
            for (Map.Entry<Integer, List<RutaPedido>> entry : asignaciones.entrySet()) {
                int idPedido = entry.getKey();
                Pedido pedido = pedidos.get(idPedido);
                
                if (pedido == null) {
                    System.out.println("Pedido " + idPedido + " no encontrado");
                    continue;
                }
                
                double cantidadAsignada = 0;
                
                for (RutaPedido ruta : entry.getValue()) {
                    // Verificar que la fracción sea válida
                    if (ruta.getFraccion() <= 0 || ruta.getFraccion() > 1) {
                        System.out.println("Fracción inválida: " + ruta.getFraccion());
                        this.fitness = Double.MAX_VALUE / 1000.0;
                        return;
                    }
                    
                    // Acumular la cantidad asignada
                    cantidadAsignada += ruta.getFraccion() * pedido.getCantidad();
                    
                    // Calcular costo de la ruta
                    double costoRuta = ruta.getCosto() * ruta.getFraccion();
                    System.out.println("Costo de ruta para pedido " + idPedido + ": " + costoRuta);
                    
                    if (Double.isInfinite(costoRuta) || Double.isNaN(costoRuta)) {
                        System.out.println("¡CUIDADO! Costo inválido detectado: " + costoRuta);
                        costoRuta = 0;
                    }
                    
                    costoTotal += costoRuta;
                    
                    // Calcular penalización por tardanza
                    if (ruta.getEta() != null && pedido.getDeadline() != null) {
                        if (ruta.getEta().isAfter(pedido.getDeadline())) {
                            long horasTardanza = Duration.between(pedido.getDeadline(), ruta.getEta()).toHours();
                            double penalizacion = Math.min(1000, horasTardanza) * ruta.getFraccion();
                            System.out.println("Penalización por tardanza: " + penalizacion + " horas");
                            penalizacionTardanza += penalizacion;
                        }
                    }
                    
                    // Acumular capacidad utilizada en cada vuelo
                    for (Vuelo vuelo : ruta.getVuelos()) {
                        try {
                            String idVuelo = "";
                            try {
                                idVuelo = (String) vuelo.getClass().getMethod("getIdVuelo").invoke(vuelo);
                            } catch (Exception e) {
                                idVuelo = String.format("%s-%s-%s", 
                                    vuelo.getOrigen(), vuelo.getDestino(), 
                                    vuelo.getHoraOrigen().toString());
                            }
                            
                            double capacidadUsada = capacidadPorVuelo.getOrDefault(idVuelo, 0.0);
                            double incremento = pedido.getCantidad() * ruta.getFraccion();
                            capacidadUsada += incremento;
                            capacidadPorVuelo.put(idVuelo, capacidadUsada);
                            
                            System.out.println("Vuelo " + idVuelo + " - Capacidad usada: " + capacidadUsada);
                            
                        } catch (Exception e) {
                            System.out.println("Error al procesar vuelo: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
                // Verificar que se haya asignado la cantidad correcta (con cierta tolerancia)
                double diferencia = Math.abs(cantidadAsignada - pedido.getCantidad());
                System.out.println("Cantidad asignada: " + cantidadAsignada + "/" + pedido.getCantidad() + 
                                 " (diferencia: " + diferencia + ")");
                
                if (diferencia > 0.01) {
                    System.out.println("Error: Cantidad asignada no coincide con la requerida");
                    this.fitness = Double.MAX_VALUE / 1000.0;
                    return;
                }
            }
            
            // Calcular penalización por sobrecapacidad
            System.out.println("\nVerificando capacidad de vuelos:");
            for (Map.Entry<String, Double> entry : capacidadPorVuelo.entrySet()) {
                String idVuelo = entry.getKey();
                double capacidadUsada = entry.getValue();
                
                System.out.println("  Vuelo " + idVuelo + " - Capacidad usada: " + capacidadUsada);
                
                try {
                    Vuelo vuelo = grafo.obtenerVueloPorId(idVuelo);
                    if (vuelo != null) {
                        double capacidadMaxima = 100; // Valor por defecto
                        try {
                            Object capacidadObj = vuelo.getClass().getMethod("getCapacidad").invoke(vuelo);
                            if (capacidadObj instanceof Number) {
                                capacidadMaxima = ((Number)capacidadObj).doubleValue();
                            }
                        } catch (Exception e) {
                            System.out.println("    Usando capacidad por defecto (100) para vuelo " + idVuelo);
                        }
                        
                        System.out.println("    Capacidad máxima: " + capacidadMaxima);
                        
                        if (capacidadUsada > capacidadMaxima) {
                            double exceso = capacidadUsada - capacidadMaxima;
                            double penalizacion = Math.min(1000, exceso * 10);
                            System.out.println("    Penalización por sobrecapacidad: " + penalizacion);
                            penalizacionCapacidad += penalizacion;
                        }
                    } else {
                        System.out.println("    Vuelo no encontrado en el grafo");
                    }
                } catch (Exception e) {
                    System.out.println("    Error al verificar capacidad: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Calcular el fitness final (menor es mejor)
            System.out.println("\n=== RESUMEN EVALUACIÓN ===");
            System.out.println("Costo total: " + costoTotal);
            System.out.println("Penalización por tardanza: " + (penalizacionTardanza * 10));
            System.out.println("Penalización por capacidad: " + (penalizacionCapacidad * 5));
            
            this.fitness = costoTotal + 
                         (penalizacionTardanza * 10) + 
                         (penalizacionCapacidad * 5);
            
            System.out.println("Fitness calculado: " + this.fitness);
            
            // Asegurar que el fitness no sea infinito
            if (Double.isInfinite(this.fitness) || Double.isNaN(this.fitness)) {
                System.out.println("¡ADVERTENCIA! Fitness inválido detectado");
                this.fitness = Double.MAX_VALUE / 1000.0;
            }
            
            System.out.println("Fitness final: " + this.fitness);
            System.out.println("=== FIN EVALUACIÓN ===\n");
            
        } catch (Exception e) {
            System.out.println("ERROR en evaluación: " + e.getMessage());
            e.printStackTrace();
            this.fitness = Double.MAX_VALUE / 1000.0;
        }
    }
    
    // Método para clonar el plan
    public Plan clonar() {
        return new Plan(this);
    }
    
    // Método para agregar una ruta al plan
    public void agregarRuta(RutaPedido ruta) {
        int idPedido = ruta.getIdPedido();
        if (!asignaciones.containsKey(idPedido)) {
            asignaciones.put(idPedido, new ArrayList<>());
        }
        asignaciones.get(idPedido).add(ruta);
    }
    
    // Método para obtener todas las rutas del plan
    public List<RutaPedido> getRutas() {
        return asignaciones.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
    
    // Getters y setters
    public Map<Integer, List<RutaPedido>> getAsignaciones() {
        return new HashMap<>(asignaciones);
    }
    
    public void setAsignaciones(Map<Integer, List<RutaPedido>> asignaciones) {
        this.asignaciones = new HashMap<>();
        if (asignaciones != null) {
            for (Map.Entry<Integer, List<RutaPedido>> entry : asignaciones.entrySet()) {
                List<RutaPedido> rutas = new ArrayList<>();
                for (RutaPedido ruta : entry.getValue()) {
                    rutas.add(new RutaPedido(ruta));
                }
                this.asignaciones.put(entry.getKey(), rutas);
            }
        }
    }
    
    public Map<Integer, Pedido> getPedidos() {
        return new HashMap<>(pedidos);
    }
    
    public void setPedidos(Map<Integer, Pedido> pedidos) {
        this.pedidos = new HashMap<>(pedidos);
    }
    
    public TEGraph getGrafo() {
        return grafo;
    }
    
    public void setGrafo(TEGraph grafo) {
        this.grafo = grafo;
    }
    
    public double getFitness() {
        return fitness;
    }
    
    @Override
    public String toString() {
        return String.format("Plan{fitness=%.2f, rutas=%d}", 
            fitness, 
            asignaciones.values().stream().mapToInt(List::size).sum());
    }
}
