package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;

public final class Pruebas {
    private static int comprobaciones;
    private static void comprobar(boolean condicion) {
        comprobaciones++;
        if (!condicion) throw new AssertionError("Comprobación " + comprobaciones);
    }
    private static void falla(Runnable accion) {
        try { accion.run(); } catch (IllegalArgumentException e) { comprobaciones++; return; }
        throw new AssertionError("Se esperaba rechazo");
    }
    private static void igual(String valor, java.math.BigDecimal actual) { comprobar(monto(valor).compareTo(actual) == 0); }
    public static void main(String[] args) {
        LocalDate hoy = LocalDate.of(2026, 10, 18);
        PasarelaCobro falsa = new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) {}
            public void reembolsar(String id, java.math.BigDecimal total) {}
        };
        GestorPedidos g = new GestorPedidos(Configuracion.predeterminada(), falsa);
        Cliente regular = new Cliente("1", "Ana", TipoCliente.REGULAR, "Chimbote", "");
        Cliente vip = new Cliente("2", "Luis", TipoCliente.VIP, "Lima", "");
        Producto fisico = new Producto("F", "Libro", TipoProducto.FISICO, "10", "1", 100);
        Producto digital = new Producto("D", "Curso", TipoProducto.DIGITAL, "118", "0", 0);
        Pedido tarjeta = g.registrar(regular, List.of(new Item(fisico, 10)), Envio.ESTANDAR, Pago.TARJETA, null, hoy);
        igual("100", tarjeta.resumen().subtotal()); igual("3", tarjeta.resumen().descuento());
        igual("28", tarjeta.resumen().envio()); igual("3.75", tarjeta.resumen().recargo());
        igual("128.75", tarjeta.resumen().total()); igual("19.64", tarjeta.resumen().igv());
        g.pagar(tarjeta, hoy); comprobar(fisico.stock() == 90);
        comprobar(tarjeta.documento().numero().equals("B001-00000001"));
        g.cancelar(tarjeta); comprobar(fisico.stock() == 100 && tarjeta.reembolsoRegistrado());
        comprobar(g.ventas(hoy, hoy).isEmpty());
        Pedido soloDigital = g.registrar(vip, List.of(new Item(digital, 1)), null, Pago.YAPE, null, hoy);
        g.observar((p, anterior, nuevo) -> { throw new IllegalStateException("SMTP caído"); });
        g.pagar(soloDigital, hoy); comprobar(soloDigital.estado() == Estado.ENTREGADO);
        comprobar(soloDigital.accesoHabilitado()); comprobar(g.erroresNotificacion().size() == 2);
        falla(() -> g.cancelar(soloDigital)); falla(() -> g.despachar(soloDigital));
        Producto escaso = new Producto("E", "Escaso", TipoProducto.FISICO, "10", "1", 1);
        Pedido sinStock = g.registrar(regular, List.of(new Item(fisico, 1), new Item(escaso, 2)), Envio.RECOJO, Pago.TARJETA, null, hoy);
        falla(() -> g.pagar(sinStock, hoy)); comprobar(fisico.stock() == 100 && escaso.stock() == 1);
        comprobar(sinStock.estado() == Estado.PENDIENTE);
        Pedido repetidos = g.registrar(regular, List.of(new Item(escaso, 1), new Item(escaso, 1)), Envio.RECOJO, Pago.YAPE, null, hoy);
        falla(() -> g.pagar(repetidos, hoy)); comprobar(escaso.stock() == 1);
        Cupon cupon = new Cupon("PROMO", "0.50", hoy);
        Pedido promo = g.registrar(vip, List.of(new Item(fisico, 25)), Envio.ESTANDAR, Pago.TRANSFERENCIA, cupon, hoy);
        igual("75", promo.resumen().descuento()); igual("0", promo.resumen().envio());
        falla(() -> g.pagar(promo, hoy)); g.confirmarDeposito(promo, hoy);
        falla(() -> g.registrar(vip, List.of(new Item(digital, 1)), null, Pago.YAPE, cupon, hoy));
        falla(() -> g.registrar(regular, List.of(new Item(digital, 1)), null, Pago.YAPE,
                new Cupon("VENCIDO", "0.10", hoy.minusDays(1)), hoy));
        falla(() -> g.registrar(regular, List.of(new Item(digital, 5)), null, Pago.YAPE, null, hoy));
        Cliente corporativo = new Cliente("3", "Empresa", TipoCliente.CORPORATIVO, "Chimbote", "12345678901");
        Pedido empresa = g.registrar(corporativo, List.of(new Item(digital, 1)), null, Pago.TRANSFERENCIA, null, hoy);
        g.confirmarDeposito(empresa, hoy); comprobar(empresa.documento().numero().equals("F001-00000001"));
        Cliente sinRuc = new Cliente("4", "Sin RUC", TipoCliente.CORPORATIVO, "Lima", "");
        Pedido invalido = g.registrar(sinRuc, List.of(new Item(digital, 1)), null, Pago.TARJETA, null, hoy);
        falla(() -> g.pagar(invalido, hoy));
        Pedido contraentrega = g.registrar(regular, List.of(new Item(fisico, 1)), Envio.ESTANDAR, Pago.CONTRAENTREGA, null, hoy);
        comprobar(contraentrega.estado() == Estado.PAGADO);
        g.despachar(contraentrega); g.entregar(contraentrega); falla(() -> g.cancelar(contraentrega));
        for (Pedido p : g.ventas(hoy, hoy)) comprobar(p.documento().resumen().equals(p.resumen()));
        falla(() -> g.registrar(regular, List.of(new Item(digital, 1)), Envio.ESTANDAR, Pago.TARJETA, null, hoy));
        Cliente lejos = new Cliente("5", "María", TipoCliente.REGULAR, "Cusco", "");
        falla(() -> g.registrar(lejos, List.of(new Item(fisico, 1)), Envio.EXPRESS, Pago.TARJETA, null, hoy));
        ReporteVentas reporte = g.reporte(hoy, hoy);
        comprobar(reporte.lineas().size() == 4);
        igual("496.00", reporte.subtotal());
        comprobar(reporte.total().compareTo(g.ventas(hoy, hoy).stream().map(p -> p.documento().resumen().total())
                .reduce(monto("0.00"), java.math.BigDecimal::add)) == 0);
        ReporteVentas vacio = g.reporte(hoy.plusDays(1), hoy.plusDays(1));
        comprobar(vacio.lineas().isEmpty()); igual("0.00", vacio.total());
        falla(() -> g.reporte(hoy, hoy.minusDays(1)));
        try { reporte.lineas().clear(); throw new AssertionError("Reporte mutable"); }
        catch (UnsupportedOperationException esperado) { comprobaciones++; }

        // El IGV es parametrizable, pero nunca se recalculan documentos históricos.
        Resumen emitido = soloDigital.documento().resumen();
        Configuracion c = Configuracion.predeterminada();
        g.configurar(new Configuracion(monto("0.20"), c.vip(), c.corporativo(), c.volumen10(), c.volumen25(), c.tope(), c.recargoTarjeta()));
        comprobar(soloDigital.documento().resumen().equals(emitido));
        Pedido nuevoIgv = g.registrar(regular, List.of(new Item(digital, 1)), null, Pago.YAPE, null, hoy.plusDays(1));
        g.pagar(nuevoIgv, hoy.plusDays(1)); igual("19.67", nuevoIgv.documento().resumen().igv());
        comprobar(g.reporte(hoy, hoy).equals(reporte));
        igual("118.00", g.reporte(hoy.plusDays(1), hoy.plusDays(1)).total());

        // Límites de volumen: 9, 10, 24 y 25 unidades; incluye todo el subtotal.
        GestorPedidos limites = new GestorPedidos(c, falsa);
        for (int n : new int[]{9, 10, 24, 25}) {
            Pedido p = limites.registrar(regular, List.of(new Item(fisico, n)), Envio.RECOJO, Pago.TRANSFERENCIA, null, hoy);
            igual(n == 9 ? "0" : n == 10 ? "3" : n == 24 ? "7.20" : "15", p.resumen().descuento());
        }
        Pedido mixto = limites.registrar(regular, List.of(new Item(fisico, 10), new Item(digital, 1)), Envio.RECOJO, Pago.TRANSFERENCIA, null, hoy);
        igual("6.54", mixto.resumen().descuento());
        Producto quinientos = new Producto("500", "Límite Yape", TipoProducto.DIGITAL, "500", "0", 0);
        Pedido yape500 = limites.registrar(regular, List.of(new Item(quinientos, 1)), null, Pago.YAPE, null, hoy);
        limites.pagar(yape500, hoy); igual("500", yape500.resumen().total());
        falla(() -> limites.registrar(regular, List.of(new Item(quinientos, 1), new Item(digital, 1)), null, Pago.YAPE, null, hoy));
        Cupon pendiente = new Cupon("EXPIRA", "0.10", hoy);
        Pedido expira = limites.registrar(regular, List.of(new Item(digital, 1)), null, Pago.YAPE, pendiente, hoy);
        falla(() -> limites.pagar(expira, hoy.plusDays(1)));
        comprobar(expira.estado() == Estado.PENDIENTE && expira.documento() == null);

        // Un rechazo de la pasarela no modifica inventario ni consume el cupón.
        GestorPedidos rechazo = new GestorPedidos(c, new PasarelaCobro() {
            public void cobrar(String id, java.math.BigDecimal total) { throw new IllegalArgumentException("Banco rechazó el cobro"); }
            public void reembolsar(String id, java.math.BigDecimal total) { throw new IllegalArgumentException("Banco rechazó el reembolso"); }
        });
        Cupon sinConsumo = new Cupon("BANCO", "0.10", hoy);
        int stockAntes = fisico.stock();
        Pedido rechazado = rechazo.registrar(regular, List.of(new Item(fisico, 1)), Envio.RECOJO, Pago.TARJETA, sinConsumo, hoy);
        falla(() -> rechazo.pagar(rechazado, hoy));
        comprobar(fisico.stock() == stockAntes && rechazado.estado() == Estado.PENDIENTE && rechazado.documento() == null);
        rechazo.registrar(regular, List.of(new Item(fisico, 1)), Envio.RECOJO, Pago.TRANSFERENCIA, sinConsumo, hoy);
        comprobaciones++;
        probarParametros();
        System.out.println("OK: " + comprobaciones + " comprobaciones");
    }
    private static void probarParametros() {
        try {
            Configuracion cargada = Parametros.cargar(java.nio.file.Path.of("config", "negocio.properties"));
            comprobar(cargada.equals(Configuracion.predeterminada()));
            java.nio.file.Path archivo = java.nio.file.Files.createTempFile("tienda-parametros-", ".properties");
            try {
                java.nio.file.Files.writeString(archivo, "igv=0.20\n");
                try { Parametros.cargar(archivo); throw new AssertionError("Faltan claves"); }
                catch (IllegalArgumentException esperado) { comprobaciones++; }
                String original = java.nio.file.Files.readString(java.nio.file.Path.of("config", "negocio.properties"));
                java.nio.file.Files.writeString(archivo, original.replace("igv=0.18", "igv=incorrecto"));
                try { Parametros.cargar(archivo); throw new AssertionError("Tasa inválida"); }
                catch (IllegalArgumentException esperado) { comprobaciones++; }
                java.nio.file.Files.writeString(archivo, original.replace("igv=0.18", "igv=-0.18"));
                try { Parametros.cargar(archivo); throw new AssertionError("Tasa negativa"); }
                catch (IllegalArgumentException esperado) { comprobaciones++; }
            } finally { java.nio.file.Files.deleteIfExists(archivo); }
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
