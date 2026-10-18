#!/bin/sh
set -eu
cd "$(dirname "$0")"
sh ejecutar.sh --pruebas
temporal=$(mktemp -d)
trap 'rm -rf -- "$temporal"' EXIT
# Compilación limpia: no incluir clases de pruebas ni restos de versiones anteriores.
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$temporal" src/pe/tiendaandina/*.java
mkdir -p dist/config
jar --create --file dist/tienda-andina.jar --main-class pe.tiendaandina.Aplicacion -C "$temporal" .
if [ ! -f dist/config/negocio.properties ]; then
    cp config/negocio.properties dist/config/negocio.properties
fi
cp GUIA_FINAL.md ARQUITECTURA.md VERIFICACION_FINAL.md FRONTEND.md dist/
cp iniciar-jar.sh dist/iniciar.sh
jar --create --no-manifest --file dist/tienda-andina-entrega.zip \
    -C dist tienda-andina.jar -C dist config -C dist iniciar.sh \
    -C dist GUIA_FINAL.md -C dist ARQUITECTURA.md -C dist VERIFICACION_FINAL.md -C dist FRONTEND.md
echo 'Creado: dist/tienda-andina.jar (Java 21 o superior)'
echo 'Distribución: dist/tienda-andina-entrega.zip (sin datos personales ni licencia de Astah)'
