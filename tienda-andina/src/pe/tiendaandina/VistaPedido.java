package pe.tiendaandina;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import static pe.tiendaandina.Dominio.*;

/** Presentación de un pedido, sin reglas ni cálculos de negocio. */
public final class VistaPedido extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;
    private final JLabel titulo = Estilo.titulo("Selecciona un pedido", 23), estado = new JLabel("SIN SELECCIÓN"),
            cliente = Estilo.nota("Aquí verás sus productos, importes y comprobante."), total = Estilo.titulo("S/ 0.00", 30),
            documento = Estilo.nota("Aún no hay comprobante"), accesos = Estilo.nota(" ");
    private final DefaultTableModel filas = new DefaultTableModel(new String[]{"Producto", "Cantidad", "Precio S/"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int fila, int columna) { return false; }
    };
    private final JLabel[] importes = new JLabel[6];
    public VistaPedido() {
        super(new BorderLayout(16, 16)); setOpaque(false);
        JPanel cabecera = new JPanel(new BorderLayout(8, 8)); cabecera.setOpaque(false);
        cabecera.add(titulo, BorderLayout.NORTH); cabecera.add(cliente, BorderLayout.CENTER); cabecera.add(estado, BorderLayout.EAST);
        estado.setOpaque(true); estado.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        estado.setFont(estado.getFont().deriveFont(Font.BOLD, 11f));
        add(cabecera, BorderLayout.NORTH);
        JTable tabla = new JTable(filas); Estilo.tabla(tabla); tabla.setPreferredScrollableViewportSize(new Dimension(450, 160));
        JPanel detalle = Estilo.tarjeta(); detalle.add(new JScrollPane(tabla), BorderLayout.CENTER);
        JPanel pie = new JPanel(new GridLayout(0, 1, 0, 8)); pie.setOpaque(false); pie.add(documento); pie.add(accesos);
        detalle.add(pie, BorderLayout.SOUTH); add(detalle, BorderLayout.CENTER);
        JPanel resumen = Estilo.tarjeta(); JPanel renglones = new JPanel(new GridLayout(0, 2, 12, 8)); renglones.setOpaque(false);
        String[] conceptos = {"Subtotal", "Descuento", "Envío", "Recargo", "Base imponible", "IGV incluido"};
        for (int i = 0; i < conceptos.length; i++) { renglones.add(Estilo.nota(conceptos[i])); importes[i] = new JLabel("—", SwingConstants.RIGHT); renglones.add(importes[i]); }
        resumen.add(renglones, BorderLayout.CENTER);
        JPanel suma = new JPanel(new BorderLayout()); suma.setOpaque(false); suma.add(Estilo.titulo("Total del pedido", 16), BorderLayout.WEST); suma.add(total, BorderLayout.EAST);
        suma.setLayout(new GridLayout(0, 1, 0, 8));
        resumen.add(suma, BorderLayout.SOUTH); resumen.setPreferredSize(new Dimension(255, 280));
        add(resumen, BorderLayout.EAST); vaciar();
    }
    public void vaciar() {
        titulo.setText("Selecciona un pedido"); cliente.setText("Crea uno nuevo o elige un pedido de la lista."); filas.setRowCount(0);
        estado.setText("SIN SELECCIÓN"); estado.setBackground(Estilo.FONDO); estado.setForeground(Estilo.SUAVE);
        for (JLabel importe : importes) importe.setText("—"); total.setText("S/ 0.00"); documento.setText("Aún no hay comprobante"); accesos.setText(" ");
    }
    public void mostrar(Pedido p) {
        titulo.setText(p.id); cliente.setText(p.cliente.nombre() + " · " + p.cliente.ciudad()); estado.setText(p.estado().name());
        estado.setBackground(p.estado() == Estado.PENDIENTE ? Color.decode("#FFF1D4") : p.estado() == Estado.CANCELADO ? Color.decode("#FCE7E7") : Color.decode("#E0F1E7"));
        estado.setForeground(p.estado() == Estado.PENDIENTE ? Color.decode("#78520B") : p.estado() == Estado.CANCELADO ? Color.decode("#9F3030") : Estilo.VERDE);
        filas.setRowCount(0); p.items.forEach(i -> filas.addRow(new Object[]{i.producto().nombre(), i.cantidad(), i.precioUnitario().setScale(2, java.math.RoundingMode.HALF_UP)}));
        Resumen r = p.resumen(); java.math.BigDecimal[] valores = {r.subtotal(), r.descuento(), r.envio(), r.recargo(), r.base(), r.igv()};
        for (int i = 0; i < valores.length; i++) importes[i].setText("S/ " + valores[i].toPlainString());
        total.setText("S/ " + r.total().toPlainString());
        documento.setText(p.documento() == null ? "El comprobante se emite al confirmar el pago." : "Comprobante de demostración · " + p.documento().numero());
        accesos.setText(p.accesos().isEmpty() ? " " : p.accesos().size() + " acceso(s) digital(es) habilitado(s)");
    }
    public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 320); }
    public int getScrollableUnitIncrement(Rectangle r, int orientacion, int direccion) { return 20; }
    public int getScrollableBlockIncrement(Rectangle r, int orientacion, int direccion) { return 160; }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return true; }
}
