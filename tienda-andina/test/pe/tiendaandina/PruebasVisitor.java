package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;

public final class PruebasVisitor {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Visitor: " + pruebas); pruebas++; }
    public static void main(String[] args) {
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        });
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Producto producto = new Producto("F", "Cuaderno", TipoProducto.FISICO, "10", "1", 100);
        Cliente cliente = new Cliente("C", "Empresa", TipoCliente.CORPORATIVO, "Chimbote", "20123456789");
        Pedido p = g.registrar(cliente, List.of(new Item(producto, 10)), Envio.ESTANDAR, Pago.TARJETA, null, fecha);
        try { CalculoTotal.ImpresorTicket.imprimir(p); throw new AssertionError("Ticket sin pagar"); }
        catch (IllegalArgumentException esperado) { pruebas++; }
        g.pagar(p, fecha);
        comprobar(CalculoTotal.calcular(p.conceptos()).equals(p.documento().resumen()));
        String ticket = CalculoTotal.ImpresorTicket.imprimir(p);
        comprobar(ticket.contains("F001-00000001")); comprobar(ticket.contains("RUC: 20123456789"));
        comprobar(ticket.contains("10 x Cuaderno")); comprobar(ticket.contains("TOTAL: S/ 121.54"));
        comprobar(ticket.contains("IGV incluido: S/ " + p.resumen().igv()));
        comprobar(ticket.contains("Sin validez tributaria"));
        Configuracion c = Configuracion.predeterminada();
        g.configurar(new Configuracion(monto("0.20"), c.vip(), c.corporativo(), c.volumen10(), c.volumen25(), c.tope(), monto("0.10")));
        comprobar(CalculoTotal.ImpresorTicket.imprimir(p).equals(ticket));
        comprobar(CalculoTotal.calcular(p.conceptos()).equals(p.documento().resumen()));
        comprobar(g.reporte(fecha, fecha).total().equals(p.documento().resumen().total()));
        try { p.conceptos().clear(); throw new AssertionError("Conceptos mutables"); }
        catch (UnsupportedOperationException esperado) { pruebas++; }
        g.cancelar(p); comprobar(CalculoTotal.ImpresorTicket.imprimir(p).equals(ticket));
        System.out.println("OK: " + pruebas + " comprobaciones de Visitor/ticket");
    }
}
