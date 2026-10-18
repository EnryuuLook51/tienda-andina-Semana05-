# Tienda Andina — aplicación académica

**Inicio y aceptación final:** consultar [GUIA_FINAL.md](GUIA_FINAL.md).
**Mapa del diagrama y diferencias reales:** [ARQUITECTURA.md](ARQUITECTURA.md).
**Resultado de cierre:** [VERIFICACION_FINAL.md](VERIFICACION_FINAL.md) — 307 comprobaciones
y prueba gráfica con tres roles aprobadas. JAR disponible en `dist/`.
**Rediseño visual Swing:** [FRONTEND.md](FRONTEND.md) — carrito, detalle y reportes en tablas,
navegación por secciones, tema verde y controles según estado del pedido.
Este archivo conserva el historial de etapas; sus notas describen el estado de cada etapa,
no necesariamente el estado actual.

Java 21 o superior, sin dependencias externas. Fuente de reglas: `Practica_Refactoring.pdf`, §3.1–3.11.

## Compilar y probar

Desde esta carpeta:

```sh
mkdir -p out
javac -encoding UTF-8 -d out src/pe/tiendaandina/*.java test/pe/tiendaandina/*.java
java -cp out pe.tiendaandina.Pruebas
java -cp out pe.tiendaandina.Aplicacion
```

## Primera etapa

- Cálculo monetario con BigDecimal, descuentos, envíos, recargos e IGV incluido.
- Pagos simulables mediante un puerto; transferencia con confirmación separada.
- Ciclo de vida, entrega digital automática, inventario local todo-o-nada y cancelación.
- Cupones por cliente, documentos con series independientes y consulta de ventas.
- Observadores de estado: las excepciones no revierten el pedido.

## Segunda etapa

- Crear y aplicar cupones desde la interfaz; los códigos no pueden duplicarse.
- Reporte por rango inclusivo de fechas de pago, con subtotal, descuentos, IGV y total
  por pedido y acumulados. Usa los resúmenes inmutables de los comprobantes emitidos.
- Parámetros desde `config/negocio.properties`, incluyendo IGV. Editar y pulsar
  **Recargar parámetros** sin recompilar. Una configuración inválida se rechaza.
  Al recargar se conservan documentos emitidos; los pedidos pendientes se recalculan al pagar.
- 63 comprobaciones automatizadas: límites de descuentos, Yape, fallos de cobro,
  configuración inválida, fechas y conservación de comprobantes, entre otras.

Lanzamiento desde cualquier carpeta:

```sh
sh /ruta/a/tienda-andina/ejecutar.sh
sh /ruta/a/tienda-andina/ejecutar.sh --pruebas
```

Los cupones creados también son en memoria. Los porcentajes de la interfaz se escriben
como `10` para 10 %; en el archivo de configuración se escribe `0.10`.

## Tercera etapa: acceso por rol

Login local y controladores que validan permisos e identidad del dueño del pedido.
Contraseñas derivadas con PBKDF2 y sal individual. Son cuentas públicas de demostración,
no credenciales aptas para producción. No hay registro público ni recuperación de claves.

| Usuario | Rol / perfil |
|---|---|
| `cliente` | Cliente regular de Chimbote |
| `vip` | Cliente VIP de Lima |
| `empresa` | Cliente corporativo con RUC |
| `operador` | Confirma transferencias y representa al almacén al despachar |
| `admin` | Crea cupones, recarga parámetros y consulta reportes |

Contraseña de todas las cuentas: `Demo2026!`. **Cambiar usuario** conserva los pedidos,
vacía el carrito y filtra la lista del cliente por propietario. El cliente no puede
editar su identidad ni tipo desde la pantalla de compras. La asignación del despacho
al operador es una decisión de esta demo: el PDF menciona almacén sin asignarle rol.
La seguridad es local a los controladores de aplicación, no un servidor remoto;
`Sesion` es un valor interno y no un token para aceptar de clientes externos.

## Cuarta etapa: State y Strategy

`EstadosPedido.java` contiene las cinco clases de estado: cada una acepta sus
operaciones válidas y rechaza las demás. El pedido mantiene un estado por composición.
La entrega digital automática es distinta de confirmar la recepción de un envío.

`MediosPago.java` separa las cuatro estrategias de pago: recargos, restricciones,
confirmación manual, cobro y reversa. El enum de la interfaz se traduce a una
estrategia en un único punto de composición. El gestor ya no decide con condicionales
qué medio cobra o reembolsa. Las estrategias de transferencia y contraentrega no
invocan la pasarela. Los cobros externos siguen simulados.

Pruebas adicionales cubren la matriz de operaciones de los cinco estados, las
cuatro estrategias y un fallo de reembolso sin alterar estado, stock o comprobante.

## Quinta etapa: Visitor y ticket

