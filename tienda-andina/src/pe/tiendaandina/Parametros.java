package pe.tiendaandina;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static pe.tiendaandina.Dominio.*;

/** Carga explícita: un archivo inválido nunca se reemplaza silenciosamente por defaults. */
public final class Parametros {
    private Parametros() {}
    public static Configuracion cargar(Path archivo) throws IOException {
        Properties p = new Properties();
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) { p.load(lector); }
        return new Configuracion(tasa(p, "igv"), tasa(p, "descuento.vip"), tasa(p, "descuento.corporativo"),
                tasa(p, "descuento.volumen10"), tasa(p, "descuento.volumen25"),
                tasa(p, "descuento.tope"), tasa(p, "recargo.tarjeta"));
    }
    private static java.math.BigDecimal tasa(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Falta el parámetro: " + clave);
        try { return monto(valor.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Parámetro numérico inválido: " + clave, e); }
    }
}
