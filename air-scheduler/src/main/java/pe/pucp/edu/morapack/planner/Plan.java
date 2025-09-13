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
        if (grafo == null || pedidos == null || pedidos.isEmpty()) {
            this.fitness = 0.0;
            return;
        }

        // Mostrar los primeros 5 pedidos para referencia
        System.out.println("\n=== INICIO EVALUACIÓN ===");
        System.out.println("Total de pedidos: " + pedidos.size());
        System.out.println("Pedidos con rutas asignadas: " + asignaciones.size());

        // Variables para el cálculo del fitness
        double costoTotal = 0;
        double penalizacionTardanza = 0;
        double penalizacionCapacidad = 0;
        double penalizacionEscalas = 0;
        double penalizacionTiempoViaje = 0;
        
        int totalPedidos = Math.max(1, pedidos.size());
        int pedidosAsignados = 0;
        
        // Mapas para rastrear la capacidad utilizada
        Map<Integer, Integer> capacidadVueloUtilizada = new HashMap<>();
        Map<String, Integer> capacidadAeropuertoUtilizada = new HashMap<>();
        
        // Inicializar capacidades de aeropuertos
        if (grafo.getAeropuertos() != null) {
            grafo.getAeropuertos().values().forEach(aeropuerto -> 
                capacidadAeropuertoUtilizada.put(aeropuerto.getCodigo(), 0));
        }
        
        // Evaluar cada pedido asignado
        for (Map.Entry<Integer, List<RutaPedido>> entry : asignaciones.entrySet()) {
            int idPedido = entry.getKey();
            Pedido pedido = pedidos.get(idPedido);
            
            if (pedido == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            
            boolean pedidoValido = true;
            List<RutaPedido> rutas = entry.getValue();
            
            // Primera pasada: verificar capacidades sin modificar
            for (RutaPedido ruta : rutas) {
                if (ruta == null || ruta.getVuelos() == null || ruta.getVuelos().isEmpty()) {
                    pedidoValido = false;
                    break;
                }
                
                // Verificar capacidad de cada vuelo en la ruta
                for (Vuelo vuelo : ruta.getVuelos()) {
                    int capacidadVuelo = vuelo.getCapacidad();
                    int capacidadUsada = capacidadVueloUtilizada.getOrDefault(vuelo.getId(), 0);
                    
                    if (capacidadUsada + pedido.getCantidad() > capacidadVuelo) {
                        // Penalización por exceder capacidad de vuelo
                        double exceso = (capacidadUsada + pedido.getCantidad() - capacidadVuelo) * 50.0;
                        penalizacionCapacidad += exceso;
                        pedidoValido = false;
                    }
                    
                    // Verificar capacidad del aeropuerto de origen
                    String codigoOrigen = vuelo.getOrigen();
                    Aeropuerto aeropuerto = grafo.getAeropuerto(codigoOrigen);
                    if (aeropuerto != null) {
                        int capacidadAeropuerto = aeropuerto.getCapacidad();
                        int capacidadAeropuertoUsada = capacidadAeropuertoUtilizada.getOrDefault(codigoOrigen, 0);
                        
                        if (capacidadAeropuertoUsada + pedido.getCantidad() > capacidadAeropuerto) {
                            // Penalización por exceder capacidad del aeropuerto
                            double exceso = (capacidadAeropuertoUsada + pedido.getCantidad() - capacidadAeropuerto) * 30.0;
                            penalizacionCapacidad += exceso;
                            pedidoValido = false;
                        }
                    }
                }
            }
            
            // Si el pedido no es válido, saltar a la siguiente iteración
            if (!pedidoValido) {
                continue;
            }
            
            // Segunda pasada: actualizar capacidades y calcular costos
            pedidosAsignados++;
            
            for (RutaPedido ruta : rutas) {
                // Actualizar capacidades de vuelos
                for (Vuelo vuelo : ruta.getVuelos()) {
                    // Actualizar capacidad del vuelo
                    int capacidadUsada = capacidadVueloUtilizada.getOrDefault(vuelo.getId(), 0);
                    capacidadVueloUtilizada.put(vuelo.getId(), capacidadUsada + pedido.getCantidad());
                    
                    // Actualizar capacidad del aeropuerto de origen
                    String codigoOrigen = vuelo.getOrigen();
                    int capacidadAeropuertoUsada = capacidadAeropuertoUtilizada.getOrDefault(codigoOrigen, 0);
                    capacidadAeropuertoUtilizada.put(codigoOrigen, capacidadAeropuertoUsada + pedido.getCantidad());
                }
                
                // Calcular costos y penalizaciones para esta ruta
                costoTotal += ruta.calcularCosto();
                
                // Penalización por tiempo de viaje
                long tiempoViajeMinutos = ruta.calcularTiempoViajeMinutos();
                if (tiempoViajeMinutos > 240) { // 4 horas = 240 minutos
                    penalizacionTiempoViaje += (tiempoViajeMinutos - 240) * 0.5;
                }
                
                // Penalización por número de escalas
                int numEscalas = Math.max(0, ruta.getVuelos().size() - 1);
                penalizacionEscalas += numEscalas * 10.0;
                
                // Penalización por tardanza
                LocalDateTime eta = ruta.calcularEta();
                if (eta != null && pedido.getDeadline() != null && eta.isAfter(pedido.getDeadline())) {
                    long minutosTardanza = Duration.between(pedido.getDeadline(), eta).toMinutes();
                    penalizacionTardanza += minutosTardanza * 2.0;
                }
            }
        }
        
        // Calcular el fitness (invertimos las penalizaciones para que mayor sea mejor)
        double tasaAsignacion = (double) pedidosAsignados / totalPedidos;
        double sumaPenalizaciones = penalizacionCapacidad + penalizacionTardanza + 
                                  penalizacionEscalas + penalizacionTiempoViaje;
        
        // Asegurar que no haya valores negativos
        sumaPenalizaciones = Math.max(0, sumaPenalizaciones);
        
        // El fitness es una combinación de la tasa de asignación y el inverso de las penalizaciones
        // Añadimos 1 al denominador para evitar división por cero
        this.fitness = tasaAsignacion / (1.0 + sumaPenalizaciones / 1000.0);
        
        // Mostrar resumen
        System.out.println("\n=== RESUMEN FINAL ===");
        System.out.printf("Pedidos asignados: %d (%.1f%%)\n", 
            pedidosAsignados, (tasaAsignacion * 100));
        System.out.printf("Costo total: %.2f\n", costoTotal);
        System.out.printf("Penalización total: %.2f\n", sumaPenalizaciones);
        System.out.printf("  - Por capacidad: %.2f\n", penalizacionCapacidad);
        System.out.printf("  - Por tardanza: %.2f\n", penalizacionTardanza);
        System.out.printf("  - Por escalas: %.2f\n", penalizacionEscalas);
        System.out.printf("  - Por tiempo de viaje: %.2f\n", penalizacionTiempoViaje);
        System.out.printf("Fitness: %.6f\n", this.fitness);
        System.out.println("===================\n");
    }
    
    public void mostrarResumen() {
        System.out.println("\n=== RESUMEN DEL MEJOR PLAN ===");
        System.out.printf("Fitness: %.4f\n", this.fitness);
        System.out.printf("Número de pedidos asignados: %d\n", this.asignaciones.size());
        
        double costoTotal = 0;
        int pedidosAtiempo = 0;
        int pedidosRetrasados = 0;
        
        for (List<RutaPedido> rutas : asignaciones.values()) {
            for (RutaPedido ruta : rutas) {
                Pedido pedido = pedidos.get(ruta.getIdPedido());
                List<Vuelo> vuelos = ruta.getVuelos();
                
                if (vuelos == null || vuelos.isEmpty()) continue;
                
                Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
                LocalDateTime tiempoEntrega = LocalDateTime.of(LocalDate.now(), ultimoVuelo.getHoraDestino());
                boolean aTiempo = pedido.getDeadline() == null || !tiempoEntrega.isAfter(pedido.getDeadline());
                
                if (aTiempo) pedidosAtiempo++;
                else pedidosRetrasados++;
                
                // Acumular costos
                for (Vuelo vuelo : vuelos) {
                    try {
                        Object costoObj = vuelo.getClass().getMethod("getCosto").invoke(vuelo);
                        if (costoObj instanceof Number) {
                            double costoVuelo = ((Number)costoObj).doubleValue();
                            costoTotal += costoVuelo * (pedido.getCantidad() / 100.0);
                        }
                    } catch (Exception e) {
                        costoTotal += 100.0 * (pedido.getCantidad() / 100.0);
                    }
                }
            }
        }
        
        System.out.printf("Costo total estimado: $%.2f\n", costoTotal);
        System.out.printf("Pedidos a tiempo: %d (%.1f%%)\n", 
                pedidosAtiempo, 
                (pedidosAtiempo * 100.0 / Math.max(1, pedidosAtiempo + pedidosRetrasados)));
        System.out.printf("Pedidos retrasados: %d (%.1f%%)\n", 
                pedidosRetrasados, 
                (pedidosRetrasados * 100.0 / Math.max(1, pedidosAtiempo + pedidosRetrasados)));
        
        System.out.println("\nDetalle de pedidos:");
        System.out.println("ID\tOrigen\tDestino\tTiempo Límite\tEstado");
        System.out.println("-".repeat(80));
        
        for (List<RutaPedido> rutas : asignaciones.values()) {
            for (RutaPedido ruta : rutas) {
                Pedido p = pedidos.get(ruta.getIdPedido());
                List<Vuelo> vuelosRuta = ruta.getVuelos();
                
                if (vuelosRuta == null || vuelosRuta.isEmpty()) {
                    System.out.printf("%d\t%s\t%s\t%s\tNO ASIGNADO\n", 
                            p.getIdPedido(), 
                            "N/A", 
                            p.getDestino(), 
                            p.getDeadline() != null ? p.getDeadline().toString() : "Sin límite");
                } else {
                    Vuelo ultimoVuelo = vuelosRuta.get(vuelosRuta.size() - 1);
                    LocalDateTime tiempoEntrega = LocalDateTime.of(LocalDate.now(), ultimoVuelo.getHoraDestino());
                    boolean aTiempo = p.getDeadline() == null || !tiempoEntrega.isAfter(p.getDeadline());
                    
                    System.out.printf("%d\t%s\t%s\t%s\t%s (%s)\n", 
                            p.getIdPedido(), 
                            vuelosRuta.get(0).getOrigen(), 
                            p.getDestino(), 
                            p.getDeadline() != null ? p.getDeadline().toString() : "Sin límite",
                            aTiempo ? "A TIEMPO" : "RETRASADO",
                            tiempoEntrega);
                }
            }
        }
        
        System.out.println("\n=== FIN DEL REPORTE ===\n");
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
