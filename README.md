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
- Ventanas: Ventana → "Nueva ventana de este circuito" abre otra vista del mismo documento; el menú Ventana lista todas. Soltar un archivo .vsim o .json sobre una ventana lo abre; abrir un archivo ya abierto enfoca su ventana.
- Click sobre un interruptor lo cambia mientras simula.
- Ctrl+rueda o Ctrl + / Ctrl − (también en el teclado numérico): zoom; rueda / Mayús+rueda: desplazar; botón del medio o Espacio+arrastrar: mover la vista.
- Los cortocircuitos se ven en rojo pulsante, con un halo en cada pin que maneja la red; al pasar el mouse sobre el cable se listan los drivers. Click en "⚠ N cortocircuitos" o F8 / Mayús+F8 salta al siguiente/anterior; la pestaña Problemas los lista (doble click navega). Simulación → "Pausar al detectar cortocircuito" detiene la simulación cuando aparece uno nuevo.
- Arrastrar: al mover la selección o el rectángulo de selección cerca del borde del canvas, la vista se desplaza sola. Al llevar la selección sobre otra ventana empieza un arrastre entre ventanas: soltarla en otra vista del mismo circuito la mueve (un solo paso de deshacer); en otro circuito se pega una copia flotando donde se suelta. Con Ctrl al soltar se copia (entre ventanas en macOS, Opción), también dentro del mismo canvas. Ctrl se presiona después de empezar a arrastrar (Ctrl al hacer click alterna la selección). Si el arrastre vuelve al canvas de origen, se ve dónde quedará.
- Árbol de componentes: la pestaña Componentes del inspector lista los componentes del circuito ("etiqueta · tipo"); "Agrupar por tipo" los agrupa. Los componentes propios se despliegan en sus partes, y sus pines y LEDs internos muestran el valor actual mientras simula. Elegir una fila selecciona y centra el componente (o la instancia que lo contiene).

## Demo de consola (M1)

    ./gradlew :core:run --args="../examples/vsim1/4-bit-adder.json"

Licencia: GPL (ver `license.txt`).
