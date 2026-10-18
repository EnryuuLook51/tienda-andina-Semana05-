package pe.tiendaandina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Reglas 3.1–3.11. No realiza cobros ni envía mensajes externos. */
public final class Dominio {
    private Dominio() {}
    public static BigDecimal monto(String valor) { return new BigDecimal(valor); }
    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalArgumentException(mensaje);
    }
    public enum TipoProducto { FISICO, DIGITAL, SUSCRIPCION }
    public enum TipoCliente { REGULAR, VIP, CORPORATIVO }
    public enum Envio { ESTANDAR, EXPRESS, RECOJO }
    public enum Pago { TARJETA, YAPE, TRANSFERENCIA, CONTRAENTREGA }
    public enum Estado { PENDIENTE, PAGADO, ENVIADO, ENTREGADO, CANCELADO }

    public static final class Cliente extends Usuario {
        // El record anterior usaba UID 0; se conservan nombres y tipos de sus campos.
        private static final long serialVersionUID = 0L;
        private final String id, nombre, ciudad, ruc;
        private final TipoCliente tipo;
        public Cliente(String id, String nombre, TipoCliente tipo, String ciudad, String ruc) {
            Objects.requireNonNull(tipo);
            exigir(id != null && !id.isBlank(), "Identificador requerido");
            exigir(nombre != null && !nombre.isBlank(), "Nombre requerido");
            exigir(ciudad != null && !ciudad.isBlank(), "Ciudad requerida");
            this.id = id; this.nombre = nombre; this.tipo = tipo; this.ciudad = ciudad; this.ruc = ruc;
        }
        public String id() { return id; }
        public String nombre() { return nombre; }
        public TipoCliente tipo() { return tipo; }
        public String ciudad() { return ciudad; }
        public String ruc() { return ruc; }
        @Override public Seguridad.Rol rol() { return Seguridad.Rol.CLIENTE; }
        @Override public String clienteId() { return id; }
        @Override public boolean equals(Object otro) {
            return otro instanceof Cliente c && Objects.equals(id, c.id) && Objects.equals(nombre, c.nombre)
                    && tipo == c.tipo && Objects.equals(ciudad, c.ciudad) && Objects.equals(ruc, c.ruc);
        }
        @Override public int hashCode() { return Objects.hash(id, nombre, tipo, ciudad, ruc); }
        void validarPago() {
            exigir(tipo != TipoCliente.CORPORATIVO || (ruc != null && ruc.matches("[0-9]{11}")),
                    "El cliente corporativo necesita RUC de 11 dígitos");
        }
    }
    public static class Producto implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String codigo;
        private String nombre;
        public final TipoProducto tipo;
        private BigDecimal precio, peso;
        private int stock;
        private boolean baja;
        public Producto(String codigo, String nombre, TipoProducto tipo, String precio, String peso, int stock) {
            exigir(codigo != null && !codigo.isBlank() && nombre != null && !nombre.isBlank(), "Código y nombre requeridos");
            this.codigo = Objects.requireNonNull(codigo);
            this.nombre = Objects.requireNonNull(nombre);
            this.tipo = Objects.requireNonNull(tipo);
            this.precio = monto(precio);
            this.peso = monto(peso);
            exigir(this.precio.signum() >= 0 && this.peso.signum() >= 0 && stock >= 0, "Valores negativos");
            exigir(tipo == TipoProducto.FISICO || (this.peso.signum() == 0 && stock == 0),
                    "Digitales y suscripciones no tienen peso ni stock");
            this.stock = stock;
        }
        public int stock() { return stock; }
        public String nombre() { return nombre; }
        public BigDecimal precio() { return precio; }
        public BigDecimal peso() { return peso; }
        public boolean activo() { return !baja; }
        public boolean requiereEnvio() { return tipo == TipoProducto.FISICO; }
        public void aceptar(OperacionesProducto.VisitanteProducto visitante, int cantidad) {
            exigir(cantidad > 0, "Cantidad inválida");
            // Compatibilidad con productos del snapshot anterior a la jerarquía.
            switch (tipo) {
                case FISICO -> visitante.visitarFisico(this, cantidad);
                case DIGITAL -> visitante.visitarDigital(this, cantidad);
                case SUSCRIPCION -> visitante.visitarSuscripcion(this, cantidad);
            }
        }
        void verificarStock(int cantidad) { exigir(cantidad > 0 && stock >= cantidad, "Stock insuficiente: " + nombre); }
        void descontarStock(int cantidad) { verificarStock(cantidad); stock -= cantidad; }
        void reponerStock(int cantidad) { exigir(cantidad > 0, "Cantidad inválida"); stock = Math.addExact(stock, cantidad); }
        void verificarReposicion(int cantidad) {
            exigir(cantidad > 0 && (long) stock + cantidad <= Integer.MAX_VALUE, "La reposición supera el stock máximo: " + nombre);
        }
        public static Producto crear(String codigo, String nombre, TipoProducto tipo, String precio, String peso, int stock) {
            return switch (tipo) {
                case FISICO -> new ProductoFisico(codigo, nombre, precio, peso, stock);
                case DIGITAL -> {
                    exigir(monto(peso).signum() == 0 && stock == 0, "Digitales no tienen peso ni stock");
                    yield new ProductoDigital(codigo, nombre, precio);
                }
                case SUSCRIPCION -> {
                    exigir(monto(peso).signum() == 0 && stock == 0, "Suscripciones no tienen peso ni stock");
                    yield new Suscripcion(codigo, nombre, precio);
                }
            };
        }
        public void darDeBaja() { baja = true; }
        public void editar(String nombre, String precio, String peso, int stock) {
            exigir(nombre != null && !nombre.isBlank(), "Nombre requerido");
            BigDecimal nuevoPrecio = monto(precio), nuevoPeso = monto(peso);
            exigir(nuevoPrecio.signum() >= 0 && nuevoPeso.signum() >= 0 && stock >= 0, "Valores negativos");
            exigir(tipo == TipoProducto.FISICO || (nuevoPeso.signum() == 0 && stock == 0), "Digitales y suscripciones no tienen peso ni stock");
            this.nombre = nombre.trim(); this.precio = nuevoPrecio; this.peso = nuevoPeso; this.stock = stock;
        }
        @Override public String toString() { return codigo + " — " + nombre + " — S/ " + precio + (baja ? " [BAJA]" : ""); }
    }
    public static final class ProductoFisico extends Producto {
        private static final long serialVersionUID = 1L;
        public ProductoFisico(String codigo, String nombre, String precio, String peso, int stock) { super(codigo, nombre, TipoProducto.FISICO, precio, peso, stock); }
        @Override public boolean requiereEnvio() { return true; }
        @Override public void aceptar(OperacionesProducto.VisitanteProducto v, int cantidad) { exigir(cantidad > 0, "Cantidad inválida"); v.visitarFisico(this, cantidad); }
    }
    public static final class ProductoDigital extends Producto {
        private static final long serialVersionUID = 1L;
        public ProductoDigital(String codigo, String nombre, String precio) { super(codigo, nombre, TipoProducto.DIGITAL, precio, "0", 0); }
        @Override public boolean requiereEnvio() { return false; }
        @Override public void aceptar(OperacionesProducto.VisitanteProducto v, int cantidad) { exigir(cantidad > 0, "Cantidad inválida"); v.visitarDigital(this, cantidad); }
    }
    public static final class Suscripcion extends Producto {
        private static final long serialVersionUID = 1L;
        public Suscripcion(String codigo, String nombre, String precio) { super(codigo, nombre, TipoProducto.SUSCRIPCION, precio, "0", 0); }
        @Override public boolean requiereEnvio() { return false; }
        @Override public void aceptar(OperacionesProducto.VisitanteProducto v, int cantidad) { exigir(cantidad > 0, "Cantidad inválida"); v.visitarSuscripcion(this, cantidad); }
    }
    public record Item(Producto producto, int cantidad, BigDecimal precioUnitario) implements java.io.Serializable {
        public Item(Producto producto, int cantidad) { this(producto, cantidad, producto.precio); }
        public Item {
            Objects.requireNonNull(producto);
            exigir(cantidad > 0, "La cantidad debe ser positiva");
            exigir(precioUnitario != null && precioUnitario.signum() >= 0, "Precio inválido");
        }
        BigDecimal subtotal() { return precioUnitario.multiply(BigDecimal.valueOf(cantidad)); }
    }
    // HashSet concreto serializable; se mantiene Set como contrato.
    @SuppressWarnings("serial")
    public static final class Cupon implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String codigo;
        public final BigDecimal porcentaje;
        public final LocalDate vencimiento;
        private final Set<String> usados = new HashSet<>();
        public Cupon(String codigo, String porcentaje, LocalDate vencimiento) {
            exigir(codigo != null && !codigo.isBlank(), "Código de cupón requerido");
            this.codigo = Objects.requireNonNull(codigo);
            this.porcentaje = monto(porcentaje);
            this.vencimiento = Objects.requireNonNull(vencimiento);
            exigir(this.porcentaje.signum() >= 0 && this.porcentaje.compareTo(monto("1")) <= 0,
                    "Porcentaje fuera de rango");
        }
        void validar(Cliente cliente, LocalDate fecha) {
            exigir(!fecha.isAfter(vencimiento), "Cupón vencido");
            exigir(!usados.contains(cliente.id()), "El cliente ya utilizó el cupón");
        }
    }
    public record Configuracion(BigDecimal igv, BigDecimal vip, BigDecimal corporativo,
                                BigDecimal volumen10, BigDecimal volumen25, BigDecimal tope,
                                BigDecimal recargoTarjeta) implements java.io.Serializable {
        public Configuracion {
            for (BigDecimal valor : List.of(igv, vip, corporativo, volumen10, volumen25, tope, recargoTarjeta))
                exigir(valor.signum() >= 0 && valor.compareTo(BigDecimal.ONE) <= 0, "Tasa inválida");
        }
        public static Configuracion predeterminada() {
            return new Configuracion(monto("0.18"), monto("0.05"), monto("0.10"), monto("0.03"),
                    monto("0.06"), monto("0.30"), monto("0.03"));
        }
    }
    public record Resumen(BigDecimal subtotal, BigDecimal descuento, BigDecimal envio,
                          BigDecimal recargo, BigDecimal igv, BigDecimal base, BigDecimal total) implements java.io.Serializable {}
    public record Documento(String numero, Resumen resumen) implements java.io.Serializable {}
    public record LineaVenta(String pedido, LocalDate fechaPago, String documento, Resumen resumen) {}
    public record ReporteVentas(List<LineaVenta> lineas, BigDecimal subtotal, BigDecimal descuento,
                                BigDecimal igv, BigDecimal total) {
        public ReporteVentas { lineas = List.copyOf(lineas); }
    }
    public interface PasarelaCobro {
        void cobrar(String pedido, BigDecimal total);
        void reembolsar(String pedido, BigDecimal total);
    }
    public interface ObservadorPedido { void cambio(Pedido pedido, Estado anterior, Estado nuevo); }

    // List.copyOf devuelve listas serializables si sus elementos lo son.
    @SuppressWarnings("serial")
    public static final class Pedido implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public final Cliente cliente;
        public final List<Item> items;
        public final Envio envio;
        public final Pago pago;
        private final Cupon cupon;
        private EstadosPedido.EstadoPedido estado = new EstadosPedido.Pendiente();
        private final MediosPago.MedioPago medioPago;
        private Resumen resumen;
        private Documento documento;
        private List<CalculoTotal.ConceptoTotal> conceptos = List.of();
        private LocalDate fechaPago;
        private boolean accesoHabilitado, reembolsoRegistrado;
        private List<Acceso> accesos = List.of();
        private Pedido(String id, Cliente cliente, List<Item> items, Envio envio, Pago pago, Cupon cupon) {
            this.id = id; this.cliente = Objects.requireNonNull(cliente);
            this.items = List.copyOf(items); this.envio = envio;
            this.pago = Objects.requireNonNull(pago); this.cupon = cupon;
            this.medioPago = MediosPago.para(pago);
            exigir(!items.isEmpty(), "El pedido debe tener ítems");
            exigir(items.stream().allMatch(i -> i.producto().activo()), "El pedido contiene productos dados de baja");
        }
        public Estado estado() { return estado.nombre(); }
        public boolean esVenta() { return estado.esVenta(); }
        public LocalDate fechaPago() { return fechaPago; }
        public Resumen resumen() { return resumen; }
        public Documento documento() { return documento; }
        public List<CalculoTotal.ConceptoTotal> conceptos() { return conceptos; }
        public boolean accesoHabilitado() { return accesoHabilitado; }
        public List<Acceso> accesos() {
            // Compatibilidad: snapshots antiguos solo conservaban indicador y fecha de pago.
            if (accesos == null) return accesoHabilitado
                    ? OperacionesProducto.VisitanteHabilitacion.accesos(items, cliente, fechaPago) : List.of();
            return List.copyOf(accesos);
        }
        public boolean reembolsoRegistrado() { return reembolsoRegistrado; }
        public boolean requiereEnvio() { return items.stream().anyMatch(i -> i.producto.requiereEnvio()); }
    }

    /** Serializa las operaciones locales para evitar descuentos parciales de inventario. */
    // Las listas persistidas son ArrayList; los adaptadores son transitorios.
    @SuppressWarnings("serial")
    public static final class GestorPedidos implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private Configuracion configuracion;
        private ConfiguracionEnvios envios = ConfiguracionEnvios.predeterminada();
        private transient PasarelaCobro pasarela;
        private transient List<ObservadorPedido> observadores = new ArrayList<>();
        private final List<Pedido> pedidos = new ArrayList<>();
        private transient PedidoRepositorio repositorioPedidos;
        private final List<String> erroresNotificacion = new ArrayList<>();
        private long siguientePedido = 1, boleta = 1, factura = 1;
        public GestorPedidos(Configuracion configuracion, PasarelaCobro pasarela) {
            this.configuracion = Objects.requireNonNull(configuracion);
            this.pasarela = Objects.requireNonNull(pasarela);
        }
        public GestorPedidos(Configuracion configuracion, PasarelaCobro pasarela, PedidoRepositorio repositorio) {
            this(configuracion, pasarela);
            Objects.requireNonNull(repositorio);
            exigir(repositorio.todos().isEmpty(), "Un gestor nuevo requiere repositorio vacío; para restaurar use el snapshot");
            this.repositorioPedidos = repositorio;
        }
        /** Mantiene el formato persistido anterior, incluso con un adaptador inyectado. */
        private synchronized void writeObject(java.io.ObjectOutputStream salida) throws java.io.IOException {
            List<Pedido> actuales = repositorio().todos();
            pedidos.clear(); pedidos.addAll(actuales);
            salida.defaultWriteObject();
        }
        public synchronized void configurar(Configuracion configuracion) { this.configuracion = Objects.requireNonNull(configuracion); }
        public synchronized ConfiguracionEnvios configuracionEnvios() {
            // Snapshots anteriores a esta funcionalidad no contienen este campo.
            return envios == null ? ConfiguracionEnvios.predeterminada() : envios;
        }
        public synchronized void configurarEnvios(ConfiguracionEnvios envios) { this.envios = Objects.requireNonNull(envios); }
        private PedidoRepositorio repositorio() {
            if (repositorioPedidos == null) repositorioPedidos = new PedidosEnMemoria(pedidos);
            return repositorioPedidos;
        }
        public synchronized List<Pedido> pedidos() { return repositorio().todos(); }
        public synchronized List<Pedido> transferenciasPendientes() { return repositorio().transferenciasPendientes(); }
        public synchronized void conectar(PasarelaCobro pasarela) {
            this.pasarela = Objects.requireNonNull(pasarela);
            this.observadores = new ArrayList<>();
        }
        public synchronized void observar(ObservadorPedido observador) { observadores.add(Objects.requireNonNull(observador)); }
        public synchronized List<String> erroresNotificacion() { return List.copyOf(erroresNotificacion); }
        public synchronized Pedido registrar(Cliente cliente, List<Item> items, Envio envio, Pago pago,
                                              Cupon cupon, LocalDate fecha) {
            exigir(fecha != null, "Fecha requerida");
            Pedido p = new Pedido("P%08d".formatted(siguientePedido++), cliente, items, envio, pago, cupon);
            p.resumen = calcular(p, fecha);
            if (p.medioPago.nacePagado()) confirmarInterno(p, fecha);
            repositorio().agregar(p);
            return p;
        }
        public synchronized Resumen calcular(Pedido p, LocalDate fecha) {
            exigir(fecha != null, "Fecha requerida");
            Resumen resumen = CalculoTotal.calcular(construirConceptos(p, fecha));
            p.medioPago.validar(p, resumen.total());
            return resumen;
        }
        private List<CalculoTotal.ConceptoTotal> construirConceptos(Pedido p, LocalDate fecha) {
            if (p.cupon != null) p.cupon.validar(p.cliente, fecha);
            return new CalculoTotal.ConstructorConceptos().construir(p, configuracion,
                    p.cupon == null ? BigDecimal.ZERO : p.cupon.porcentaje, configuracionEnvios());
        }
        public synchronized void pagar(Pedido p, LocalDate fecha) {
            propio(p);
            exigir(!p.medioPago.exigeConfirmacion(), "Un operador debe confirmar el depósito");
            confirmarInterno(p, fecha);
        }
        public synchronized void confirmarDeposito(Pedido p, LocalDate fecha) {
            propio(p);
            exigir(p.medioPago.exigeConfirmacion(), "El pedido no usa transferencia");
            confirmarInterno(p, fecha);
        }
        private void confirmarInterno(Pedido p, LocalDate fecha) {
            exigir(fecha != null, "Fecha requerida");
            p.estado.pagar();
            p.cliente.validarPago();
            List<CalculoTotal.ConceptoTotal> conceptos = construirConceptos(p, fecha);
            Resumen resumen = CalculoTotal.calcular(conceptos);
            p.medioPago.validar(p, resumen.total());
            OperacionesProducto.VisitanteStock stock = OperacionesProducto.VisitanteStock.para(p.items);
            stock.validar();
            List<Acceso> accesos = OperacionesProducto.VisitanteHabilitacion.accesos(p.items, p.cliente, fecha);
            p.medioPago.cobrar(pasarela, p.id, resumen.total());
            stock.descontar();
            if (p.cupon != null) p.cupon.usados.add(p.cliente.id());
            p.resumen = resumen; p.fechaPago = fecha;
            p.conceptos = conceptos;
            p.documento = new Documento(p.cliente.tipo() == TipoCliente.CORPORATIVO
                    ? "F001-%08d".formatted(factura++) : "B001-%08d".formatted(boleta++), resumen);
            p.accesos = accesos;
            p.accesoHabilitado = !p.accesos.isEmpty();
            cambiar(p, Estado.PAGADO);
            if (!p.requiereEnvio()) { p.estado.entregarDigital(); cambiar(p, Estado.ENTREGADO); }
        }
        public synchronized void despachar(Pedido p) {
            propio(p); p.estado.despachar(); cambiar(p, Estado.ENVIADO);
        }
        public synchronized void entregar(Pedido p) {
            propio(p);
            p.estado.recibir(); cambiar(p, Estado.ENTREGADO);
        }
        public synchronized void cancelar(Pedido p) {
            propio(p);
            p.estado.cancelar();
            if (p.estado.reembolsaAlCancelar()) {
                OperacionesProducto.VisitanteStock stock = OperacionesProducto.VisitanteStock.para(p.items);
                stock.validarReposicion();
                p.medioPago.reversar(pasarela, p.id, p.resumen.total());
                stock.reponer();
                p.reembolsoRegistrado = true;
            }
            cambiar(p, Estado.CANCELADO);
        }
        public synchronized List<Pedido> ventas(LocalDate desde, LocalDate hasta) {
            return repositorio().ventas(desde, hasta);
        }
        public synchronized ReporteVentas reporte(LocalDate desde, LocalDate hasta) {
            List<LineaVenta> lineas = ventas(desde, hasta).stream()
                    .map(p -> new LineaVenta(p.id, p.fechaPago, p.documento.numero(), p.documento.resumen())).toList();
            BigDecimal subtotal = monto("0.00"), descuento = monto("0.00"), igv = monto("0.00"), total = monto("0.00");
            for (LineaVenta linea : lineas) {
                Resumen r = linea.resumen();
                subtotal = subtotal.add(r.subtotal()); descuento = descuento.add(r.descuento());
                igv = igv.add(r.igv()); total = total.add(r.total());
            }
            return new ReporteVentas(lineas, subtotal, descuento, igv, total);
        }
        private void propio(Pedido p) {
            exigir(p != null, "Pedido requerido");
            exigir(repositorio().buscar(p.id) == p, "Pedido ajeno al gestor");
        }
        private void cambiar(Pedido p, Estado nuevo) {
            Estado anterior = p.estado(); p.estado = EstadosPedido.para(nuevo);
            for (ObservadorPedido o : List.copyOf(observadores)) {
                try { o.cambio(p, anterior, nuevo); }
                catch (RuntimeException e) {
                    erroresNotificacion.add(p.id + " | " + anterior + " → " + nuevo + " | "
                            + o.getClass().getSimpleName() + " | " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }
}
