package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;
import static pe.tiendaandina.Seguridad.*;
import static pe.tiendaandina.Controladores.*;

public final class PruebasSeguridad {
    private static int pruebas;
    private static void verificar(boolean valor) { if (!valor) throw new AssertionError(); pruebas++; }
    private static void denegado(Runnable accion) {
        try { accion.run(); throw new AssertionError("Acceso no rechazado"); }
        catch (SecurityException esperado) { pruebas++; }
    }
    public static void main(String[] args) {
        Autenticacion auth = new Autenticacion();
        auth.registrar("ana", "Clave2026!".toCharArray(), Rol.CLIENTE, "ana");
        auth.registrar("op", "Clave2026!".toCharArray(), Rol.OPERADOR, null);
        auth.registrar("admin", "Clave2026!".toCharArray(), Rol.ADMINISTRADOR, null);
        denegado(() -> auth.entrar("ana", "incorrecta".toCharArray()));
        denegado(() -> auth.entrar("nadie", "Clave2026!".toCharArray()));
        Sesion ana = auth.entrar("ana", "Clave2026!".toCharArray());
        Sesion op = auth.entrar("op", "Clave2026!".toCharArray());
        Sesion admin = auth.entrar("admin", "Clave2026!".toCharArray());
        verificar(ana.rol() == Rol.CLIENTE);
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        });
        Cliente cliente = new Cliente("ana", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        Cliente otro = new Cliente("otro", "Otro", TipoCliente.REGULAR, "Chimbote", "");
        denegado(() -> new ClienteControlador(op, cliente, g));
        denegado(() -> new ClienteControlador(ana, otro, g));
        denegado(() -> new OperadorControlador(ana, g));
        denegado(() -> new AdministradorControlador(op, g));
        var cc = new ClienteControlador(ana, cliente, g);
        var co = new OperadorControlador(op, g);
        var ca = new AdministradorControlador(admin, g);
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Producto p = new Producto("SEG", "Producto", TipoProducto.FISICO, "10", "1", 10);
        Pedido pedido = cc.registrar(List.of(new Item(p, 1)), Envio.ESTANDAR, Pago.TRANSFERENCIA, null, fecha);
        Pedido ajeno = g.registrar(otro, List.of(new Item(p, 1)), Envio.ESTANDAR, Pago.YAPE, null, fecha);
        denegado(() -> cc.pagar(ajeno, fecha)); denegado(() -> cc.cancelar(ajeno)); denegado(() -> cc.recibir(ajeno));
        verificar(ajeno.estado() == Estado.PENDIENTE && p.stock() == 10);
        co.confirmarDeposito(pedido, fecha); verificar(pedido.estado() == Estado.PAGADO);
        co.despachar(pedido); cc.recibir(pedido); verificar(pedido.estado() == Estado.ENTREGADO);
        verificar(ca.reporte(fecha, fecha).lineas().size() == 1);
        verificar(ca.crearCupon("PRUEBA", "0.10", fecha).codigo.equals("PRUEBA"));
        System.out.println("OK: " + pruebas + " comprobaciones de seguridad");
    }
}
