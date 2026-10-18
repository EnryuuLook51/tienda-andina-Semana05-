package pe.tiendaandina;

import java.time.LocalDate;
import java.util.*;
import java.nio.file.*;
import static pe.tiendaandina.Dominio.*;

public final class PruebasEnvios {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Envíos " + pruebas); pruebas++; }
    private static void falla(Runnable accion) {
        try { accion.run(); throw new AssertionError("Faltó rechazo"); }
        catch (IllegalArgumentException esperado) { pruebas++; }
    }
    public static void main(String[] args) throws Exception {
        ConfiguracionEnvios tarifas = ConfiguracionEnvios.predeterminada();
        Cliente regular = new Cliente("C", "Ana", TipoCliente.REGULAR, " Chimbote ", "");
        Cliente vip = new Cliente("V", "Luis", TipoCliente.VIP, "Lima", "");
        Cliente cusco = new Cliente("K", "María", TipoCliente.REGULAR, "Cusco", "");
        comprobar(tarifas.costo(Envio.ESTANDAR, regular, monto("2")).compareTo(monto("12")) == 0);
        comprobar(tarifas.costo(Envio.EXPRESS, regular, monto("2")).compareTo(monto("23")) == 0);
        comprobar(tarifas.costo(Envio.ESTANDAR, vip, monto("2")).signum() == 0);
        comprobar(tarifas.costo(Envio.RECOJO, cusco, monto("2")).signum() == 0);
        falla(() -> tarifas.costo(Envio.EXPRESS, cusco, monto("2")));
        falla(() -> new ConfiguracionEnvios(monto("-1"), monto("2"), monto("15"), monto("4"), List.of("Lima")));
        falla(() -> new ConfiguracionEnvios(monto("8"), monto("2"), monto("15"), monto("4"), List.of("Lima", " ")));
        ConfiguracionEnvios nuevas = new ConfiguracionEnvios(monto("10"), monto("3"), monto("20"), monto("5"), List.of("CUSCO", " cusco "));
        comprobar(nuevas.ciudadesExpress().equals(List.of("cusco")));
        comprobar(nuevas.costo(Envio.EXPRESS, cusco, monto("2")).compareTo(monto("30")) == 0);
        falla(() -> nuevas.costo(Envio.EXPRESS, vip, monto("2")));
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela);
        Producto p = new Producto("F", "Libro", TipoProducto.FISICO, "10", "2", 10);
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido pagado = g.registrar(regular, List.of(new Item(p, 1)), Envio.ESTANDAR, Pago.YAPE, null, fecha);
        g.pagar(pagado, fecha); String ticket = CalculoTotal.ImpresorTicket.imprimir(pagado);
        Pedido pendiente = g.registrar(regular, List.of(new Item(p, 1)), Envio.ESTANDAR, Pago.TRANSFERENCIA, null, fecha);
        g.configurarEnvios(nuevas); g.confirmarDeposito(pendiente, fecha);
        comprobar(pendiente.resumen().envio().compareTo(monto("16")) == 0);
        comprobar(pagado.resumen().envio().compareTo(monto("12")) == 0);
        comprobar(CalculoTotal.ImpresorTicket.imprimir(pagado).equals(ticket));
        Path carpeta = Files.createTempDirectory("tienda-envios-"); Path archivo = carpeta.resolve("datos.bin");
        try {
            RepositorioArchivo repo = new RepositorioArchivo(archivo);
            repo.guardar(new RepositorioArchivo.Datos(g, List.of(p), Map.of()));
            comprobar(repo.cargar(pasarela).gestor().configuracionEnvios().equals(nuevas));
        } finally { Files.deleteIfExists(archivo); Files.delete(carpeta); }
        // Compatibilidad de snapshots sin el campo: simula su valor por defecto al deserializar.
        var campo = GestorPedidos.class.getDeclaredField("envios"); campo.setAccessible(true); campo.set(g, null);
        comprobar(g.configuracionEnvios().equals(tarifas));
        System.out.println("OK: " + pruebas + " comprobaciones de envíos");
    }
}
