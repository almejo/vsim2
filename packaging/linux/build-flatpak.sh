#!/usr/bin/env bash
# Builds the Flatpak bundle of VSim 2 from a jpackage app image (./gradlew :ui:jpackageImage -PvsimPackageFormat=flatpak).
#
#   packaging/linux/build-flatpak.sh <image-dir> <version> <output>
#
# <output> is e.g. ui/build/distribution/VSim2-0.6-linux-x86_64.flatpak. Needs flatpak and flatpak-builder, and a
# Flathub remote (flatpak remote-add --user --if-not-exists flathub https://flathub.org/repo/flathub.flatpakrepo):
# the runtime org.freedesktop.Platform//25.08 and its Sdk are installed from there. The image folder is only read.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <image-dir> <version> <output>" >&2
  exit 2
fi
image_directory=$(cd "$1" && pwd)
version=$2
output=$3
script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
packaging_directory=$(dirname "$script_directory")
application_id=io.github.almejo.vsim2

if [ ! -x "$image_directory/bin/VSim2" ]; then
  echo "$image_directory is not a VSim2 app image (bin/VSim2 is missing)" >&2
  exit 1
fi
mkdir -p "$(dirname "$output")"
output=$(cd "$(dirname "$output")" && pwd)/$(basename "$output")

work_directory=$(mktemp -d)
trap 'rm -rf "$work_directory"' EXIT
mkdir "$work_directory/sources"
cp -a "$image_directory" "$work_directory/sources/image"
cp "$script_directory/$application_id.desktop" "$script_directory/$application_id.metainfo.xml" \
  "$script_directory/$application_id.mime.xml" "$work_directory/sources/"
cp "$packaging_directory"/icons/"$application_id"-*.png "$work_directory/sources/"

# Icons: <id>-<size>.png → hicolor/<size>x<size>/apps/<id>.png.
icon_commands=""
for icon in "$packaging_directory"/icons/"$application_id"-*.png; do
  size=$(basename "$icon" .png)
  size=${size##*-}
  icon_commands="$icon_commands
      - install -Dm644 $application_id-$size.png /app/share/icons/hicolor/${size}x${size}/apps/$application_id.png"
done

cat > "$work_directory/$application_id.yml" <<MANIFEST
app-id: $application_id
runtime: org.freedesktop.Platform
runtime-version: '25.08'
sdk: org.freedesktop.Sdk
command: VSim2
finish-args:
  - --share=ipc
  - --socket=x11
  - --share=network
  - --device=dri
  - --filesystem=home
modules:
  - name: vsim2
    buildsystem: simple
    build-commands:
      - mkdir -p /app/vsim2 /app/bin
      - cp -a image/. /app/vsim2/
      - ln -s /app/vsim2/bin/VSim2 /app/bin/VSim2
      - install -Dm644 $application_id.desktop /app/share/applications/$application_id.desktop
      - install -Dm644 $application_id.metainfo.xml /app/share/metainfo/$application_id.metainfo.xml
      - install -Dm644 $application_id.mime.xml /app/share/mime/packages/$application_id.xml$icon_commands
    sources:
      - type: dir
        path: sources
MANIFEST

cd "$work_directory"
flatpak-builder --force-clean --user --disable-rofiles-fuse --install-deps-from=flathub \
  --default-branch=stable --repo=repository build "$application_id.yml"
flatpak build-bundle repository "$output" "$application_id" stable
echo "Built $output (VSim 2 $version)"
