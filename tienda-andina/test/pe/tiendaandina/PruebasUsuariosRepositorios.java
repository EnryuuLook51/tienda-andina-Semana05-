package pe.tiendaandina;

import java.util.*;
import java.time.LocalDate;
import static pe.tiendaandina.Dominio.*;

public final class PruebasUsuariosRepositorios {
    private static int pruebas;
    private static void comprobar(boolean valor) { if (!valor) throw new AssertionError(); pruebas++; }
    public static void main(String[] args) {
        Cliente cliente = new Cliente("C", "Ana", TipoCliente.VIP, "Chimbote", "");
        List<Usuario> usuarios = List.of(cliente, new Operador("O", "Operador"), new Administrador("A", "Admin"));
        Seguridad.Autenticacion auth = new Seguridad.Autenticacion();
        for (Usuario u : usuarios) {
            auth.registrar(u.id(), "Prueba2026!".toCharArray(), u);
            var sesion = auth.entrar(u.id(), "Prueba2026!".toCharArray());
            comprobar(sesion.rol() == u.rol()); comprobar(Objects.equals(sesion.clienteId(), u.clienteId()));
        }
        comprobar(cliente.equals(new Cliente("C", "Ana", TipoCliente.VIP, "Chimbote", "")));
        Producto producto = new ProductoDigital("D", "Curso", "10");
        Repositorio<Producto> productos = new ProductosEnMemoria(List.of(producto));
        comprobar(productos.buscar("D") == producto);
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        });
        LocalDate fecha = LocalDate.of(2026, 10, 18);
        Pedido pedido = g.registrar(cliente, List.of(new Item(producto, 1)), null, Pago.TRANSFERENCIA, null, fecha);
        comprobar(g.transferenciasPendientes().equals(List.of(pedido)));
        PedidoRepositorio repo = new PedidosEnMemoria(new ArrayList<>(g.pedidos()));
        comprobar(repo.buscar(pedido.id) == pedido); comprobar(repo.ventas(fecha, fecha).isEmpty());
        g.confirmarDeposito(pedido, fecha);
        comprobar(g.transferenciasPendientes().isEmpty()); comprobar(repo.ventas(fecha, fecha).equals(List.of(pedido)));
        try { repo.agregar(pedido); throw new AssertionError(); } catch (IllegalArgumentException esperado) { pruebas++; }
        try { repo.todos().clear(); throw new AssertionError(); } catch (UnsupportedOperationException esperado) { pruebas++; }
        System.out.println("OK: " + pruebas + " comprobaciones de usuarios/repositorios");
    }
}
