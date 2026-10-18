package pe.tiendaandina;

import java.time.LocalDate;
import java.util.*;
import static pe.tiendaandina.Dominio.*;
import static pe.tiendaandina.Notificaciones.*;

public final class PruebasNotificaciones {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Notificación " + pruebas); pruebas++; }
    private static GestorPedidos gestor() {
        return new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        });
    }
    public static void main(String[] args) {
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        for (TipoCliente tipo : TipoCliente.values()) {
            GestorPedidos g = gestor();
            List<Mensaje> correos = new ArrayList<>(), sms = new ArrayList<>();
            g.observar(new ObservadorCorreo(correos::add)); g.observar(new ObservadorSms(sms::add));
            Cliente cliente = new Cliente(tipo.name(), "Cliente", tipo, "Chimbote", "20123456789");
            Producto producto = new Producto("F", "Físico", TipoProducto.FISICO, "10", "1", 10);
            Pedido p = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.ESTANDAR, Pago.YAPE, null, fecha);
            comprobar(correos.isEmpty() && sms.isEmpty()); // registrar no es una transición
            g.pagar(p, fecha); g.despachar(p); g.entregar(p);
            comprobar(correos.size() == 3);
            comprobar(correos.stream().map(Mensaje::nuevo).toList().equals(List.of(Estado.PAGADO, Estado.ENVIADO, Estado.ENTREGADO)));
            comprobar(correos.get(0).anterior() == Estado.PENDIENTE);
            comprobar(correos.stream().allMatch(m -> m.clienteId().equals(cliente.id()) && m.pedidoId().equals(p.id)));
            comprobar(sms.size() == (tipo == TipoCliente.REGULAR ? 0 : 1));
            if (!sms.isEmpty()) comprobar(sms.get(0).nuevo() == Estado.ENVIADO);
            try { g.despachar(p); throw new AssertionError("Transición inválida aceptada"); }
            catch (IllegalArgumentException esperado) { comprobar(correos.size() == 3); }
        }
        GestorPedidos g = gestor();
        List<Mensaje> sms = new ArrayList<>();
        g.observar(new ObservadorCorreo(m -> { throw new IllegalStateException("SMTP simulado caído"); }));
        g.observar(new ObservadorSms(sms::add));
        Cliente vip = new Cliente("V", "VIP", TipoCliente.VIP, "Lima", "");
        Producto fisico = new Producto("F", "Libro", TipoProducto.FISICO, "10", "1", 2);
        Pedido p = g.registrar(vip, List.of(new Item(fisico, 1)), Envio.ESTANDAR, Pago.YAPE, null, fecha);
        g.pagar(p, fecha); g.despachar(p);
        comprobar(p.estado() == Estado.ENVIADO && fisico.stock() == 1);
        comprobar(sms.size() == 1); comprobar(g.erroresNotificacion().size() == 2);
        comprobar(g.erroresNotificacion().get(0).contains("ObservadorCorreo"));
        comprobar(g.erroresNotificacion().get(1).contains("ENVIADO"));
        GestorPedidos digital = gestor(); List<Mensaje> correos = new ArrayList<>(), smsDigital = new ArrayList<>();
        digital.observar(new ObservadorCorreo(correos::add)); digital.observar(new ObservadorSms(smsDigital::add));
        Producto curso = new Producto("D", "Curso", TipoProducto.DIGITAL, "10", "0", 0);
        Pedido d = digital.registrar(vip, List.of(new Item(curso, 1)), null, Pago.YAPE, null, fecha);
        digital.pagar(d, fecha);
        comprobar(correos.size() == 2 && smsDigital.isEmpty());
        comprobar(correos.get(1).anterior() == Estado.PAGADO && correos.get(1).nuevo() == Estado.ENTREGADO);
        Pedido cancelado = digital.registrar(vip, List.of(new Item(curso, 1)), null, Pago.TRANSFERENCIA, null, fecha);
        digital.cancelar(cancelado); comprobar(correos.get(2).nuevo() == Estado.CANCELADO && smsDigital.isEmpty());
        List<String> log = new ArrayList<>();
        new CanalSimulado("CORREO", log::add).enviar(correos.get(0)); comprobar(log.get(0).startsWith("CORREO SIMULADO:"));
        System.out.println("OK: " + pruebas + " comprobaciones de notificaciones");
    }
}
