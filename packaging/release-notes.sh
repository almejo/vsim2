#!/usr/bin/env bash
# Prints the body of a release's page: the «Resumen» (the first paragraph under «## Versión X.Y — date» in
# CHANGELOG.md), where to find the installers and the link to the CHANGELOG at that tag.
#
#   packaging/release-notes.sh <version>        e.g. 0.5
#
# Exits 1 when the version has no section or its first paragraph is not plain text (a heading or a list).
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <version>" >&2
  exit 2
fi
version=$1
script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
changelog="${CHANGELOG:-$(dirname "$script_directory")/CHANGELOG.md}"
repository=https://github.com/almejo/vsim2
tag="${RELEASE_TAG:-v$version}"

summary=$(awk -v heading="## Versión $version " '
  index($0, heading) == 1 { inside = 1; next }
  inside && /^## / { exit }
  inside && /^[[:space:]]*$/ { if (found) exit; next }
  inside { if (!found && ($0 ~ /^#/ || $0 ~ /^[-*] /)) exit; found = 1; print }
' "$changelog")

if [ -z "$summary" ]; then
  echo "CHANGELOG.md has no «Resumen» paragraph under «## Versión $version — …» (the first paragraph, before «### Agregado»)" >&2
  exit 1
fi

cat <<NOTES
$summary

## Instalar

Descarga el archivo de tu sistema de los adjuntos de abajo; los instaladores traen su propio Java.
\`SHA256SUMS\` sirve para comprobar la descarga. Estas versiones todavía no están firmadas: Windows y macOS muestran un
aviso la primera vez. Cómo seguir, formato por formato, en
[Cómo instalar y probar]($repository/blob/$tag/docs/instalar.md).

## Qué cambió

El detalle está en el [CHANGELOG]($repository/blob/$tag/CHANGELOG.md).
NOTES
