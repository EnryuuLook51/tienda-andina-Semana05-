# Verificación de cierre

## Resultado ejecutado

- Compilación Java 21 con `-Xlint:all -Werror`: correcta, sin advertencias.
- Suite de reglas, seguridad, patrones, persistencia, catálogo y regresión: **307 comprobaciones aprobadas**.
- Prueba Swing con escritorio real y datos aislados: aprobada. Login cliente → compra
  con tarjeta → cerrar/guardar → login operador → despachar → cerrar/guardar → login
  cliente → recibir → cerrar/guardar → login admin → reporte. También verifica permisos.
- Capturas revisadas tras el rediseño: compra con carrito tabular, pedido con resumen lateral
  y administración con reporte tabular. Prueba Swing ampliada con comprobaciones del carrito,
  eliminación de filas y controles deshabilitados según estado. Consultar FRONTEND.md.
- Compatibilidad: se cargó un snapshot creado antes de cambiar Cliente de record a clase.
- JAR construido desde un directorio de compilación limpio, sin clases de prueba.
- `java -jar ... --verificar` ejecutado desde otra carpeta: configuración encontrada correctamente.

## Correcciones de cierre

- Fecha de pago requerida antes de iniciar cobro o modificar inventario.
- Accesos preparados antes del cobro, evitando fallos tardíos de construcción.
- Reposición validada en todo el lote antes de reembolsar o modificar stock; rechazo
  de desbordamiento sin cambios parciales.
- Fechas de reporte y códigos de cupón vacíos rechazados explícitamente.
- Arranque del JAR con rutas relativas a su ubicación y diagnóstico sin interfaz.

## Qué significa «listo»

Listo como **aplicación académica local para demostrar y ejecutar pruebas de aceptación**.
No es un sistema de pagos de producción ni se ha certificado equivalencia con la suite
original ausente. No se han probado Windows/macOS. El guion manual ampliado está en
GUIA_FINAL.md; las diferencias reales con Astah están en ARQUITECTURA.md.

El DOCX, la imagen y el proyecto Astah no fueron modificados. La documentación de
entrega sigue a cargo del usuario. No presentar el código como copia exacta del diagrama.
