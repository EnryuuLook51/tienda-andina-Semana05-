# Correspondencia entre diseño e implementación

| Elemento del diseño | Código |
|---|---|
| Usuario, Cliente, Operador, Administrador | Usuario.java, Dominio.Cliente, Operador.java, Administrador.java |
| Sesión, rol, autenticación | Seguridad.java (PBKDF2 + sal por usuario) |
| Controladores por actor | Controladores.java |
| GestorPedidos, Pedido, ItemPedido | Dominio.GestorPedidos, Dominio.Pedido, Dominio.Item |
| Producto y subclases | Dominio.Producto, ProductoFisico, ProductoDigital, Suscripcion |
| State | EstadosPedido.java: interfaz y cinco implementaciones |
| Strategy de pagos | MediosPago.java: interfaz y cuatro implementaciones |
| Visitor de total | CalculoTotal.java: conceptos, CalculadoraTotal, ConstructorResumen, ImpresorTicket |
| Reglas de descuento | ReglasDescuento.java: cliente, volumen y cupón |
| Visitor de productos | OperacionesProducto.java: stock y habilitación |
| Dinero y resumen | Dinero.java, Dominio.Resumen |
| Acceso | Acceso.java, generado al confirmar pago |
| Observer y canales | Notificaciones.java, Dominio.ObservadorPedido |
| Repositorios | Repositorio<T>, PedidoRepositorio, ProductoRepositorio |
| Adaptadores locales | PedidosEnMemoria, ProductosEnMemoria, RepositorioArchivo |
| Configuración y envío | Parametros.java, Dominio.Configuracion, ConfiguracionEnvios |
| Documento y reporte | Dominio.Documento, LineaVenta, ReporteVentas |

## Diferencias que permanecen

- Varias clases son anidadas y usan nombres cortos: `Item` equivale a ItemPedido,
  `Resumen` a ResumenPedido, `Documento` a DocumentoVenta y `Configuracion` a
  ConfiguracionNegocio. Los archivos no corresponden uno a uno con las cajas.
- `Producto` sigue siendo concreto para cargar snapshots anteriores. Las nuevas altas
  usan las subclases. Su ruta de compatibilidad conserva un switch por tipo antiguo.
- El resumen serializado mantiene BigDecimal para compatibilidad; `Dinero` se usa en
  aritmética del visitante, no en todos los campos monetarios de cada entidad.
- Las reglas de descuento son estrategias separadas que producen un concepto agregado;
  no son tres elementos de la jerarquía de ConceptoTotal como en el dibujo.
- ConstructorResumen delega a CalculadoraTotal para no duplicar reglas. ImpresorTicket
  recorre los conceptos históricos y usa los importes del documento, sin recalcularlos.
- PedidoRepositorio es inyectable; el adaptador de snapshots conserva la colección
  histórica del gestor. No existe una unidad de trabajo relacional ni se afirma que
  un repositorio remoto sea intercambiable sin resolver transacciones.
- VisitanteStock opera sobre los objetos de producto del agregado; no recibe directamente
  ProductoRepositorio como en el diagrama. Primero valida todo el lote.
- Los visitantes de producto reciben el tipo base para soportar objetos históricos.
- Sesion contiene una identidad resumida, no una referencia completa a Usuario. Los
  usuarios autenticados sí pertenecen a la jerarquía Usuario.
- Los tipos de documento y envío se representan con enum/datos y registros, no con todas
  las clases y asociaciones exactas de Astah. Correlativos viven en el gestor local.
- No hay integraciones externas reales; hay puertos y adaptadores simulados para la demo.

## Responsabilidades y restricciones

La UI solo compone casos de uso y muestra resultados. Los controladores autorizan rol
y propietario; el gestor coordina pago, stock, documento, accesos y eventos. State limita
transiciones; Strategy encapsula el medio de pago; Visitor construye salidas; Observer
aísla canales; Repository separa las consultas. La composición inicial se hace manualmente,
sin contenedor de dependencias ni librerías adicionales.

La práctica original pide preservar comportamiento de un sistema existente. Sin ese
código y su suite, solo puede verificarse conformidad con las reglas escritas del PDF,
no equivalencia observacional con una implementación que no se recibió.
