package pe.tiendaandina;

import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import static pe.tiendaandina.Dominio.*;

public final class PruebasValores {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Valores " + pruebas); pruebas++; }
    public static void main(String[] args) throws Exception {
        comprobar(Dinero.de("1").equals(Dinero.de("1.00")));
        comprobar(Dinero.de("1.005").equals(Dinero.de("1.01")));
        comprobar(Dinero.de("0.10").sumar(Dinero.de("0.20")).equals(Dinero.de("0.30")));
        comprobar(Dinero.de("10").restar(Dinero.de("2")).equals(Dinero.de("8")));
        comprobar(Dinero.de("10").multiplicar(monto("0.03")).equals(Dinero.de("0.30")));
        comprobar(Dinero.de("1").dividir(monto("3")).equals(Dinero.de("0.33")));
        comprobar(Dinero.de("-1").compareTo(Dinero.de("0")) < 0);
        try { Dinero.de("1").dividir(monto("0")); throw new AssertionError(); }
        catch (ArithmeticException esperado) { pruebas++; }
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela);
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Producto digital = new ProductoDigital("D", "Curso", "10"), mensual = new Suscripcion("S", "Biblioteca", "10");
        Pedido p = g.registrar(cliente, List.of(new Item(digital, 2), new Item(digital, 1), new Item(mensual, 1)), null, Pago.YAPE, null, fecha);
        comprobar(p.accesos().isEmpty()); g.pagar(p, fecha);
        comprobar(p.accesos().size() == 2); // Un derecho por producto; las cantidades no duplican credenciales.
        comprobar(p.accesos().stream().allMatch(a -> a.clienteId().equals(cliente.id()) && a.fechaHabilitacion().equals(fecha)));
        comprobar(p.accesos().stream().anyMatch(a -> a.tipo() == TipoProducto.SUSCRIPCION));
        try { p.accesos().clear(); throw new AssertionError(); } catch (UnsupportedOperationException esperado) { pruebas++; }
        try { new Acceso("C", "F", TipoProducto.FISICO, fecha); throw new AssertionError(); }
        catch (IllegalArgumentException esperado) { pruebas++; }
        CalculoTotal.ConstructorResumen constructor = new CalculoTotal.ConstructorResumen();
        p.conceptos().forEach(c -> c.aceptar(constructor));
        comprobar(constructor.resumen().equals(p.resumen()));
        Path carpeta = Files.createTempDirectory("tienda-valores-"); Path archivo = carpeta.resolve("datos.bin");
        try {
            RepositorioArchivo repo = new RepositorioArchivo(archivo);
            repo.guardar(new RepositorioArchivo.Datos(g, List.of(digital, mensual), Map.of()));
            comprobar(repo.cargar(pasarela).gestor().pedidos().get(0).accesos().equals(p.accesos()));
        } finally { Files.deleteIfExists(archivo); Files.delete(carpeta); }
        // Verifica reconstrucción del campo ausente en snapshots anteriores.
        var campo = Pedido.class.getDeclaredField("accesos"); campo.setAccessible(true); campo.set(p, null);
        comprobar(p.accesos().size() == 2);
        Producto fisico = new ProductoFisico("F", "Libro", "10", "1", 10);
        Pedido mixto = g.registrar(cliente, List.of(new Item(fisico, 1), new Item(digital, 1)), Envio.RECOJO, Pago.TARJETA, null, fecha);
        g.pagar(mixto, fecha); comprobar(mixto.accesos().size() == 1);
        List<Acceso> accesos = mixto.accesos(); g.cancelar(mixto);
        comprobar(mixto.accesos().equals(accesos)); // No se inventa revocación no especificada por el PDF.
        System.out.println("OK: " + pruebas + " comprobaciones de dinero/resumen/accesos");
    }
}