`CalculoTotal.java` separa la construcción de conceptos y su recorrido. Un visitante
calcula subtotal, descuento, envío, recargo e IGV; otro imprime el ticket sin recalcular
los importes del comprobante. Al pagar se conservan conceptos inmutables con la
configuración de esa operación. Cambiar las tasas no altera tickets históricos.

**Guardar ticket** exporta texto UTF-8 con confirmación antes de sobrescribir. Requiere
comprobante emitido e incluye número, cliente, RUC cuando corresponde y desglose.
Es un comprobante de demostración **sin validez tributaria**, no una integración SUNAT.
Un pedido cancelado conserva el documento original; eso no implementa notas de crédito.

La adaptación a Visitor sigue siendo parcial respecto del diagrama: falta separar
los subtipos de descuento y el visitante constructor de resumen. No se duplicó la
aritmética para imprimir: el ticket toma los importes del resumen emitido.

## Sexta etapa: persistencia local

Pedidos, productos/stock, cupones/usos, estados, conceptos históricos y correlativos
se guardan en `datos/tienda.bin`. Hay guardado tras acciones completadas y al cerrar;
**Guardar datos** permite reintentar si ocurre un error. El carrito no se persiste.
Las cuentas demo se reconstruyen al iniciar; no se guardan credenciales en el snapshot.

`RepositorioArchivo` usa un snapshot binario versionado, escritura temporal sincronizada
y sustitución atómica. Un bloqueo impide dos instancias escritoras. Un archivo dañado
o incompatible impide el arranque y no se sobrescribe. Pasarela y observadores se reconectan.

No importar snapshots de terceros: hay filtro de clases y límites, pero es almacenamiento
local de confianza, no un formato de intercambio. Respaldar `datos/tienda.bin` con la app
cerrada. Cambios futuros del modelo pueden requerir migración; aún no hay migrador ni
copias automáticas. Si falla el guardado, el cambio sigue en memoria y se muestra aviso.

## Séptima etapa: administración del catálogo

En sesión `admin`, **Administrar catálogo** permite alta, edición de nombre/precio/peso/stock
y baja lógica. Código y tipo son inmutables. Los códigos no se reutilizan ni con bajas.
Digitales/suscripciones exigen peso y stock cero. Los cambios inválidos no modifican el producto.

El repositorio de productos mantiene identidad compartida con pedidos para descontar/reponer
el mismo inventario. Los nuevos pedidos solo aceptan productos activos; un pedido existente
puede terminar de pagarse aunque luego se dé de baja el producto. El precio de cada ítem
se captura al añadir al carrito; cambiar catálogo no revaloriza ese ítem. Los comprobantes
emitidos conservan además los conceptos originales. En pendientes, nombre y peso se leen
del catálogo al pagar (decisión que debe contrastarse con el código original).

El stock del formulario es una corrección absoluta del inventario actual, no una entrada
de mercadería; aún no hay historial de ajustes. No se eliminan registros históricos.
La baja lógica se guarda junto al catálogo en el snapshot local.

## Octava etapa: administración de envíos

`admin` puede usar **Administrar envíos** para cambiar fijo y precio por kg de estándar
y express, y la lista de ciudades express. Las tarifas iniciales corresponden al PDF.
Las ciudades se normalizan sin distinguir mayúsculas y espacios externos. Se rechazan
tarifas negativas y ciudades vacías. Estándar VIP y recojo en Chimbote siguen gratuitos.

La configuración se guarda en el snapshot local. No es parte de `negocio.properties`:
recargar IGV/descuentos no restablece las tarifas de envío. Los snapshots anteriores sin
ese campo usan tarifas originales. Los pendientes se recalculan con las tarifas vigentes
al pagar; los documentos emitidos y sus tickets no cambian.

## Novena etapa: notificaciones desacopladas

`Notificaciones.java` contiene `CanalMensaje`, `ObservadorCorreo`, `ObservadorSms`
y `CanalSimulado`. Cada observador recibe su canal por constructor. Correo recibe
todas las transiciones válidas; SMS únicamente ENVIADO para VIP y CORPORATIVO.
La creación pendiente no genera aviso: no es un cambio de estado. La entrega digital
genera los dos correos de sus transiciones PAGADO y ENTREGADO.

El gestor aísla cada excepción para permitir los demás avisos. El error identifica
pedido, transición, observador y tipo de excepción. `admin` puede consultar
**Errores de notificación**; ese registro se conserva en el snapshot. No hay reintentos
ni entrega garantizada. Los canales siguen siendo simulados; no se han agregado
credenciales, conexiones SMTP ni números de teléfono reales.

## Décima etapa: productos, reglas y visitantes del modelo

