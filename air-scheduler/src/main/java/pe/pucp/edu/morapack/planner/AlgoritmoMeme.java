package pe.pucp.edu.morapack.planner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AlgoritmoMeme {
    // Parámetros ajustados
    private static final int TAMANO_POBLACION = 100;
    private static final int MAX_GENERACIONES = 500;
    private static final double PROB_MUTACION_INICIAL = 0.7;  // Aumentado para más exploración
    private static final double PROB_MUTACION_MINIMA = 0.2;
    private static final int TAMANO_TORNEO = 5;
    private static final double PROB_BUSQUEDA_LOCAL = 0.2;
    private static final int GENERACIONES_SIN_MEJORA = 20;  // Reducido para reiniciar más frecuentemente
    private static final double UMBRAL_DIVERSIDAD_BAJA = 0.2;  // Aumentado para detectar antes la pérdida de diversidad
    private static final int PORCENTAJE_ELITISISMO = 10;  // Porcentaje de los mejores individuos a preservar
    
    private final TEGraph grafo;
    private final Map<Integer, Pedido> pedidos;
    private final String aeropuertoOrigen;
    private final Random random = new Random();
    
    public AlgoritmoMeme(TEGraph grafo, Map<Integer, Pedido> pedidos, String aeropuertoOrigen) {
        this.grafo = grafo;
        this.pedidos = new HashMap<>(pedidos); // Hacemos una copia defensiva
        this.aeropuertoOrigen = aeropuertoOrigen;
        
        System.out.println("\n=== INICIALIZACIÓN ALGORITMO MEMÉTICO ===");
        System.out.println("Aeropuerto de origen: " + aeropuertoOrigen);
        System.out.println("Número de pedidos: " + this.pedidos.size());
        
        if (this.pedidos.isEmpty()) {
            System.err.println("ERROR: No se han proporcionado pedidos para el algoritmo");
        }
        
        // Verificar que los pedidos tengan destinos válidos
        int destinosInvalidos = 0;
        for (Map.Entry<Integer, Pedido> entry : this.pedidos.entrySet()) {
            Pedido p = entry.getValue();
            if (p.getDestino() == null || p.getDestino().trim().isEmpty()) {
                System.err.println("ERROR: Pedido " + entry.getKey() + " tiene destino nulo o vacío");
                destinosInvalidos++;
            }
        }
        
        if (destinosInvalidos > 0) {
            System.err.println("ADVERTENCIA: " + destinosInvalidos + " pedidos tienen destinos inválidos");
        }
    }
    
    public Plan ejecutar() {
        System.out.println("Iniciando algoritmo memético mejorado...");
        System.out.println("Tamaño de población: " + TAMANO_POBLACION);
        System.out.println("Número máximo de generaciones: " + MAX_GENERACIONES);
        
        List<Plan> poblacion = inicializarPoblacion();
        if (poblacion.isEmpty()) {
            System.out.println("Error: No se pudo inicializar la población. Verifica los datos de entrada.");
            return null;
        }
        
        Plan mejorPlan = null;
        double mejorFitness = -1;
        int generacionSinMejora = 0;
        double[] fitnessHistorico = new double[20];  // Increased from 10 to 20 for better trend detection
        Arrays.fill(fitnessHistorico, -1);
        
        // Bucle principal de evolución
        for (int generacion = 0; generacion < MAX_GENERACIONES; generacion++) {
            // Mostrar progreso cada 5 generaciones
            if (generacion % 5 == 0) {
                double diversidad = calcularDiversidad(poblacion);
                System.out.printf("\rGeneración %d/%d - Mejor fitness: %.6f - Diversidad: %.4f - Pob: %d", 
                    generacion, MAX_GENERACIONES, mejorFitness, diversidad, poblacion.size());
                
                // Si la diversidad es muy baja, inyectar diversidad
                if (diversidad < UMBRAL_DIVERSIDAD_BAJA) {
                    System.out.println("\n¡Diversidad baja detectada! Inyectando diversidad...");
                    inyectarDiversidad(poblacion, 0.3); // Reemplazar 30% de la población
                }
            }
            
            // Evaluar población
            double[] fitnessPoblacion = evaluarPoblacion(poblacion);
            
            // Actualizar mejor solución
            int mejorIndice = encontrarMejorIndice(fitnessPoblacion);
            if (fitnessPoblacion[mejorIndice] > mejorFitness) {
                mejorFitness = fitnessPoblacion[mejorIndice];
                mejorPlan = poblacion.get(mejorIndice).clonar();
                generacionSinMejora = 0;
                
                System.out.printf("\nMejora en generación %d: %.6f\n", generacion, mejorFitness);
            } else {
                generacionSinMejora++;
            }
            
            // Actualizar historial de fitness solo si hay una mejora
            if (fitnessHistorico[fitnessHistorico.length-1] < mejorFitness) {
                actualizarHistorialFitness(fitnessHistorico, mejorFitness);
            }
            
            // Verificar convergencia o estancamiento
            boolean convergencia = haConvergido(fitnessHistorico);
            
            if (convergencia || generacionSinMejora >= GENERACIONES_SIN_MEJORA) {
                if (convergencia) {
                    System.out.printf("\nConvergencia detectada (fitness: %.6f). ", mejorFitness);
                } else {
                    System.out.printf("\nSin mejora en %d generaciones. ", GENERACIONES_SIN_MEJORA);
                }
                
                // Solo reiniciar si el fitness es bajo
                if (mejorFitness < 0.1) {
                    System.out.println("Reiniciando población...");
                    poblacion = reiniciarPoblacion(poblacion, mejorPlan);
                    generacionSinMejora = 0;
                    // Limpiar el historial después del reinicio
                    Arrays.fill(fitnessHistorico, -1);
                    fitnessHistorico[fitnessHistorico.length-1] = mejorFitness;
                } else {
                    System.out.println("Solución aceptable encontrada.");
                    break;
                }
            }
            
            // Crear nueva generación
            List<Plan> nuevaGeneracion = siguienteGeneracion(poblacion);
            
            poblacion = nuevaGeneracion;
        }
        
        System.out.println("\n\n=== RESULTADOS FINALES ===");
        System.out.println("Mejor fitness encontrado: " + String.format("%.6f", mejorFitness));
        
        return mejorPlan;
    }
    
    private List<Plan> siguienteGeneracion(List<Plan> poblacion) {
        List<Plan> nuevaGeneracion = new ArrayList<>();
        
        // Ordenar la población por fitness
        poblacion.sort((p1, p2) -> Double.compare(p2.getFitness(), p1.getFitness()));
        
        // Aplicar elitismo: preservar los mejores individuos
        int numElitismo = (poblacion.size() * PORCENTAJE_ELITISISMO) / 100;
        for (int i = 0; i < numElitismo; i++) {
            if (i < poblacion.size()) {
                nuevaGeneracion.add(new Plan(poblacion.get(i)));
            }
        }
        
        // Completar la nueva generación con cruces y mutaciones
        while (nuevaGeneracion.size() < TAMANO_POBLACION) {
            // Seleccionar padres por torneo
            Plan padre1 = seleccionarPorTorneo(poblacion);
            Plan padre2 = seleccionarPorTorneo(poblacion);
            
            // Cruzar los padres para obtener un hijo
            Plan hijo = cruzar(padre1, padre2);
            
            // Aplicar mutación con probabilidad adaptativa
            double probMutacion = PROB_MUTACION_MINIMA + 
                (PROB_MUTACION_INICIAL - PROB_MUTACION_MINIMA) * 
                (1.0 - (double)nuevaGeneracion.size() / TAMANO_POBLACION);
                
            if (random.nextDouble() < probMutacion) {
                mutar(hijo);
            }
            
            // Aplicar búsqueda local con cierta probabilidad
            if (random.nextDouble() < PROB_BUSQUEDA_LOCAL) {
                busquedaLocal(hijo);
            }
            
            nuevaGeneracion.add(hijo);
        }
        
        return nuevaGeneracion;
    }
    
    private Plan seleccionarPorTorneo(List<Plan> poblacion) {
        Plan mejor = null;
        
        // Seleccionar k individuos aleatorios y quedarse con el mejor
        for (int i = 0; i < TAMANO_TORNEO; i++) {
            Plan candidato = poblacion.get(random.nextInt(poblacion.size()));
            if (mejor == null || candidato.getFitness() > mejor.getFitness()) {
                mejor = candidato;
            }
        }
        
        return new Plan(mejor);  // Devolver una copia
    }
    
    private void inyectarDiversidad(List<Plan> poblacion, double porcentaje) {
        if (porcentaje <= 0 || porcentaje > 1) return;
        
        // Ordenar la población por fitness (ascendente)
        List<Plan> poblacionOrdenada = new ArrayList<>(poblacion);
        poblacionOrdenada.sort(Comparator.comparingDouble(Plan::getFitness));
        
        // Reemplazar los peores individuos con nuevas soluciones aleatorias
        int numReemplazos = (int)(poblacion.size() * porcentaje);
        
        for (int i = 0; i < numReemplazos; i++) {
            int idx = poblacion.indexOf(poblacionOrdenada.get(i));
            if (idx != -1) {
                Plan nuevo = generarSolucionAleatoria();
                if (nuevo != null) {
                    poblacion.set(idx, nuevo);
                }
            }
        }
        
        // Forzar diversidad adicional si es necesario
        if (calcularDiversidad(poblacion) < UMBRAL_DIVERSIDAD_BAJA * 0.5) {
            // Si la diversidad es muy baja, reemplazar más individuos
            inyectarDiversidad(poblacion, porcentaje * 1.5);
        }
    }
    
    private double calcularDiversidad(List<Plan> poblacion) {
        if (poblacion.size() <= 1) return 1.0;
        
        int numMuestras = Math.min(100, poblacion.size() * (poblacion.size() - 1) / 2);
        double similitudTotal = 0.0;
        int contador = 0;
        
        for (int i = 0; i < numMuestras; i++) {
            int idx1 = random.nextInt(poblacion.size());
            int idx2;
            do {
                idx2 = random.nextInt(poblacion.size());
            } while (idx1 == idx2);
            
            similitudTotal += calcularSimilitud(poblacion.get(idx1), poblacion.get(idx2));
            contador++;
        }
        
        double similitudPromedio = similitudTotal / contador;
        return 1.0 - similitudPromedio; // Convert similarity to diversity
    }
    
    private double calcularSimilitud(Plan plan1, Plan plan2) {
        // Contar cuántas rutas son idénticas
        int rutasIdenticas = 0;
        int totalRutas = 0;
        
        // Comparar rutas de pedidos comunes
        for (Integer idPedido : plan1.getAsignaciones().keySet()) {
            if (plan2.getAsignaciones().containsKey(idPedido)) {
                List<RutaPedido> rutas1 = plan1.getAsignaciones().get(idPedido);
                List<RutaPedido> rutas2 = plan2.getAsignaciones().get(idPedido);
                
                if (!rutas1.isEmpty() && !rutas2.isEmpty()) {
                    // Comparar las rutas del primer pedido
                    if (sonRutasIguales(rutas1.get(0), rutas2.get(0))) {
                        rutasIdenticas++;
                    }
                    totalRutas++;
                }
            }
        }
        
        // Asegurar que no haya división por cero
        return totalRutas > 0 ? (double)rutasIdenticas / totalRutas : 0.0;
    }
    
    private boolean sonRutasIguales(RutaPedido r1, RutaPedido r2) {
        List<Vuelo> vuelos1 = r1.getVuelos();
        List<Vuelo> vuelos2 = r2.getVuelos();
        
        if (vuelos1.size() != vuelos2.size()) return false;
        
        for (int i = 0; i < vuelos1.size(); i++) {
            // Changed from equals() to == since IDs are integers
            if (vuelos1.get(i).getId() != vuelos2.get(i).getId()) {
                return false;
            }
        }
        
        return true;
    }
    
    // Initialize population with random solutions
    private List<Plan> inicializarPoblacion() {
        List<Plan> poblacion = new ArrayList<>();
        for (int i = 0; i < TAMANO_POBLACION; i++) {
            Plan plan = generarSolucionAleatoria();
            if (plan != null) {
                poblacion.add(plan);
            }
        }
        return poblacion;
    }

    // Evaluate population and return fitness array
    private double[] evaluarPoblacion(List<Plan> poblacion) {
        return poblacion.stream()
                .mapToDouble(Plan::getFitness)
                .toArray();
    }

    // Find index of best solution in population
    private int encontrarMejorIndice(double[] fitnessPoblacion) {
        int mejorIndice = 0;
        for (int i = 1; i < fitnessPoblacion.length; i++) {
            if (fitnessPoblacion[i] > fitnessPoblacion[mejorIndice]) {
                mejorIndice = i;
            }
        }
        return mejorIndice;
    }

    // Update fitness history with new best fitness
    private void actualizarHistorialFitness(double[] historial, double nuevoFitness) {
        // Shift array left
        System.arraycopy(historial, 1, historial, 0, historial.length - 1);
        // Add new fitness at the end
        historial[historial.length - 1] = nuevoFitness;
    }

    // Check if population has converged
    private boolean haConvergido(double[] historialFitness) {
        if (historialFitness[0] < 0) return false; // Not enough data
        
        // Calculate average of first and second half
        int mid = historialFitness.length / 2;
        double sumFirstHalf = 0, sumSecondHalf = 0;
        int countFirst = 0, countSecond = 0;
        
        for (int i = 0; i < historialFitness.length; i++) {
            if (historialFitness[i] < 0) continue;
            
            if (i < mid) {
                sumFirstHalf += historialFitness[i];
                countFirst++;
            } else {
                sumSecondHalf += historialFitness[i];
                countSecond++;
            }
        }
        
        if (countFirst == 0 || countSecond == 0) return false;
        
        double avgFirst = sumFirstHalf / countFirst;
        double avgSecond = sumSecondHalf / countSecond;
        
        // Consider converged if improvement is less than 1%
        return Math.abs(avgSecond - avgFirst) / (avgFirst + 1e-10) < 0.01;
    }

    // Restart population while keeping best solution
    private List<Plan> reiniciarPoblacion(List<Plan> poblacionActual, Plan mejorPlan) {
        List<Plan> nuevaPoblacion = new ArrayList<>();
        
        // Keep the best solution
        if (mejorPlan != null) {
            nuevaPoblacion.add(new Plan(mejorPlan));
        }
        
        // Generate new random solutions
        while (nuevaPoblacion.size() < TAMANO_POBLACION) {
            Plan nuevo = generarSolucionAleatoria();
            if (nuevo != null) {
                nuevaPoblacion.add(nuevo);
            }
        }
        
        return nuevaPoblacion;
    }

    // Generate a random solution with up to 2 connections
    private Plan generarSolucionAleatoria() {
        Plan plan = new Plan();
        plan.setGrafo(grafo);
        plan.setPedidos(new HashMap<>(pedidos)); // Asegurarse de que el plan tenga los pedidos
        
        // Mapa para rastrear la capacidad utilizada en cada vuelo
        Map<Integer, Integer> capacidadUtilizada = new HashMap<>();
        
        // Contadores para estadísticas
        int pedidosAsignados = 0;
        int pedidosNoAsignados = 0;
        
        // For each order, try to find a valid route
        for (Map.Entry<Integer, Pedido> entry : pedidos.entrySet()) {
            int idPedido = entry.getKey();
            Pedido pedido = entry.getValue();
            
            // Comentado para reducir salida en consola
            // System.out.printf("\nProcesando pedido %d: %s -> %s (cantidad: %d, deadline: %s)%n",
            //     idPedido, aeropuertoOrigen, pedido.getDestino(), 
            //     pedido.getCantidad(), pedido.getDeadline());
            
            // Try to find a route with 0, 1, or 2 connections
            List<Vuelo> mejorRuta = null;
            int maxIntentos = 3; // Número máximo de intentos para encontrar una ruta
            
            for (int intento = 0; intento < maxIntentos && mejorRuta == null; intento++) {
                mejorRuta = encontrarRutaAleatoria(
                    aeropuertoOrigen, 
                    pedido.getDestino(), 
                    pedido.getDeadline(), 
                    new HashSet<>(), 
                    0, 
                    2
                );
                
                if (mejorRuta != null) {
                    // Comentado para reducir salida en consola
                    // System.out.printf("  Intento %d: Ruta encontrada con %d vuelos%n", 
                    //                 intento + 1, mejorRuta.size());
                    
                    // Verificar capacidad
                    if (verificarCapacidad(mejorRuta, capacidadUtilizada, pedido.getCantidad())) {
                        // Comentado para reducir salida en consola
                        // System.out.println("  Capacidad verificada para la ruta");
                    } else {
                        // Comentado para reducir salida en consola
                        // System.out.println("  No hay capacidad disponible en la ruta");
                        mejorRuta = null; // Intentar otra ruta
                    }
                } else {
                    // Comentado para reducir salida en consola
                    // System.out.printf("  Intento %d: No se encontró ruta%n", intento + 1);
                }
            }
            
            if (mejorRuta != null && !mejorRuta.isEmpty()) {
                RutaPedido rutaPedido = new RutaPedido(pedido, mejorRuta);
                plan.agregarRuta(rutaPedido);
                actualizarCapacidad(mejorRuta, capacidadUtilizada, pedido.getCantidad());
                pedidosAsignados++;
                // Comentado para reducir salida en consola
                // System.out.println("  ✅ Pedido asignado exitosamente");
            } else {
                pedidosNoAsignados++;
                // Comentado para reducir salida en consola
                // System.out.println("  ❌ No se pudo asignar el pedido");
            }
        }
        
        // Comentado para reducir salida en consola
        // System.out.printf("\nResumen de asignación: %d asignados, %d no asignados%n", 
        //                  pedidosAsignados, pedidosNoAsignados);
        
        plan.evaluar();
        return plan;
    }
    
    // Verificar si hay capacidad disponible en todos los vuelos de la ruta
    private boolean verificarCapacidad(List<Vuelo> ruta, Map<Integer, Integer> capacidadUtilizada, int cantidad) {
        for (Vuelo vuelo : ruta) {
            int capacidadDisponible = vuelo.getCapacidad() - capacidadUtilizada.getOrDefault(vuelo.getId(), 0);
            if (capacidadDisponible < cantidad) {
                return false;
            }
        }
        return true;
    }
    
    // Actualizar la capacidad utilizada en los vuelos
    private void actualizarCapacidad(List<Vuelo> ruta, Map<Integer, Integer> capacidadUtilizada, int cantidad) {
        for (Vuelo vuelo : ruta) {
            int capacidadActual = capacidadUtilizada.getOrDefault(vuelo.getId(), 0);
            capacidadUtilizada.put(vuelo.getId(), capacidadActual + cantidad);
        }
    }

    // Método auxiliar recursivo para encontrar una ruta con hasta maxEscalas escalas
    private List<Vuelo> encontrarRutaAleatoria(String origen, String destino, 
                                             LocalDateTime deadline, 
                                             Set<String> visitados, 
                                             int escalasActuales, 
                                             int maxEscalas) {
        // Evitar ciclos
        if (visitados.contains(origen)) {
            return null;
        }
        
        // Si hemos alcanzado el destino, retornar ruta vacía
        if (origen.equals(destino)) {
            return new ArrayList<>();
        }
        
        // Si hemos alcanzado el máximo de escalas, buscar solo vuelos directos
        if (escalasActuales >= maxEscalas) {
            List<Vuelo> vuelosDirectos = grafo.obtenerVuelosSalientes(origen).stream()
                .filter(v -> v.getDestino().equals(destino))
                .filter(v -> {
                    LocalDateTime horaVuelo = LocalDateTime.of(deadline.toLocalDate(), v.getHoraOrigen());
                    return !horaVuelo.isAfter(deadline);
                })
                .collect(Collectors.toList());
                
            if (!vuelosDirectos.isEmpty()) {
                return Collections.singletonList(vuelosDirectos.get(random.nextInt(vuelosDirectos.size())));
            }
            return null;
        }
        
        // Obtener vuelos salientes del origen actual
        List<Vuelo> vuelosSalientes = grafo.obtenerVuelosSalientes(origen);
        if (vuelosSalientes == null || vuelosSalientes.isEmpty()) {
            return null;
        }
        
        // Mezclar los vuelos para añadir aleatoriedad
        Collections.shuffle(vuelosSalientes);
        
        // Limitar el número de intentos para evitar búsquedas muy largas
        int maxIntentos = Math.min(5, vuelosSalientes.size());
        
        for (int i = 0; i < maxIntentos; i++) {
            Vuelo vuelo = vuelosSalientes.get(i);
            
            // Verificar restricciones de tiempo
            LocalDateTime horaSalida = LocalDateTime.of(deadline.toLocalDate(), vuelo.getHoraOrigen());
            if (horaSalida.isAfter(deadline)) {
                continue;
            }
            
            // Si encontramos un vuelo directo al destino, lo tomamos
            if (vuelo.getDestino().equals(destino)) {
                return Collections.singletonList(vuelo);
            }
            
            // Si no, buscamos recursivamente desde el aeropuerto de llegada
            Set<String> nuevosVisitados = new HashSet<>(visitados);
            nuevosVisitados.add(origen);
            
            List<Vuelo> subRuta = encontrarRutaAleatoria(
                vuelo.getDestino(), 
                destino, 
                deadline,
                nuevosVisitados, 
                escalasActuales + 1, 
                maxEscalas
            );
                
            if (subRuta != null) {
                List<Vuelo> rutaCompleta = new ArrayList<>();
                rutaCompleta.add(vuelo);
                rutaCompleta.addAll(subRuta);
                
                // Verificar que la ruta completa no exceda el máximo de escalas
                if (rutaCompleta.size() - 1 <= maxEscalas) {
                    return rutaCompleta;
                }
            }
        }
        
        return null;
    }
    
    // Crossover two parents to produce a child
    private Plan cruzar(Plan padre1, Plan padre2) {
        Plan hijo = new Plan();
        hijo.setGrafo(grafo);
        
        // For each order, randomly select which parent's route to inherit
        for (Integer idPedido : pedidos.keySet()) {
            boolean usarPadre1 = random.nextBoolean();
            Plan padreElegido = usarPadre1 ? padre1 : padre2;
            
            List<RutaPedido> rutasPadre = padreElegido.getAsignaciones().get(idPedido);
            if (rutasPadre != null && !rutasPadre.isEmpty()) {
                // Create a deep copy of the route
                RutaPedido rutaOriginal = rutasPadre.get(0);
                List<Vuelo> vuelosCopia = new ArrayList<>(rutaOriginal.getVuelos());
                Pedido pedido = pedidos.get(idPedido);
                RutaPedido rutaCopia = new RutaPedido(pedido, vuelosCopia);
                hijo.agregarRuta(rutaCopia);
            }
        }
        
        hijo.evaluar();
        return hijo;
    }

    // Mutate a solution
    private void mutar(Plan plan) {
        // With 50% probability, remove a random route
        if (random.nextDouble() < 0.5 && !plan.getAsignaciones().isEmpty()) {
            List<Integer> idsPedidos = new ArrayList<>(plan.getAsignaciones().keySet());
            int idAEliminar = idsPedidos.get(random.nextInt(idsPedidos.size()));
            plan.getAsignaciones().remove(idAEliminar);
        }
        
        // With 50% probability, add a new random route
        if (random.nextDouble() < 0.5) {
            // Select a random unassigned order
            List<Pedido> pedidosNoAsignados = pedidos.values().stream()
                .filter(p -> !plan.getAsignaciones().containsKey(p.getIdPedido()))
                .collect(Collectors.toList());
                
            if (!pedidosNoAsignados.isEmpty()) {
                Pedido pedido = pedidosNoAsignados.get(random.nextInt(pedidosNoAsignados.size()));
                
                // Buscar un vuelo directo al destino del pedido
                List<Vuelo> vuelosSalientes = grafo.obtenerVuelosSalientes(aeropuertoOrigen);
                for (Vuelo vuelo : vuelosSalientes) {
                    if (vuelo.getDestino().equals(pedido.getDestino())) {
                        // Check if flight time is before the deadline
                        LocalDateTime flightTime = LocalDateTime.of(pedido.getDeadline().toLocalDate(), vuelo.getHoraOrigen());
                        if (!flightTime.isAfter(pedido.getDeadline())) {
                            List<Vuelo> ruta = Collections.singletonList(vuelo);
                            RutaPedido rutaPedido = new RutaPedido(pedido, ruta);
                            plan.agregarRuta(rutaPedido);
                            break;
                        }
                    }
                }
            }
        }
        
        // Re-evaluate the plan after mutation
        plan.evaluar();
    }

    // Local search to improve a solution
    private void busquedaLocal(Plan plan) {
        // Simple local search: try to find better routes for each order
        for (Map.Entry<Integer, List<RutaPedido>> entry : new HashMap<>(plan.getAsignaciones()).entrySet()) {
            int idPedido = entry.getKey();
            Pedido pedido = pedidos.get(idPedido);
            
            // Find alternative route for this order
            List<Vuelo> mejorRuta = null;
            List<Vuelo> vuelosSalientes = grafo.obtenerVuelosSalientes(aeropuertoOrigen);
            
            // Simple approach: find the earliest flight to destination
            for (Vuelo vuelo : vuelosSalientes) {
                if (vuelo.getDestino().equals(pedido.getDestino())) {
                    // Check if flight time is before the deadline
                    LocalDateTime flightTime = LocalDateTime.of(pedido.getDeadline().toLocalDate(), vuelo.getHoraOrigen());
                    if (!flightTime.isAfter(pedido.getDeadline())) {
                        mejorRuta = Collections.singletonList(vuelo);
                        break;
                    }
                }
            }
            
            if (mejorRuta != null && !mejorRuta.isEmpty()) {
                // If found a better route, replace the current one
                plan.getAsignaciones().remove(idPedido);
                RutaPedido nuevaRuta = new RutaPedido(pedido, mejorRuta);
                plan.agregarRuta(nuevaRuta);
            }
        }
        
        // Re-evaluate the plan after local search
        plan.evaluar();
    }
}
