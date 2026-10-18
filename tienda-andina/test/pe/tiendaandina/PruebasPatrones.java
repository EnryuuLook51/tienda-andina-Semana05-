package pe.tiendaandina;

import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import static pe.tiendaandina.Dominio.*;

public final class PruebasPatrones {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Prueba " + pruebas); pruebas++; }
    public static void main(String[] args) {
        // Matriz completa de operaciones: pagar, despachar, recibir, cancelar y entrega digital.
        for (Estado e : Estado.values()) {
            EstadosPedido.EstadoPedido estado = EstadosPedido.para(e);
            Set<Integer> permitidas = switch (e) {
                case PENDIENTE -> Set.of(0, 3); case PAGADO -> Set.of(1, 3, 4);
                case ENVIADO -> Set.of(2); default -> Set.of();
            };
            List<Runnable> acciones = List.of(estado::pagar, estado::despachar, estado::recibir, estado::cancelar, estado::entregarDigital);
            for (int i = 0; i < acciones.size(); i++) {
                boolean aceptada = true;
                try { acciones.get(i).run(); } catch (IllegalArgumentException ex) { aceptada = false; }
                comprobar(aceptada == permitidas.contains(i));
            }
            comprobar(estado.nombre() == e);
            comprobar(estado.esVenta() == (e == Estado.PAGADO || e == Estado.ENVIADO || e == Estado.ENTREGADO));
            comprobar(estado.reembolsaAlCancelar() == (e == Estado.PAGADO));
        }
        int[] cobros = {0}, reversas = {0};
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) { cobros[0]++; }
            public void reembolsar(String id, java.math.BigDecimal total) { reversas[0]++; }
        };
        for (Pago pago : Pago.values()) {
            MediosPago.MedioPago medio = MediosPago.para(pago);
            int anterior = cobros[0], reversionAnterior = reversas[0];
            medio.cobrar(pasarela, "P", monto("100")); medio.reversar(pasarela, "P", monto("100"));
            boolean externo = pago == Pago.TARJETA || pago == Pago.YAPE;
            comprobar(cobros[0] - anterior == (externo ? 1 : 0));
            comprobar(reversas[0] - reversionAnterior == (externo ? 1 : 0));
            comprobar(medio.exigeConfirmacion() == (pago == Pago.TRANSFERENCIA));
            comprobar(medio.nacePagado() == (pago == Pago.CONTRAENTREGA));
            comprobar(medio.recargo(monto("100"), Configuracion.predeterminada()).compareTo(monto(pago == Pago.TARJETA ? "3" : "0")) == 0);
        }
        // Los errores al reembolsar preservan estado, stock y documento.
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) { throw new IllegalArgumentException("Reembolso rechazado"); }
        });
        Producto producto = new Producto("P", "Físico", TipoProducto.FISICO, "10", "1", 2);
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido p = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TARJETA, null, fecha);
        g.pagar(p, fecha); Documento documento = p.documento();
        try { g.cancelar(p); throw new AssertionError("No rechazó reembolso"); }
        catch (IllegalArgumentException esperado) { pruebas++; }
        comprobar(p.estado() == Estado.PAGADO && producto.stock() == 1 && !p.reembolsoRegistrado());
        comprobar(p.documento().equals(documento));
        System.out.println("OK: " + pruebas + " comprobaciones de State/Strategy");
    }
}
