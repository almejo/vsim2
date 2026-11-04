# Cómo instalar y probar VSim 2

Descarga el archivo de tu sistema desde la versión más nueva en [Releases](https://github.com/almejo/vsim2/releases).
`<v>` es el número de versión (por ejemplo `0.6`). Cada versión trae un archivo `SHA256SUMS` por si quieres comprobar
la descarga. Los instaladores traen su propio Java: no necesitas instalar nada más. Solo el `.jar` pide Java 21.

Estas versiones todavía no están firmadas, así que Windows y macOS muestran un aviso la primera vez. Abajo está
cómo seguir.

- [Windows](#windows)
- [macOS](#macos)
- [Linux](#linux)
- [Cualquier sistema: el jar](#cualquier-sistema-el-jar)
- [Dónde se guardan tus preferencias](#dónde-se-guardan-tus-preferencias)
- [Reportar un problema](#reportar-un-problema)

## Windows

Dos opciones:

- **Instalador:** `VSim2-<v>-windows-x64.msi`. Se instala solo para tu usuario, sin pedir permisos de
  administrador, y deja VSim 2 en el menú Inicio y en el escritorio. Para actualizar, instala el `.msi` de la versión
  nueva encima. Para quitarlo, usa «Aplicaciones instaladas».
- **Portable:** `VSim2-<v>-windows-x64-portable.zip`. Descomprímelo donde quieras y abre `VSim2.exe`. No instala
  nada.

**Aviso de SmartScreen.** Al abrir el instalador (o el `.exe` del zip) Windows puede decir «Windows protegió su PC».
Haz clic en **Más información** y luego en **Ejecutar de todas formas**. Pasa porque el archivo aún no está firmado.

## macOS

Antes de VSim 1.0 no hay un `.dmg` oficial en Releases. Tienes dos caminos:

- Pídele el `.dmg` al autor (`VSim2-<v>-macos-arm64.dmg` para Mac con chip Apple, `VSim2-<v>-macos-x64.dmg` para
  Mac con Intel).
- Usa el [jar](#cualquier-sistema-el-jar) con Java 21.

Con el `.dmg`: ábrelo y arrastra VSim 2 a Aplicaciones. Como no está firmado, macOS lo bloquea la primera vez.
Desde macOS Sequoia (15) el clic derecho ▸ Abrir ya no sirve. Hazlo así:

1. Intenta abrir VSim 2; macOS dirá que no pudo verificarlo. Cierra el aviso.
2. Abre **Ajustes del Sistema ▸ Privacidad y seguridad**, baja hasta el aviso de VSim 2 y pulsa **Abrir igualmente**.
3. Confirma con **Abrir**. Queda como excepción y no se vuelve a preguntar.

Si eso no funciona, en una terminal:

```
xattr -dr com.apple.quarantine /Applications/VSim2.app
```

## Linux

Elige un formato. Todos traen su propio Java.

### Debian y Ubuntu (`.deb`)

```
sudo apt install ./VSim2-<v>-linux-amd64.deb
```

### Fedora y derivadas (`.rpm`)

```
sudo dnf install ./VSim2-<v>-linux-x86_64.rpm
```

### AppImage

```
chmod +x VSim2-<v>-x86_64.AppImage
./VSim2-<v>-x86_64.AppImage
```

Los AppImage necesitan FUSE 2. En Ubuntu 24.04: `sudo apt install libfuse2t64`. Si no quieres instalarlo:

```
./VSim2-<v>-x86_64.AppImage --appimage-extract-and-run
```

El AppImage no se agrega solo al menú de aplicaciones; herramientas como Gear Lever pueden hacerlo, o puedes
ejecutarlo a mano como arriba.

### Flatpak (`.flatpak`)

Necesita tener Flathub configurado, porque el archivo usa el runtime de Flathub:

```
flatpak install --user ./VSim2-<v>-linux-x86_64.flatpak
```

### Snap (`.snap`)

Es una instalación local, por eso lleva `--dangerous` (el paquete no viene de la tienda de Snap):

```
sudo snap install --dangerous ./VSim2-<v>-linux-amd64.snap
```

Para abrir o guardar circuitos en discos USB u otros medios extraíbles:

```
sudo snap connect vsim2:removable-media
```

## Cualquier sistema: el jar

1. Instala OpenJDK 21 (en Linux, el paquete `openjdk-21-jre` de tu distribución; en Windows y macOS, el instalador de
   OpenJDK 21 que prefieras).
2. Descarga `vsim2-<v>.jar`.
3. Ábrelo con doble clic, o en una terminal: `java -jar vsim2-<v>.jar`.

## Dónde se guardan tus preferencias

VSim 2 usa las preferencias de Java, así que viven en el lugar habitual de cada sistema. Tus circuitos son archivos
`.vsim` que guardas tú donde quieras: nunca se guardan ahí.

- Windows: en el registro, bajo `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\cl\almejo\vsim2`.
- macOS: en `~/Library/Preferences/com.apple.java.util.prefs.plist`.
- Linux: en `~/.java/.userPrefs/cl/almejo/vsim2`.

En Flatpak y Snap el programa corre en un entorno aislado, así que esos archivos quedan dentro del espacio de
la aplicación y no se mezclan con los de otros formatos. Quitar una preferencia a mano no hace falta: se cambian desde
Archivo ▸ Preferencias.

## Reportar un problema

- En VSim 2: **Ayuda ▸ Reportar un problema…** abre una página de GitHub con la versión, el sistema y el formato
  ya escritos. Necesitas una cuenta de GitHub.
- Sin cuenta: abre **Ayuda ▸ Acerca de VSim 2…**, copia los datos de la instalación y envíalos al autor junto con lo
  que pasó y cómo repetirlo.

No pegues datos personales en el reporte: revisa que el circuito o las capturas no los incluyan.
