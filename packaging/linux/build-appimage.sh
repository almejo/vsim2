#!/usr/bin/env bash
# Builds the AppImage of VSim 2 from a jpackage app image (./gradlew :ui:jpackageImage -PvsimPackageFormat=appimage).
#
#   packaging/linux/build-appimage.sh <image-dir> <version> <output>
#
# <image-dir> is ui/build/packaging/image/VSim2; <output> is e.g. ui/build/distribution/VSim2-0.6-x86_64.AppImage.
# appimagetool 1.9.1 comes from $APPIMAGETOOL if set, else from the PATH, else it is downloaded next to <output>
# (in a folder called appimagetool-1.9.1). The image folder is only read: the AppDir is a copy in a temporary folder.
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

appimagetool=${APPIMAGETOOL:-}
if [ -z "$appimagetool" ] && command -v appimagetool > /dev/null; then
  appimagetool=$(command -v appimagetool)
fi
if [ -z "$appimagetool" ]; then
  tool_directory="$(dirname "$output")/appimagetool-1.9.1"
  appimagetool="$tool_directory/appimagetool-x86_64.AppImage"
  if [ ! -x "$appimagetool" ]; then
    mkdir -p "$tool_directory"
    curl -sSfL -o "$appimagetool" \
      https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage
    chmod +x "$appimagetool"
  fi
fi

work_directory=$(mktemp -d)
trap 'rm -rf "$work_directory"' EXIT
application_directory="$work_directory/VSim2.AppDir"
cp -a "$image_directory" "$application_directory"

cp "$packaging_directory/icons/$application_id.png" "$application_directory/$application_id.png"
cp "$packaging_directory/icons/$application_id.png" "$application_directory/.DirIcon"
cp "$script_directory/$application_id.desktop" "$application_directory/$application_id.desktop"
mkdir -p "$application_directory/usr/share/metainfo"
cp "$script_directory/$application_id.metainfo.xml" \
  "$application_directory/usr/share/metainfo/$application_id.metainfo.xml"
cat > "$application_directory/AppRun" <<'APPRUN'
#!/bin/sh
here=$(dirname "$(readlink -f "$0")")
exec "$here/bin/VSim2" "$@"
APPRUN
chmod +x "$application_directory/AppRun"

# APPIMAGE_EXTRACT_AND_RUN: runners (and containers) often have no FUSE. --no-appstream: no appstreamcli needed.
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$appimagetool" --no-appstream "$application_directory" "$output"
echo "Built $output (VSim 2 $version)"
