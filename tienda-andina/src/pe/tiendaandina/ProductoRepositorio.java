package pe.tiendaandina;

import java.util.List;
import pe.tiendaandina.Dominio.Producto;

public interface ProductoRepositorio extends Repositorio<Producto> {
    List<Producto> activos();
}
