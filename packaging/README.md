# Paquetes de VSim 2

Todo lo que no es Java para armar los instaladores. Cada paquete lleva su propio Java (un runtime hecho con `jlink`
solo con los módulos que VSim usa), así nadie tiene que instalar Java para usar VSim.

| Carpeta | Contenido |
| --- | --- |
| `packaging.gradle.kts` | las tareas de Gradle (aplicado desde `ui/build.gradle.kts`) |
| `icons/` | los íconos (PNG, `.ico`, `.icns`), hechos una vez desde `vsim.svg`; ver [icons/README.md](icons/README.md) |
| `linux/` | entrada de escritorio `io.github.almejo.vsim2.desktop` y datos AppStream (`.metainfo.xml`) para AppImage, Flatpak y Snap |
| `linux/jpackage/` | lo que reemplaza archivos de jpackage en el deb y el rpm: `VSim2.desktop` (agrega `StartupWMClass`) y `preinst` (crea `/usr/share/desktop-directories`, sin la cual el `postinst` de jpackage falla) |
| `file-associations/` | la asociación de los archivos `.vsim` (`application/x-vsim2`) |

## Requisitos

- Un JDK 21 completo, con `jlink`, `jpackage` y la carpeta `jmods` (Temurin la trae; en Ubuntu, `openjdk-21-jdk`
  más `openjdk-21-jmods`). Gradle usa el JDK 21 de su toolchain; otro se elige con `-PvsimPackagingJdk=/ruta/al/jdk`.
- Linux: `dpkg-deb` y `fakeroot` para el deb; `rpmbuild` (paquete `rpm`) para el rpm. Sin `rpmbuild` el rpm se
  salta con un aviso.
- Windows: WiX 3 (`candle.exe` y `light.exe` en el `PATH`) para el msi.
- macOS: nada más.

## Comandos

Desde la raíz del repositorio:

    ./gradlew :ui:runtimeImage          # el Java de VSim → ui/build/packaging/runtime
    ./gradlew :ui:jpackageImage         # la carpeta de la aplicación → ui/build/packaging/image/VSim2 (VSim2.app en macOS)
    ./gradlew :ui:nativePackages        # los instaladores de este sistema → ui/build/distribution/
    ./gradlew :ui:packagedRuntimeCheck  # comprueba que el Java de la imagen basta para VSim

`nativePackages` deja, según el sistema:

| Sistema | Archivos |
| --- | --- |
| Linux | `VSim2-<v>-linux-amd64.deb`, `VSim2-<v>-linux-x86_64.rpm` |
| Windows | `VSim2-<v>-windows-x64.msi`, `VSim2-<v>-windows-x64-portable.zip` |
| macOS | `VSim2-<v>-macos-arm64.dmg` (o `-x64`) |

La arquitectura sale de la máquina; `-PvsimPackageArchitecture=x64|arm64` la cambia en los nombres.

### La imagen y su formato

Cada lanzador le dice a VSim de qué paquete viene con `-Dvsim.packageFormat=<formato>` (`msi`, `zip`, `deb`, `rpm`,
`appimage`, `flatpak`, `snap`, `dmg`; sin la propiedad, VSim se considera el jar). Los instaladores lo ponen solos. La
imagen de `jpackageImage` sirve para varios formatos, así que se elige con `-PvsimPackageFormat`; por omisión es
`zip` en Windows, `dmg` en macOS y `appimage` en Linux:

    ./gradlew :ui:jpackageImage -PvsimPackageFormat=flatpak

### Versión en macOS

macOS no acepta una versión que empiece con 0, así que antes de VSim 1.0 la versión escondida del paquete es
`1.0.<menor>` (VSim 0.6 → 1.0.6); desde 1.0 es la misma versión. Gradle la calcula (`macBundleVersion`) y
`packagingVersionRules` (parte de `test`) revisa la regla.

### Comprobar un paquete instalado

`PackagedRuntimeCheck` corre sin ventanas con el Java del paquete: carga los textos en español e inglés y los nombres
de los meses en español, abre y simula el ejemplo «Sumador de 4 bits», exporta SVG y PDF, enciende el servidor de la
IA en un puerto libre de 127.0.0.1 y le hace una petición. Termina con `PackagedRuntimeCheck OK` y código 0, o con
`PackagedRuntimeCheck FAILED at <paso>: <causa>` y código 1. Para un paquete ya instalado:

    ./gradlew :ui:packagedRuntimeCheck -PvsimInstalledApplication=/opt/vsim2     # deb y rpm

En Windows y macOS se da la carpeta donde quedó instalado (la que tiene `runtime/` y `app/`, o el `.app`). También
se puede correr directamente, sin Gradle (Linux):

    /opt/vsim2/lib/runtime/bin/java -cp /opt/vsim2/lib/app/vsim2-<v>.jar cl.almejo.vsim.ui.app.PackagedRuntimeCheck

### Si falta un módulo

La lista de módulos está en `packaging.gradle.kts` (`runtimeModules`), con cómo se obtuvo. Si una dependencia cambia,
se vuelve a calcular con

    ./gradlew :ui:executableJar
    jdeps --multi-release 21 --ignore-missing-deps --print-module-deps ui/build/libs/vsim2-<v>.jar

y se corre `./gradlew :ui:packagedRuntimeCheck`.

## Publicar una versión

El flujo `.github/workflows/release.yml` arma todos los instaladores y deja una release **en borrador** (nunca la
publica):

1. Sube la versión en `build.gradle.kts` y escribe el «Resumen» de la versión en `CHANGELOG.md` (el primer párrafo bajo
   `## Versión X.Y — fecha`, de 2 a 4 líneas; lo lee `packaging/release-notes.sh`).
2. Crea y sube la etiqueta: `git tag vX.Y && git push origin vX.Y`. La etiqueta debe ser `v` + la versión de
   `build.gradle.kts` (con un sufijo, `vX.Y-rc1`, la release queda como pre-release). Si no coincide, el flujo falla
   antes de construir.
3. El flujo corre las pruebas y arma el jar, `msi` y zip portátil (Windows), `deb`, `rpm`, AppImage, Flatpak y Snap
   (Linux); cada paquete se instala (o se desempaqueta, el rpm) en su máquina y corre `PackagedRuntimeCheck` con su propio
   Java. macOS (dmg `arm64` y `x64`) se construye solo desde la versión 1.0 o en una ejecución manual.
4. Si todo pasó, el último trabajo escribe `SHA256SUMS` y crea la release en borrador con las notas de
   `release-notes.sh` y cada archivo adjunto. Revisa el borrador en GitHub y publícalo a mano.

Ejecutar a mano (Actions ▸ Release ▸ Run workflow) construye todo, macOS incluido, como artefactos y no crea release.
Los scripts se pueden correr en local: `packaging/linux/build-appimage.sh <carpeta-imagen> <versión> <salida>` (baja
appimagetool 1.9.1 si no está), `packaging/linux/build-flatpak.sh` (pide `flatpak-builder` y Flathub),
`packaging/snap/prepare-snap.sh` (deja la carpeta que construye snapcraft) y `packaging/release-notes.sh <versión>`.
La imagen de jpackage de cada formato (`-PvsimPackageFormat=appimage|flatpak|snap`) se arma en la misma carpeta, así
que se construyen uno tras otro.
