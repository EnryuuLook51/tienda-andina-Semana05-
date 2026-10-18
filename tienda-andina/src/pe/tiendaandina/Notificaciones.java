package pe.tiendaandina;

import java.util.Objects;
import java.util.function.Consumer;
import static pe.tiendaandina.Dominio.*;

/** Puertos de notificación: el dominio no conoce SMTP ni proveedores SMS. */
public final class Notificaciones {
    private Notificaciones() {}
    public record Mensaje(String clienteId, String pedidoId, Estado anterior, Estado nuevo, String texto) {}
    @FunctionalInterface
    public interface CanalMensaje { void enviar(Mensaje mensaje); }
    public static final class ObservadorCorreo implements ObservadorPedido {
        private final CanalMensaje canal;
        public ObservadorCorreo(CanalMensaje canal) { this.canal = Objects.requireNonNull(canal); }
        public void cambio(Pedido p, Estado anterior, Estado nuevo) {
            canal.enviar(new Mensaje(p.cliente.id(), p.id, anterior, nuevo, "Pedido " + p.id + ": " + anterior + " → " + nuevo));
        }
    }
    public static final class ObservadorSms implements ObservadorPedido {
        private final CanalMensaje canal;
        public ObservadorSms(CanalMensaje canal) { this.canal = Objects.requireNonNull(canal); }
        public void cambio(Pedido p, Estado anterior, Estado nuevo) {
            if (nuevo == Estado.ENVIADO && (p.cliente.tipo() == TipoCliente.VIP || p.cliente.tipo() == TipoCliente.CORPORATIVO))
                canal.enviar(new Mensaje(p.cliente.id(), p.id, anterior, nuevo, "Su pedido " + p.id + " fue enviado"));
        }
    }
    /** Adaptador local: no transmite mensajes por red. */
    public static final class CanalSimulado implements CanalMensaje {
        private final String nombre;
        private final Consumer<String> registro;
        public CanalSimulado(String nombre, Consumer<String> registro) {
            this.nombre = Objects.requireNonNull(nombre); this.registro = Objects.requireNonNull(registro);
        }
        public void enviar(Mensaje mensaje) { registro.accept(nombre + " SIMULADO: " + mensaje.texto()); }
    }
}
