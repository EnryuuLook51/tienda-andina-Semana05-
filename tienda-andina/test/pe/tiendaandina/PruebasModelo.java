package pe.tiendaandina;

import java.time.LocalDate;
import java.util.*;
import java.nio.file.*;
import static pe.tiendaandina.Dominio.*;

public final class PruebasModelo {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Modelo " + pruebas); pruebas++; }
    public static void main(String[] args) throws Exception {
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela);
        Producto fisico = Producto.crear("F", "Físico", TipoProducto.FISICO, "10", "1", 100);
        Producto digital = Producto.crear("D", "Digital", TipoProducto.DIGITAL, "10", "0", 0);
        Producto suscripcion = Producto.crear("S", "Suscripción", TipoProducto.SUSCRIPCION, "10", "0", 0);
        comprobar(fisico instanceof ProductoFisico); comprobar(digital instanceof ProductoDigital); comprobar(suscripcion instanceof Suscripcion);
        comprobar(fisico.requiereEnvio() && !digital.requiereEnvio() && !suscripcion.requiereEnvio());
        Cliente vip = new Cliente("V", "VIP", TipoCliente.VIP, "Chimbote", "");
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido mixto = g.registrar(vip, List.of(new Item(fisico, 5), new Item(fisico, 5), new Item(digital, 50)), Envio.ESTANDAR, Pago.TARJETA, null, fecha);
        Configuracion c = Configuracion.predeterminada();
        comprobar(new ReglasDescuento.DescuentoVolumen().porcentaje(mixto, c).compareTo(monto("0.03")) == 0);
        comprobar(new ReglasDescuento.DescuentoPorTipoCliente().porcentaje(mixto, c).compareTo(monto("0.05")) == 0);
        comprobar(new ReglasDescuento.DescuentoCupon(monto("0.10")).porcentaje(mixto, c).compareTo(monto("0.10")) == 0);
        g.pagar(mixto, fecha); comprobar(fisico.stock() == 90 && digital.stock() == 0 && mixto.accesoHabilitado());
        g.cancelar(mixto); comprobar(fisico.stock() == 100);
        Pedido mensual = g.registrar(vip, List.of(new Item(suscripcion, 1)), null, Pago.YAPE, null, fecha);
        g.pagar(mensual, fecha); comprobar(mensual.estado() == Estado.ENTREGADO && mensual.accesoHabilitado());
        Producto escaso = new ProductoFisico("E", "Escaso", "10", "1", 1);
        Pedido fallo = g.registrar(vip, List.of(new Item(fisico, 1), new Item(escaso, 2)), Envio.ESTANDAR, Pago.YAPE, null, fecha);
        try { g.pagar(fallo, fecha); throw new AssertionError("Stock insuficiente aceptado"); }
        catch (IllegalArgumentException esperado) { comprobar(fisico.stock() == 100 && escaso.stock() == 1); }
        // El visitante también soporta instancias base de snapshots anteriores.
        Producto antiguo = new Producto("A", "Anterior", TipoProducto.DIGITAL, "10", "0", 0);
        comprobar(OperacionesProducto.VisitanteHabilitacion.para(List.of(new Item(antiguo, 1))));
        Path carpeta = Files.createTempDirectory("tienda-modelo-"); Path archivo = carpeta.resolve("datos.bin");
        try {
            RepositorioArchivo repo = new RepositorioArchivo(archivo);
            repo.guardar(new RepositorioArchivo.Datos(g, List.of(fisico, digital, suscripcion), Map.of()));
            var datos = repo.cargar(pasarela);
            comprobar(datos.productos().get(0) instanceof ProductoFisico);
            comprobar(datos.productos().get(1) instanceof ProductoDigital);
            comprobar(datos.productos().get(2) instanceof Suscripcion);
            comprobar(datos.gestor().pedidos().get(0).items.get(0).producto() == datos.productos().get(0));
        } finally { Files.deleteIfExists(archivo); Files.delete(carpeta); }
        System.out.println("OK: " + pruebas + " comprobaciones del modelo de productos/descuentos");
    }
}
