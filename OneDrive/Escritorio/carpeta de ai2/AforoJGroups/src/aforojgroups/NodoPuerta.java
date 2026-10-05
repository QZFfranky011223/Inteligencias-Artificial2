package aforojgroups;

import org.jgroups.*;
import org.jgroups.util.Util;

import java.io.*;
import java.util.List;
import java.util.Scanner;

public class NodoPuerta extends ReceiverAdapter {
    
    private JChannel canal;
    private String nombre;
    private int aforoMaximo;
    private int ocupacionActual = 0;
    private View vistaActual;

    public NodoPuerta(String nombre, int aforoMaximo) {
        this.nombre = nombre;
        this.aforoMaximo = aforoMaximo;
    }

    public void iniciar() throws Exception {
        canal = new JChannel();
        canal.name(nombre); // Asigna el nombre al nodo en el canal
        canal.setReceiver(this);
        canal.connect("AforoSIS258");
        
        // Petición de transferencia de estado (10 segundos de timeout)
        canal.getState(null, 10000);

        loopDeComandos();

        canal.close();
    }

    private void loopDeComandos() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Nodo Puerta '" + nombre + "' iniciado.");
        System.out.println("Aforo máximo configurado: " + aforoMaximo);
        System.out.println("Comandos disponibles: /entrar n, /salir n, /estado");
        
