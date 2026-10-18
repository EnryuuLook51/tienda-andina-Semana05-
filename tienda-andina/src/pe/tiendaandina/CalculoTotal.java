package pe.tiendaandina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import static pe.tiendaandina.Dominio.*;

/** Conceptos ordenados: líneas, descuento, envío, recargo e IGV incluido. */
public final class CalculoTotal {
    private CalculoTotal() {}
    public interface VisitanteTotal {
        void visitar(LineaProducto linea);
        void visitar(Descuento descuento);
        void visitar(CostoEnvio envio);
        void visitar(RecargoMedioPago recargo);
        void visitar(Igv igv);
    }
    public sealed interface ConceptoTotal extends java.io.Serializable permits LineaProducto, Descuento, CostoEnvio, RecargoMedioPago, Igv {
        void aceptar(VisitanteTotal visitante);
    }
    public record LineaProducto(String nombre, int cantidad, BigDecimal precio) implements ConceptoTotal {
        public void aceptar(VisitanteTotal v) { v.visitar(this); }
        public BigDecimal importe() { return precio.multiply(BigDecimal.valueOf(cantidad)); }
    }
    public record Descuento(BigDecimal porcentaje) implements ConceptoTotal {
        public void aceptar(VisitanteTotal v) { v.visitar(this); }
    }
    public record CostoEnvio(BigDecimal importe) implements ConceptoTotal {
        public void aceptar(VisitanteTotal v) { v.visitar(this); }
    }
    public record RecargoMedioPago(MediosPago.MedioPago medio, Configuracion configuracion) implements ConceptoTotal {
        public void aceptar(VisitanteTotal v) { v.visitar(this); }
    }
    public record Igv(BigDecimal tasa) implements ConceptoTotal {
        public void aceptar(VisitanteTotal v) { v.visitar(this); }
    }
    public static final class ConstructorConceptos {
        public List<ConceptoTotal> construir(Pedido p, Configuracion c, BigDecimal porcentajeCupon) {
            return construir(p, c, porcentajeCupon, ConfiguracionEnvios.predeterminada());
        }
        public List<ConceptoTotal> construir(Pedido p, Configuracion c, BigDecimal porcentajeCupon, ConfiguracionEnvios tarifas) {
            List<ConceptoTotal> conceptos = new ArrayList<>();
            for (Item item : p.items) conceptos.add(new LineaProducto(item.producto().nombre(), item.cantidad(), item.precioUnitario()));
            BigDecimal cliente = new ReglasDescuento.DescuentoPorTipoCliente().porcentaje(p, c);
            BigDecimal volumen = new ReglasDescuento.DescuentoVolumen().porcentaje(p, c);
            BigDecimal cupon = new ReglasDescuento.DescuentoCupon(porcentajeCupon).porcentaje(p, c);
            conceptos.add(new Descuento(cliente.max(volumen).add(cupon).min(c.tope())));
            BigDecimal envio = BigDecimal.ZERO;
            if (p.requiereEnvio()) {
                if (p.envio == null) throw new IllegalArgumentException("Seleccione el envío");
                BigDecimal peso = p.items.stream().filter(i -> i.producto().requiereEnvio())
                        .map(i -> i.producto().peso().multiply(BigDecimal.valueOf(i.cantidad()))).reduce(BigDecimal.ZERO, BigDecimal::add);
                envio = tarifas.costo(p.envio, p.cliente, peso);
            } else if (p.envio != null) throw new IllegalArgumentException("Los pedidos sin productos físicos no eligen envío");
            conceptos.add(new CostoEnvio(redondear(envio)));
            conceptos.add(new RecargoMedioPago(MediosPago.para(p.pago), c));
            conceptos.add(new Igv(c.igv()));
            return List.copyOf(conceptos);
        }
    }
    /** Una instancia por cálculo; el IGV desglosa el total y nunca se suma otra vez. */
    public static final class CalculadoraTotal implements VisitanteTotal {
        private BigDecimal subtotal = monto("0.00"), descuento = monto("0.00"), envio = monto("0.00"),
                recargo = monto("0.00"), igv = monto("0.00");
        public void visitar(LineaProducto l) { subtotal = subtotal.add(l.importe()); }
        public void visitar(Descuento d) {
            Dinero base = new Dinero(subtotal); subtotal = base.valor(); descuento = base.multiplicar(d.porcentaje()).valor();
        }
        public void visitar(CostoEnvio e) { envio = e.importe(); }
        public void visitar(RecargoMedioPago r) { recargo = r.medio().recargo(subtotal.subtract(descuento).add(envio), r.configuracion()); }
        public void visitar(Igv i) { igv = total().multiply(i.tasa()).divide(BigDecimal.ONE.add(i.tasa()), 2, RoundingMode.HALF_UP); }
        private BigDecimal total() {
            return new Dinero(subtotal).restar(new Dinero(descuento)).sumar(new Dinero(envio)).sumar(new Dinero(recargo)).valor();
        }
        public Resumen resumen() { return new Resumen(subtotal, descuento, envio, recargo, igv, total().subtract(igv), total()); }
    }
    public static Resumen calcular(List<ConceptoTotal> conceptos) {
        ConstructorResumen constructor = new ConstructorResumen();
        conceptos.forEach(c -> c.aceptar(constructor));
        return constructor.resumen();
    }
    /** Construye la salida inmutable delegando la aritmética al visitante calculador. */
    public static final class ConstructorResumen implements VisitanteTotal {
        private final CalculadoraTotal calculadora = new CalculadoraTotal();
        public void visitar(LineaProducto l) { calculadora.visitar(l); }
        public void visitar(Descuento d) { calculadora.visitar(d); }
        public void visitar(CostoEnvio e) { calculadora.visitar(e); }
        public void visitar(RecargoMedioPago r) { calculadora.visitar(r); }
        public void visitar(Igv i) { calculadora.visitar(i); }
        public Resumen resumen() { return calculadora.resumen(); }
    }
    /** Recorre los conceptos conservados al pagar, pero imprime los importes del documento. */
    public static final class ImpresorTicket implements VisitanteTotal {
        private final StringBuilder texto = new StringBuilder();
        private final Resumen resumen;
        private ImpresorTicket(Resumen resumen) { this.resumen = resumen; }
        public void visitar(LineaProducto l) { texto.append(l.cantidad()).append(" x ").append(l.nombre()).append(" @ S/ ").append(l.precio()).append("\n"); }
        public void visitar(Descuento d) { texto.append("Subtotal: S/ ").append(resumen.subtotal()).append("\nDescuento: S/ ").append(resumen.descuento()).append('\n'); }
        public void visitar(CostoEnvio e) { texto.append("Envío: S/ ").append(resumen.envio()).append('\n'); }
        public void visitar(RecargoMedioPago r) { texto.append("Recargo: S/ ").append(resumen.recargo()).append('\n'); }
        public void visitar(Igv i) {
            texto.append("Base imponible: S/ ").append(resumen.base()).append("\nIGV incluido: S/ ").append(resumen.igv())
                    .append("\nTOTAL: S/ ").append(resumen.total()).append('\n');
        }
        public static String imprimir(Pedido p) {
            if (p.documento() == null) throw new IllegalArgumentException("El pedido todavía no tiene comprobante");
            ImpresorTicket impresor = new ImpresorTicket(p.documento().resumen());
            impresor.texto.append("TIENDA ANDINA — COMPROBANTE DE DEMOSTRACIÓN\nSin validez tributaria\n")
                    .append(p.documento().numero()).append("\nPedido: ").append(p.id)
                    .append("\nCliente: ").append(p.cliente.nombre()).append('\n');
            if (p.cliente.tipo() == TipoCliente.CORPORATIVO) impresor.texto.append("RUC: ").append(p.cliente.ruc()).append('\n');
            p.conceptos().forEach(c -> c.aceptar(impresor));
            return impresor.texto.toString();
        }
    }
}
