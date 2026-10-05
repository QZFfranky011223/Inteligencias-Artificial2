package aforojgroups;

import java.io.Serializable;

public class MensajeAforo implements Serializable {
    
    public enum Tipo { 
        SOLICITUD, ACEPTADO, RECHAZADO, SALIDA 
    }
    
    private Tipo tipo;
    private String puerta;
    private int personas;
    
    public MensajeAforo(Tipo tipo, String puerta, int personas) {
        this.tipo = tipo;
        this.puerta = puerta;
        this.personas = personas;
    }
    
    public Tipo getTipo() {
        return tipo;
    }
    
    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }
    
    public String getPuerta() {
        return puerta;
    }
    
    public void setPuerta(String puerta) {
        this.puerta = puerta;
    }
    
    public int getPersonas() {
        return personas;
    }
    
    public void setPersonas(int personas) {
        this.personas = personas;
    }
}
