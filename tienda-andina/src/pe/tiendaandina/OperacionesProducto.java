package pe.tiendaandina;

import java.util.*;
import static pe.tiendaandina.Dominio.*;

public final class OperacionesProducto {
    private OperacionesProducto() {}
    public interface VisitanteProducto {
        void visitarFisico(Producto producto, int cantidad);
        void visitarDigital(Producto producto, int cantidad);
        void visitarSuscripcion(Producto producto, int cantidad);
    }
    /** Acumula antes de validar/modificar: soporta ítems repetidos del mismo producto. */
    public static final class VisitanteStock implements VisitanteProducto {
        private final Map<Producto, Integer> cantidades = new IdentityHashMap<>();
        public void visitarFisico(Producto p, int cantidad) { cantidades.merge(p, cantidad, Math::addExact); }
        public void visitarDigital(Producto p, int cantidad) {}
        public void visitarSuscripcion(Producto p, int cantidad) {}
        public static VisitanteStock para(List<Item> items) {
            VisitanteStock visitante = new VisitanteStock();
            items.forEach(i -> i.producto().aceptar(visitante, i.cantidad()));
            return visitante;
        }
        public void validar() { cantidades.forEach(Producto::verificarStock); }
        public void descontar() { validar(); cantidades.forEach(Producto::descontarStock); }
        public void validarReposicion() { cantidades.forEach(Producto::verificarReposicion); }
        public void reponer() { validarReposicion(); cantidades.forEach(Producto::reponerStock); }
    }
    public static final class VisitanteHabilitacion implements VisitanteProducto {
        private boolean habilitado;
        private final Map<String, Producto> productos = new LinkedHashMap<>();
        public void visitarFisico(Producto p, int cantidad) {}
        public void visitarDigital(Producto p, int cantidad) { habilitado = true; productos.putIfAbsent(p.codigo, p); }
        public void visitarSuscripcion(Producto p, int cantidad) { habilitado = true; productos.putIfAbsent(p.codigo, p); }
        public boolean habilitado() { return habilitado; }
        public static boolean para(List<Item> items) {
            VisitanteHabilitacion visitante = new VisitanteHabilitacion();
            items.forEach(i -> i.producto().aceptar(visitante, i.cantidad()));
            return visitante.habilitado();
        }
        public static List<Acceso> accesos(List<Item> items, Cliente cliente, java.time.LocalDate fecha) {
            VisitanteHabilitacion visitante = new VisitanteHabilitacion();
            items.forEach(i -> i.producto().aceptar(visitante, i.cantidad()));
            return visitante.productos.values().stream().map(p -> new Acceso(cliente.id(), p.codigo, p.tipo, fecha)).toList();
        }
    }
}
