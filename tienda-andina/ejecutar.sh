#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d out src/pe/tiendaandina/*.java test/pe/tiendaandina/*.java
if [ "${1:-}" = "--pruebas" ]; then
    java -cp out pe.tiendaandina.Pruebas
    java -cp out pe.tiendaandina.PruebasSeguridad
    java -cp out pe.tiendaandina.PruebasPatrones
    java -cp out pe.tiendaandina.PruebasVisitor
    java -cp out pe.tiendaandina.PruebasPersistencia
    java -cp out pe.tiendaandina.PruebasCatalogo
    java -cp out pe.tiendaandina.PruebasEnvios
    java -cp out pe.tiendaandina.PruebasNotificaciones
    java -cp out pe.tiendaandina.PruebasModelo
    java -cp out pe.tiendaandina.PruebasValores
    java -cp out pe.tiendaandina.PruebasUsuariosRepositorios
    java -cp out pe.tiendaandina.PruebasInyeccion
    exec java -cp out pe.tiendaandina.PruebasCierre
fi
exec java -cp out pe.tiendaandina.Aplicacion
