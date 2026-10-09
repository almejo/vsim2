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

- Paleta a la izquierda: elegir un componente y hacer click en el canvas para colocarlo (R / Mayús+R rota antes de colocar, Esc termina).
- Herramienta Cable: arrastrar desde un pin; el cable sale en "L".
- Selección: click selecciona, arrastrar mueve, Supr borra, R / Mayús+R rota, doble click configura, botón derecho abre el menú.
- Click sobre un interruptor lo cambia mientras simula.
- Ctrl+rueda: zoom; rueda / Mayús+rueda: desplazar; botón del medio o Espacio+arrastrar: mover la vista.
- Los cortocircuitos se ven en rojo, con un halo en cada pin que maneja la red.

## Demo de consola (M1)

    ./gradlew :core:run --args="../examples/vsim1/4-bit-adder.json"

Licencia: GPL (ver `license.txt`).
