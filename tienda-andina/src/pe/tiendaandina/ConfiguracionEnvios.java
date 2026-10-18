package pe.tiendaandina;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import static pe.tiendaandina.Dominio.*;

/** Tipos de envío como datos editables, no una jerarquía de subclases. */
public record ConfiguracionEnvios(BigDecimal fijoEstandar, BigDecimal kgEstandar,
                                  BigDecimal fijoExpress, BigDecimal kgExpress,
                                  List<String> ciudadesExpress) implements Serializable {
    public ConfiguracionEnvios {
        for (BigDecimal valor : List.of(fijoEstandar, kgEstandar, fijoExpress, kgExpress))
            if (valor.signum() < 0) throw new IllegalArgumentException("Las tarifas no pueden ser negativas");
        ciudadesExpress = ciudadesExpress.stream().map(String::trim).map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
        if (ciudadesExpress.isEmpty() || ciudadesExpress.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Indique al menos una ciudad express, sin entradas vacías");
    }
    public static ConfiguracionEnvios predeterminada() {
        return new ConfiguracionEnvios(monto("8"), monto("2"), monto("15"), monto("4"), List.of("Lima", "Chimbote"));
    }
    public BigDecimal costo(Envio envio, Cliente cliente, BigDecimal peso) {
        if (peso.signum() < 0) throw new IllegalArgumentException("Peso negativo");
        return redondear(switch (envio) {
            case ESTANDAR -> cliente.tipo() == TipoCliente.VIP ? BigDecimal.ZERO : fijoEstandar.add(kgEstandar.multiply(peso));
            case EXPRESS -> {
                if (!ciudadesExpress.contains(cliente.ciudad().trim().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Express disponible en: " + String.join(", ", ciudadesExpress));
                yield fijoExpress.add(kgExpress.multiply(peso));
            }
            // Punto de recojo fijo en la tienda de Chimbote, independientemente del domicilio.
            case RECOJO -> BigDecimal.ZERO;
        });
    }
}
