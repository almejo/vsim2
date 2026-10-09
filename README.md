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
- Selección: click selecciona, arrastrar el cuerpo mueve, Supr borra, R / Mayús+R rota, doble click configura, botón derecho abre el menú.
- Click sobre un interruptor lo cambia mientras simula.
- Ctrl+rueda o Ctrl + / Ctrl − (también en el teclado numérico): zoom; rueda / Mayús+rueda: desplazar; botón del medio o Espacio+arrastrar: mover la vista.
- Los cortocircuitos se ven en rojo, con un halo en cada pin que maneja la red.

## Demo de consola (M1)

    ./gradlew :core:run --args="../examples/vsim1/4-bit-adder.json"

Licencia: GPL (ver `license.txt`).
