package pe.tiendaandina;

import java.time.LocalDate;
import java.nio.file.*;
import java.util.*;
import static pe.tiendaandina.Dominio.*;

public final class PruebasInyeccion {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError("Inyección " + pruebas); pruebas++; }
    /** Doble de prueba: implementación distinta, sin heredar del adaptador real. */
    private static final class RepositorioPrueba implements PedidoRepositorio {
        private final Map<String, Pedido> mapa = new LinkedHashMap<>();
        private int altas, consultas;
        public Pedido buscar(String id) { consultas++; return Objects.requireNonNull(mapa.get(id)); }
        public List<Pedido> todos() { return List.copyOf(mapa.values()); }
        public void agregar(Pedido pedido) { altas++; mapa.put(pedido.id, pedido); }
        public List<Pedido> ventas(LocalDate desde, LocalDate hasta) {
            return todos().stream().filter(Pedido::esVenta)
                    .filter(p -> !p.fechaPago().isBefore(desde) && !p.fechaPago().isAfter(hasta)).toList();
        }
        public List<Pedido> transferenciasPendientes() {
            return todos().stream().filter(p -> p.pago == Pago.TRANSFERENCIA && p.estado() == Estado.PENDIENTE).toList();
        }
    }
    public static void main(String[] args) throws Exception {
        RepositorioPrueba repo = new RepositorioPrueba();
        PasarelaCobro pasarela = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), pasarela, repo);
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        Producto producto = new ProductoDigital("D", "Curso", "10");
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido p = g.registrar(cliente, List.of(new Item(producto, 1)), null, Pago.TRANSFERENCIA, null, fecha);
        comprobar(repo.altas == 1 && g.pedidos().equals(repo.todos()));
        comprobar(g.transferenciasPendientes().equals(List.of(p)));
        g.confirmarDeposito(p, fecha); comprobar(repo.consultas > 0 && p.estado() == Estado.ENTREGADO);
        comprobar(g.reporte(fecha, fecha).lineas().size() == 1);
        Path carpeta = Files.createTempDirectory("tienda-inyeccion-"); Path archivo = carpeta.resolve("datos.bin");
        try {
            RepositorioArchivo persistencia = new RepositorioArchivo(archivo);
            persistencia.guardar(new RepositorioArchivo.Datos(g, List.of(producto), Map.of()));
            GestorPedidos restaurado = persistencia.cargar(pasarela).gestor();
            comprobar(restaurado.pedidos().size() == 1);
            comprobar(restaurado.reporte(fecha, fecha).total().equals(g.reporte(fecha, fecha).total()));
            // Un segundo guardado no borra ni duplica el repositorio interno compartido.
            persistencia.guardar(new RepositorioArchivo.Datos(restaurado, List.of(producto), Map.of()));
            comprobar(persistencia.cargar(pasarela).gestor().pedidos().size() == 1);
            comprobar(g.pedidos().size() == 1 && repo.altas == 1);
        } finally { Files.deleteIfExists(archivo); Files.delete(carpeta); }
        try { new GestorPedidos(Configuracion.predeterminada(), pasarela, repo); throw new AssertionError(); }
        catch (IllegalArgumentException esperado) { pruebas++; }
        System.out.println("OK: " + pruebas + " comprobaciones de inyección de repositorios");
    }
}
