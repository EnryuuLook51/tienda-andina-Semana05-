package pe.tiendaandina;

import java.time.LocalDate;
import java.util.*;
import java.nio.file.*;
import static pe.tiendaandina.Dominio.*;
import static pe.tiendaandina.Seguridad.*;
import static pe.tiendaandina.Controladores.*;

public final class PruebasCatalogo {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Catálogo " + pruebas); pruebas++; }
    private static void falla(Runnable accion) {
        try { accion.run(); throw new AssertionError("Falta validación"); }
        catch (IllegalArgumentException esperado) { pruebas++; }
    }
    public static void main(String[] args) throws Exception {
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela);
        AdministradorControlador admin = new AdministradorControlador(new Sesion("admin", Rol.ADMINISTRADOR, null), g);
        ProductoRepositorio repo = new ProductosEnMemoria(List.of());
        admin.crearProducto(repo, "F1", "Libro", TipoProducto.FISICO, "10", "1", 10);
        Producto producto = repo.buscar("F1");
        comprobar(repo.activos().size() == 1);
        falla(() -> admin.crearProducto(repo, "f1", "Otro", TipoProducto.FISICO, "10", "1", 10));
        falla(() -> admin.crearProducto(repo, "D", "Digital", TipoProducto.DIGITAL, "10", "1", 0));
        falla(() -> admin.crearProducto(repo, "", "Sin código", TipoProducto.FISICO, "10", "1", 10));
        falla(() -> admin.editarProducto(repo, "F1", "Nuevo", "-1", "1", 10));
        comprobar(producto.precio().compareTo(monto("10")) == 0 && producto.nombre().equals("Libro"));
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido pagado = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TARJETA, null, fecha);
        g.pagar(pagado, fecha);
        String ticket = CalculoTotal.ImpresorTicket.imprimir(pagado);
        Pedido pendiente = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.TRANSFERENCIA, null, fecha);
        admin.editarProducto(repo, "F1", "Libro actualizado", "20", "1", 9);
        comprobar(repo.buscar("F1") == producto);
        comprobar(CalculoTotal.ImpresorTicket.imprimir(pagado).equals(ticket));
        comprobar(pendiente.items.get(0).precioUnitario().compareTo(monto("10")) == 0);
        Pedido nuevo = g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.YAPE, null, fecha);
        comprobar(nuevo.items.get(0).precioUnitario().compareTo(monto("20")) == 0);
        admin.bajaProducto(repo, "F1"); comprobar(repo.activos().isEmpty() && repo.todos().size() == 1);
        falla(() -> g.registrar(cliente, List.of(new Item(producto, 1)), Envio.RECOJO, Pago.YAPE, null, fecha));
        falla(() -> admin.crearProducto(repo, "F1", "Reutilizado", TipoProducto.FISICO, "10", "1", 10));
        g.confirmarDeposito(pendiente, fecha); comprobar(pendiente.estado() == Estado.PAGADO);
        g.cancelar(pagado); comprobar(producto.stock() == 9);
        Path carpeta = Files.createTempDirectory("tienda-catalogo-");
        Path archivo = carpeta.resolve("datos.bin");
        try {
            RepositorioArchivo persistencia = new RepositorioArchivo(archivo);
            persistencia.guardar(new RepositorioArchivo.Datos(g, repo.todos(), Map.of()));
            Producto recuperado = persistencia.cargar(pasarela).productos().get(0);
            comprobar(!recuperado.activo() && recuperado.nombre().equals("Libro actualizado"));
            comprobar(recuperado.precio().compareTo(monto("20")) == 0 && recuperado.stock() == 9);
        } finally { Files.deleteIfExists(archivo); Files.delete(carpeta); }
        System.out.println("OK: " + pruebas + " comprobaciones de catálogo");
    }
}
