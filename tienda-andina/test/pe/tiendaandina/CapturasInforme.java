package pe.tiendaandina;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static pe.tiendaandina.Dominio.*;

/** Recorre la UI real y captura sus estados con datos separados de los del usuario. */
public final class CapturasInforme {
    private record Paso(String ventana, Consumer<Window> accion) {}
    private static final Queue<Paso> pasos = new ArrayDeque<>();
    private static final StringBuilder informe = new StringBuilder();
    private static Aplicacion app;
    private static Path destino;
    private static int numero;
    private static Object campo(String nombre) {
        try { var f = Aplicacion.class.getDeclaredField(nombre); f.setAccessible(true); return f.get(app); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static <T> List<T> componentes(Component c, Class<T> tipo) {
        List<T> salida = new ArrayList<>(); if (tipo.isInstance(c)) salida.add(tipo.cast(c));
        if (c instanceof Container contenedor) for (Component hijo : contenedor.getComponents()) salida.addAll(componentes(hijo, tipo));
        return salida;
    }
    private static void click(Window w, String texto) {
        JButton b = componentes(w, JButton.class).stream().filter(x -> texto.equals(x.getText())).findFirst().orElseThrow();
        if (!b.isEnabled()) throw new IllegalStateException("Botón deshabilitado: " + texto); b.doClick();
    }
    private static void opcion(Window w, int valor) {
        JOptionPane pane = componentes(w, JOptionPane.class).get(0);
        Object[] opciones = pane.getOptions();
        pane.setValue(opciones != null && valor >= 0 && valor < opciones.length ? opciones[valor] : valor);
    }
    private static void captura(Window w, String nombre, String titulo, String texto) {
        try {
            w.invalidate(); w.validate();
            BufferedImage imagen = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagen.createGraphics(); w.paint(g); g.dispose();
            String archivo = "%02d_%s.png".formatted(++numero, nombre);
            javax.imageio.ImageIO.write(imagen, "png", destino.resolve(archivo).toFile());
            informe.append("Imagen ").append(numero).append(". ").append(titulo).append("\n")
                    .append("Archivo: ").append(archivo).append("\n\n").append(texto).append("\n\n");
            System.out.println("Capturada: " + archivo);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static void principal(Consumer<Window> accion) { pasos.add(new Paso("APP", accion)); }
    private static void dialogo(String titulo, Consumer<Window> accion) { pasos.add(new Paso(titulo, accion)); }
    private static void tab(int indice) { ((JTabbedPane) campo("paginas")).setSelectedIndex(indice); }
    private static void elegirProducto(String codigo) {
        JComboBox<?> combo = (JComboBox<?>) campo("productos");
        for (int i = 0; i < combo.getItemCount(); i++) if (((Producto) combo.getItemAt(i)).codigo.equals(codigo)) { combo.setSelectedIndex(i); return; }
        throw new IllegalStateException("Producto no encontrado: " + codigo);
    }
    private static void compra(Window w, String codigo, int cantidad, String envio, Pago pago, String cupon) {
        tab(0); elegirProducto(codigo); ((JSpinner) campo("cantidad")).setValue(cantidad);
        ((JComboBox<?>) campo("envio")).setSelectedItem(envio); ((JComboBox<?>) campo("pago")).setSelectedItem(pago);
        ((JTextField) campo("codigoCupon")).setText(cupon); click(w, "Agregar ítem");
    }
    private static void pedido(int indice) { tab(1); ((JComboBox<?>) campo("seleccion")).setSelectedIndex(indice); }
    private static void usuario(String nombre) {
        principal(w -> click(w, "Cambiar usuario"));
        dialogo("Tienda Andina — iniciar sesión", w -> entrar(w, nombre));
    }
    private static void entrar(Window w, String nombre) {
        componentes(w, JTextField.class).stream().filter(t -> !(t instanceof JPasswordField)).findFirst().orElseThrow().setText(nombre);
        componentes(w, JPasswordField.class).get(0).setText("Demo2026!"); opcion(w, JOptionPane.OK_OPTION);
    }
    private static void llenar(Window w, String... valores) {
        List<JTextField> campos = componentes(w, JTextField.class);
        if (campos.size() != valores.length) throw new IllegalStateException("Número de campos inesperado: " + campos.size());
        for (int i = 0; i < valores.length; i++) campos.get(i).setText(valores[i]);
    }
    private static void programa() {
        dialogo("Tienda Andina — iniciar sesión", w -> {
            captura(w, "login", "Inicio de sesión y acceso por roles",
                    "El aplicativo solicita usuario y contraseña antes de permitir el acceso. La cuenta autenticada determina las operaciones disponibles para Cliente, Operador o Administrador. Las cuentas mostradas son exclusivamente de demostración."); entrar(w, "cliente");
        });
        principal(w -> captura(w, "inicio_cliente", "Pantalla inicial del cliente",
                "La pantalla de compra presenta el catálogo, la cantidad, el tipo de envío, el medio de pago y el cupón opcional. El carrito se muestra separado del formulario y los indicadores superiores resumen los pedidos de la sesión del cliente."));
        principal(w -> {
            compra(w, "F01", 1, "ESTANDAR", Pago.TARJETA, ""); elegirProducto("D01"); click(w, "Agregar ítem");
            captura(w, "carrito_mixto", "Carrito con productos físicos y digitales",
                    "El cliente agrega una mochila y un curso digital. La tabla permite revisar cantidades, precios e importes antes de registrar el pedido. El subtotal mostrado corresponde a los productos; los descuentos, el envío y el recargo se calculan al registrar."); click(w, "Registrar pedido");
        });
        principal(w -> { captura(w, "pedido_pendiente", "Pedido registrado en estado PENDIENTE",
                "Al registrar el carrito se crea el pedido y se presenta el desglose de subtotal, descuentos, envío, recargo, base imponible, IGV incluido y total. El comprobante todavía no se ha emitido y el stock se descuenta únicamente cuando se confirma el pago."); click(w, "Pagar"); });
        principal(w -> { captura(w, "pedido_pagado", "Confirmación de pago y emisión de boleta",
                "La confirmación cambia el pedido a PAGADO, descuenta el stock del producto físico, genera la boleta y habilita el acceso al curso digital. El cobro se realiza mediante una pasarela simulada. Al contener un artículo físico, el pedido debe pasar por despacho y recepción."); click(w, "Guardar ticket"); });
        dialogo("Guardar ticket", w -> {
            JFileChooser selector = componentes(w, JFileChooser.class).get(0);
            selector.setSelectedFile(destino.resolve("ejemplo_ticket.txt").toFile());
            captura(w, "guardar_ticket", "Exportación del ticket de compra",
                    "El usuario puede guardar el ticket como archivo de texto. El contenido utiliza los importes del comprobante emitido, sin recalcularlos, e incluye la advertencia de que se trata de una demostración sin validez tributaria."); selector.approveSelection();
        });
        principal(w -> { tab(3); captura(w, "actividad_pago", "Registro de actividad y notificación del pago",
                "La sección Actividad registra la operación de cobro simulada y el aviso de cambio de estado. Las notificaciones se envían por observadores desacoplados del pedido; en esta versión los canales de correo y SMS son simulados.");
            compra(w, "F02", 2, "ESTANDAR", Pago.TRANSFERENCIA, ""); click(w, "Registrar pedido"); });
        principal(w -> captura(w, "transferencia_pendiente", "Pedido con transferencia pendiente de confirmación",
                "Cuando se elige transferencia, el pedido permanece en PENDIENTE. El cliente no puede confirmar el depósito; la acción corresponde al operador. Esta separación evita tratar una transferencia no verificada como una venta."));
        principal(w -> { compra(w, "F02", 10000, "ESTANDAR", Pago.TARJETA, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        dialogo("Operación rechazada", w -> { captura(w, "error_stock", "Validación de inventario insuficiente",
                "Si la cantidad solicitada supera el inventario disponible, se rechaza el pago y el pedido permanece pendiente. La validación del lote se realiza antes del cobro y del descuento de existencias, evitando modificaciones parciales del stock."); opcion(w, JOptionPane.OK_OPTION); });
        principal(w -> { compra(w, "D01", 1, "SIN ENVÍO", Pago.YAPE, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "digital_entregado", "Entrega automática de un producto digital",
                "Un pedido que contiene únicamente productos digitales no selecciona envío ni afecta stock. Al confirmar el pago pasa de PAGADO a ENTREGADO automáticamente y registra el acceso digital del cliente. No se implementa una descarga externa real."));
        usuario("operador");
        principal(w -> { pedido(1); captura(w, "operador", "Vista del operador y permisos disponibles",
                "El operador dispone de las acciones de confirmación de depósito y despacho. Las acciones exclusivas del cliente y del administrador permanecen deshabilitadas. Los permisos se validan también en los controladores, no solamente en la interfaz."); click(w, "Transferencias pendientes"); tab(3); });
        principal(w -> { captura(w, "lista_transferencias", "Consulta de transferencias pendientes",
                "El operador consulta los pedidos que esperan verificación bancaria. El listado utiliza el repositorio de pedidos y muestra los importes que deben contrastarse con el depósito antes de confirmar el pago."); pedido(1); click(w, "Confirmar depósito"); });
        principal(w -> { captura(w, "deposito_confirmado", "Confirmación del depósito por el operador",
                "Tras confirmar el depósito, el pedido pasa a PAGADO, se descuenta el inventario y se emite la boleta. La confirmación manual no aplica recargo por medio de pago."); click(w, "Despachar"); });
        principal(w -> captura(w, "pedido_enviado", "Despacho del pedido y estado ENVIADO",
                "El operador registra el despacho de un pedido pagado. El sistema cambia el estado a ENVIADO y notifica el cambio. Desde este estado ya no se permite cancelar el pedido; la siguiente transición es la confirmación de recepción."));
        usuario("cliente");
        principal(w -> { pedido(0); click(w, "Cancelar"); });
        dialogo("Confirmar cancelación", w -> { captura(w, "confirmacion_cancelacion", "Confirmación previa a cancelar un pedido pagado",
                "La interfaz solicita confirmación antes de cancelar e informa que se repondrá el stock y se registrará un reembolso simulado. Esta advertencia ayuda a prevenir cancelaciones accidentales."); opcion(w, JOptionPane.YES_OPTION); });
        principal(w -> { captura(w, "pedido_cancelado", "Pedido cancelado con reposición de stock",
                "El pedido pagado se cancela, se repone el inventario físico y se registra el reembolso. Se conserva el documento original como registro histórico, pero el pedido CANCELADO deja de formar parte del reporte de ventas."); pedido(1); click(w, "Recibir"); });
        principal(w -> captura(w, "recepcion", "Confirmación de recepción y estado ENTREGADO",
                "El cliente confirma la recepción del pedido previamente enviado. El estado cambia a ENTREGADO y las acciones de pago, despacho, recepción y cancelación dejan de estar disponibles cuando ya no corresponden al ciclo de vida."));
        usuario("vip");
        principal(w -> { compra(w, "F01", 1, "ESTANDAR", Pago.YAPE, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "beneficios_vip", "Descuento y envío estándar gratuito del cliente VIP",
                "Para un cliente VIP se aplica el 5 % de descuento sobre el subtotal y el envío estándar es gratuito. El resumen muestra ambos beneficios sin alterar la regla del IGV incluido. El pedido físico queda pagado y a la espera de despacho."));
        usuario("operador");
        principal(w -> { pedido(4); click(w, "Despachar"); tab(3); });
        principal(w -> captura(w, "sms_vip", "Correo y SMS simulado al despachar un pedido VIP",
                "Al pasar a ENVIADO se registra el correo y, adicionalmente, el SMS para el cliente VIP. Esta política adicional también aplica a corporativos, pero no a clientes regulares. Ambos canales siguen siendo simulados."));
        usuario("empresa");
        principal(w -> { compra(w, "D01", 1, "SIN ENVÍO", Pago.YAPE, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "factura_corporativa", "Pedido corporativo y emisión de factura",
                "La cuenta corporativa de demostración cuenta con RUC registrado. Se aplica el descuento del 10 % y se emite una factura con serie F001, independiente del correlativo de boletas B001. Al tratarse de un producto digital, el pedido se entrega automáticamente."));
        usuario("admin");
        principal(w -> { click(w, "Reporte de ventas"); captura(w, "reporte_admin", "Panel de administración y reporte por fechas",
                "El administrador consulta un rango de fechas y obtiene subtotal, descuento, IGV y total por pedido, además del acumulado general. Se incluyen solo los estados PAGADO, ENVIADO y ENTREGADO; se excluyen pendientes y cancelados. Los valores provienen de los comprobantes conservados."); click(w, "Crear cupón"); });
        dialogo("Crear cupón de demostración", w -> {
            llenar(w, "ANDINA10", "10", LocalDate.now().plusDays(30).toString());
            captura(w, "crear_cupon", "Creación de un cupón promocional",
                    "El administrador registra un código, un porcentaje y una fecha de vencimiento. El sistema impide códigos duplicados y valida el vencimiento y el uso único por cliente al registrar o pagar el pedido. El cupón se suma al mayor descuento entre cliente y volumen, con tope global del 30 %."); opcion(w, JOptionPane.OK_OPTION);
        });
        principal(w -> click(w, "Crear cupón"));
        dialogo("Crear cupón de demostración", w -> { llenar(w, "VENCIDO", "10", LocalDate.now().minusDays(1).toString()); opcion(w, JOptionPane.OK_OPTION); });
        principal(w -> click(w, "Administrar catálogo"));
        dialogo("Catálogo (incluye bajas)", w -> { captura(w, "catalogo", "Administración del catálogo de productos",
                "El administrador puede crear productos, editar datos y efectuar una baja lógica. El catálogo contempla artículos físicos, digitales y suscripciones. La baja no elimina los pedidos ni los comprobantes históricos."); opcion(w, 0); });
        dialogo("Nuevo producto", w -> { llenar(w, "PTEST", "Agenda Andina", "25.00", "0.40", "30");
            captura(w, "alta_producto", "Alta de un producto físico",
                    "El formulario solicita código, nombre, tipo, precio con IGV, peso y stock. El sistema exige códigos únicos y valores no negativos; para digitales y suscripciones el peso y el stock deben ser cero."); opcion(w, JOptionPane.OK_OPTION); });
        principal(w -> click(w, "Administrar catálogo"));
        dialogo("Catálogo (incluye bajas)", w -> { JComboBox<?> c = componentes(w, JComboBox.class).get(0); c.setSelectedIndex(c.getItemCount()-1); opcion(w, 1); });
        dialogo("Editar producto", w -> { llenar(w, "PTEST", "Agenda Andina Plus", "29.90", "0.40", "30");
            captura(w, "editar_producto", "Modificación de los datos de catálogo",
                    "El administrador actualiza nombre, precio, peso o inventario. Código y tipo no se modifican. Los precios de los ítems ya agregados y los importes de comprobantes emitidos se conservan para evitar cambios retroactivos en las ventas."); opcion(w, JOptionPane.OK_OPTION); });
        principal(w -> click(w, "Administrar catálogo"));
        dialogo("Catálogo (incluye bajas)", w -> { JComboBox<?> c = componentes(w, JComboBox.class).get(0); c.setSelectedIndex(c.getItemCount()-1); opcion(w, 2); });
        dialogo("Baja lógica", w -> { captura(w, "baja_producto", "Confirmación de baja lógica de un producto",
                "Antes de dar de baja un producto se solicita confirmación. El registro queda conservado, pero deja de aparecer como opción para nuevas compras. Los pedidos existentes pueden continuar su ciclo de vida."); opcion(w, JOptionPane.YES_OPTION); });
        principal(w -> click(w, "Administrar envíos"));
        dialogo("Administrar envíos", w -> { captura(w, "envios", "Configuración de tarifas y cobertura de envío",
                "El administrador modifica el costo fijo y por kilogramo de estándar y express, así como las ciudades atendidas por express. Se mantienen las reglas de estándar gratuito para VIP y recojo gratuito en Chimbote. Los cambios se guardan localmente y no alteran comprobantes ya emitidos."); opcion(w, JOptionPane.CANCEL_OPTION); });
        principal(w -> { click(w, "Recargar parámetros"); tab(3); captura(w, "parametros", "Recarga de parámetros sin recompilar",
                "El porcentaje de IGV y los descuentos se cargan desde config/negocio.properties. La acción Recargar parámetros permite aplicar una configuración válida sin recompilar. Los importes históricos quedan conservados; los pedidos pendientes se recalculan al confirmar el pago."); tab(2); click(w, "Errores de notificación"); });
        principal(w -> captura(w, "errores_notificacion", "Consulta del registro de errores de notificación",
                "El administrador puede consultar fallos de los observadores. En esta ejecución los canales simulados respondieron correctamente y el contador es cero. Las pruebas automatizadas verifican por separado que una falla de notificación no revierte el estado del pedido ni bloquea los demás avisos."));
        usuario("cliente");
        principal(w -> { compra(w, "D01", 1, "SIN ENVÍO", Pago.YAPE, "VENCIDO"); click(w, "Registrar pedido"); });
        dialogo("Operación rechazada", w -> { captura(w, "cupon_vencido", "Rechazo de un cupón vencido",
                "Cuando el cliente intenta registrar un pedido con cupón vencido, la operación se rechaza con un mensaje explícito. No se confirma el pago ni se consume el cupón. El carrito permanece disponible para corregir la compra."); opcion(w, JOptionPane.OK_OPTION); });
        principal(w -> { ((JTextField) campo("codigoCupon")).setText("ANDINA10"); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "cupon_aplicado", "Compra con cupón válido y descuento registrado",
                "Después de corregir el código, se aplica el cupón vigente del 10 %. El resumen y el comprobante muestran el descuento y el total resultante. Al confirmar el pago se registra el uso del cupón para ese cliente y se habilita el acceso digital."));
        principal(w -> { compra(w, "D01", 1, "SIN ENVÍO", Pago.YAPE, "ANDINA10"); click(w, "Registrar pedido"); });
        dialogo("Operación rechazada", w -> { captura(w, "cupon_reutilizado", "Control de uso único del cupón por cliente",
                "El sistema rechaza un segundo intento de utilización del mismo cupón por el cliente que ya lo consumió. Esta restricción se mantiene en los datos guardados, evitando usos repetidos tras reiniciar la aplicación."); opcion(w, JOptionPane.OK_OPTION); });
        usuario("admin");
        principal(w -> { click(w, "Reporte de ventas"); captura(w, "reporte_final", "Reporte consolidado tras completar los flujos",
                "El reporte final reúne las ventas válidas obtenidas durante la demostración. Los pedidos cancelados y aquellos rechazados o pendientes por falta de stock no se suman. El renglón TOTALES permite comparar los importes con los comprobantes individuales."); w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING)); });
        pasos.add(new Paso("INICIAR", w -> iniciar()));
        dialogo("Tienda Andina — iniciar sesión", w -> entrar(w, "admin"));
        principal(w -> { click(w, "Reporte de ventas"); captura(w, "persistencia", "Recuperación de datos después de cerrar y abrir",
                "La aplicación se cierra y se inicia nuevamente sobre el directorio de demostración. Se recuperan los pedidos, los estados, los comprobantes, los usos de cupón, el catálogo y los correlativos. El reporte muestra las ventas conservadas y evidencia la persistencia local."); });
        usuario("cliente");
        principal(w -> { compra(w, "F02", 1, "ESTANDAR", Pago.CONTRAENTREGA, ""); click(w, "Registrar pedido"); });
        principal(w -> captura(w, "contraentrega", "Registro de un pedido contraentrega",
                "Para el cliente regular con envío estándar, contraentrega considera el pedido PAGADO desde el registro. Se emite la boleta y se descuenta el stock sin invocar un cobro bancario externo. La regla no admite envío express, recojo ni cliente corporativo."));
        principal(w -> { compra(w, "S01", 1, "SIN ENVÍO", Pago.YAPE, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "suscripcion", "Pago del primer mes de una suscripción",
                "La suscripción cobra el primer mes, no requiere peso, stock ni envío y se entrega automáticamente al pagar. El pedido registra el acceso local a la biblioteca mensual. No se implementan renovaciones automáticas ni cobros futuros."));
        principal(w -> { compra(w, "F01", 1, "EXPRESS", Pago.TARJETA, ""); click(w, "Registrar pedido"); });
        principal(w -> captura(w, "express", "Cálculo de envío express y recargo de tarjeta",
                "El cliente de Chimbote puede seleccionar express según la cobertura configurada. El envío considera costo fijo más tarifa por kilogramo; el recargo de tarjeta se calcula sobre subtotal menos descuentos más envío, y el IGV se desglosa del total incluido."));
        principal(w -> { compra(w, "F02", 10, "RECOJO", Pago.YAPE, ""); click(w, "Registrar pedido"); click(w, "Pagar"); });
        principal(w -> captura(w, "volumen_recojo", "Descuento por volumen y recojo gratuito",
                "El pedido reúne diez unidades físicas y obtiene el descuento por volumen del 3 %. El recojo en la tienda de Chimbote tiene costo cero. El volumen cuenta únicamente unidades físicas; se compara con el beneficio por tipo de cliente para aplicar el mayor, no sumar ambos."));
        principal(w -> { compra(w, "D01", 5, "SIN ENVÍO", Pago.YAPE, ""); click(w, "Registrar pedido"); });
        dialogo("Operación rechazada", w -> { captura(w, "limite_yape", "Validación del monto máximo permitido para Yape",
                "Un pedido de cinco cursos digitales supera S/ 500 y se rechaza al seleccionar Yape. El mensaje comunica el límite del medio de pago y el carrito queda disponible para modificar la cantidad o elegir otro medio. No se produce un cobro ni una venta."); opcion(w, JOptionPane.OK_OPTION); });
        usuario("admin");
        principal(w -> { click(w, "Reporte de ventas"); captura(w, "reporte_completo", "Reporte de ventas incluyendo todos los medios y tipos de producto",
                "El reporte ampliado incorpora las ventas válidas de artículos físicos, productos digitales y suscripciones. Incluye el pedido contraentrega y el caso de volumen con recojo. El pedido express todavía pendiente y las operaciones rechazadas no se incluyen en los totales."); w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING)); });
    }
    private static void iniciar() {
        try { app = new Aplicacion(); var metodo = Aplicacion.class.getDeclaredMethod("iniciar"); metodo.setAccessible(true); metodo.invoke(app); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Se necesita un escritorio gráfico");
        destino = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(destino);
        if (Files.exists(destino.resolve("Texto_para_informe.txt"))) throw new IllegalStateException("La carpeta ya contiene un informe; utilice una nueva");
        Path datos = Files.createTempDirectory("tienda-capturas-"); Files.createDirectories(datos.resolve("config"));
        Files.copy(Path.of("config/negocio.properties"), datos.resolve("config/negocio.properties"));
        System.setProperty("tienda.directorio", datos.toString());
        informe.append("3. APLICATIVO JAVA SWING — TIENDA ANDINA\n\n")
                .append("Texto sugerido para el informe. Insertar cada PNG después de su título y usar el párrafo como explicación. La numeración puede adaptarse a las figuras previas del documento.\n\n")
                .append("El aplicativo implementa los flujos de pedidos definidos en la práctica, con acceso separado para Cliente, Operador y Administrador. Se desarrolló en Java Swing y utiliza persistencia local. Las siguientes capturas corresponden a una ejecución real con datos de demostración aislados; pagos y notificaciones son simulados.\n\n");
        programa();
        CountDownLatch fin = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        javax.swing.Timer reloj = new javax.swing.Timer(260, null);
        reloj.addActionListener(evento -> {
            if (error.get() != null || pasos.isEmpty()) { reloj.stop(); fin.countDown(); return; }
            if (System.nanoTime() > limite) { error.set(new IllegalStateException("Tiempo agotado esperando " + pasos.peek().ventana())); reloj.stop(); fin.countDown(); return; }
            Paso paso = pasos.peek(); Window elegida = null;
            if (!paso.ventana().equals("INICIAR")) {
                for (Window w : Window.getWindows()) if (w.isShowing()) {
                    String titulo = w instanceof JFrame f ? f.getTitle() : w instanceof JDialog d ? d.getTitle() : "";
                    if ((paso.ventana().equals("APP") && w instanceof JFrame && titulo.startsWith("Tienda Andina"))
                            || (!paso.ventana().equals("APP") && titulo.equals(paso.ventana()))) { elegida = w; break; }
                }
                if (elegida == null) return;
            }
            pasos.remove(); Window objetivo = elegida;
            SwingUtilities.invokeLater(() -> { try { paso.accion().accept(objetivo); } catch (Throwable e) { error.set(e); } });
        });
        SwingUtilities.invokeLater(() -> { reloj.start(); SwingUtilities.invokeLater(CapturasInforme::iniciar); });
        fin.await(); SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) w.dispose(); });
        if (error.get() != null) throw new AssertionError("Capturas incompletas; datos aislados: " + datos, error.get());
        informe.append("CONCLUSIÓN SUGERIDA\n\nLa demostración recorre registro, cálculo, pago, despacho, entrega y cancelación de pedidos. También evidencia validaciones de stock y cupones, separación de permisos y conservación de los datos. Los patrones State, Strategy, Visitor, Observer y Repository distribuyen estas responsabilidades. No se afirma equivalencia con el código original ausente ni integración real con banco, SUNAT, SMTP o SMS. Las diferencias concretas frente al diagrama están documentadas en ARQUITECTURA.md.\n\n")
                .append("EVIDENCIA Y ALCANCE\nCapturas PNG obtenidas de ventanas y diálogos reales de Swing durante el recorrido automatizado. No se retocaron importes ni se generaron pantallas ficticias. El archivo ejemplo_ticket.txt se exportó desde Guardar ticket. Las pruebas adicionales incluyen casos no fotografiados (por ejemplo, RUC inválido y fallos de pasarela).\n")
                .append("Fecha de ejecución: ").append(LocalDate.now()).append("\nTotal de imágenes: ").append(numero).append("\n");
        Files.writeString(destino.resolve("Texto_para_informe.txt"), informe);
        System.out.println("LISTO: " + numero + " imágenes y texto para el informe en " + destino);
    }
}
