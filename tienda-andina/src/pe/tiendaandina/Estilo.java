package pe.tiendaandina;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Sistema visual Swing local, sin fuentes ni dependencias descargadas. */
public final class Estilo {
    private Estilo() {}
    public static final Color FONDO = Color.decode("#F3F6F4"), TINTA = Color.decode("#203A32"),
            SUAVE = Color.decode("#63766E"), VERDE = Color.decode("#237654"), LINEA = Color.decode("#DBE5DF");
    public static void aplicar() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception e) { System.err.println("Tema estándar: " + e.getMessage()); }
        Font fuente = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        for (Object clave : java.util.Collections.list(UIManager.getDefaults().keys()))
            if (UIManager.get(clave) instanceof javax.swing.plaf.FontUIResource)
                UIManager.put(clave, new javax.swing.plaf.FontUIResource(fuente));
        UIManager.put("Panel.background", FONDO); UIManager.put("Label.foreground", TINTA);
        UIManager.put("TextField.background", Color.WHITE); UIManager.put("TextField.foreground", TINTA);
        UIManager.put("TextField.inactiveForeground", SUAVE); UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ComboBox.foreground", TINTA); UIManager.put("Table.selectionBackground", Color.decode("#DFEFE5"));
        UIManager.put("Table.selectionForeground", TINTA); UIManager.put("Table.gridColor", LINEA);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("OptionPane.background", FONDO);
        UIManager.put("Viewport.background", FONDO);
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", FONDO);
    }
    public static JLabel titulo(String texto, int tamano) {
        JLabel label = new JLabel(texto); label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano)); return label;
    }
    public static JLabel nota(String texto) { JLabel l = new JLabel(texto); l.setForeground(SUAVE); l.setFont(l.getFont().deriveFont(12f)); return l; }
    public static JPanel tarjeta() {
        JPanel p = new JPanel(new BorderLayout(12, 12)); p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINEA), new EmptyBorder(20, 20, 20, 20)));
        return p;
    }
    public static JPanel campo(String nombre, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(0, 7)); panel.setOpaque(false);
        JLabel etiqueta = new JLabel(nombre); etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD, 12f));
        etiqueta.setLabelFor(control); control.getAccessibleContext().setAccessibleName(nombre);
        control.setPreferredSize(new Dimension(150, 36));
        if (control instanceof JTextField texto) texto.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINEA), new EmptyBorder(6, 9, 6, 9)));
        panel.add(etiqueta, BorderLayout.NORTH); panel.add(control, BorderLayout.CENTER); return panel;
    }
    public static void tabla(JTable tabla) {
        tabla.setRowHeight(34); tabla.setShowVerticalLines(false); tabla.setShowHorizontalLines(true);
        tabla.setIntercellSpacing(new Dimension(10, 0)); tabla.setFillsViewportHeight(true);
        tabla.setBackground(Color.WHITE); tabla.setForeground(TINTA);
        tabla.getTableHeader().setFont(tabla.getFont().deriveFont(Font.BOLD, 12f));
        tabla.getTableHeader().setBackground(FONDO); tabla.getTableHeader().setForeground(SUAVE);
        tabla.getTableHeader().setReorderingAllowed(false);
        javax.swing.table.DefaultTableCellRenderer numeros = new javax.swing.table.DefaultTableCellRenderer();
        numeros.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 0; i < tabla.getColumnCount(); i++) {
            String nombre = tabla.getColumnName(i);
            if (nombre.contains("S/") || java.util.Set.of("Cantidad", "Cant.", "Subtotal", "Descuento", "IGV", "Total").contains(nombre))
                tabla.getColumnModel().getColumn(i).setCellRenderer(numeros);
        }
    }
    public static void pestanas(JTabbedPane panel) {
        panel.setBackground(FONDO); panel.setBorder(BorderFactory.createEmptyBorder());
        panel.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() { super.installDefaults(); tabInsets = new Insets(12, 18, 12, 18); contentBorderInsets = new Insets(0, 0, 0, 0); }
            @Override protected void paintContentBorder(Graphics g, int ubicacion, int seleccionado) {}
            @Override protected void paintTabBackground(Graphics g, int ubicacion, int indice, int x, int y, int ancho, int alto, boolean seleccionado) {
                g.setColor(seleccionado ? Color.WHITE : FONDO); g.fillRect(x, y, ancho, alto);
            }
            @Override protected void paintTabBorder(Graphics g, int ubicacion, int indice, int x, int y, int ancho, int alto, boolean seleccionado) {
                if (seleccionado) { g.setColor(VERDE); g.fillRect(x, y + alto - 3, ancho, 3); }
            }
        });
    }
    public static final class Boton extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean primario;
        public Boton(String texto, boolean primario) {
            super(texto); this.primario = primario;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setBorder(new EmptyBorder(10, 15, 10, 15)); setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setFont(getFont().deriveFont(Font.BOLD, 12f));
            setForeground(primario ? Color.WHITE : TINTA);
            addChangeListener(e -> repaint());
        }
        @Override protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fondo = primario ? VERDE : Color.WHITE;
            if (!isEnabled()) fondo = Color.decode("#EEF1EF");
            else if (getModel().isPressed()) fondo = primario ? Color.decode("#174F39") : Color.decode("#E2EBE6");
            else if (getModel().isRollover()) fondo = primario ? Color.decode("#1B6547") : Color.decode("#F0F5F2");
            g.setColor(fondo); g.fillRoundRect(1, 1, getWidth()-3, getHeight()-3, 10, 10);
            g.setColor(isFocusOwner() ? VERDE : LINEA); g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 10, 10); g.dispose(); super.paintComponent(original);
        }
    }
}
