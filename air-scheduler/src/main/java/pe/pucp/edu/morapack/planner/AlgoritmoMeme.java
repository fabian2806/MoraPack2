package pe.pucp.edu.morapack.planner;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AlgoritmoMeme {
    // Parámetros del algoritmo
    private static final int TAMANO_POBLACION = 100;
    private static final int MAX_GENERACIONES = 1000;
    private static final double PROB_MUTACION = 0.1;
    private static final int TAMANO_TORNEO = 5;
    private static final double PROB_BUSQUEDA_LOCAL = 0.3;
    
    private final TEGraph grafo;
    private final Map<Integer, Pedido> pedidos;
    private final String aeropuertoOrigen;
    private final Random random = new Random();
    
    public AlgoritmoMeme(TEGraph grafo, Map<Integer, Pedido> pedidos, String aeropuertoOrigen) {
        this.grafo = grafo;
        this.pedidos = new HashMap<>(pedidos);
        this.aeropuertoOrigen = aeropuertoOrigen;
    }
    
    public Plan ejecutar() {
        System.out.println("Inicializando población...");
        List<Plan> poblacion = inicializarPoblacion();
        
        if (poblacion.isEmpty()) {
            System.out.println("No se pudo inicializar la población. Verifica los datos de entrada.");
            return null;
        }
        
        System.out.println("Población inicializada con " + poblacion.size() + " individuos");
        
        Plan mejorPlan = null;
        double mejorFitness = Double.NEGATIVE_INFINITY;
        
        // 2. Bucle evolutivo
        System.out.println("\nIniciando bucle evolutivo...");
        for (int generacion = 0; generacion < MAX_GENERACIONES; generacion++) {
            System.out.print("\rGeneración " + (generacion + 1) + "/" + MAX_GENERACIONES + " - Evaluando población...");
            
            // Evaluar población
            for (Plan plan : poblacion) {
                plan.evaluar();
                
                // Actualizar mejor plan
                if (plan.getFitness() > mejorFitness) {
                    mejorFitness = plan.getFitness();
                    mejorPlan = plan.clonar();
                    System.out.printf("\rNuevo mejor fitness: %.2f (Gen %d/%d)", mejorFitness, generacion + 1, MAX_GENERACIONES);
                }
            }
            
            // Selección
            System.out.print("\rGeneración " + (generacion + 1) + "/" + MAX_GENERACIONES + " - Seleccionando...");
            List<Plan> nuevaPoblacion = new ArrayList<>();
            
            // Elitismo: mantener el mejor plan
            if (mejorPlan != null) {
                nuevaPoblacion.add(mejorPlan.clonar());
            }
            
            // Llenar la nueva población con cruces
            System.out.print("\rGeneración " + (generacion + 1) + "/" + MAX_GENERACIONES + " - Cruzando...");
            while (nuevaPoblacion.size() < TAMANO_POBLACION) {
                Plan padre1 = seleccionarPorTorneo(poblacion);
                Plan padre2 = seleccionarPorTorneo(poblacion);
                
                Plan hijo = cruzar(padre1, padre2);
                
                // Mutación
                if (random.nextDouble() < PROB_MUTACION) {
                    mutar(hijo);
                }
                
                // Búsqueda local
                if (random.nextDouble() < PROB_BUSQUEDA_LOCAL) {
                    busquedaLocal(hijo);
                }
                
                nuevaPoblacion.add(hijo);
            }
            
            poblacion = nuevaPoblacion;
        }
        
        System.out.println("\nBúsqueda completada. Encontrando el mejor plan...");
        
        // Encontrar el mejor plan de la última generación
        for (Plan plan : poblacion) {
            plan.evaluar();
            if (mejorPlan == null || plan.getFitness() > mejorFitness) {
                mejorPlan = plan.clonar();
                mejorFitness = plan.getFitness();
            }
        }
        
        System.out.println("Mejor fitness encontrado: " + mejorFitness);
        return mejorPlan;
    }
    
    private List<Plan> inicializarPoblacion() {
        System.out.println("Inicializando población con tamaño: " + TAMANO_POBLACION);
        List<Plan> poblacion = new ArrayList<>();
        int intentos = 0;
        final int MAX_INTENTOS = TAMANO_POBLACION * 2; // Límite de intentos
        
        while (poblacion.size() < TAMANO_POBLACION && intentos < MAX_INTENTOS) {
            intentos++;
            Plan plan = new Plan();
            int pedidosProcesados = 0;
            int rutasGeneradas = 0;
            
            // Para cada pedido, generar una ruta aleatoria
            for (Pedido pedido : pedidos.values()) {
                System.out.print("\rGenerando ruta para pedido " + pedido.getIdPedido() + " (" + 
                              (poblacion.size() + 1) + "/" + TAMANO_POBLACION + ") - " + 
                              pedido.getDestino() + "...");
                
                List<Vuelo> ruta = generarRutaAleatoria(pedido);
                if (!ruta.isEmpty()) {
                    plan.agregarRuta(new RutaPedido(pedido, ruta));
                    rutasGeneradas++;
                }
                pedidosProcesados++;
            }
            
            // Si el plan no está vacío, agregarlo a la población
            if (!plan.getRutas().isEmpty()) {
                poblacion.add(plan);
                System.out.println("\rPlan " + poblacion.size() + " generado con " + 
                                 rutasGeneradas + " rutas de " + pedidosProcesados + " pedidos");
            }
        }
        
        if (poblacion.isEmpty()) {
            System.out.println("No se pudo generar ninguna solución inicial válida.");
        } else {
            System.out.println("Población inicial generada con " + poblacion.size() + " individuos");
        }
        
        return poblacion;
    }
    
    private List<Vuelo> generarRutaAleatoria(Pedido pedido) {
        List<Vuelo> ruta = new ArrayList<>();
        String origen = aeropuertoOrigen;
        String destino = pedido.getDestino();
        final int MAX_INTENTOS = 10; // Límite de intentos para encontrar una ruta
        int intentos = 0;
        
        // Verificar si el destino es un aeropuerto válido
        if (!grafo.existeAeropuerto(destino)) {
            return ruta; // Lista vacía
        }
        
        // 1. Intentar encontrar un vuelo directo
        List<Vuelo> vuelosDirectos = grafo.obtenerVuelosSalientes(origen).stream()
            .filter(v -> v.getDestino().equals(destino))
            .collect(Collectors.toList());
            
        if (!vuelosDirectos.isEmpty()) {
            Vuelo vuelo = vuelosDirectos.get(random.nextInt(vuelosDirectos.size()));
            ruta.add(vuelo);
            return ruta;
        }
        
        // 2. Intentar con una escala
        List<Vuelo> vuelosDesdeOrigen = grafo.obtenerVuelosSalientes(origen);
        if (vuelosDesdeOrigen.isEmpty()) {
            return ruta; // No hay vuelos desde el origen
        }
        
        // Mezclar los vuelos para aleatorizar la búsqueda
        Collections.shuffle(vuelosDesdeOrigen, random);
        
        // Limitar el número de intentos para evitar búsquedas muy largas
        int maxIntentos = Math.min(5, vuelosDesdeOrigen.size());
        
        for (Vuelo primerVuelo : vuelosDesdeOrigen) {
            if (intentos >= maxIntentos) break;
            intentos++;
            
            String escala = primerVuelo.getDestino();
            
            // Buscar vuelo desde la escala hasta el destino
            List<Vuelo> segundosVuelos = grafo.obtenerVuelosSalientes(escala).stream()
                .filter(v -> v.getDestino().equals(destino) && 
                           v.getHoraOrigen().isAfter(primerVuelo.getHoraDestino()))
                .collect(Collectors.toList());
                
            if (!segundosVuelos.isEmpty()) {
                Vuelo segundoVuelo = segundosVuelos.get(0);
                
                // Verificar tiempos de conexión (mínimo 30 minutos)
                Duration conexion = Duration.between(
                    primerVuelo.getHoraDestino(), 
                    segundoVuelo.getHoraOrigen()
                );
                
                if (conexion.toMinutes() >= 30) {
                    ruta.add(primerVuelo);
                    ruta.add(segundoVuelo);
                    return ruta;
                }
            }
        }
        
        return ruta; // Si no se encuentra ninguna ruta válida, devolver lista vacía
    }
    
    private Plan seleccionarPorTorneo(List<Plan> poblacion) {
        Plan mejor = null;
        
        for (int i = 0; i < TAMANO_TORNEO; i++) {
            Plan candidato = poblacion.get(random.nextInt(poblacion.size()));
            if (mejor == null || candidato.getFitness() < mejor.getFitness()) {
                mejor = candidato;
            }
        }
        
        return new Plan(mejor);
    }
    
    private Plan cruzar(Plan padre1, Plan padre2) {
        Plan hijo = new Plan();
        hijo.setGrafo(grafo);
        hijo.setPedidos(new HashMap<>(pedidos));
        
        Map<Integer, List<RutaPedido>> asignaciones = new HashMap<>();
        
        // Cruzar las asignaciones de los padres
        for (Map.Entry<Integer, List<RutaPedido>> entry : padre1.getAsignaciones().entrySet()) {
            int idPedido = entry.getKey();
            
            // Elegir aleatoriamente de cuál padre tomar las rutas
            List<RutaPedido> rutasPadre = random.nextBoolean() ? 
                entry.getValue() : 
                padre2.getAsignaciones().getOrDefault(idPedido, new ArrayList<>());
            
            if (!rutasPadre.isEmpty()) {
                List<RutaPedido> rutasHijo = new ArrayList<>();
                for (RutaPedido ruta : rutasPadre) {
                    rutasHijo.add(new RutaPedido(ruta));
                }
                asignaciones.put(idPedido, rutasHijo);
            }
        }
        
        hijo.setAsignaciones(asignaciones);
        return hijo;
    }
    
    private void mutar(Plan plan) {
        // Mutación: cambiar aleatoriamente algunas rutas
        for (List<RutaPedido> rutas : plan.getAsignaciones().values()) {
            if (!rutas.isEmpty() && random.nextDouble() < 0.1) {
                int idx = random.nextInt(rutas.size());
                RutaPedido ruta = rutas.get(idx);
                Pedido pedido = pedidos.get(ruta.getIdPedido());
                
                // Cambiar la ruta con cierta probabilidad
                if (random.nextDouble() < 0.5) {
                    List<Vuelo> nuevosVuelos = generarRutaAleatoria(pedido);
                    if (!nuevosVuelos.isEmpty()) {
                        rutas.set(idx, new RutaPedido(ruta.getIdPedido(), ruta.getFraccion(), nuevosVuelos));
                    }
                } 
                // O cambiar la fracción
                else {
                    double nuevaFraccion = Math.max(0.1, Math.min(0.9, ruta.getFraccion() + (random.nextDouble() * 0.4 - 0.2)));
                    ruta.setFraccion(nuevaFraccion);
                }
            }
        }
        
        // Normalizar fracciones para que sumen 1 por pedido
        normalizarFracciones(plan);
    }
    
    private void normalizarFracciones(Plan plan) {
        for (List<RutaPedido> rutas : plan.getAsignaciones().values()) {
            if (rutas.size() > 1) {
                double suma = rutas.stream().mapToDouble(RutaPedido::getFraccion).sum();
                if (suma > 0) {
                    for (RutaPedido ruta : rutas) {
                        ruta.setFraccion(ruta.getFraccion() / suma);
                    }
                }
            }
        }
    }
    
    private void busquedaLocal(Plan plan) {
        // Mejora local: intentar mejorar las rutas de un pedido aleatorio
        if (!plan.getAsignaciones().isEmpty()) {
            List<Integer> idsPedidos = new ArrayList<>(plan.getAsignaciones().keySet());
            int idPedido = idsPedidos.get(random.nextInt(idsPedidos.size()));
            List<RutaPedido> rutas = plan.getAsignaciones().get(idPedido);
            
            if (rutas != null && !rutas.isEmpty()) {
                // Intercambiar dos rutas aleatorias
                if (rutas.size() > 1 && random.nextDouble() < 0.5) {
                    int i = random.nextInt(rutas.size());
                    int j = random.nextInt(rutas.size());
                    if (i != j) {
                        Collections.swap(rutas, i, j);
                    }
                }
                // O intentar mejorar una ruta específica
                else {
                    int idx = random.nextInt(rutas.size());
                    RutaPedido ruta = rutas.get(idx);
                    Pedido pedido = pedidos.get(ruta.getIdPedido());
                    
                    // Generar una nueva ruta y ver si es mejor
                    List<Vuelo> nuevosVuelos = generarRutaAleatoria(pedido);
                    if (!nuevosVuelos.isEmpty()) {
                        RutaPedido nuevaRuta = new RutaPedido(
                            ruta.getIdPedido(), 
                            ruta.getFraccion(), 
                            nuevosVuelos
                        );
                        
                        // Si la nueva ruta es mejor, reemplazarla
                        if (nuevaRuta.getCosto() < ruta.getCosto() || 
                            (nuevaRuta.getEta() != null && ruta.getEta() != null && 
                             nuevaRuta.getEta().isBefore(ruta.getEta()))) {
                            rutas.set(idx, nuevaRuta);
                        }
                    }
                }
                
                // Re-evaluar el plan después de la búsqueda local
                plan.evaluar();
            }
        }
    }
}
