package pe.tiendaandina;

import java.math.BigDecimal;
import static pe.tiendaandina.Dominio.*;

/** Las estrategias aíslan recargo, restricciones y comunicación con la pasarela. */
public final class MediosPago {
    private MediosPago() {}
    public interface MedioPago extends java.io.Serializable {
        default BigDecimal recargo(BigDecimal base, Configuracion configuracion) { return monto("0.00"); }
        default void validar(Pedido pedido, BigDecimal total) {}
        default boolean nacePagado() { return false; }
        default boolean exigeConfirmacion() { return false; }
        default void cobrar(PasarelaCobro pasarela, String pedido, BigDecimal total) {}
        default void reversar(PasarelaCobro pasarela, String pedido, BigDecimal total) {}
    }
    private abstract static class CobroExterno implements MedioPago {
        private static final long serialVersionUID = 1L;
        @Override public void cobrar(PasarelaCobro pasarela, String pedido, BigDecimal total) { pasarela.cobrar(pedido, total); }
        @Override public void reversar(PasarelaCobro pasarela, String pedido, BigDecimal total) { pasarela.reembolsar(pedido, total); }
    }
    public static final class PagoTarjeta extends CobroExterno {
        private static final long serialVersionUID = 1L;
        @Override public BigDecimal recargo(BigDecimal base, Configuracion c) { return redondear(base.multiply(c.recargoTarjeta())); }
    }
    public static final class PagoYape extends CobroExterno {
        private static final long serialVersionUID = 1L;
        @Override public void validar(Pedido p, BigDecimal total) {
            if (total.compareTo(monto("500")) > 0) throw new IllegalArgumentException("Yape admite hasta S/ 500");
        }
    }
    public static final class PagoTransferencia implements MedioPago {
        private static final long serialVersionUID = 1L;
        @Override public boolean exigeConfirmacion() { return true; }
    }
    public static final class PagoContraentrega implements MedioPago {
        private static final long serialVersionUID = 1L;
        @Override public boolean nacePagado() { return true; }
        @Override public void validar(Pedido p, BigDecimal total) {
            if (!p.requiereEnvio() || p.envio != Envio.ESTANDAR || p.cliente.tipo() == TipoCliente.CORPORATIVO)
                throw new IllegalArgumentException("Contraentrega requiere envío estándar y cliente REGULAR o VIP");
        }
    }
    // Único punto de composición para traducir la selección de la interfaz a una estrategia.
    public static MedioPago para(Pago pago) {
        return switch (pago) {
            case TARJETA -> new PagoTarjeta(); case YAPE -> new PagoYape();
            case TRANSFERENCIA -> new PagoTransferencia(); case CONTRAENTREGA -> new PagoContraentrega();
        };
    }
}
