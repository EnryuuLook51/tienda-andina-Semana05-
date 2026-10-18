package pe.tiendaandina;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static pe.tiendaandina.Dominio.*;

public final class PruebasPersistencia {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Persistencia " + pruebas); pruebas++; }
    public static void main(String[] args) throws Exception {
        Path carpeta = Files.createTempDirectory("tienda-prueba-");
        Path archivo = carpeta.resolve("datos.bin");
        RepositorioArchivo repo = new RepositorioArchivo(archivo);
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        try {
            comprobar(!repo.existe());
            GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela);
            Producto producto = new Producto("F", "Libro", TipoProducto.FISICO, "10", "1", 10);
            Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
            Cupon cupon = new Cupon("PROMO", "0.10", LocalDate.of(2027, 1, 1));
            LocalDate fecha = LocalDate.of(2026, 10, 18);
            Pedido pagado = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.ESTANDAR, Pago.TARJETA, cupon, fecha);
            g.pagar(pagado, fecha);
            Pedido pendiente = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TRANSFERENCIA, null, fecha);
            String ticket = CalculoTotal.ImpresorTicket.imprimir(pagado);
            repo.guardar(new RepositorioArchivo.Datos(g, List.of(producto), Map.of("PROMO", cupon)));
            comprobar(repo.existe());
            RepositorioArchivo.Datos datos = repo.cargar(pasarela);
            GestorPedidos cargado = datos.gestor();
            comprobar(cargado.pedidos().size() == 2);
            Pedido primero = cargado.pedidos().get(0), segundo = cargado.pedidos().get(1);
            comprobar(datos.productos().get(0).stock() == 9);
            comprobar(primero.items.get(0).producto() == datos.productos().get(0));
            comprobar(segundo.estado() == Estado.PENDIENTE);
            comprobar(CalculoTotal.ImpresorTicket.imprimir(primero).equals(ticket));
            comprobar(CalculoTotal.calcular(primero.conceptos()).equals(primero.resumen()));
            try {
                cargado.registrar(cliente, List.of(new Item(datos.productos().get(0), 1)), Envio.RECOJO, Pago.YAPE, datos.cupones().get("PROMO"), fecha);
                throw new AssertionError("Cupón reutilizado");
            } catch (IllegalArgumentException esperado) { pruebas++; }
            cargado.observar((p, a, n) -> {});
            cargado.confirmarDeposito(segundo, fecha);
            comprobar(segundo.documento().numero().equals("B001-00000002"));
            comprobar(datos.productos().get(0).stock() == 8);
            Pedido nuevo = cargado.registrar(cliente, List.of(new Item(datos.productos().get(0), 1)), Envio.RECOJO, Pago.YAPE, null, fecha);
            comprobar(!nuevo.id.equals(primero.id) && !nuevo.id.equals(pendiente.id));
            cargado.cancelar(primero); comprobar(datos.productos().get(0).stock() == 9);
            repo.guardar(new RepositorioArchivo.Datos(cargado, datos.productos(), datos.cupones()));
            comprobar(repo.cargar(pasarela).gestor().pedidos().get(0).estado() == Estado.CANCELADO);
            AutoCloseable bloqueo = repo.bloquear();
            try {
                try { repo.bloquear(); throw new AssertionError("Doble bloqueo"); }
                catch (java.nio.channels.OverlappingFileLockException esperado) { pruebas++; }
            } finally { bloqueo.close(); }
            Files.writeString(archivo, "archivo dañado");
            try { repo.cargar(pasarela); throw new AssertionError("Aceptó archivo dañado"); }
            catch (java.io.IOException esperado) { pruebas++; }
            comprobar(Files.readString(archivo).equals("archivo dañado"));
        } finally {
            Files.deleteIfExists(archivo); Files.deleteIfExists(carpeta.resolve("datos.bin.lock")); Files.delete(carpeta);
        }
        System.out.println("OK: " + pruebas + " comprobaciones de persistencia");
    }
}
