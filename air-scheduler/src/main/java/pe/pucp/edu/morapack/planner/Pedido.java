package pe.pucp.edu.morapack.planner;

public class Pedido {
    private String id;
    private String origen;
    private String destino;
    private double peso;
    private long fechaPedido; // en timestamp o en horas simuladas

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getPeso() {
        return peso;
    }

    public void setPeso(double peso) {
        this.peso = peso;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public long getFechaPedido() {
        return fechaPedido;
    }

    public void setFechaPedido(long fechaPedido) {
        this.fechaPedido = fechaPedido;
    }

    public Pedido(String id, String origen, String destino, double peso, long fechaPedido) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.peso = peso;
        this.fechaPedido = fechaPedido;
    }

    public Pedido(String id, String origen, String destino, double peso) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.peso = peso;
        this.fechaPedido = fechaPedido;
    }

    @Override
    public String toString() {
        return id + ": " + origen + " → " + destino + " (" + peso + "kg)";
    }
}
