package pe.tiendaandina;

import java.util.List;

/** Contrato mínimo compartido de acceso a agregados. */
public interface Repositorio<T> {
    T buscar(String codigo);
    List<T> todos();
    void agregar(T elemento);
}
