#!/usr/bin/env bash
# Lays out the folder snapcraft builds VSim 2 from.
#
#   packaging/snap/prepare-snap.sh <image-dir> <version> <output-dir>
#
# <output-dir> gets snap/snapcraft.yaml (version filled in), snap/gui/ (icon + desktop entry) and image/ (a copy of the
# jpackage app image built with -PvsimPackageFormat=snap). Then snapcraft (or snapcore/action-build with
# path: <output-dir>) leaves vsim2_<version>_amd64.snap there.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <image-dir> <version> <output-dir>" >&2
  exit 2
fi
image_directory=$(cd "$1" && pwd)
version=$2
script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
packaging_directory=$(dirname "$script_directory")
application_id=io.github.almejo.vsim2

if [ ! -x "$image_directory/bin/VSim2" ]; then
  echo "$image_directory is not a VSim2 app image (bin/VSim2 is missing)" >&2
  exit 1
fi
mkdir -p "$3"
output_directory=$(cd "$3" && pwd)
rm -rf "$output_directory/snap" "$output_directory/image"
mkdir -p "$output_directory/snap/gui"
sed "s/@VERSION@/$version/" "$script_directory/snapcraft.yaml" > "$output_directory/snap/snapcraft.yaml"
cp "$packaging_directory/icons/$application_id.png" "$output_directory/snap/gui/vsim2.png"
# snapcraft wants Icon=${SNAP}/meta/gui/<icon> and the desktop file named after the app.
sed -e 's|^Icon=.*|Icon=${SNAP}/meta/gui/vsim2.png|' -e 's|^Exec=.*|Exec=vsim2 %F|' \
  "$packaging_directory/linux/$application_id.desktop" > "$output_directory/snap/gui/vsim2.desktop"
cp -a "$image_directory" "$output_directory/image"
echo "Snap build folder ready in $output_directory (VSim 2 $version)"
