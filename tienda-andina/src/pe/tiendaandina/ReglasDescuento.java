package pe.tiendaandina;

import java.math.BigDecimal;
import static pe.tiendaandina.Dominio.*;

/** Reglas independientes; la composición mayor(cliente, volumen) + cupón se hace en la fábrica de conceptos. */
public final class ReglasDescuento {
    private ReglasDescuento() {}
    public interface Descuento { BigDecimal porcentaje(Pedido pedido, Configuracion configuracion); }
    public static final class DescuentoPorTipoCliente implements Descuento {
        public BigDecimal porcentaje(Pedido p, Configuracion c) {
            return switch (p.cliente.tipo()) { case VIP -> c.vip(); case CORPORATIVO -> c.corporativo(); default -> BigDecimal.ZERO; };
        }
    }
    public static final class DescuentoVolumen implements Descuento {
        public BigDecimal porcentaje(Pedido p, Configuracion c) {
            long unidades = p.items.stream().filter(i -> i.producto().requiereEnvio()).mapToLong(Item::cantidad).sum();
            return unidades >= 25 ? c.volumen25() : unidades >= 10 ? c.volumen10() : BigDecimal.ZERO;
        }
    }
    public static final class DescuentoCupon implements Descuento {
        private final BigDecimal porcentaje;
        public DescuentoCupon(BigDecimal porcentaje) {
            if (porcentaje == null || porcentaje.signum() < 0 || porcentaje.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Porcentaje de cupón inválido");
            this.porcentaje = porcentaje;
        }
        public BigDecimal porcentaje(Pedido p, Configuracion c) { return porcentaje; }
    }
}
