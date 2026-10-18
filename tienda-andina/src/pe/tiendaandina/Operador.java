package pe.tiendaandina;

public final class Operador extends Usuario {
    private static final long serialVersionUID = 1L;
    private final String id, nombre;
    public Operador(String id, String nombre) {
        if (id == null || id.isBlank() || nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Identidad requerida");
        this.id = id; this.nombre = nombre;
    }
    public String id() { return id; }
    public String nombre() { return nombre; }
    public Seguridad.Rol rol() { return Seguridad.Rol.OPERADOR; }
}
