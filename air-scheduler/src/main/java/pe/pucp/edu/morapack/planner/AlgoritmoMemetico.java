package pe.pucp.edu.morapack.planner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AlgoritmoMemetico {
    private final TEGraph grafo;
    private final CapacityBook capacidadGlobal;
    private final Map<Integer, Pedido> pedidosMap;
    private final Map<Integer, List<CandidateRoute>> candidatasPorPedido;
    private final Random random;
    
    // Parámetros del algoritmo
    private final int tamanoPoblacion = 100;
    private final int maxGeneraciones = 100;
    private final double probCruce = 0.8;
    private final double probMutacion = 0.1;
    private final int tamanoTorneo = 5;
    private final int frecuenciaBusquedaLocal = 5; // Cada cuántas generaciones aplicar búsqueda local
    
    public AlgoritmoMemetico(TEGraph grafo, 
                            CapacityBook capacidadGlobal,
                            Map<Integer, Pedido> pedidosMap,
                            Map<Integer, List<CandidateRoute>> candidatasPorPedido) {
        this.grafo = grafo;
        this.capacidadGlobal = capacidadGlobal;
        this.pedidosMap = pedidosMap;
        this.candidatasPorPedido = candidatasPorPedido;
        this.random = new Random();
    }

    //Para imprimir bien las horas:
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    
    // Clase interna para representar un individuo (solución)
    private class Individuo {
        // Mapa de pedidoId a índice de ruta candidata (-1 si no se asigna ninguna ruta)
        private final Map<Integer, Integer> asignaciones;
        // Mapa de capacidad local (arcId -> carga adicional en este individuo)
        private final Map<String, Integer> deltaCapacidad;
        private double fitness;
        private boolean fitnessCalculado;
        private int pedidosAsignados;
        private int cantidadTotalAsignada;
        
        public Individuo() {
            this.asignaciones = new HashMap<>();
            this.deltaCapacidad = new HashMap<>();
            this.fitness = Double.NEGATIVE_INFINITY;
            this.fitnessCalculado = false;
            this.pedidosAsignados = 0;
            this.cantidadTotalAsignada = 0;
            
            // Inicializar asignaciones a -1 (sin asignar)
            for (Integer pedidoId : pedidosMap.keySet()) {
                asignaciones.put(pedidoId, -1);
            }
        }
        
        public Individuo(Individuo otro) {
            this.asignaciones = new HashMap<>(otro.asignaciones);
            this.deltaCapacidad = new HashMap<>(otro.deltaCapacidad);
            this.fitness = otro.fitness;
            this.fitnessCalculado = otro.fitnessCalculado;
            this.pedidosAsignados = otro.pedidosAsignados;
            this.cantidadTotalAsignada = otro.cantidadTotalAsignada;
        }
        
        public boolean puedeAsignarRuta(int pedidoId, int idxRuta) {
            Pedido pedido = pedidosMap.get(pedidoId);
            if (pedido == null) return false;
            
            List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
            if (candidatas == null || idxRuta < 0 || idxRuta >= candidatas.size()) {
                return false; // No hay rutas candidatas o índice inválido
            }
            
            CandidateRoute ruta = candidatas.get(idxRuta);
            int cantidad = pedido.getCantidad();
            
            // Verificar capacidad para cada arco de la ruta
            for (String arcId : ruta.arcIds) {
                TEGraph.Arc arco = grafo.arcsById.get(arcId);
                if (arco == null) continue;
                
                // Capacidad residual global - carga adicional en este individuo
                int capacidadResidual = capacidadGlobal.residual(arco) - 
                                      deltaCapacidad.getOrDefault(arcId, 0);
                
                if (capacidadResidual < cantidad) {
                    return false; // No hay capacidad suficiente
                }
            }
            
            return true;
        }
        
        public void asignarRuta(int pedidoId, int idxRuta) {
            if (!puedeAsignarRuta(pedidoId, idxRuta)) {
                return; // No se puede asignar la ruta
            }
            
            Pedido pedido = pedidosMap.get(pedidoId);
            List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
            CandidateRoute ruta = candidatas.get(idxRuta);
            int cantidad = pedido.getCantidad();
            
            // Actualizar delta de capacidad
            for (String arcId : ruta.arcIds) {
                deltaCapacidad.merge(arcId, cantidad, Integer::sum);
            }
            
            // Actualizar asignaciones
            int asignacionAnterior = asignaciones.getOrDefault(pedidoId, -1);
            if (asignacionAnterior == -1) {
                // Nueva asignación
                pedidosAsignados++;
                cantidadTotalAsignada += cantidad;
            } else {
                // Reemplazar asignación anterior
                CandidateRoute rutaAnterior = candidatasPorPedido.get(pedidoId).get(asignacionAnterior);
                for (String arcId : rutaAnterior.arcIds) {
                    deltaCapacidad.merge(arcId, -cantidad, (a, b) -> (a + b == 0) ? null : a + b);
                }
            }
            
            asignaciones.put(pedidoId, idxRuta);
            fitnessCalculado = false;
        }
        
        public void eliminarAsignacion(int pedidoId) {
            Integer idxRuta = asignaciones.get(pedidoId);
            if (idxRuta == null || idxRuta == -1) return;
            
            Pedido pedido = pedidosMap.get(pedidoId);
            List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
            if (candidatas == null || idxRuta >= candidatas.size()) return;
            
            CandidateRoute ruta = candidatas.get(idxRuta);
            int cantidad = pedido.getCantidad();
            
            // Actualizar delta de capacidad
            for (String arcId : ruta.arcIds) {
                deltaCapacidad.merge(arcId, -cantidad, (a, b) -> (a + b == 0) ? null : a + b);
            }
            
            // Actualizar contadores
            pedidosAsignados--;
            cantidadTotalAsignada -= cantidad;
            asignaciones.put(pedidoId, -1);
            fitnessCalculado = false;
        }
        
        public double getFitness() {
            if (!fitnessCalculado) {
                calcularFitness();
            }
            return fitness;
        }
        
        private void calcularFitness() {
            if (pedidosAsignados == 0) {
                fitness = 0.0;
                fitnessCalculado = true;
                return;
            }
            
            // 1. Componente principal: número de pedidos asignados (prioridad máxima)
            double fitnessPedidos = pedidosAsignados * 1_000_000.0;
            
            // 2. Componente secundaria: cantidad total asignada
            double fitnessCantidad = cantidadTotalAsignada * 1_000.0;
            
            // 3. Componente terciaria: penalización por ETA y saltos
            double fitnessTiempo = 0.0;
            double fitnessHops = 0.0;
            
            for (Map.Entry<Integer, Integer> entry : asignaciones.entrySet()) {
                int pedidoId = entry.getKey();
                int indiceRuta = entry.getValue();
                
                if (indiceRuta == -1) continue;
                
                List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
                if (candidatas == null || indiceRuta >= candidatas.size()) continue;
                
                CandidateRoute ruta = candidatas.get(indiceRuta);
                
                // Penalización por tiempo de llegada (ETA más temprano es mejor)
                long horasHastaETA = (long)(ruta.arrUTC.toLocalTime().getHour() + 
                                   ruta.arrUTC.toLocalTime().getMinute() / 60.0);
                fitnessTiempo += horasHastaETA;
                
                // Penalización por número de saltos (menos saltos es mejor)
                fitnessHops += ruta.hops;
            }
            
            // Normalizar componentes
            fitnessTiempo = (fitnessTiempo / pedidosAsignados) * 0.01; // PESO_ETA
            fitnessHops = (fitnessHops / pedidosAsignados) * 0.1;      // PESO_HOPS
            
            // Calcular fitness total (maximizar pedidos, cantidad; minimizar tiempo y saltos)
            fitness = fitnessPedidos + fitnessCantidad - fitnessTiempo - fitnessHops;
            fitnessCalculado = true;
        }
        
        public int getPedidosAsignados() {
            return pedidosAsignados;
        }
        
        public Map<Integer, Integer> getAsignaciones() {
            return new HashMap<>(asignaciones);
        }
        
        private void copiarDesde(Individuo otro) {
            this.asignaciones.clear();
            this.asignaciones.putAll(otro.asignaciones);
            this.deltaCapacidad.clear();
            this.deltaCapacidad.putAll(otro.deltaCapacidad);
            this.fitness = otro.fitness;
            this.fitnessCalculado = otro.fitnessCalculado;
            this.pedidosAsignados = otro.pedidosAsignados;
            this.cantidadTotalAsignada = otro.cantidadTotalAsignada;
        }
        
        public void aplicarBusquedaLocal() {
            // Implementación de búsqueda local
            // Seleccionar un pedido aleatorio y reasignar su ruta
            List<Integer> pedidos = new ArrayList<>(asignaciones.keySet());
            if (pedidos.isEmpty()) return;
            
            int pedidoId = pedidos.get(random.nextInt(pedidos.size()));
            int idxRuta = asignaciones.get(pedidoId);
            if (idxRuta == -1) return;
            
            List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
            if (candidatas == null || candidatas.isEmpty()) return;
            
            // Seleccionar una ruta aleatoria diferente a la actual
            int nuevoIdxRuta = random.nextInt(candidatas.size());
            while (nuevoIdxRuta == idxRuta) {
                nuevoIdxRuta = random.nextInt(candidatas.size());
            }
            
            // Reasignar la ruta
            eliminarAsignacion(pedidoId);
            asignarRuta(pedidoId, nuevoIdxRuta);
        }
    }
    
    // Método principal para ejecutar el algoritmo
    public void ejecutar() {
        // Inicializar población
        List<Individuo> poblacion = inicializarPoblacion();
        evaluarPoblacion(poblacion);
        
        // Mejor solución global
        Individuo mejorGlobal = obtenerMejorIndividuo(poblacion);
        
        // Bucle principal del algoritmo genético
        for (int generacion = 0; generacion < maxGeneraciones; generacion++) {
            // Crear nueva población
            List<Individuo> nuevaPoblacion = new ArrayList<>();
            
            // Elitismo: conservar al mejor individuo
            nuevaPoblacion.add(new Individuo(mejorGlobal));
            
            // Cruzar y mutar para crear nuevos individuos
            while (nuevaPoblacion.size() < tamanoPoblacion) {
                // Seleccionar padres
                Individuo padre1 = seleccionarPorTorneo(poblacion);
                Individuo padre2 = seleccionarPorTorneo(poblacion);
                
                // Cruzar
                if (random.nextDouble() < probCruce) {
                    List<Individuo> hijos = cruzar(padre1, padre2);
                    for (Individuo hijo : hijos) {
                        // Mutar
                        if (random.nextDouble() < probMutacion) {
                            mutar(hijo);
                        }
                        // Aplicar búsqueda local con cierta probabilidad
                        if (generacion % frecuenciaBusquedaLocal == 0 && random.nextDouble() < 0.1) {
                            hijo.aplicarBusquedaLocal();
                        }
                        nuevaPoblacion.add(hijo);
                        
                        // Mantener el tamaño de la población
                        if (nuevaPoblacion.size() >= tamanoPoblacion) break;
                    }
                }
            }
            
            // Reemplazar la población
            poblacion = nuevaPoblacion;
            evaluarPoblacion(poblacion);
            
            // Actualizar mejor global
            Individuo mejorGeneracion = obtenerMejorIndividuo(poblacion);
            if (mejorGeneracion.getFitness() > mejorGlobal.getFitness()) {
                mejorGlobal = new Individuo(mejorGeneracion);
            }
            
            // Mostrar progreso cada 10 generaciones
            if (generacion % 10 == 0) {
                System.out.printf("Generación %d - Mejor fitness: %.2f%n", 
                    generacion, mejorGlobal.getFitness());
            }
        }
        
        // Aplicar la mejor solución encontrada
        aplicarMejorSolucion(mejorGlobal);
        
        // Mostrar estadísticas detalladas
        System.out.println("\n=== SOLUCIÓN FINAL ENCONTRADA ===");
        System.out.printf("Fitness: %.2f%n", mejorGlobal.getFitness());
        mostrarEstadisticas(mejorGlobal);
        
        // Mostrar asignaciones detalladas
        System.out.println("\n=== DETALLE DE ASIGNACIONES ===");
        for (Map.Entry<Integer, Integer> entry : mejorGlobal.asignaciones.entrySet()) {
            int pedidoId = entry.getKey();
            int idxRuta = entry.getValue();
            
            if (idxRuta != -1) {
                Pedido pedido = pedidosMap.get(pedidoId);
                CandidateRoute ruta = candidatasPorPedido.get(pedidoId).get(idxRuta);
                
                System.out.printf("Pedido %d (Cantidad: %d) (Fecha de creacion: %s) -> ",
                    pedidoId, pedido.getCantidad(), pedido.getFecha().format(fmt));
                System.out.printf("ETA: %s, Saltos: %d%n", 
                    ruta.arrUTC.toLocalTime().toString().substring(0, 5),
                    ruta.hops);
                
                // Mostrar ruta detallada
                System.out.print("  Ruta: \n");
                if (ruta.arcIds == null || ruta.arcIds.isEmpty()) {
                    // Para pedidos directos, mostrar ID del pedido y destino
                    System.out.printf("Pedido %d - %s (Directo)", pedidoId, pedido.getDestino());
                } else {
                    String lastAirport = "";
                    int waitCount = 0;
                    LocalDateTime waitTimeStart = null;
                    
                    for (String arcId : ruta.arcIds) {
                        TEGraph.Arc arco = grafo.arcsById.get(arcId);
                        if (arco != null) {
                            String from = arco.getFrom().getAeropuerto().getCodigo();
                            String to = arco.getTo().getAeropuerto().getCodigo();

                            if (waitTimeStart == null) {
                                waitTimeStart = arco.getFrom().getTimestampUTC();
                            }

                            if (from.equals(to)) {
                                // Es un arco de espera
                                waitCount++;
                                lastAirport = to;
                            } else {
                                // Mostrar espera acumulada si existe
                                if (waitCount > 0) {
                                    LocalDateTime dep = arco.getFrom().getTimestampUTC();
                                    System.out.printf("  %s (espera %d) (Inicio: %s | Fin: %s)%n", lastAirport, waitCount, waitTimeStart.format(fmt), dep.format(fmt));
                                    waitTimeStart = null;
                                    waitCount = 0;
                                }
                                System.out.print("  " + from + " -> ");
                                lastAirport = to;
                            }
                        }
                    }
                    
                    // Mostrar última espera si existe
                    if (waitCount > 0) {
                        System.out.printf("%s (espera %d) -> ", lastAirport, waitCount);
                    }
                    
                    // Mostrar último aeropuerto si hay arcos
                    if (!ruta.arcIds.isEmpty()) {
                        TEGraph.Arc lastArco = grafo.arcsById.get(ruta.arcIds.get(ruta.arcIds.size() - 1));
                        if (lastArco != null) {
                            String codigo = lastArco.getTo().getAeropuerto().getCodigo();
                            int capacidad = lastArco.getCapacity();
                            int ocupacion = capacidadGlobal.used(lastArco.getArcId());
                            LocalDateTime dep = lastArco.getFrom().getTimestampUTC();
                            LocalDateTime arr = lastArco.getTo().getTimestampUTC();



                            System.out.printf("%s (Inicio: %s | Fin: %s) (Capacidad: %s/%s)", codigo, dep.format(fmt), arr.format(fmt), ocupacion, capacidad);
                        }
                    }
                }
                
                System.out.println("\n" + "-".repeat(50));
            } else {
                // Solo mostrar el destino para pedidos no asignados
                Pedido pedido = pedidosMap.get(pedidoId);
                System.out.printf("\nPedido %d (Cantidad: %d) -> NO ASIGNADO A %s%n", 
                    pedidoId, 
                    pedido.getCantidad(),
                    pedido.getDestino());
            }
        }
    }
    
    // Métodos auxiliares que se implementarán
    private List<Individuo> inicializarPoblacion() {
        List<Individuo> poblacion = new ArrayList<>();
        
        // Verificar que hay pedidos con rutas candidatas
        if (pedidosMap.isEmpty() || candidatasPorPedido.isEmpty()) {
            throw new IllegalStateException("No hay pedidos o rutas candidatas para inicializar la población");
        }
        
        // Crear una lista de pedidos con al menos una ruta candidata
        List<Integer> pedidosValidos = new ArrayList<>();
        for (Map.Entry<Integer, List<CandidateRoute>> entry : candidatasPorPedido.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                pedidosValidos.add(entry.getKey());
            }
        }
        
        if (pedidosValidos.isEmpty()) {
            throw new IllegalStateException("No hay pedidos con rutas candidatas válidas");
        }
        
        // Crear población inicial
        for (int i = 0; i < tamanoPoblacion; i++) {
            Individuo individuo = new Individuo();
            
            // Asignar rutas aleatorias a algunos pedidos
            for (int pedidoId : pedidosValidos) {
                List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
                if (candidatas != null && !candidatas.isEmpty() && random.nextDouble() < 0.7) {
                    // 70% de probabilidad de asignar una ruta a este pedido
                    int idxRuta = random.nextInt(candidatas.size());
                    if (individuo.puedeAsignarRuta(pedidoId, idxRuta)) {
                        individuo.asignarRuta(pedidoId, idxRuta);
                    }
                }
            }
            
            // Asegurarse de que el individuo tenga al menos una ruta asignada
            if (individuo.getPedidosAsignados() == 0 && !pedidosValidos.isEmpty()) {
                // Si no se asignó ninguna ruta, forzar la asignación de al menos una
                int pedidoId = pedidosValidos.get(random.nextInt(pedidosValidos.size()));
                List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
                if (candidatas != null && !candidatas.isEmpty()) {
                    int idxRuta = random.nextInt(candidatas.size());
                    if (individuo.puedeAsignarRuta(pedidoId, idxRuta)) {
                        individuo.asignarRuta(pedidoId, idxRuta);
                    }
                }
            }
            
            poblacion.add(individuo);
        }
        
        return poblacion;
    }
    
    private void evaluarPoblacion(List<Individuo> poblacion) {
        // Evaluar la población
        for (Individuo individuo : poblacion) {
            individuo.getFitness();
        }
    }
    
    private Individuo obtenerMejorIndividuo(List<Individuo> poblacion) {
        if (poblacion == null || poblacion.isEmpty()) {
            throw new IllegalStateException("No se puede obtener el mejor individuo de una población vacía");
        }
        
        Individuo mejor = poblacion.get(0);
        for (int i = 1; i < poblacion.size(); i++) {
            if (poblacion.get(i).getFitness() > mejor.getFitness()) {
                mejor = poblacion.get(i);
            }
        }
        return mejor;
    }
    
    private Individuo seleccionarPorTorneo(List<Individuo> poblacion) {
        // Selección por torneo
        List<Individuo> torneo = new ArrayList<>();
        for (int i = 0; i < tamanoTorneo; i++) {
            torneo.add(poblacion.get(random.nextInt(poblacion.size())));
        }
        
        Individuo mejor = torneo.get(0);
        for (int i = 1; i < torneo.size(); i++) {
            if (torneo.get(i).getFitness() > mejor.getFitness()) {
                mejor = torneo.get(i);
            }
        }
        return mejor;
    }
    
    private List<Individuo> cruzar(Individuo padre1, Individuo padre2) {
        // Cruzar
        List<Individuo> hijos = new ArrayList<>();
        
        // Seleccionar un punto de corte aleatorio
        int puntoCorte = random.nextInt(pedidosMap.size());
        
        // Crear hijos
        Individuo hijo1 = new Individuo();
        Individuo hijo2 = new Individuo();
        
        // Asignar rutas de los padres a los hijos
        for (int pedidoId : pedidosMap.keySet()) {
            if (pedidoId < puntoCorte) {
                hijo1.asignarRuta(pedidoId, padre1.asignaciones.get(pedidoId));
                hijo2.asignarRuta(pedidoId, padre2.asignaciones.get(pedidoId));
            } else {
                hijo1.asignarRuta(pedidoId, padre2.asignaciones.get(pedidoId));
                hijo2.asignarRuta(pedidoId, padre1.asignaciones.get(pedidoId));
            }
        }
        
        hijos.add(hijo1);
        hijos.add(hijo2);
        
        return hijos;
    }
    
    private void mutar(Individuo individuo) {
        // Mutar
        // Seleccionar un pedido aleatorio y reasignar su ruta
        List<Integer> pedidos = new ArrayList<>(individuo.asignaciones.keySet());
        if (pedidos.isEmpty()) return;
        
        int pedidoId = pedidos.get(random.nextInt(pedidos.size()));
        int idxRuta = individuo.asignaciones.get(pedidoId);
        if (idxRuta == -1) return;
        
        List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
        if (candidatas == null || candidatas.isEmpty()) return;
        
        // Seleccionar una ruta aleatoria diferente a la actual
        int nuevoIdxRuta = random.nextInt(candidatas.size());
        while (nuevoIdxRuta == idxRuta) {
            nuevoIdxRuta = random.nextInt(candidatas.size());
        }
        
        // Reasignar la ruta
        individuo.eliminarAsignacion(pedidoId);
        individuo.asignarRuta(pedidoId, nuevoIdxRuta);
    }
    
    private void aplicarMejorSolucion(Individuo mejor) {
        // Aplicar la mejor solución al CapacityBook global
        for (Map.Entry<Integer, Integer> entry : mejor.asignaciones.entrySet()) {
            int pedidoId = entry.getKey();
            int idxRuta = entry.getValue();
            
            if (idxRuta == -1) continue;
            
            List<CandidateRoute> candidatas = candidatasPorPedido.get(pedidoId);
            if (candidatas == null || idxRuta >= candidatas.size()) continue;
            
            CandidateRoute ruta = candidatas.get(idxRuta);
            int cantidad = pedidosMap.get(pedidoId).getCantidad();
            
            // Actualizar capacidad global usando el método reserve
            for (String arcId : ruta.arcIds) {
                TEGraph.Arc arco = grafo.arcsById.get(arcId);
                if (arco != null) {
                    capacidadGlobal.reserve(arco, cantidad);
                }
            }
        }
    }
    
    private void mostrarEstadisticas(Individuo mejor) {
        System.out.println("\n=== ESTADÍSTICAS DE LA SOLUCIÓN ===");
        
        int totalPedidos = pedidosMap.size();
        int pedidosAsignados = 0;
        int cantidadTotalAsignada = 0;
        double tiempoPromedio = 0;
        int saltosPromedio = 0;
        
        // Contar pedidos asignados y recolectar estadísticas
        for (Map.Entry<Integer, Integer> entry : mejor.asignaciones.entrySet()) {
            int pedidoId = entry.getKey();
            int idxRuta = entry.getValue();
            
            if (idxRuta != -1) {
                pedidosAsignados++;
                Pedido pedido = pedidosMap.get(pedidoId);
                cantidadTotalAsignada += pedido.getCantidad();
                
                CandidateRoute ruta = candidatasPorPedido.get(pedidoId).get(idxRuta);
                tiempoPromedio += ruta.arrUTC.toLocalTime().getHour() + 
                                ruta.arrUTC.toLocalTime().getMinute() / 60.0;
                saltosPromedio += ruta.hops;
                
                // Mostrar ruta detallada para cada pedido
                System.out.printf("\nPedido %d (Cantidad: %d) -> ", pedidoId, pedido.getCantidad());
                System.out.printf("ETA: %s, Saltos: %d%n", 
                    ruta.arrUTC.toLocalTime().toString().substring(0, 5),
                    ruta.hops);
                
                // Mostrar ruta detallada
                System.out.print("  Ruta: ");
                if (ruta.arcIds == null || ruta.arcIds.isEmpty()) {
                    // Para pedidos directos, mostrar ID del pedido y destino
                    System.out.printf("%s (Directo)",  pedido.getDestino());
                } else {
                    String lastAirport = "";
                    int waitCount = 0;
                    
                    for (String arcId : ruta.arcIds) {
                        TEGraph.Arc arco = grafo.arcsById.get(arcId);
                        if (arco != null) {
                            String from = arco.getFrom().getAeropuerto().getCodigo();
                            String to = arco.getTo().getAeropuerto().getCodigo();
                            
                            if (from.equals(to)) {
                                // Es un arco de espera
                                waitCount++;
                                lastAirport = to;
                            } else {
                                // Mostrar espera acumulada si existe
                                if (waitCount > 0) {
                                    System.out.printf("%s (espera %d) -> ", lastAirport, waitCount);
                                    waitCount = 0;
                                }
                                System.out.print(from + " -> ");
                                lastAirport = to;
                            }
                        }
                    }
                    
                    // Mostrar última espera si existe
                    if (waitCount > 0) {
                        System.out.printf("%s (espera %d) -> ", lastAirport, waitCount);
                    }
                    
                    // Mostrar último aeropuerto si hay arcos
                    if (!ruta.arcIds.isEmpty()) {
                        TEGraph.Arc lastArco = grafo.arcsById.get(ruta.arcIds.get(ruta.arcIds.size() - 1));
                        if (lastArco != null) {
                            System.out.print(lastArco.getTo().getAeropuerto().getCodigo());
                        }
                    }
                }
                
                System.out.println("\n" + "-".repeat(50));
            } else {
                // Solo mostrar el destino para pedidos no asignados
                Pedido pedido = pedidosMap.get(pedidoId);
                System.out.printf("\nPedido %d (Cantidad: %d) -> NO ASIGNADO A %s%n", 
                    pedidoId, 
                    pedido.getCantidad(),
                    pedido.getDestino());
            }
        }
        
        // Calcular y mostrar promedios
        if (pedidosAsignados > 0) {
            tiempoPromedio /= pedidosAsignados;
            saltosPromedio = (int) Math.ceil((double) saltosPromedio / pedidosAsignados);
            
            System.out.printf("\n=== RESUMEN ===\n");
            System.out.printf("Total de pedidos: %d%n", totalPedidos);
            System.out.printf("Pedidos asignados: %d (%.1f%%)%n", 
                pedidosAsignados, (pedidosAsignados * 100.0) / totalPedidos);
            System.out.printf("Cantidad total asignada: %d%n", cantidadTotalAsignada);
            System.out.printf("Tiempo promedio de entrega: %.2f horas%n", tiempoPromedio);
            System.out.printf("Número promedio de saltos: %d%n", saltosPromedio);
        }
        
        // Mostrar capacidad utilizada
        if (!mejor.deltaCapacidad.isEmpty()) {
            System.out.println("\nCapacidad utilizada por arco:");
            for (Map.Entry<String, Integer> entry : mejor.deltaCapacidad.entrySet()) {
                TEGraph.Arc arco = grafo.arcsById.get(entry.getKey());
                if (arco != null) {
                    int capacidadTotal = arco.getCapacity();
                    int capacidadUsada = entry.getValue();
                    double porcentaje = (capacidadUsada * 100.0) / capacidadTotal;
                    System.out.printf("Arco %s -> %s: %d/%d (%.1f%%)%n", 
                        arco.getFrom().getAeropuerto().getCodigo(),
                        arco.getTo().getAeropuerto().getCodigo(),
                        capacidadUsada, capacidadTotal, porcentaje);
                }
            }
        }
    }
}
