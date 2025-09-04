package pe.pucp.edu.morapack.planner;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Aeropuerto {
    private int id;
    private String codigo;
    private String ciudad;
    private String pais;
    private int GMT;
    private int capacidad;
    private String latitud;
    private String longitud;

    public Aeropuerto() {
    }

    public Aeropuerto(int id, String codigo, String ciudad, String pais, int GMT,
                      int capacidad, String latitud, String longitud) {
        this.id = id;
        this.codigo = codigo;
        this.ciudad = ciudad;
        this.pais = pais;
        this.GMT = GMT;
        this.capacidad = capacidad;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public int getGMT() {
        return GMT;
    }

    public void setGMT(int GMT) {
        this.GMT = GMT;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    public String getLatitud() {
        return latitud;
    }

    public void setLatitud(String latitud) {
        this.latitud = latitud;
    }

    public String getLongitud() {
        return longitud;
    }

    public void setLongitud(String longitud) {
        this.longitud = longitud;
    }

    public String leer(Scanner sc){
        if (!sc.hasNextLine()) return null;
        String linea = sc.nextLine();
        Pattern p = Pattern.compile(
                "(\\d+)\\s+" +            // ID
                        "(\\w+)\\s+" +            // Código
                        "(.+?)\\s{2,}" +          // Ciudad (cualquier cosa hasta dos o más espacios)
                        "(.+?)\\s+" +             // País (hasta el siguiente espacio)
                        "(\\w+)\\s+" +            // Abreviatura (ignored si quieres)
                        "([+-]?\\d+)\\s+" +       // GMT
                        "(\\d+)\\s+" +            // Capacidad
                        "Latitude:\\s+(.+?)\\s+" + // Latitud
                        "Longitude:\\s+(.+)"       // Longitud
        );
        Matcher m = p.matcher(linea);
        if (m.matches()) {
            this.id = Integer.parseInt(m.group(1));
            this.codigo = m.group(2);
            this.ciudad = m.group(3);
            this.pais = m.group(4);
            //this.abreviatura = m.group(5);
            this.GMT = Integer.parseInt(m.group(6));
            this.capacidad=Integer.parseInt(m.group(7));
            this.latitud = m.group(8);
            this.longitud = m.group(9);

            System.out.println(id+" "+codigo+" "+ciudad+" "+pais+" "+GMT+" "+capacidad+" "+latitud+" "+longitud);

        }
        return this.codigo;
    }

}
