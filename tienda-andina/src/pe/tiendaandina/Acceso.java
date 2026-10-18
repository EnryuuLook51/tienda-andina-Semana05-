package pe.tiendaandina;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import static pe.tiendaandina.Dominio.TipoProducto;

/** Derecho local habilitado por el pago, no una URL ni una credencial externa. */
public record Acceso(String clienteId, String productoCodigo, TipoProducto tipo, LocalDate fechaHabilitacion) implements Serializable {
    public Acceso {
        Objects.requireNonNull(fechaHabilitacion);
        if (clienteId == null || clienteId.isBlank() || productoCodigo == null || productoCodigo.isBlank())
            throw new IllegalArgumentException("El acceso necesita cliente y producto");
        if (tipo != TipoProducto.DIGITAL && tipo != TipoProducto.SUSCRIPCION)
            throw new IllegalArgumentException("Un producto físico no genera acceso");
    }
}
