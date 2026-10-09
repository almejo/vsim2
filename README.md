# VSim 2

Simulador de circuitos digitales. Tercera versión, después de
[JCSim](https://github.com/almejo/jcsim) (memoria de título, `memoria.doc`) y VSim.

Diseño: `docs/superpowers/specs/2026-10-08-vsim2-design.md`.

## Construir y probar

    ./gradlew test

## Editor (M2)

    ./gradlew :ui:run
    ./gradlew :ui:run --args="../examples/vsim1/4-bit-adder.json"
    ./gradlew :ui:run -PvsimLanguage=en

- Paleta: click en un componente y luego click en el canvas para colocarlo (vuelve solo a Selección; con Mayús se siguen colocando). También se puede arrastrar desde la paleta al canvas. R / Mayús+R rota antes de colocar.
- Cables: arrastrar desde un pin (se marca con un anillo al pasar el mouse) o desde un cable dibuja un cable en "L". Para mover un cable, primero click para seleccionarlo y luego arrastrarlo.
- Las herramientas Cable y Mover de la barra de vista siguen existiendo como modos opcionales.
- Selección: click selecciona; Mayús+click o Ctrl+click agrega o quita; arrastrar en vacío dibuja un rectángulo que selecciona lo que queda completamente adentro (con Mayús o Ctrl se suma a la selección); Ctrl+A selecciona todo y Esc la limpia. Arrastrar cualquier elemento seleccionado mueve todo el grupo (también tomándolo por un pin de un componente seleccionado). Supr borra, R / Mayús+R rota el grupo, doble click configura, botón derecho abre el menú.
- Portapapeles: Ctrl+X / Ctrl+C / Ctrl+V (⌘ en macOS). Lo pegado flota semitransparente bajo el mouse (o junto al original si el mouse está fuera del canvas): un click lo deja ahí; si en cambio se arrastra, queda flotando donde se suelta y se fija con un click fuera de él o con Enter. Esc cancela. Ctrl+D duplica sin tocar el portapapeles.
- Etiquetas: F2, "Etiqueta…" del menú contextual o la pestaña Propiedades editan la etiqueta del componente (vacía la quita). "Agregar texto aquí" en el menú del canvas vacío crea un texto libre, que se mueve y borra como cualquier elemento y se edita con F2 o doble click. La pestaña Propiedades muestra y edita el elemento seleccionado. En un cable, "Borrar tramo" quita solo el trozo entre los puntos de conexión vecinos.
- Ventanas: Ventana → "Nueva ventana de este circuito" abre otra vista del mismo documento; el menú Ventana lista todas. Arrastrar la selección fuera del canvas la lleva a otra ventana (se pega flotando donde se suelta). Soltar un archivo .vsim o .json sobre una ventana lo abre; abrir un archivo ya abierto enfoca su ventana.
- Click sobre un interruptor lo cambia mientras simula.
- "Crear componente desde selección…" (menú Editar o menú contextual) reemplaza la selección por el componente: los cables y pines de afuera que llegaban a un "Pin de entrada" o "Pin de salida" se conectan al pin del componente con un tramo de cable nuevo; los que tocaban otras partes de la selección quedan sueltos. Si algún pin no se puede conectar sin tocar otra red, queda sin conectar y un mensaje lo nombra; si un pin del componente quedaría sobre otro cable o pin, la selección no se reemplaza (el componente igual queda en la librería).
- Ctrl+rueda o Ctrl + / Ctrl − (también en el teclado numérico): zoom; rueda / Mayús+rueda: desplazar; botón del medio o Espacio+arrastrar: mover la vista.
- Los cortocircuitos se ven en rojo pulsante, con un halo en cada pin que maneja la red; al pasar el mouse sobre el cable se listan los drivers. Click en "⚠ N cortocircuitos" o F8 / Mayús+F8 salta al siguiente/anterior; la pestaña Problemas los lista (doble click navega). Simulación → "Pausar al detectar cortocircuito" detiene la simulación cuando aparece uno nuevo. Un cortocircuito dentro de un componente propio resalta su instancia y F8 lleva a ella; si une redes del circuito a través de una instancia, solo se resalta (y muestra los drivers al pasar el mouse) la red principal.
- Componentes propios: marca las entradas y salidas de un circuito con «Pin de entrada» y «Pin de salida» (su etiqueta es el nombre del pin), selecciónalo y usa Editar → «Crear componente desde selección…» (también en el menú contextual). El diálogo pide un nombre único y deja ordenar los pines; por defecto la selección se reemplaza por el componente (un solo paso de deshacer). El componente se guarda en la librería (`~/.local/share/vsim2/components` en Linux, `~/Library/Application Support/VSim2/components` en macOS, `%APPDATA%\VSim2\components` en Windows) y aparece en «Mis componentes» de todas las ventanas; los archivos agregados o borrados en esa carpeta desde fuera también se reflejan.
- En la paleta, clic derecho sobre «Mis componentes»: Renombrar, Exportar…, Mostrar en carpeta, Eliminar de la librería. Archivo → «Importar componente…» (o soltar archivos `.vsimcomponent.json` sobre la paleta) los agrega a la librería. Un circuito guarda una copia de cada componente que usa: si la librería no lo tiene aparece en «Componentes de este circuito» con «Agregar a mi librería»; si la librería tiene otra versión, la instancia muestra un punto y su menú ofrece «Actualizar desde la librería».
- Clic derecho sobre una instancia → «Desarmar» la reemplaza por sus partes; tras cambiarlas, «Actualizar componente…» reemplaza la definición y todas sus instancias cambian (un paso de deshacer). Los cortocircuitos dentro de un componente se describen con su ruta («U1 › U3 (NOT)») y F8 lleva a la instancia; los archivos de la librería que no se pudieron leer aparecen en la pestaña Problemas.

## Demo de consola (M1)

    ./gradlew :core:run --args="../examples/vsim1/4-bit-adder.json"

Licencia: GPL (ver `license.txt`).
