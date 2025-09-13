package pe.pucp.edu.morapack.planner;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        // 1. Cargar datos
        System.out.println("Cargando datos...");
        AeropuertosMap aeropuertosMap = new AeropuertosMap();
        VuelosMap vuelosMap = new VuelosMap(aeropuertosMap);
        CargarPedidos cargadorPedidos = new CargarPedidos();
        
        // Cargar datos de archivos
        try (Scanner scAeropuertos = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.Aeropuerto.husos.v1.20250818__estudiantes.txt")) {
            if (scAeropuertos != null) {
                aeropuertosMap.leerDatos(scAeropuertos);
            }
        } catch (Exception e) {
            System.err.println("Error al cargar aeropuertos: " + e.getMessage());
            return;
        }

        try (Scanner scVuelos = ArchivoUtils.getScannerFromResource("c.1inf54.25.2.planes_vuelo.v4.20250818.txt")) {
            if (scVuelos != null) {
                vuelosMap.leerDatos(scVuelos);
            }
        } catch (Exception e) {
            System.err.println("Error al cargar vuelos: " + e.getMessage());
            return;
        }

        try (Scanner scPedidos = ArchivoUtils.getScannerFromResource("pedidos.txt")) {
            if (scPedidos != null) {
                cargadorPedidos.leerDatos(scPedidos);
            }
        } catch (Exception e) {
            System.err.println("Error al cargar pedidos: " + e.getMessage());
            return;
        }
        
        // 2. Verificar datos cargados
        System.out.println("\n=== ESTADO DE CARGA ===");
        System.out.println("Aeropuertos cargados: " + aeropuertosMap.getAeropuertos().size());
        System.out.println("Vuelos cargados: " + vuelosMap.getVuelosPorOrigen().values().stream()
                .mapToInt(List::size).sum());
        System.out.println("Pedidos cargados: " + cargadorPedidos.getPedidos().size());
        
        // Mostrar todos los códigos de aeropuertos disponibles
        System.out.println("\nCódigos de aeropuertos disponibles:");
        aeropuertosMap.getAeropuertos().values().stream()
            .map(Aeropuerto::getCodigo)
            .sorted()
            .forEach(codigo -> System.out.print(codigo + " "));
        System.out.println();
        
        // Verificar destinos de los primeros 5 pedidos
        System.out.println("\nVerificando destinos de los primeros 5 pedidos:");
        cargadorPedidos.getPedidos().stream()
            .limit(5)
            .forEach(pedido -> {
                String destino = pedido.getDestino();
                boolean existe = aeropuertosMap.existe(destino);
                System.out.printf("Pedido %d - Destino: %s - %s%n", 
                    pedido.getIdPedido(), 
                    destino, 
                    existe ? "VÁLIDO" : "NO VÁLIDO");
            });
            
        // 3. Mostrar algunos datos de ejemplo
        System.out.println("\nAlgunos aeropuertos de ejemplo:");
        aeropuertosMap.getAeropuertos().values().stream()
            .limit(5)
            .forEach(a -> System.out.println(" - " + a.getCodigo() + ": " + a.getCiudad()));
            
        // 4. Crear grafo
        System.out.println("\nCreando grafo de planificación...");
        TEGraph grafo = new TEGraph(aeropuertosMap, vuelosMap);
        
        // 5. Preparar pedidos con deadlines
        System.out.println("\nAsignando deadlines a los pedidos...");
        
        // Verificar aeropuerto de origen
        String codigoOrigen = "LIM";  // Código de Lima
        Aeropuerto aeropuertoOrigen = aeropuertosMap.obtener(codigoOrigen);
        
        if (aeropuertoOrigen == null) {
            System.out.println("¡ADVERTENCIA! No se encontró el aeropuerto de origen: " + codigoOrigen);
            System.out.println("Usando el primer aeropuerto disponible como origen...");
            // Usar el primer aeropuerto disponible como origen
            aeropuertoOrigen = aeropuertosMap.getAeropuertos().values().iterator().next();
            System.out.println("Aeropuerto de origen seleccionado: " + aeropuertoOrigen.getCodigo() + " - " + aeropuertoOrigen.getCiudad());
        } else {
            System.out.println("Aeropuerto de origen: " + aeropuertoOrigen.getCodigo() + " - " + aeropuertoOrigen.getCiudad());
        }
        
        Map<Integer, Pedido> pedidos = new HashMap<>();
        LocalDateTime ahora = LocalDateTime.now();
        
        for (Pedido pedido : cargadorPedidos.getPedidos()) {
            // Asignar un deadline basado en la fecha del pedido + plazo de entrega
            Aeropuerto destino = aeropuertosMap.obtener(pedido.getDestino());
            
            if (aeropuertoOrigen != null && destino != null) {
                boolean mismoContinente = aeropuertoOrigen.getContinente().equals(destino.getContinente());
                int plazoDias = mismoContinente ? 2 : 4;
                LocalDateTime deadline = ahora.plusDays(plazoDias);
                pedido.setDeadline(deadline);
                pedidos.put(pedido.getIdPedido(), pedido);
            } else {
                System.out.println("Advertencia: No se pudo procesar el pedido " + pedido.getIdPedido() + 
                                 ". Origen o destino no válido.");
                if (aeropuertoOrigen == null) {
                    System.out.println("  - Razón: No se pudo determinar el aeropuerto de origen");
                }
                if (destino == null) {
                    System.out.println("  - Razón: No se encontró el aeropuerto de destino: " + pedido.getDestino());
                }
            }
        }
        
        if (pedidos.isEmpty()) {
            System.err.println("Error: No hay pedidos válidos para procesar.");
            return;
        }
        
        // 6. Inicializar y ejecutar algoritmo
        System.out.println("\n=== INICIANDO ALGORITMO MEMÉTICO ===");
        System.out.println("Número de pedidos: " + pedidos.size());
        System.out.println("Número de aeropuertos: " + aeropuertosMap.getAeropuertos().size());
        System.out.println("Número de vuelos: " + vuelosMap.getVuelosPorOrigen().values().stream()
                .mapToInt(List::size).sum());
        
        System.out.println("\nEjecutando algoritmo...");
        
        // Crear instancia del algoritmo con el aeropuerto de origen
        AlgoritmoMeme algoritmo = new AlgoritmoMeme(grafo, pedidos, aeropuertoOrigen.getCodigo());
        
        long inicio = System.currentTimeMillis();
        Plan mejorPlan = algoritmo.ejecutar();
        long fin = System.currentTimeMillis();
        
        // 7. Mostrar resultados
        System.out.println("\n=== RESULTADOS ===");
        System.out.printf("Tiempo de ejecución: %.2f segundos%n", (fin - inicio) / 1000.0);
        
        System.out.println("\nMejor plan encontrado:");
        System.out.println("Fitness: " + mejorPlan.getFitness());
        
        // Mostrar resumen de rutas
        System.out.println("\nRutas generadas por pedido:");
        for (Map.Entry<Integer, List<RutaPedido>> entry : mejorPlan.getAsignaciones().entrySet()) {
            Pedido pedido = pedidos.get(entry.getKey());
            System.out.println("\nPedido " + entry.getKey() + " a " + pedido.getDestino() + 
                             " (Deadline: " + pedido.getDeadline() + ")");
            
            for (RutaPedido ruta : entry.getValue()) {
                System.out.println("  - Ruta (" + (ruta.getFraccion() * 100) + "%): " + 
                                 ruta.getVuelos().stream()
                                    .map(v -> v.getOrigen() + "->" + v.getDestino())
                                    .collect(Collectors.joining(", ")));
            }
        }
    }
}
