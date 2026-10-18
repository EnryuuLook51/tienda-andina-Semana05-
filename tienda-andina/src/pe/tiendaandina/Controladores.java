package pe.tiendaandina;

import java.time.LocalDate;
import java.util.List;
import static pe.tiendaandina.Dominio.*;
import static pe.tiendaandina.Seguridad.*;

public final class Controladores {
    private Controladores() {}
    public static final class ClienteControlador {
        private final Sesion sesion;
        private final Cliente cliente;
        private final GestorPedidos gestor;
        public ClienteControlador(Sesion sesion, Cliente cliente, GestorPedidos gestor) {
            sesion.exigir(Rol.CLIENTE);
            if (!cliente.id().equals(sesion.clienteId())) throw new SecurityException("Identidad del cliente incorrecta");
            this.sesion = sesion; this.cliente = cliente; this.gestor = gestor;
        }
        public Pedido registrar(List<Item> items, Envio envio, Pago pago, Cupon cupon, LocalDate fecha) {
            sesion.exigir(Rol.CLIENTE); return gestor.registrar(cliente, items, envio, pago, cupon, fecha);
        }
        private void propio(Pedido pedido) {
            sesion.exigir(Rol.CLIENTE);
            if (!pedido.cliente.id().equals(sesion.clienteId())) throw new SecurityException("El pedido pertenece a otro cliente");
        }
        public void pagar(Pedido p, LocalDate fecha) { propio(p); gestor.pagar(p, fecha); }
        public void cancelar(Pedido p) { propio(p); gestor.cancelar(p); }
        public void recibir(Pedido p) { propio(p); gestor.entregar(p); }
    }
    public static final class OperadorControlador {
        private final Sesion sesion;
        private final GestorPedidos gestor;
        public OperadorControlador(Sesion sesion, GestorPedidos gestor) {
            sesion.exigir(Rol.OPERADOR); this.sesion = sesion; this.gestor = gestor;
        }
        public void confirmarDeposito(Pedido p, LocalDate fecha) { sesion.exigir(Rol.OPERADOR); gestor.confirmarDeposito(p, fecha); }
        public List<Pedido> transferenciasPendientes() { sesion.exigir(Rol.OPERADOR); return gestor.transferenciasPendientes(); }
        // El enunciado no asigna rol al almacén: el operador lo representa en esta demo.
        public void despachar(Pedido p) { sesion.exigir(Rol.OPERADOR); gestor.despachar(p); }
    }
    public static final class AdministradorControlador {
        private final Sesion sesion;
        private final GestorPedidos gestor;
        public AdministradorControlador(Sesion sesion, GestorPedidos gestor) {
            sesion.exigir(Rol.ADMINISTRADOR); this.sesion = sesion; this.gestor = gestor;
        }
        public ReporteVentas reporte(LocalDate desde, LocalDate hasta) { sesion.exigir(Rol.ADMINISTRADOR); return gestor.reporte(desde, hasta); }
        public void configurar(Configuracion c) { sesion.exigir(Rol.ADMINISTRADOR); gestor.configurar(c); }
        public ConfiguracionEnvios envios() { sesion.exigir(Rol.ADMINISTRADOR); return gestor.configuracionEnvios(); }
        public List<String> erroresNotificacion() { sesion.exigir(Rol.ADMINISTRADOR); return gestor.erroresNotificacion(); }
        public void configurarEnvios(ConfiguracionEnvios e) { sesion.exigir(Rol.ADMINISTRADOR); gestor.configurarEnvios(e); }
        public Cupon crearCupon(String codigo, String porcentaje, LocalDate vence) {
            sesion.exigir(Rol.ADMINISTRADOR); return new Cupon(codigo, porcentaje, vence);
        }
        public void crearProducto(ProductoRepositorio repo, String codigo, String nombre, TipoProducto tipo, String precio, String peso, int stock) {
            sesion.exigir(Rol.ADMINISTRADOR);
            repo.agregar(Producto.crear(codigo.trim(), nombre.trim(), tipo, precio, peso, stock));
        }
        public void editarProducto(ProductoRepositorio repo, String codigo, String nombre, String precio, String peso, int stock) {
            sesion.exigir(Rol.ADMINISTRADOR); repo.buscar(codigo).editar(nombre, precio, peso, stock);
        }
        public void bajaProducto(ProductoRepositorio repo, String codigo) {
            sesion.exigir(Rol.ADMINISTRADOR); repo.buscar(codigo).darDeBaja();
        }
    }
}
