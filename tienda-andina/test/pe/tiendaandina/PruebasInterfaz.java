package pe.tiendaandina;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import static pe.tiendaandina.Dominio.*;

/** Prueba de humo real de Swing con directorio temporal; no toca el catálogo del usuario. */
public final class PruebasInterfaz {
    private static Object campo(Object objeto, String nombre) throws ReflectiveOperationException {
        Field f = objeto.getClass().getDeclaredField(nombre); f.setAccessible(true); return f.get(objeto);
    }
    private static <T> T encontrar(Component c, Class<T> tipo, java.util.function.Predicate<T> filtro) {
        if (tipo.isInstance(c) && filtro.test(tipo.cast(c))) return tipo.cast(c);
        if (c instanceof Container contenedor) for (Component hijo : contenedor.getComponents()) {
            T resultado = encontrar(hijo, tipo, filtro); if (resultado != null) return resultado;
        }
        return null;
    }
    private static JButton boton(JFrame ventana, String texto) {
        JButton b = encontrar(ventana, JButton.class, candidato -> candidato.getText().equals(texto));
        if (b == null) throw new AssertionError("Botón ausente: " + texto); return b;
    }
    private static void comprobar(boolean valor, String mensaje) { if (!valor) throw new AssertionError(mensaje); }
    private static void captura(JFrame ventana, String sufijo) throws java.io.IOException {
        String base = System.getProperty("tienda.captura"); if (base == null) return;
        ventana.invalidate(); ventana.validate();
        BufferedImage imagen = new BufferedImage(ventana.getWidth(), ventana.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics(); ventana.paint(g); g.dispose();
        javax.imageio.ImageIO.write(imagen, "png", Path.of(base.replace(".png", "-" + sufijo + ".png")).toFile());
    }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Esta prueba necesita DISPLAY; no se cuenta como aprobada en headless");
        Path temporal = Files.createTempDirectory("tienda-ui-");
        Files.createDirectories(temporal.resolve("config"));
        Files.copy(Path.of("config/negocio.properties"), temporal.resolve("config/negocio.properties"));
        System.setProperty("tienda.directorio", temporal.toString());
        CountDownLatch fin = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Aplicacion[] app = {null}; int[] fase = {0}, ticks = {0};
        javax.swing.Timer reloj = new javax.swing.Timer(150, null);
        Runnable iniciar = () -> {
            try {
                app[0] = new Aplicacion(); var metodo = Aplicacion.class.getDeclaredMethod("iniciar");
                metodo.setAccessible(true); metodo.invoke(app[0]);
            } catch (Throwable e) { error.set(e); reloj.stop(); fin.countDown(); }
        };
        reloj.addActionListener(evento -> {
            try {
                if (++ticks[0] > 120) throw new AssertionError("La interfaz no completó el arranque en 18 segundos");
                for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof JDialog dialogo) {
                    if (!dialogo.getTitle().contains("iniciar sesión")) throw new AssertionError("Diálogo inesperado: " + dialogo.getTitle());
                    JTextField usuario = encontrar(dialogo, JTextField.class, t -> !(t instanceof JPasswordField));
                    JPasswordField clave = encontrar(dialogo, JPasswordField.class, t -> true);
                    usuario.setText(switch (fase[0]) { case 0, 2 -> "cliente"; case 1 -> "operador"; default -> "admin"; });
                    clave.setText("Demo2026!");
                    JOptionPane pane = encontrar(dialogo, JOptionPane.class, p -> true);
                    pane.setValue(JOptionPane.OK_OPTION); return;
                }
                for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof JFrame ventana && ventana.getTitle().startsWith("Tienda Andina")) {
                    if (fase[0] == 0) {
                        comprobar(!boton(ventana, "Administrar catálogo").isEnabled(), "Cliente con permiso admin");
                        ((JComboBox<?>) campo(app[0], "envio")).setSelectedItem("ESTANDAR");
                        comprobar(!boton(ventana, "Registrar pedido").isEnabled(), "Registro habilitado sin carrito");
                        boton(ventana, "Agregar ítem").doClick();
                        var modelo = (javax.swing.table.DefaultTableModel) campo(app[0], "filasCarrito");
                        comprobar(modelo.getRowCount() == 1, "El carrito no muestra el producto");
                        JTable tabla = encontrar(ventana, JTable.class, t -> t.getModel() == modelo);
                        tabla.setRowSelectionInterval(0, 0); boton(ventana, "Quitar ítem").doClick();
                        comprobar(modelo.getRowCount() == 0, "No quitó la fila del carrito");
                        boton(ventana, "Agregar ítem").doClick(); captura(ventana, "carrito");
                        boton(ventana, "Registrar pedido").doClick(); boton(ventana, "Pagar").doClick();
                        comprobar(!boton(ventana, "Pagar").isEnabled(), "Pago duplicado habilitado");
                        Object objeto = campo(app[0], "todos");
                        Pedido p = (Pedido) ((List<?>) objeto).get(0);
                        comprobar(p.estado() == Estado.PAGADO && p.documento() != null, "No se completó compra desde UI");
                        ventana.invalidate(); ventana.validate();
                        BufferedImage captura = new BufferedImage(ventana.getWidth(), ventana.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = captura.createGraphics(); ventana.paint(g); g.dispose();
                        Path imagen = Path.of(System.getProperty("tienda.captura", temporal.resolve("interfaz.png").toString()));
                        javax.imageio.ImageIO.write(captura, "png", imagen.toFile());
                        fase[0] = 1; ventana.dispatchEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
                        SwingUtilities.invokeLater(iniciar); return;
                    }
                    if (fase[0] == 1) {
                        comprobar(boton(ventana, "Despachar").isEnabled() && !boton(ventana, "Pagar").isEnabled(), "Permisos del operador incorrectos");
                        boton(ventana, "Despachar").doClick();
                        Pedido p = (Pedido) ((List<?>) campo(app[0], "todos")).get(0);
                        comprobar(p.estado() == Estado.ENVIADO, "Operador no despachó");
                        fase[0] = 2; ventana.dispatchEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
                        SwingUtilities.invokeLater(iniciar); return;
                    }
                    if (fase[0] == 2) {
                        boton(ventana, "Recibir").doClick();
                        Pedido p = (Pedido) ((List<?>) campo(app[0], "todos")).get(0);
                        comprobar(p.estado() == Estado.ENTREGADO, "Cliente no recibió");
                        fase[0] = 3; ventana.dispatchEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
                        SwingUtilities.invokeLater(iniciar); return;
                    }
                    comprobar(boton(ventana, "Administrar catálogo").isEnabled(), "Admin no puede administrar");
                    comprobar(!boton(ventana, "Pagar").isEnabled(), "Admin puede pagar como cliente");
                    comprobar(((List<?>) campo(app[0], "todos")).size() == 1, "No se restauró el pedido de la UI");
                    boton(ventana, "Reporte de ventas").doClick();
                    JTextArea salida = (JTextArea) campo(app[0], "salida");
                    comprobar(salida.getText().contains("TOTALES |"), "No se mostró el reporte");
                    comprobar(((javax.swing.table.DefaultTableModel) campo(app[0], "filasReporte")).getRowCount() == 2, "Reporte sin fila y total");
                    captura(ventana, "admin");
                    ventana.dispatchEvent(new WindowEvent(ventana, WindowEvent.WINDOW_CLOSING));
                    reloj.stop(); fin.countDown(); return;
                }
            } catch (Throwable e) { error.set(e); reloj.stop(); fin.countDown(); }
        });
        SwingUtilities.invokeLater(() -> { reloj.start(); SwingUtilities.invokeLater(iniciar); });
        fin.await();
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) w.dispose(); });
        if (error.get() != null) throw new AssertionError("Falló prueba Swing. Datos de diagnóstico: " + temporal, error.get());
        System.out.println("OK: Swing — tres roles, compra, despacho, recepción, reinicios y reporte (datos aislados: " + temporal + ")");
    }
}
