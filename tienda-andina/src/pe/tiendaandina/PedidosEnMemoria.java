package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;

/** Vista sobre la colección persistida del gestor; no duplica agregados ni inventario. */
public final class PedidosEnMemoria implements PedidoRepositorio {
    private final List<Pedido> pedidos;
    public PedidosEnMemoria(List<Pedido> pedidos) { this.pedidos = java.util.Objects.requireNonNull(pedidos); }
    public Pedido buscar(String id) {
        return pedidos.stream().filter(p -> p.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pedido inexistente"));
    }
    public List<Pedido> todos() { return List.copyOf(pedidos); }
    public void agregar(Pedido p) {
        if (pedidos.stream().anyMatch(actual -> actual.id.equals(p.id))) throw new IllegalArgumentException("Pedido duplicado");
        pedidos.add(p);
    }
    public List<Pedido> ventas(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new IllegalArgumentException("Fechas requeridas");
        if (desde.isAfter(hasta)) throw new IllegalArgumentException("Rango de fechas inválido");
        return pedidos.stream().filter(Pedido::esVenta)
                .filter(p -> !p.fechaPago().isBefore(desde) && !p.fechaPago().isAfter(hasta)).toList();
    }
    public List<Pedido> transferenciasPendientes() {
        return pedidos.stream().filter(p -> p.estado() == Estado.PENDIENTE && p.pago == Pago.TRANSFERENCIA).toList();
    }
}
