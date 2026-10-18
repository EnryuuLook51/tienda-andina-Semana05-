package pe.tiendaandina;

import java.util.*;
import java.time.LocalDate;
import static pe.tiendaandina.Dominio.*;

public final class PruebasCierre {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Cierre " + pruebas); pruebas++; }
    private static void falla(Runnable accion) {
        try { accion.run(); throw new AssertionError("Validación ausente"); }
        catch (IllegalArgumentException esperado) { pruebas++; }
    }
    public static void main(String[] args) {
        int[] cobros = {0}, reembolsos = {0};
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) { cobros[0]++; }
            public void reembolsar(String id, java.math.BigDecimal total) { reembolsos[0]++; }
        });
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        Producto producto = new ProductoFisico("F", "Físico", "10", "1", 10);
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        falla(() -> g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TARJETA, null, null));
        Pedido p = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TARJETA, null, fecha);
        falla(() -> g.pagar(p, null));
        comprobar(p.estado() == Estado.PENDIENTE && producto.stock() == 10 && cobros[0] == 0);
        g.pagar(p, fecha); comprobar(cobros[0] == 1);
        producto.editar("Físico", "10", "1", Integer.MAX_VALUE);
        falla(() -> g.cancelar(p));
        comprobar(p.estado() == Estado.PAGADO && !p.reembolsoRegistrado() && reembolsos[0] == 0);
        comprobar(producto.stock() == Integer.MAX_VALUE);
        producto.editar("Físico", "10", "1", 9); g.cancelar(p);
        comprobar(producto.stock() == 10 && reembolsos[0] == 1);
        falla(() -> g.reporte(null, fecha)); falla(() -> g.reporte(fecha, null));
        falla(() -> new Cupon(" ", "0.1", fecha));
        Producto primero = new ProductoFisico("1", "Uno", "10", "1", 0);
        Producto segundo = new ProductoFisico("2", "Dos", "10", "1", Integer.MAX_VALUE);
        var stock = OperacionesProducto.VisitanteStock.para(List.of(new Item(primero, 1), new Item(segundo, 1)));
        falla(stock::reponer); comprobar(primero.stock() == 0 && segundo.stock() == Integer.MAX_VALUE);
        System.out.println("OK: " + pruebas + " comprobaciones de regresión final");
    }
}
