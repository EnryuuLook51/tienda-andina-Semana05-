package pe.tiendaandina;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor monetario en soles; normaliza a céntimos con HALF_UP. */
public record Dinero(BigDecimal valor) implements Comparable<Dinero>, Serializable {
    public Dinero { valor = Objects.requireNonNull(valor).setScale(2, RoundingMode.HALF_UP); }
    public static Dinero de(String valor) { return new Dinero(new BigDecimal(valor)); }
    public Dinero sumar(Dinero otro) { return new Dinero(valor.add(otro.valor)); }
    public Dinero restar(Dinero otro) { return new Dinero(valor.subtract(otro.valor)); }
    public Dinero multiplicar(BigDecimal factor) { return new Dinero(valor.multiply(factor)); }
    public Dinero dividir(BigDecimal divisor) { return new Dinero(valor.divide(divisor, 2, RoundingMode.HALF_UP)); }
    @Override public int compareTo(Dinero otro) { return valor.compareTo(otro.valor); }
    @Override public String toString() { return "S/ " + valor.toPlainString(); }
}