Altas de catálogo y datos demo usan `ProductoFisico`, `ProductoDigital` y `Suscripcion`.
`Producto.crear` es el punto de composición. La clase base se conserva concreta para
leer snapshots previos que la instanciaban; aún no es abstracta como en el diagrama.
Los productos nuevos despachan sus operaciones por polimorfismo; solo la ruta de
compatibilidad de la clase base distingue el enum antiguo.

`VisitanteStock` acumula todos los ítems físicos antes de validar, descontar o reponer;
`VisitanteHabilitacion` identifica los accesos digitales y suscripciones sin afectar stock.
Por ahora habilitación sigue representada por un indicador del pedido, no por entidades
`Acceso` con duración, URL o credenciales. No se han simulado renovaciones mensuales.

`DescuentoPorTipoCliente`, `DescuentoVolumen` y `DescuentoCupon` implementan una regla
común. `ConstructorConceptos` compone mayor(cliente, volumen) + cupón y aplica el tope.
Las reglas quedan separadas del visitante que realiza la aritmética monetaria.

## Undécima etapa: Dinero, ConstructorResumen y Acceso

`Dinero` es un valor inmutable en soles con aritmética y redondeo HALF_UP. Se utiliza
en el visitante calculador para descuento y total. Las líneas se acumulan antes de
redondear, conservando el orden de cálculo original. `Resumen` mantiene sus campos
BigDecimal para compatibilidad con snapshots previos: la migración a Dinero no es total.

`ConstructorResumen` implementa VisitanteTotal y delega la aritmética a CalculadoraTotal,
sin replicar reglas. El mismo resultado inmutable se usa en documento, ticket y reporte.

`Acceso` registra cliente, código de producto, tipo y fecha al confirmar el pago.
Hay un derecho por producto digital/suscripción del pedido, aunque tenga varios ítems
o unidades. No se crean accesos para productos físicos ni antes de confirmar el pago.
Se persisten los derechos y se reconstruyen desde la fecha de pago para snapshots
anteriores que solo tenían el indicador booleano. La UI muestra el detalle al consultar.
Son derechos locales: no implementan descargas reales, caducidad, renovaciones ni
revocación al cancelar, que el PDF no define.

## Duodécima etapa: usuarios y repositorios

`Cliente`, `Operador` y `Administrador` heredan de `Usuario`. La autenticación admite
una identidad polimórfica y obtiene su rol, en lugar de guardarlo por separado.
Se conserva la sobrecarga de registro antigua para compatibilidad de llamadas.
Se verificó la carga de un snapshot escrito antes de convertir Cliente de record a clase.

`Repositorio<T>` declara el contrato común. `PedidoRepositorio` lo especializa con
ventas y transferencias pendientes; `PedidosEnMemoria` implementa esas consultas.
El gestor delega las búsquedas a ese contrato; conserva su colección serializada para
leer snapshots anteriores. La decimotercera etapa completa la separación del contrato
de productos y permite inyectar un repositorio de pedidos independiente.

## Decimotercera etapa: repositorios intercambiables

`ProductoRepositorio` ahora es una interfaz especializada; `ProductosEnMemoria` contiene
la implementación. Controladores y UI dependen del contrato para gestionar catálogo.
`GestorPedidos` admite un `PedidoRepositorio` por constructor; la UI lo proporciona
explícitamente. Las pruebas usan otro adaptador, sin heredar de PedidosEnMemoria.

Para un gestor nuevo el repositorio inyectado debe estar vacío; la restauración de datos
se hace mediante el snapshot, que conserva los correlativos. Al serializar, se captura
el contenido del adaptador sin serializar el propio repositorio. Se comprobó guardar,
restaurar y volver a guardar sin pérdida ni duplicados, además de leer un snapshot previo.
El operador tiene un botón para listar transferencias pendientes a través del controlador.

Esto no implementa transacciones de base de datos: los adaptadores deben conservar las
identidades de los agregados en memoria. Un repositorio remoto requeriría unidad de trabajo,
contrato de fallos y transacciones antes de conectarse a una pasarela real.

### Trabajo restante

Interfaz Swing completa (el demostrador inicial ya permite crear y operar pedidos);
adaptación completa
a las clases y patrones del diagrama (actualmente es un núcleo inicial, no el diseño final).

Los datos se guardan localmente; el carrito se pierde al cerrar. No hay cobros ni comunicaciones reales.
Las pruebas son nuevas, no la suite original que menciona el enunciado.

Decisiones pendientes de contrastar con el código original: modo HALF_UP para empates,
fecha de pago para filtrar ventas, cupón consumido al confirmar el pago (no al crear
el pedido), y disponibilidad de recojo en Chimbote independientemente de residencia.
No se restauran cupones al cancelar: el enunciado no lo especifica.

La atomicidad es local a una instancia del gestor; no es una transacción distribuida
con un banco real. La numeración se conserva en el snapshot local.
