package pe.tiendaandina;

import java.io.Serializable;
import static pe.tiendaandina.Seguridad.Rol;

/** Identidad común; cada usuario tiene exactamente un rol. */
public abstract class Usuario implements Serializable {
    private static final long serialVersionUID = 1L;
    public abstract String id();
    public abstract String nombre();
    public abstract Rol rol();
    public String clienteId() { return null; }
}
