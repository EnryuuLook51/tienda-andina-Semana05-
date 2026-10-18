# Interfaz Swing renovada

La interfaz sigue siendo Java Swing, sin dependencias, fuentes remotas ni cambios
en los contratos del dominio o el formato de datos.

## Organización

- **Cabecera:** identidad, perfil y cambio de usuario. Indicadores de pedidos,
  pendientes y ventas confirmadas según los pedidos visibles para la sesión.
- **Nueva compra:** catálogo, cantidad, envío, pago y cupón junto al carrito en tabla.
  Permite quitar un ítem y muestra el subtotal de productos; no lo confunde con el
  total final con envío y descuentos. Registrar abre el detalle del pedido.
- **Pedidos:** selector, tabla de productos, estado, comprobante y desglose monetario
  separado. El total permanece a la vista en la tarjeta lateral.
- **Administración:** herramientas del negocio y reporte en tabla con renglón de totales.
- **Actividad:** conserva la bitácora técnica y notificaciones simuladas sin saturar
  el detalle del pedido. La barra inferior muestra el último mensaje.

Las pestañas de compra/administración se habilitan por rol. Los controles también
conservan sus validaciones de autorización. Acciones de pago, recepción, cancelación
y despacho se deshabilitan cuando no corresponden al estado seleccionado.

## Presentación y accesibilidad

Paleta verde con fondos claros, tipografía del sistema, tarjetas, espaciado consistente,
importes alineados a la derecha y estado expresado con texto además de color.
Botones con estados de hover, pulsación, foco de teclado y deshabilitado. Los campos
tienen etiquetas asociadas y nombre accesible. Las tablas son de solo lectura;
el formulario se puede desplazar cuando la ventana es pequeña.

No se añadieron animaciones decorativas: compras y gestión son operaciones frecuentes.
La cancelación requiere confirmación y explica si habrá reposición/reembolso simulado.
Una cantidad mal escrita se rechaza antes de agregar al carrito.

## Comprobación

La prueba `PruebasInterfaz` ahora verifica tabla de carrito, quitar ítem, bloqueo de
registro vacío, bloqueo de pago duplicado y tabla de reporte, además del recorrido
cliente → operador → cliente → administrador con persistencia entre reinicios.
Las capturas de compra, pedido y administración se revisaron en Linux.

Ejecutar la versión empaquetada reconstruida:

```sh
java -jar tienda-andina/dist/tienda-andina.jar
```

Cierra la ventana antigua antes de abrir la nueva. El JAR usa su misma carpeta de
datos anterior; el rediseño no migra ni borra pedidos.
