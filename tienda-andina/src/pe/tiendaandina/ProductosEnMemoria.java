package pe.tiendaandina;

import java.util.*;
import static pe.tiendaandina.Dominio.*;

/** Repositorio en memoria; su catálogo se incluye en el snapshot local. */
public final class ProductosEnMemoria implements ProductoRepositorio {
    private final List<Producto> productos = new ArrayList<>();
    public ProductosEnMemoria(List<Producto> iniciales) { iniciales.forEach(this::agregar); }
    public List<Producto> todos() { return List.copyOf(productos); }
    public List<Producto> activos() { return productos.stream().filter(Producto::activo).toList(); }
    public Producto buscar(String codigo) {
        return productos.stream().filter(p -> p.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Producto inexistente"));
    }
    public void agregar(Producto p) {
        if (p.codigo.isBlank() || p.nombre().isBlank()) throw new IllegalArgumentException("Código y nombre requeridos");
        if (productos.stream().anyMatch(actual -> actual.codigo.equalsIgnoreCase(p.codigo)))
            throw new IllegalArgumentException("El código ya existe, incluso si está dado de baja");
        productos.add(p);
    }
}
