package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;

public interface PedidoRepositorio extends Repositorio<Pedido> {
    List<Pedido> ventas(LocalDate desde, LocalDate hasta);
    List<Pedido> transferenciasPendientes();
}