        while (true) {
            try {
                System.out.print("> ");
                System.out.flush();
                String linea = scanner.nextLine().trim();
                
                if (linea.isEmpty()) continue;
                
                String[] partes = linea.split(" ");
                String comando = partes[0];

                if (comando.equals("/entrar") && partes.length == 2) {
                    int n = Integer.parseInt(partes[1]);
                    Address coordinador = vistaActual.getMembers().get(0);
                    MensajeAforo msg = new MensajeAforo(MensajeAforo.Tipo.SOLICITUD, nombre, n);
                    
                    // Unicast: Envía la SOLICITUD solo al coordinador
                    canal.send(new Message(coordinador, msg));
                    
                } else if (comando.equals("/salir") && partes.length == 2) {
                    int n = Integer.parseInt(partes[1]);
                    MensajeAforo msg = new MensajeAforo(MensajeAforo.Tipo.SALIDA, nombre, n);
                    
                    // Multicast: Envía SALIDA a todos para que resten
                    canal.send(new Message(null, msg));
                    
                } else if (comando.equals("/estado")) {
                    System.out.println("Ocupación: " + ocupacionActual + "/" + aforoMaximo);
                    if (ocupacionActual >= aforoMaximo) {
                        System.out.println("AFORO COMPLETO");
                    }
                    
                } else if (comando.equals("/salir_sistema")) {
                    break;
                } else {
                    System.out.println("Comando no reconocido. Use: /entrar n | /salir n | /estado | /salir_sistema");
                }
            } catch (NumberFormatException ex) {
                System.out.println("Por favor ingrese un número válido para n.");
            } catch (Exception e) {
                System.err.println("Error enviando mensaje: " + e.getMessage());
            }
        }
    }

    @Override
    public void viewAccepted(View new_view) {
        System.out.println("\n--- [Cambio de Membresía] ---");
        
        if (vistaActual != null) {
            List<Address> oldMembers = vistaActual.getMembers();
            List<Address> newMembers = new_view.getMembers();
            
            // Determinar quién entró
            for (Address a : newMembers) {
                if (!oldMembers.contains(a)) {
                    System.out.println("Entró la puerta: " + a);
                }
            }
            
            // Determinar quién salió
            for (Address a : oldMembers) {
                if (!newMembers.contains(a)) {
                    System.out.println("Salió la puerta: " + a);
                }
            }
        } else {
            System.out.println("Puertas iniciales: " + new_view.getMembers());
        }
        
        vistaActual = new_view;
        Address coordinador = vistaActual.getMembers().get(0);
        System.out.println("Coordinador actual: " + coordinador);
        System.out.println("-----------------------------");
        System.out.print("> ");
    }

    @Override
    public void receive(Message msg) {
        try {
            MensajeAforo aforoMsg = (MensajeAforo) msg.getObject();
            if (aforoMsg == null) return;

            Address emisor = msg.getSrc();
            Address coordinadorActual = vistaActual.getMembers().get(0);

            switch (aforoMsg.getTipo()) {
                case SOLICITUD:
                    // Sólo el coordinador procesa solicitudes
                    if (canal.getAddress().equals(coordinadorActual)) {
                        if (ocupacionActual + aforoMsg.getPersonas() <= aforoMaximo) {
                            // Si entra, difunde ACEPTADO por multicast
                            MensajeAforo respuesta = new MensajeAforo(MensajeAforo.Tipo.ACEPTADO, aforoMsg.getPuerta(), aforoMsg.getPersonas());
                            canal.send(new Message(null, respuesta));
                        } else {
                            // Si no entra, responde RECHAZADO por unicast al solicitante
                            MensajeAforo respuesta = new MensajeAforo(MensajeAforo.Tipo.RECHAZADO, aforoMsg.getPuerta(), aforoMsg.getPersonas());
                            canal.send(new Message(emisor, respuesta));
                        }
                    }
                    break;

                case ACEPTADO:
                    ocupacionActual += aforoMsg.getPersonas();
                    if (aforoMsg.getPuerta().equals(nombre)) {
                        System.out.println("\n[EXITO] Tu solicitud fue aceptada. Entraron " + aforoMsg.getPersonas() + " personas.");
                    } else {
                        System.out.println("\n[AVISO] Entraron " + aforoMsg.getPersonas() + " personas por la puerta " + aforoMsg.getPuerta());
                    }
                    if (ocupacionActual >= aforoMaximo) {
                        System.out.println("AFORO COMPLETO");
                    }
                    System.out.print("> ");
                    break;

                case RECHAZADO:
                    if (aforoMsg.getPuerta().equals(nombre)) {
                        System.out.println("\n[RECHAZO] Solicitud rechazada. Superaría el aforo máximo de " + aforoMaximo);
                        System.out.print("> ");
                    }
                    break;

                case SALIDA:
                    ocupacionActual -= aforoMsg.getPersonas();
                    if (ocupacionActual < 0) ocupacionActual = 0;
                    
                    if (aforoMsg.getPuerta().equals(nombre)) {
                        System.out.println("\n[EXITO] Registraste la salida de " + aforoMsg.getPersonas() + " personas.");
                    } else {
                        System.out.println("\n[AVISO] Salieron " + aforoMsg.getPersonas() + " personas por la puerta " + aforoMsg.getPuerta());
                    }
                    System.out.print("> ");
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        // Enviar el estado actual al nodo que se acaba de unir
        synchronized (this) {
            Util.objectToStream(ocupacionActual, new DataOutputStream(output));
        }
    }

    @Override
    public void setState(InputStream input) throws Exception {
        // Recibir el estado actual del grupo al unirse
        synchronized (this) {
            ocupacionActual = (Integer) Util.objectFromStream(new DataInputStream(input));
            System.out.println("\n[ESTADO] Sincronizado. Ocupación actual de la red: " + ocupacionActual);
            System.out.print("> ");
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso correcto: java NodoPuerta <nombre_puerta> <aforo_maximo>");
            System.out.println("Ejemplo en Netbeans (Click derecho > Properties > Run > Arguments): PuertaPrincipal 100");
            return;
        }

        try {
            String nombre = args[0];
            int aforo = Integer.parseInt(args[1]);
            
            System.setProperty("java.net.preferIPv4Stack", "true"); // Mejora compatibilidad de red para JGroups
            
            new NodoPuerta(nombre, aforo).iniciar();
            
        } catch (NumberFormatException e) {
            System.err.println("El aforo máximo debe ser un número entero.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
