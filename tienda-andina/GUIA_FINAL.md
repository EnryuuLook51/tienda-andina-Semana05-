# Tienda Andina — guía de ejecución y aceptación

## Alcance

Aplicación académica Java Swing, Java 21 o superior, sin dependencias externas.
Implementación nueva a partir de `Practica_Refactoring.pdf`, no una refactorización
verificada contra el código original: no se entregaron ese código ni sus pruebas.
El DOCX y el proyecto Astah no se han modificado.

## Ejecutar

Desde la carpeta que contiene `tienda-andina`:

```sh
sh tienda-andina/ejecutar.sh
```

Para construir el entregable (ejecuta todas las pruebas primero):

```sh
sh tienda-andina/empaquetar.sh
java -jar tienda-andina/dist/tienda-andina.jar
```

Distribuir **el JAR junto con la carpeta `config` de `dist`**. Java debe estar instalado;
no es un ejecutable nativo. El JAR localiza sus parámetros y datos junto a sí mismo,
independientemente del directorio desde el que se ejecuta. Funciona con Java 21+ y
escritorio gráfico; no se verificaron Windows/macOS en este entorno Linux.

La ejecución desde fuentes usa `tienda-andina/datos`; el JAR usa `dist/datos`.
Para continuar una demo existente, con ambas apps cerradas, copiar la carpeta `datos`
junto al JAR. No se copia automáticamente para evitar distribuir datos privados.

Diagnóstico del paquete sin abrir ventanas:

```sh
java -jar tienda-andina/dist/tienda-andina.jar --verificar
```

## Cuentas públicas de demostración

Contraseña común: **`Demo2026!`**.

| Usuario | Perfil | Acciones principales |
|---|---|---|
| cliente | Regular, Chimbote | Comprar, pagar, cancelar, recibir sus pedidos |
| vip | VIP, Lima | Igual, con beneficios VIP |
| empresa | Corporativo, RUC de prueba | Igual, recibe factura |
| operador | Operador/almacén | Transferencias pendientes, confirmar depósito, despachar |
| admin | Administrador | Reportes, catálogo, cupones, parámetros y envíos |

Los permisos se verifican en controladores además de deshabilitar botones.
Los usuarios cliente solo ven sus propios pedidos. Cambiar de usuario vacía el carrito.
No hay registro público ni administración de usuarios/contraseñas.

## Pruebas automatizadas

```sh
sh tienda-andina/ejecutar.sh --pruebas
```

Compila con `--release 21 -Xlint:all -Werror`: las advertencias también hacen fallar la
compilación. Las pruebas no cobran dinero ni envían mensajes. Los archivos de prueba
se crean en directorios temporales y no usan `datos/tienda.bin` de la aplicación.

Prueba gráfica independiente, después de compilar y con un escritorio disponible:

```sh
cd tienda-andina
java -cp out pe.tiendaandina.PruebasInterfaz
```

Comprueba los tres roles, permisos, compra desde botones, despacho, recepción,
persistencia entre reinicios y reporte.
Usa un directorio temporal y cuentas demo. Si no hay escritorio, falla explícitamente:
no se presenta como una prueba visual aprobada.

## Guion de aceptación funcional

Usar datos iniciales o crear productos nuevos para evitar que ventas previas alteren stock.

1. **Físico con tarjeta:** `cliente`, 1 Mochila, ESTANDAR, TARJETA. Registrar y pagar.
   Esperado con tarifas iniciales: subtotal 59.90, envío 9.50, recargo 2.08, total **71.48**,
   IGV incluido 10.90 y base 60.58. Se descuenta una unidad. Guardar ticket.
2. **Despacho y recepción:** cambiar a `operador`, seleccionar ese pedido, despachar;
   volver a `cliente`, recibir. Ya no debe poder cancelarse.
3. **Transferencia:** crear otro pedido con TRANSFERENCIA. El cliente no puede pagarlo;
   queda pendiente sin descuento de stock. `operador` lo lista y confirma. Pasa a pagado.
4. **Cancelación:** cancelar ese pedido pagado como dueño. Stock repuesto, reembolso
   registrado, pedido excluido de ventas. Cancelar un pendiente no repone stock.
5. **Digital VIP:** `vip`, 1 Curso digital, SIN ENVÍO, YAPE. Subtotal 118, descuento 5.90,
   total **112.10**. Tras pagar queda ENTREGADO y muestra un acceso local, sin stock.
6. **Corporativo:** `empresa`, 1 Curso digital. Total **106.20**, factura F001 y RUC.
   Los demás reciben boleta B001; cada serie lleva su propia numeración.
7. **Cupones:** `admin` crea cupón vigente. Aplicarlo en cliente y pagar. Un segundo uso
   por ese cliente se rechaza. Probar vencido; comprobar tope acumulado de 30 %.
8. **Límites:** Yape > 500 se rechaza; express solo en ciudades configuradas; contraentrega
   solo para estándar y regular/VIP. Contraentrega nace pagado y descuenta stock.
9. **Catálogo:** `admin` crea, edita y da de baja un producto. No aparece en compras nuevas.
   Los precios de ítems ya agregados y comprobantes emitidos no cambian.
10. **Parámetros/envíos:** cambiar tarifas desde `admin`, o IGV en `config/negocio.properties`
    y recargar. Nuevos pagos usan los valores nuevos; tickets históricos no cambian.
11. **Reporte:** `admin`, rango de fechas inclusivo. Solo pagados/enviados/entregados;
    subtotal, descuentos, IGV y total coinciden con los comprobantes.
12. **Reinicio:** cerrar con la X, abrir de nuevo. Verificar estado, stock, cupones, accesos,
    tarifas y numeración. Una segunda instancia sobre el mismo directorio debe rechazarse.

No afirmar que el guion manual entero fue ejecutado: la suite automatizada cubre sus
reglas, y la prueba Swing ejecuta el subconjunto indicado arriba.

## Almacenamiento y límites

- Guardado local tras acciones y al cerrar. Si falla, aparece aviso y los cambios siguen
  en memoria. Usar **Guardar datos** antes de cerrar. No es transacción bancaria distribuida.
- Respaldar `datos/tienda.bin` con la aplicación cerrada. No cargar archivos de terceros.
  Archivo dañado/incompatible: se bloquea el arranque, sin sobrescribirlo.
- Cobros/reembolsos, correo y SMS son **simulados**. No hay SMTP, banco, Yape ni SUNAT reales.
- Tickets de demostración **sin validez tributaria**. Cancelación conserva documento;
  no implementa notas de crédito ni revocación de descargas.
- Accesos digitales son registros locales; no hay descarga real, vencimiento automático
  ni renovación de suscripciones. No se inventaron reglas que el PDF no especifica.
- El reporte usa fecha de pago; cupón se consume al confirmar pago y no se restaura al
  cancelar. Redondeo HALF_UP. Son decisiones documentadas para contrastar con el original.
- Las cuentas demo no son credenciales de producción. La seguridad protege la app local,
  no un servicio remoto; quien puede modificar el proceso o sus archivos no está aislado.

## Correspondencia con el diseño

Consultar `ARQUITECTURA.md`. El código conserva los patrones principales, pero **no es
una transcripción exacta del diagrama**. No se debe afirmar lo contrario en la entrega.
