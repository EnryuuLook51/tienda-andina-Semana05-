package pe.tiendaandina;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Path;
import java.io.IOException;
import static pe.tiendaandina.Dominio.*;
import static pe.tiendaandina.Seguridad.*;
import static pe.tiendaandina.Controladores.*;

/** Aplicación académica local; cobros, mensajería y derechos de acceso simulados. */
public final class Aplicacion {
    static { Estilo.aplicar(); }
    private final JTextArea salida = new JTextArea(18, 70);
    private final DefaultComboBoxModel<PedidoVista> pedidos = new DefaultComboBoxModel<>();
    private final List<Item> carrito = new ArrayList<>();
    private final List<Pedido> todos = new ArrayList<>();
    private final Autenticacion autenticacion = new Autenticacion();
    private Sesion sesion;
    private final Map<String, Cliente> clientes = Map.of(
            "cliente", new Cliente("cliente-1", "Ana Regular", TipoCliente.REGULAR, "Chimbote", ""),
            "vip", new Cliente("cliente-2", "Luis VIP", TipoCliente.VIP, "Lima", ""),
            "empresa", new Cliente("cliente-3", "Empresa Andina", TipoCliente.CORPORATIVO, "Chimbote", "20123456789"));
    private final List<JComponent> soloCliente = new ArrayList<>(), soloOperador = new ArrayList<>(), soloAdmin = new ArrayList<>();
    private final JLabel identidad = new JLabel();
    private final Map<String, Cupon> cupones = new LinkedHashMap<>();
    private final JTextField codigoCupon = new JTextField();
    private final JTextField desde = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField hasta = new JTextField(LocalDate.now().toString(), 10);
    private final Path directorio = Path.of(System.getProperty("tienda.directorio", ".")).toAbsolutePath();
    private final Path archivoParametros = directorio.resolve("config/negocio.properties");
    private final RepositorioArchivo repositorio = new RepositorioArchivo(directorio.resolve("datos/tienda.bin"));
    private final PasarelaCobro pasarela = new PasarelaCobro() {
        public void cobrar(String id, java.math.BigDecimal total) { registrar("SIMULACIÓN: cobro " + id + " S/ " + total); }
        public void reembolsar(String id, java.math.BigDecimal total) { registrar("SIMULACIÓN: reembolso " + id + " S/ " + total); }
    };
    private GestorPedidos gestor = new GestorPedidos(Configuracion.predeterminada(), pasarela,
            new PedidosEnMemoria(new ArrayList<>()));
    private AutoCloseable bloqueo;
    private final JComboBox<Producto> productos = new JComboBox<>(new Producto[] {
            new ProductoFisico("F01", "Mochila", "59.90", "0.75", 100),
            new ProductoFisico("F02", "Cuaderno", "12.00", "0.30", 200),
            new ProductoDigital("D01", "Curso digital", "118.00"),
            new Suscripcion("S01", "Biblioteca mensual", "29.90")
    });
    private ProductoRepositorio catalogo;
    private final JTextField id = new JTextField("cliente-1"), nombre = new JTextField("Cliente de prueba"),
            ciudad = new JTextField("Chimbote"), ruc = new JTextField();
    private final JComboBox<TipoCliente> tipo = new JComboBox<>(TipoCliente.values());
    private final JComboBox<String> envio = new JComboBox<>(new String[] {"SIN ENVÍO", "ESTANDAR", "EXPRESS", "RECOJO"});
    private final JComboBox<Pago> pago = new JComboBox<>(Pago.values());
    private final JSpinner cantidad = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
    private final JComboBox<PedidoVista> seleccion = new JComboBox<>(pedidos);
    private final JTabbedPane paginas = new JTabbedPane();
    private final VistaPedido vistaPedido = new VistaPedido();
    private final JLabel estadoInterfaz = Estilo.nota("Listo"), perfil = Estilo.nota(" "),
            subtotalCarrito = Estilo.titulo("S/ 0.00", 26);
    private final JLabel tituloPrincipal = Estilo.titulo("Tienda Andina", 26);
    private final JLabel[] cifras = {Estilo.titulo("0", 25), Estilo.titulo("0", 25), Estilo.titulo("0", 25)};
    private final Map<String, JButton> botones = new LinkedHashMap<>();
    private final javax.swing.table.DefaultTableModel filasCarrito = new javax.swing.table.DefaultTableModel(
            new String[]{"Producto", "Cant.", "Precio S/", "Importe S/"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int f, int c) { return false; }
    };
    private final javax.swing.table.DefaultTableModel filasReporte = new javax.swing.table.DefaultTableModel(
            new String[]{"Pedido", "Fecha", "Documento", "Subtotal", "Descuento", "IGV", "Total"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int f, int c) { return false; }
    };
    private record PedidoVista(Pedido pedido) {
        @Override public String toString() { return pedido.id + " — " + pedido.cliente.nombre() + " — " + pedido.estado(); }
    }
    private void registrar(String texto) {
        salida.append(texto + "\n"); salida.setCaretPosition(salida.getDocument().getLength());
        if (!texto.isBlank()) estadoInterfaz.setText(texto.replace('\n', ' '));
    }
    private void mostrar(Pedido p) {
        if (sesion.rol() == Rol.CLIENTE && !p.cliente.id().equals(sesion.clienteId()))
            throw new SecurityException("Pedido de otro cliente");
        Resumen r = p.resumen();
        vistaPedido.mostrar(p);
        registrar("\n" + p.id + " | " + p.estado() + " | " + p.cliente.nombre());
        registrar("Subtotal S/ " + r.subtotal() + " | Descuento S/ " + r.descuento());
        registrar("Envío S/ " + r.envio() + " | Recargo S/ " + r.recargo());
        registrar("Base S/ " + r.base() + " | IGV incluido S/ " + r.igv() + " | TOTAL S/ " + r.total());
        if (p.documento() != null) registrar("Documento: " + p.documento().numero());
        for (Acceso acceso : p.accesos()) registrar("Acceso habilitado (local): " + acceso.productoCodigo()
                + " | " + acceso.tipo() + " | desde " + acceso.fechaHabilitacion());
    }
    private void guardarTicket() {
        Pedido p = actual();
        if (sesion.rol() == Rol.CLIENTE && !p.cliente.id().equals(sesion.clienteId()))
            throw new SecurityException("Pedido de otro cliente");
        String texto = CalculoTotal.ImpresorTicket.imprimir(p);
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar ticket");
        selector.setSelectedFile(new java.io.File(p.documento().numero() + ".txt"));
        if (selector.showSaveDialog(salida) != JFileChooser.APPROVE_OPTION) return;
        Path destino = selector.getSelectedFile().toPath();
        if (java.nio.file.Files.exists(destino) && JOptionPane.showConfirmDialog(salida,
                "El archivo existe. ¿Reemplazarlo?", "Guardar ticket", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            java.nio.file.Files.writeString(destino, texto, java.nio.charset.StandardCharsets.UTF_8);
            registrar("Ticket guardado: " + destino.toAbsolutePath());
        } catch (IOException e) { throw new IllegalArgumentException("No se pudo guardar el ticket: " + e.getMessage(), e); }
    }
    private JButton boton(String texto, Runnable accion) {
        JButton boton = new Estilo.Boton(texto, texto.equals("Registrar pedido") || texto.equals("Pagar") || texto.equals("Confirmar depósito"));
        botones.put(texto, boton);
        boton.addActionListener(e -> {
            try { accion.run(); guardarDatos(); actualizarVista(); seleccion.repaint(); }
            catch (RuntimeException ex) { JOptionPane.showMessageDialog(salida, ex.getMessage(), "Operación rechazada", JOptionPane.WARNING_MESSAGE); }
        });
        return boton;
    }
    private void guardarDatos() {
        try { repositorio.guardar(new RepositorioArchivo.Datos(gestor, catalogo.todos(), cupones)); }
        catch (IOException e) { throw new IllegalStateException("No se pudo guardar. Los cambios siguen en memoria; reintente con Guardar datos antes de cerrar. " + e.getMessage(), e); }
    }
    private void actualizarCatalogo() {
        productos.removeAllItems();
        catalogo.activos().forEach(productos::addItem);
    }
    private void administrarCatalogo() {
        sesion.exigir(Rol.ADMINISTRADOR);
        JComboBox<Producto> seleccionProducto = new JComboBox<>(catalogo.todos().toArray(Producto[]::new));
        Object[] opciones = {"Nuevo", "Editar seleccionado", "Dar de baja", "Cerrar"};
        int accion = JOptionPane.showOptionDialog(salida, seleccionProducto, "Catálogo (incluye bajas)",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[0]);
        if (accion < 0 || accion == 3) return;
        Producto p = (Producto) seleccionProducto.getSelectedItem();
        if (accion != 0 && p == null) throw new IllegalArgumentException("Seleccione un producto");
        if (accion == 2) {
            if (JOptionPane.showConfirmDialog(salida, "¿Dar de baja " + p.nombre() + "? Los pedidos existentes se conservan.",
                    "Baja lógica", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            administradorControlador().bajaProducto(catalogo, p.codigo);
            actualizarCatalogo(); registrar("Producto dado de baja: " + p.codigo); return;
        }
        boolean nuevo = accion == 0;
        JTextField codigo = new JTextField(nuevo ? "" : p.codigo), nombreProducto = new JTextField(nuevo ? "" : p.nombre()),
                precio = new JTextField(nuevo ? "0.00" : p.precio().toPlainString()), peso = new JTextField(nuevo ? "0" : p.peso().toPlainString()),
                stock = new JTextField(nuevo ? "0" : Integer.toString(p.stock()));
        JComboBox<TipoProducto> clase = new JComboBox<>(TipoProducto.values());
        if (!nuevo) clase.setSelectedItem(p.tipo);
        codigo.setEditable(nuevo); clase.setEnabled(nuevo);
        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));
        Object[][] campos = {{"Código", codigo}, {"Nombre", nombreProducto}, {"Tipo (no modificable)", clase},
                {"Precio con IGV", precio}, {"Peso kg (0 si no físico)", peso}, {"Stock actual (0 si no físico)", stock}};
        for (Object[] campo : campos) { formulario.add(new JLabel((String) campo[0])); formulario.add((Component) campo[1]); }
        if (JOptionPane.showConfirmDialog(salida, formulario, nuevo ? "Nuevo producto" : "Editar producto", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        int unidades = Integer.parseInt(stock.getText().trim());
        if (nuevo) administradorControlador().crearProducto(catalogo, codigo.getText(), nombreProducto.getText(),
                (TipoProducto) clase.getSelectedItem(), precio.getText().trim(), peso.getText().trim(), unidades);
        else administradorControlador().editarProducto(catalogo, p.codigo, nombreProducto.getText(), precio.getText().trim(), peso.getText().trim(), unidades);
        actualizarCatalogo(); registrar("Catálogo actualizado: " + codigo.getText());
    }
    private void administrarEnvios() {
        ConfiguracionEnvios e = administradorControlador().envios();
        JTextField fijo = new JTextField(e.fijoEstandar().toPlainString()), kg = new JTextField(e.kgEstandar().toPlainString()),
                express = new JTextField(e.fijoExpress().toPlainString()), kgExpress = new JTextField(e.kgExpress().toPlainString()),
                ciudades = new JTextField(String.join(", ", e.ciudadesExpress()));
        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));
        Object[][] campos = {{"Estándar: fijo S/", fijo}, {"Estándar: S/ por kg", kg},
                {"Express: fijo S/", express}, {"Express: S/ por kg", kgExpress}, {"Ciudades express (separadas por coma)", ciudades}};
        for (Object[] campo : campos) { formulario.add(new JLabel((String) campo[0])); formulario.add((Component) campo[1]); }
        formulario.add(new JLabel("VIP: estándar gratis. Recojo: gratis en Chimbote."));
        formulario.add(new JLabel("No cambia comprobantes emitidos."));
        if (JOptionPane.showConfirmDialog(salida, formulario, "Administrar envíos", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        ConfiguracionEnvios nueva = new ConfiguracionEnvios(monto(fijo.getText().trim()), monto(kg.getText().trim()),
                monto(express.getText().trim()), monto(kgExpress.getText().trim()), List.of(ciudades.getText().split(",", -1)));
        administradorControlador().configurarEnvios(nueva);
        registrar("Tarifas y cobertura express actualizadas; se aplican al confirmar pedidos pendientes.");
    }
    private Pedido actual() {
        PedidoVista vista = (PedidoVista) seleccion.getSelectedItem();
        if (vista == null) throw new IllegalArgumentException("Primero registre un pedido");
        return vista.pedido;
    }
    private ClienteControlador clienteControlador() {
        return new ClienteControlador(sesion, clientes.get(sesion.usuario()), gestor);
    }
    private OperadorControlador operadorControlador() { return new OperadorControlador(sesion, gestor); }
    private AdministradorControlador administradorControlador() { return new AdministradorControlador(sesion, gestor); }
    private JButton restringido(String texto, Rol rol, Runnable accion) {
        JButton b = boton(texto, () -> { sesion.exigir(rol); accion.run(); });
        switch (rol) { case CLIENTE -> soloCliente.add(b); case OPERADOR -> soloOperador.add(b); case ADMINISTRADOR -> soloAdmin.add(b); }
        return b;
    }
    private boolean login(Component padre) {
        JTextField usuario = new JTextField("cliente"); JPasswordField clave = new JPasswordField();
        JPanel formulario = new JPanel(new BorderLayout(0, 20));
        formulario.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel bienvenida = new JPanel(new GridLayout(0, 1, 0, 8)); bienvenida.setOpaque(false);
        bienvenida.add(Estilo.nota("TIENDA ANDINA")); bienvenida.add(Estilo.titulo("Bienvenido de nuevo", 25));
        bienvenida.add(Estilo.nota("Tus pedidos y operaciones, en un solo lugar."));
        formulario.add(bienvenida, BorderLayout.NORTH);
        JPanel credenciales = new JPanel(new GridLayout(0, 1, 0, 16)); credenciales.setOpaque(false);
        credenciales.add(Estilo.campo("Usuario", usuario)); credenciales.add(Estilo.campo("Contraseña", clave));
        formulario.add(credenciales, BorderLayout.CENTER);
        JPanel demo = new JPanel(new GridLayout(0, 1, 0, 6)); demo.setOpaque(false);
        demo.add(Estilo.nota("DEMO: cliente · vip · empresa · operador · admin")); demo.add(Estilo.nota("Contraseña de demostración: Demo2026!"));
        formulario.add(demo, BorderLayout.SOUTH);
        while (JOptionPane.showConfirmDialog(padre, formulario, "Tienda Andina — iniciar sesión", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            char[] secreto = clave.getPassword();
            try { sesion = autenticacion.entrar(usuario.getText().trim(), secreto); return true; }
            catch (SecurityException e) { JOptionPane.showMessageDialog(padre, e.getMessage()); clave.setText(""); }
            finally { java.util.Arrays.fill(secreto, '\0'); }
        }
        return false;
    }
    private void actualizarSesion() {
        carrito.clear(); salida.setText(""); pedidos.removeAllElements();
        for (Pedido p : todos) if (sesion.rol() != Rol.CLIENTE || p.cliente.id().equals(sesion.clienteId())) pedidos.addElement(new PedidoVista(p));
        soloCliente.forEach(c -> c.setEnabled(sesion.rol() == Rol.CLIENTE));
        soloOperador.forEach(c -> c.setEnabled(sesion.rol() == Rol.OPERADOR));
        soloAdmin.forEach(c -> c.setEnabled(sesion.rol() == Rol.ADMINISTRADOR));
        identidad.setText("Sesión: " + sesion.usuario() + " — " + sesion.rol());
        Cliente c = clientes.get(sesion.usuario());
        id.setText(c == null ? "" : c.id()); nombre.setText(c == null ? "" : c.nombre());
        ciudad.setText(c == null ? "" : c.ciudad()); ruc.setText(c == null ? "" : c.ruc());
        if (c != null) tipo.setSelectedItem(c.tipo());
        for (JTextField campo : List.of(id, nombre, ciudad, ruc)) campo.setEditable(false);
        tipo.setEnabled(false); codigoCupon.setText("");
        perfil.setText(c == null ? "Gestiona las operaciones de Tienda Andina" : c.nombre() + " · " + c.tipo() + " · " + c.ciudad());
        tituloPrincipal.setText(switch (sesion.rol()) {
            case CLIENTE -> "Tu próxima compra empieza aquí.";
            case OPERADOR -> "Cada pedido, en su siguiente paso.";
            case ADMINISTRADOR -> "Tu negocio, bajo control.";
        });
        if (paginas.getTabCount() > 0) {
            paginas.setEnabledAt(0, sesion.rol() == Rol.CLIENTE);
            paginas.setEnabledAt(2, sesion.rol() == Rol.ADMINISTRADOR);
            paginas.setSelectedIndex(sesion.rol() == Rol.CLIENTE ? 0 : sesion.rol() == Rol.OPERADOR ? 1 : 2);
        }
        filasReporte.setRowCount(0); actualizarVista();
        registrar("Guardado local automático; cobros y mensajes simulados. Cambiar de usuario vacía el carrito, no los pedidos.");
    }
    private void actualizarVista() {
        filasCarrito.setRowCount(0); java.math.BigDecimal subtotal = monto("0");
        for (Item item : carrito) {
            java.math.BigDecimal importe = item.precioUnitario().multiply(java.math.BigDecimal.valueOf(item.cantidad()));
            subtotal = subtotal.add(importe);
            filasCarrito.addRow(new Object[]{item.producto().nombre(), item.cantidad(), redondear(item.precioUnitario()), redondear(importe)});
        }
        subtotalCarrito.setText("S/ " + redondear(subtotal).toPlainString());
        List<Pedido> visibles = todos.stream().filter(p -> sesion.rol() != Rol.CLIENTE || p.cliente.id().equals(sesion.clienteId())).toList();
        cifras[0].setText(Integer.toString(visibles.size()));
        cifras[1].setText(Long.toString(visibles.stream().filter(p -> p.estado() == Estado.PENDIENTE).count()));
        cifras[2].setText(Long.toString(visibles.stream().filter(Pedido::esVenta).count()));
        PedidoVista elegido = (PedidoVista) seleccion.getSelectedItem();
        if (elegido == null) vistaPedido.vaciar(); else vistaPedido.mostrar(elegido.pedido());
        Pedido p = elegido == null ? null : elegido.pedido();
        boolean cliente = sesion.rol() == Rol.CLIENTE, operador = sesion.rol() == Rol.OPERADOR;
        habilitar("Pagar", cliente && p != null && p.estado() == Estado.PENDIENTE && p.pago != Pago.TRANSFERENCIA);
        habilitar("Confirmar depósito", operador && p != null && p.estado() == Estado.PENDIENTE && p.pago == Pago.TRANSFERENCIA);
        habilitar("Despachar", operador && p != null && p.estado() == Estado.PAGADO);
        habilitar("Recibir", cliente && p != null && p.estado() == Estado.ENVIADO);
        habilitar("Cancelar", cliente && p != null && (p.estado() == Estado.PENDIENTE || p.estado() == Estado.PAGADO));
        habilitar("Guardar ticket", p != null && p.documento() != null);
        habilitar("Registrar pedido", cliente && !carrito.isEmpty());
        habilitar("Vaciar carrito", cliente && !carrito.isEmpty());
        habilitar("Quitar ítem", cliente && !carrito.isEmpty());
    }
    private void habilitar(String texto, boolean disponible) {
        JButton b = botones.get(texto); if (b != null) b.setEnabled(disponible);
    }
    private void cargarParametros() {
        try {
            Configuracion nueva = Parametros.cargar(archivoParametros);
            administradorControlador().configurar(nueva);
            registrar("Parámetros cargados. IGV: " + nueva.igv().movePointRight(2) + " %. Los comprobantes emitidos no cambian.");
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer " + archivoParametros.toAbsolutePath(), e);
        }
    }
    private void crearCupon() {
        JTextField codigo = new JTextField(), porcentaje = new JTextField("10"), vencimiento = new JTextField(LocalDate.now().plusDays(30).toString());
        JPanel formulario = new JPanel(new GridLayout(0, 2, 8, 8));
        formulario.add(new JLabel("Código")); formulario.add(codigo);
        formulario.add(new JLabel("Porcentaje (10 = 10 %)")); formulario.add(porcentaje);
        formulario.add(new JLabel("Vence (AAAA-MM-DD)")); formulario.add(vencimiento);
        if (JOptionPane.showConfirmDialog(salida, formulario, "Crear cupón de demostración", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String clave = codigo.getText().trim().toUpperCase(java.util.Locale.ROOT);
        if (clave.isBlank()) throw new IllegalArgumentException("Escriba un código de cupón");
        if (cupones.containsKey(clave)) throw new IllegalArgumentException("Ese código ya existe; no se reinicia su historial de uso");
        Cupon nuevo = administradorControlador().crearCupon(clave, monto(porcentaje.getText().trim()).movePointLeft(2).toPlainString(), LocalDate.parse(vencimiento.getText().trim()));
        cupones.put(clave, nuevo); codigoCupon.setText(clave);
        registrar("Cupón creado: " + clave + " | " + porcentaje.getText() + " % | vence " + nuevo.vencimiento);
    }
    private void iniciar() {
        try {
            bloqueo = repositorio.bloquear();
            if (repositorio.existe()) {
                RepositorioArchivo.Datos datos = repositorio.cargar(pasarela);
                gestor = datos.gestor(); todos.addAll(gestor.pedidos()); cupones.putAll(datos.cupones());
                catalogo = new ProductosEnMemoria(datos.productos());
            }
            if (catalogo == null) {
                List<Producto> iniciales = new ArrayList<>();
                for (int i = 0; i < productos.getItemCount(); i++) iniciales.add(productos.getItemAt(i));
                catalogo = new ProductosEnMemoria(iniciales);
            }
            actualizarCatalogo();
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(null, "No se iniciará la app ni se sobrescribirán los datos: " + e.getMessage());
            liberarBloqueo(); return;
        }
        for (String usuario : List.of("cliente", "vip", "empresa", "operador", "admin")) {
            Usuario identidad = usuario.equals("admin") ? new Administrador(usuario, "Administrador demo")
                    : usuario.equals("operador") ? new Operador(usuario, "Operador demo") : clientes.get(usuario);
            autenticacion.registrar(usuario, "Demo2026!".toCharArray(), identidad);
        }
        try { gestor.configurar(Parametros.cargar(archivoParametros)); }
        catch (IOException | IllegalArgumentException e) { JOptionPane.showMessageDialog(null, "Configuración inválida: " + e.getMessage()); liberarBloqueo(); return; }
        if (!login(null)) { liberarBloqueo(); return; }
        JFrame ventana = new JFrame("Tienda Andina — demostrador / SIN COBROS REALES");
        ventana.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        ventana.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent evento) {
                try { guardarDatos(); liberarBloqueo(); ventana.dispose(); }
                catch (RuntimeException e) { JOptionPane.showMessageDialog(ventana, e.getMessage(), "No se pudo guardar", JOptionPane.ERROR_MESSAGE); }
            }
        });
        JPanel datos = new JPanel(new GridLayout(0, 1, 0, 15)); datos.setOpaque(false);
        JPanel controlesCompra = new JPanel(new GridLayout(0, 2, 8, 8)); controlesCompra.setOpaque(false);
        Object[][] campos = {{"Producto del catálogo", productos}, {"Cantidad", cantidad}, {"Tipo de envío", envio},
                {"Medio de pago", pago}, {"Cupón · opcional", codigoCupon}};
        for (Object[] campo : campos) datos.add(Estilo.campo((String) campo[0], (JComponent) campo[1]));
        controlesCompra.add(restringido("Agregar ítem", Rol.CLIENTE, () -> {
            try { cantidad.commitEdit(); }
            catch (java.text.ParseException e) { throw new IllegalArgumentException("Escribe una cantidad entera entre 1 y 10000", e); }
            Producto producto = (Producto) productos.getSelectedItem();
            if (producto == null || !producto.activo()) throw new IllegalArgumentException("Seleccione un producto activo");
            carrito.add(new Item(producto, (Integer) cantidad.getValue()));
            registrar("Carrito: " + cantidad.getValue() + " × " + producto.nombre() + " (stock: " + producto.stock() + ")");
        }));
        controlesCompra.add(restringido("Vaciar carrito", Rol.CLIENTE, () -> { carrito.clear(); registrar("Carrito vacío"); }));
        JTable tablaCarrito = new JTable(filasCarrito); Estilo.tabla(tablaCarrito);
        controlesCompra.add(restringido("Quitar ítem", Rol.CLIENTE, () -> {
            int fila = tablaCarrito.getSelectedRow();
            if (fila < 0) throw new IllegalArgumentException("Selecciona un producto del carrito para quitarlo");
            carrito.remove(tablaCarrito.convertRowIndexToModel(fila)); registrar("Producto retirado del carrito");
        }));
        controlesCompra.add(restringido("Registrar pedido", Rol.CLIENTE, () -> {
            Envio elegido = envio.getSelectedIndex() == 0 ? null : Envio.valueOf((String) envio.getSelectedItem());
            String clave = codigoCupon.getText().trim().toUpperCase(java.util.Locale.ROOT);
            Cupon cupon = clave.isEmpty() ? null : cupones.get(clave);
            if (!clave.isEmpty() && cupon == null) throw new IllegalArgumentException("Cupón inexistente");
            Pedido pedido = clienteControlador().registrar(carrito, elegido, (Pago) pago.getSelectedItem(), cupon, LocalDate.now());
            todos.add(pedido);
            PedidoVista vista = new PedidoVista(pedido); pedidos.addElement(vista); seleccion.setSelectedItem(vista);
            carrito.clear(); mostrar(pedido);
            paginas.setSelectedIndex(1);
        }));
        JPanel acciones = new JPanel(new GridLayout(0, 3, 8, 8)); acciones.setOpaque(false);
        acciones.add(boton("Ver", () -> mostrar(actual())));
        acciones.add(boton("Guardar ticket", this::guardarTicket));
        acciones.add(restringido("Transferencias pendientes", Rol.OPERADOR, () -> {
            List<Pedido> pendientes = operadorControlador().transferenciasPendientes();
            registrar("TRANSFERENCIAS PENDIENTES: " + pendientes.size());
            pendientes.forEach(this::mostrar);
        }));
        acciones.add(restringido("Pagar", Rol.CLIENTE, () -> { Pedido p = actual(); clienteControlador().pagar(p, LocalDate.now()); mostrar(p); }));
        acciones.add(restringido("Confirmar depósito", Rol.OPERADOR, () -> { Pedido p = actual(); operadorControlador().confirmarDeposito(p, LocalDate.now()); mostrar(p); }));
        acciones.add(restringido("Despachar", Rol.OPERADOR, () -> { Pedido p = actual(); operadorControlador().despachar(p); mostrar(p); }));
        acciones.add(restringido("Recibir", Rol.CLIENTE, () -> { Pedido p = actual(); clienteControlador().recibir(p); mostrar(p); }));
        acciones.add(restringido("Cancelar", Rol.CLIENTE, () -> {
            Pedido p = actual();
            String aviso = p.estado() == Estado.PAGADO
                    ? "Se repondrá el stock y se registrará el reembolso simulado de S/ " + p.resumen().total() + "."
                    : "El pedido dejará de estar disponible para pagar.";
            if (JOptionPane.showConfirmDialog(ventana, "¿Cancelar " + p.id + "?\n" + aviso,
                    "Confirmar cancelación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            clienteControlador().cancelar(p); mostrar(p);
        }));
        JPanel reportes = new JPanel(new GridLayout(0, 3, 10, 10)); reportes.setOpaque(false);
        reportes.add(Estilo.campo("Desde · AAAA-MM-DD", desde));
        reportes.add(Estilo.campo("Hasta · AAAA-MM-DD", hasta));
        reportes.add(restringido("Reporte de ventas", Rol.ADMINISTRADOR, () -> {
            ReporteVentas reporte = administradorControlador().reporte(LocalDate.parse(desde.getText().trim()), LocalDate.parse(hasta.getText().trim()));
            filasReporte.setRowCount(0);
            registrar("\nVENTAS " + desde.getText() + " — " + hasta.getText() + " | " + reporte.lineas().size() + " pedidos");
            for (LineaVenta linea : reporte.lineas()) {
                Resumen r = linea.resumen();
                filasReporte.addRow(new Object[]{linea.pedido(), linea.fechaPago(), linea.documento(), r.subtotal(), r.descuento(), r.igv(), r.total()});
                registrar(linea.pedido() + " | " + linea.fechaPago() + " | " + linea.documento()
                        + " | subtotal " + r.subtotal() + " | descuento " + r.descuento() + " | IGV " + r.igv() + " | total " + r.total());
            }
            registrar("TOTALES | subtotal " + reporte.subtotal() + " | descuento " + reporte.descuento()
                    + " | IGV " + reporte.igv() + " | total S/ " + reporte.total());
            filasReporte.addRow(new Object[]{"TOTALES", "", "", reporte.subtotal(), reporte.descuento(), reporte.igv(), reporte.total()});
        }));
        reportes.add(restringido("Recargar parámetros", Rol.ADMINISTRADOR, this::cargarParametros));
        reportes.add(restringido("Crear cupón", Rol.ADMINISTRADOR, this::crearCupon));
        reportes.add(restringido("Administrar catálogo", Rol.ADMINISTRADOR, this::administrarCatalogo));
        reportes.add(restringido("Administrar envíos", Rol.ADMINISTRADOR, this::administrarEnvios));
        reportes.add(restringido("Errores de notificación", Rol.ADMINISTRADOR, () -> {
            List<String> errores = administradorControlador().erroresNotificacion();
            registrar("ERRORES DE NOTIFICACIÓN: " + errores.size()); errores.forEach(this::registrar); paginas.setSelectedIndex(3);
        }));
        gestor.observar(new Notificaciones.ObservadorCorreo(new Notificaciones.CanalSimulado("CORREO", this::registrar)));
        gestor.observar(new Notificaciones.ObservadorSms(new Notificaciones.CanalSimulado("SMS", this::registrar)));
        salida.setEditable(false); salida.setLineWrap(true); salida.setWrapStyleWord(true);
        salida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); salida.setForeground(Estilo.TINTA);
        salida.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JPanel contenido = new JPanel(new BorderLayout(0, 18));
        contenido.setBorder(BorderFactory.createEmptyBorder(22, 24, 14, 24));
        JPanel encabezado = new JPanel(new BorderLayout(20, 0)); encabezado.setOpaque(false);
        JPanel marca = new JPanel(new GridLayout(0, 1, 0, 5)); marca.setOpaque(false);
        marca.add(Estilo.nota("TIENDA ANDINA / GESTIÓN DE PEDIDOS")); marca.add(tituloPrincipal); marca.add(perfil);
        encabezado.add(marca, BorderLayout.CENTER);
        JPanel cuenta = new JPanel(new GridLayout(0, 1, 0, 6)); cuenta.setOpaque(false); identidad.setHorizontalAlignment(SwingConstants.RIGHT);
        cuenta.add(identidad); cuenta.add(boton("Cambiar usuario", () -> { if (login(ventana)) actualizarSesion(); }));
        encabezado.add(cuenta, BorderLayout.EAST);
        JPanel superior = new JPanel(new BorderLayout(0, 20)); superior.setOpaque(false); superior.add(encabezado, BorderLayout.NORTH);
        JPanel indicadores = new JPanel(new GridLayout(1, 3, 14, 0)); indicadores.setOpaque(false);
        String[] etiquetas = {"PEDIDOS REGISTRADOS", "PENDIENTES DE PAGO", "VENTAS CONFIRMADAS"};
        for (int i = 0; i < cifras.length; i++) {
            JPanel indicador = Estilo.tarjeta(); indicador.add(Estilo.nota(etiquetas[i]), BorderLayout.NORTH); indicador.add(cifras[i], BorderLayout.CENTER); indicadores.add(indicador);
        }
        superior.add(indicadores, BorderLayout.CENTER); contenido.add(superior, BorderLayout.NORTH);

        JPanel compra = new JPanel(new BorderLayout(18, 0)); compra.setOpaque(false); compra.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        JPanel formulario = Estilo.tarjeta(); formulario.add(Estilo.titulo("Arma tu pedido", 19), BorderLayout.NORTH);
        formulario.add(controlesCompra, BorderLayout.SOUTH);
        formulario.add(new JScrollPane(datos), BorderLayout.CENTER); formulario.setPreferredSize(new Dimension(330, 400));
        compra.add(formulario, BorderLayout.WEST);
        JPanel carritoVisual = Estilo.tarjeta(); carritoVisual.add(Estilo.titulo("Tu carrito", 21), BorderLayout.NORTH);
        JScrollPane listaCarrito = new JScrollPane(tablaCarrito); listaCarrito.setPreferredSize(new Dimension(320, 180));
        carritoVisual.add(listaCarrito, BorderLayout.CENTER);
        JPanel pieCarrito = new JPanel(new GridLayout(0, 1, 0, 8)); pieCarrito.setOpaque(false);
        pieCarrito.add(Estilo.nota("SUBTOTAL DE PRODUCTOS")); pieCarrito.add(subtotalCarrito);
        pieCarrito.add(Estilo.nota("Envío y descuentos se calculan al registrar."));
        carritoVisual.add(pieCarrito, BorderLayout.SOUTH); compra.add(carritoVisual, BorderLayout.CENTER);

        JPanel operaciones = new JPanel(new BorderLayout(0, 16)); operaciones.setOpaque(false); operaciones.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        operaciones.add(Estilo.campo("Seleccionar pedido", seleccion), BorderLayout.NORTH);
        operaciones.add(new JScrollPane(vistaPedido), BorderLayout.CENTER); operaciones.add(acciones, BorderLayout.SOUTH);
        seleccion.addActionListener(e -> { if (sesion != null) actualizarVista(); });

        JPanel administracion = new JPanel(new BorderLayout(0, 16)); administracion.setOpaque(false); administracion.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        JPanel controlesAdmin = Estilo.tarjeta(); controlesAdmin.add(Estilo.titulo("Control del negocio", 21), BorderLayout.NORTH);
        controlesAdmin.add(reportes, BorderLayout.CENTER); administracion.add(controlesAdmin, BorderLayout.NORTH);
        JTable tablaReporte = new JTable(filasReporte); Estilo.tabla(tablaReporte); tablaReporte.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < tablaReporte.getColumnCount(); i++) tablaReporte.getColumnModel().getColumn(i).setPreferredWidth(i == 2 ? 155 : 115);
        JPanel ventas = Estilo.tarjeta(); ventas.add(Estilo.titulo("Reporte de ventas", 18), BorderLayout.NORTH); ventas.add(new JScrollPane(tablaReporte), BorderLayout.CENTER);
        administracion.add(ventas, BorderLayout.CENTER);
        JPanel actividad = Estilo.tarjeta(); actividad.add(Estilo.titulo("Actividad de la sesión", 20), BorderLayout.NORTH); actividad.add(new JScrollPane(salida), BorderLayout.CENTER);
        paginas.addTab("Nueva compra", compra); paginas.addTab("Pedidos", operaciones);
        paginas.addTab("Administración", administracion); paginas.addTab("Actividad", actividad);
        Estilo.pestanas(paginas);
        paginas.setFont(paginas.getFont().deriveFont(Font.BOLD, 13f));
        contenido.add(paginas, BorderLayout.CENTER);
        JPanel estado = new JPanel(new BorderLayout(15, 0)); estado.setOpaque(false);
        estadoInterfaz.setPreferredSize(new Dimension(400, 22)); estado.add(estadoInterfaz, BorderLayout.CENTER);
        JPanel pieDerecho = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); pieDerecho.setOpaque(false);
        pieDerecho.add(Estilo.nota("DEMO · sin cobros reales")); pieDerecho.add(boton("Guardar datos", this::guardarDatos));
        estado.add(pieDerecho, BorderLayout.EAST); contenido.add(estado, BorderLayout.SOUTH);
        ventana.setContentPane(contenido);
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        ventana.setSize(Math.min(1180, pantalla.width), Math.min(850, pantalla.height)); ventana.setLocationRelativeTo(null);
        actualizarSesion();
        registrar("Seleccione SIN ENVÍO para pedidos exclusivamente digitales o suscripciones.");
        ventana.setVisible(true);
    }
    private void liberarBloqueo() {
        if (bloqueo != null) {
            try { bloqueo.close(); } catch (Exception e) { System.err.println("No se pudo liberar el bloqueo: " + e.getMessage()); }
            bloqueo = null;
        }
    }
    public static void main(String[] args) {
        try {
            Path origen = Path.of(Aplicacion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (System.getProperty("tienda.directorio") == null && origen.toString().endsWith(".jar"))
                System.setProperty("tienda.directorio", origen.getParent().toString());
            if (args.length == 1 && args[0].equals("--verificar")) {
                Path raiz = Path.of(System.getProperty("tienda.directorio", "."));
                Parametros.cargar(raiz.resolve("config/negocio.properties"));
                System.out.println("OK: Java " + Runtime.version().feature() + "; configuración válida; datos: " + raiz.resolve("datos").toAbsolutePath());
                return;
            }
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Se requiere un escritorio gráfico para abrir Swing");
            SwingUtilities.invokeLater(() -> new Aplicacion().iniciar());
        } catch (Exception e) { System.err.println("No se pudo iniciar Tienda Andina: " + e.getMessage()); System.exit(1); }
    }
}
