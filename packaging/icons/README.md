# Íconos de VSim 2 para los paquetes

Todos salen de `ui/src/main/resources/icons/app/vsim.svg` (el mismo ícono de la ventana). Se generaron una vez y
quedan en el repositorio, así la compilación de los paquetes no necesita ninguna herramienta de imágenes.

| Archivo | Uso |
| --- | --- |
| `io.github.almejo.vsim2.png` (512 × 512) | ícono de jpackage en Linux, entrada de escritorio, AppImage, Flatpak, Snap |
| `io.github.almejo.vsim2-<n>.png` (16, 32, 48, 64, 128 y 256) | tema de íconos `hicolor` en Linux |
| `io.github.almejo.vsim2.ico` (16 a 256) | jpackage en Windows (msi y zip portátil) |
| `io.github.almejo.vsim2.icns` (16 a 1024, con versiones @2x) | jpackage en macOS (dmg) |

## Cómo se hicieron

1. `render-from-svg.html` muestra el SVG a 1024 × 1024 sobre fondo transparente. Se abrió con Chrome sin ventana y
   se guardó una captura PNG de 1024 × 1024 (por ejemplo
   `google-chrome --headless --window-size=1024,1024 --default-background-color=00000000 --screenshot=vsim-1024.png render-from-svg.html`).
   Se usa Chrome porque dibuja el SVG igual que un navegador (degradados y texto incluidos).
2. Con Pillow (Python) se redujo esa captura a cada tamaño (`Image.resize(..., Image.LANCZOS)`) y se guardaron los
   PNG, el `.ico` (`image.save("io.github.almejo.vsim2.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48),
   (64, 64), (128, 128), (256, 256)])`) y el `.icns` (`image.save("io.github.almejo.vsim2.icns")` desde la captura
   de 1024).

Si cambia `vsim.svg`, se repiten los dos pasos y se reemplazan todos los archivos de esta carpeta con los mismos
nombres. `PackagingFilesTest` revisa que cada PNG tenga su tamaño.
