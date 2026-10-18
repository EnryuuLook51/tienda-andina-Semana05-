package pe.tiendaandina;

import static pe.tiendaandina.Dominio.Estado;

/** Cada estado declara las operaciones válidas; el contrato rechaza las restantes. */
public final class EstadosPedido {
    private EstadosPedido() {}
    public interface EstadoPedido extends java.io.Serializable {
        Estado nombre();
        default void pagar() { rechazar("pagar"); }
        default void despachar() { rechazar("despachar"); }
        default void recibir() { rechazar("recibir"); }
        default void entregarDigital() { rechazar("entregar automáticamente"); }
        default void cancelar() { rechazar("cancelar"); }
        default boolean esVenta() { return false; }
        default boolean reembolsaAlCancelar() { return false; }
        private void rechazar(String accion) { throw new IllegalArgumentException("No se puede " + accion + " en estado " + nombre()); }
    }
    public static final class Pendiente implements EstadoPedido {
        private static final long serialVersionUID = 1L;
        public Estado nombre() { return Estado.PENDIENTE; }
        @Override public void pagar() {}
        @Override public void cancelar() {}
    }
    public static final class Pagado implements EstadoPedido {
        private static final long serialVersionUID = 1L;
        public Estado nombre() { return Estado.PAGADO; }
        @Override public void despachar() {}
        @Override public void entregarDigital() {}
        @Override public void cancelar() {}
        @Override public boolean esVenta() { return true; }
        @Override public boolean reembolsaAlCancelar() { return true; }
    }
    public static final class Enviado implements EstadoPedido {
        private static final long serialVersionUID = 1L;
        public Estado nombre() { return Estado.ENVIADO; }
        @Override public void recibir() {}
        @Override public boolean esVenta() { return true; }
    }
    public static final class Entregado implements EstadoPedido {
        private static final long serialVersionUID = 1L;
        public Estado nombre() { return Estado.ENTREGADO; }
        @Override public boolean esVenta() { return true; }
    }
    public static final class Cancelado implements EstadoPedido {
        private static final long serialVersionUID = 1L;
        public Estado nombre() { return Estado.CANCELADO; }
    }
    public static EstadoPedido para(Estado estado) {
        return switch (estado) {
            case PENDIENTE -> new Pendiente(); case PAGADO -> new Pagado(); case ENVIADO -> new Enviado();
            case ENTREGADO -> new Entregado(); case CANCELADO -> new Cancelado();
        };
    }
}
