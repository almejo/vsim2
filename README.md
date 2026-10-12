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
- Cables: arrastrar desde un pin (se marca con un anillo al pasar el mouse) o desde un cable dibuja un cable en "L". Para mover un cable, primero click para seleccionarlo y luego arrastrarlo. Al pasar el mouse sobre cualquier pin se ve su nombre, su bit si el componente lee sus pines como un número (display de 7 segmentos: «in0 · bit 0 (el de la derecha)»), su ancho si es de más de un bit y, mientras simula, su valor («in1 = Z (sin conectar)»). Una entrada sin nada conectado (ni cable ni otro pin) se dibuja con un anillo hueco, porque queda en Z; el anillo desaparece al conectarla.
- Las herramientas Cable y Mover de la barra de vista siguen existiendo como modos opcionales.
- Selección: click selecciona; Mayús+click o Ctrl+click agrega o quita; arrastrar en vacío dibuja un rectángulo que selecciona lo que queda completamente adentro (con Mayús o Ctrl se suma a la selección); Ctrl+A selecciona todo y Esc la limpia. Arrastrar cualquier elemento seleccionado mueve todo el grupo (también tomándolo por un pin de un componente seleccionado). Supr borra, R / Mayús+R rota el grupo, doble click configura, botón derecho abre el menú.
- Portapapeles: Ctrl+X / Ctrl+C / Ctrl+V (⌘ en macOS). Lo pegado flota semitransparente bajo el mouse (con el mouse fuera del canvas, junto al original si viene de este mismo circuito y ese lugar está a la vista; si no, en el centro de la vista): un click lo deja ahí; si en cambio se arrastra, queda flotando donde se suelta y se fija con un click fuera de él o con Enter. Esc cancela. Ctrl+D duplica sin tocar el portapapeles.
- Etiquetas: F2, "Etiqueta…" del menú contextual o la pestaña Propiedades editan la etiqueta del componente (vacía la quita). "Agregar texto aquí" en el menú del canvas vacío crea un texto libre, que se mueve y borra como cualquier elemento y se edita con F2 o doble click. La pestaña Propiedades muestra y edita el elemento seleccionado. En un cable, "Borrar tramo" quita solo el trozo entre los puntos de conexión vecinos.
- Ventanas: Ventana → "Nueva vista" abre otra vista del mismo documento; el menú Ventana lista todas. Soltar un archivo .vsim o .json sobre una ventana lo abre (un `.vsimcomponent.json` se agrega a la librería); abrir un archivo ya abierto enfoca su ventana.
- Click sobre un interruptor lo cambia mientras simula.
- Ctrl+rueda o Ctrl + / Ctrl − (también en el teclado numérico): zoom; rueda / Mayús+rueda: desplazar; botón del medio o Espacio+arrastrar: mover la vista. Un clic en «zoom 125 %» de la barra de estado vuelve a 100 % sin mover el centro.
- Los cortocircuitos se ven en rojo pulsante, con un halo en cada pin que maneja la red; al pasar el mouse sobre el cable se listan los drivers. Click en "⚠ N cortocircuitos" o F8 / Mayús+F8 salta al siguiente/anterior; la pestaña Problemas los lista (doble click navega). Simulación → "Pausar al detectar cortocircuito" detiene la simulación cuando aparece uno nuevo. Un cortocircuito dentro de un componente propio resalta su instancia y F8 lleva a ella; si une redes del circuito a través de una instancia, solo se resalta (y muestra los drivers al pasar el mouse) la red principal.
- Componentes propios: marca las entradas y salidas de un circuito con «Pin de entrada» y «Pin de salida» (su etiqueta es el nombre del pin), selecciónalo y usa Editar → «Crear componente desde selección…» (también en el menú contextual). El diálogo pide un nombre único y deja ordenar los pines; por defecto la selección se reemplaza por el componente (un solo paso de deshacer): los cables y pines de afuera que llegaban a un «Pin de entrada» o «Pin de salida» se conectan al pin del componente con un tramo de cable nuevo; los que tocaban otras partes de la selección quedan sueltos. Si algún pin no se puede conectar sin tocar otra red, queda sin conectar y un mensaje lo nombra; si un pin del componente quedaría sobre otro cable o pin, la selección no se reemplaza (el componente igual queda en la librería). El componente se guarda en la librería (`~/.local/share/vsim2/components` en Linux, `~/Library/Application Support/VSim2/components` en macOS, `%APPDATA%\VSim2\components` en Windows) y aparece en «Mis componentes» de todas las ventanas; los archivos agregados o borrados en esa carpeta desde fuera también se reflejan; si la carpeta se borra, la pestaña Problemas lo dice y VSim vuelve a seguirla en cuanto existe otra vez.
- En la paleta, clic derecho sobre «Mis componentes»: «Renombrar…», «Exportar…», «Mostrar en carpeta», «Eliminar de la librería». Archivo → «Importar componente…» (o soltar archivos `.vsimcomponent.json` sobre la paleta) los agrega a la librería. Un circuito guarda una copia de cada componente que usa: si la librería no lo tiene aparece en «Componentes de este circuito» con «Agregar a mi librería»; si la librería tiene otra versión, la instancia muestra un punto y su menú ofrece «Actualizar desde la librería».
- Clic derecho sobre una instancia → «Desarmar» la reemplaza por sus partes; tras cambiarlas, «Actualizar componente…» reemplaza la definición y todas sus instancias cambian (un paso de deshacer). Los cortocircuitos dentro de un componente se describen con su ruta («U1 › U3 (NOT)») y F8 lleva a la instancia; los archivos de la librería que no se pudieron leer aparecen en la pestaña Problemas.
- Arrastrar: al mover la selección o el rectángulo de selección cerca del borde del canvas, la vista se desplaza sola. Al llevar la selección sobre otra ventana empieza un arrastre entre ventanas: soltarla en otra vista del mismo circuito la mueve (un solo paso de deshacer); en otro circuito se pega una copia flotando donde se suelta. Con Ctrl al soltar se copia (entre ventanas en macOS, Opción), también dentro del mismo canvas. Ctrl se presiona después de empezar a arrastrar (Ctrl al hacer click alterna la selección). Si el arrastre vuelve al canvas de origen, se ve dónde quedará.
- Árbol de componentes: la pestaña Componentes del inspector lista los componentes del circuito ("etiqueta · tipo"); "Agrupar por tipo" los agrupa. Los componentes propios se despliegan en sus partes, y sus pines y LEDs internos muestran el valor actual mientras simula. Elegir una fila selecciona y centra el componente (o la instancia que lo contiene).
- Dibujos: los textos dentro de los componentes (interruptor, flip-flop, pines) se leen derechos en cualquier rotación. El display de 7 segmentos muestra 0–F con segmentos biselados y un guion en cada dígito que tenga un bit en Z o en conflicto. Al pasar el mouse sobre un display que muestra el guion dice «Alguna entrada no está definida (Z)». El diagrama de tiempo dibuja sus propias trazas con el nombre de cada entrada, una línea tenue cada 10 muestras, Z como línea punteada al medio y conflicto en rojo; muestrea cada `sample-interval` unidades de tiempo, así que pulsos más cortos que eso no se ven.
- Los puntos de unión aparecen solo donde un cable se ramifica (no donde dos cables siguen en línea recta). Dos cables que se cruzan sin unirse dejan un pequeño corte en el vertical, que «pasa por debajo» del horizontal; una T o un punto de unión nunca tienen corte.
- Íconos nuevos en menús, barra de herramientas y menú contextual (se adaptan al tema oscuro); la barra muestra solo íconos y el nombre aparece al pasar el mouse. La ventana tiene ícono propio. El tooltip de cada botón termina con su atajo actual, por ejemplo «Deshacer agregar (Ctrl+Z)», y cambia si se cambia el atajo. «Avanzar 10 t» (antes «Paso») avanza la simulación 10 unidades de tiempo; el selector de velocidad tiene un ícono y dice «Velocidad ×10» (×1 = 1 unidad de tiempo por milisegundo). En la barra de estado, «t = …» es el tiempo de simulación y un clic en «▶ corriendo» o «⏸ en pausa» pausa o inicia. «Retardo», «Tiempo en alto», «Tiempo en bajo» e «Intervalo de muestreo» dicen «(t)»: se miden en unidades de tiempo de simulación.
- Al pasar el mouse sobre «Pin de entrada» y «Pin de salida» en la paleta, un texto explica que funcionan como interruptor y LED mientras pruebas y que se vuelven los pines del componente; el diálogo de crear o actualizar un componente lo recuerda.

## Buses (M5)

- Un cable es un bus cuando une pines de más de un bit: se dibuja más grueso y muestra su valor en hexadecimal (`0x3F`; `Z` en un dígito con todos sus bits en alta impedancia, `?` si solo algunos lo están). Al pasar el mouse se ve «Bus de 8 bits = 0x3F»; el árbol de componentes también muestra los buses en hexadecimal.
- «Separador de bus» y «Unión de bus» (en Básicas) pasan de un bus a sus bits y al revés, sin retardo. «Ancho (bits)» (2 a 32, 8 por omisión) se cambia con doble click y los pines aparecen o desaparecen al instante; el bit 0 es el de arriba. Los pines de más de un bit se marcan con un cuadrado.
- «Pin de entrada» y «Pin de salida» tienen «Ancho (bits)» (1 a 64): un componente creado con ellos tiene pines de ese ancho (en la caja se leen como `a[8]`). Al probar el circuito, un pin de entrada de varios bits pone todos sus bits en 0, 1 o Z y un click alterna entre todo 0 y todo 1.
- Unir pines de anchos distintos es un problema: el cable se ve rojo y punteado, junto a cada pin aparece su ancho, la barra de estado cuenta «⚠ 1 ancho incompatible» (junto a los cortocircuitos), F8 lleva a él y al pasar el mouse se listan los pines. Esa red queda en Z.
- Los circuitos y componentes guardados antes de M5 se abren igual: todo es de 1 bit.

## Formas de onda (M6)

- Ver ▸ Formas de onda muestra, bajo el circuito, un panel con varias señales sobre el mismo eje de tiempo de simulación, como un analizador lógico. Se abre solo al agregar un canal o al abrir un circuito que tiene canales guardados; su altura se ajusta con el divisor. Desde Archivo ▸ Preferencias… ▸ Atajos de teclado se le puede asignar una tecla.
- Clic derecho en un cable: «Ver en formas de onda»; en un componente: «Ver pines en formas de onda» (entradas primero); con varios elementos seleccionados: «Ver en formas de onda». Hay un canal por red, así que seleccionar una cadena de compuertas da una fila por red, en orden: se ve cómo avanza la señal. Dos redes que un componente une por dentro (un pin conectado directo a otro) cuentan como una sola. Como máximo hay 64 canales. En el lienzo, cada pin o cable que está en formas de onda lleva una pequeña insignia junto al pin, del lado de afuera del componente (o al medio del cable), sin tapar el pin ni el componente: al pasar el ratón dice «En formas de onda: U3.q» y un clic (con la herramienta Selección) abre el panel, elige esa fila y la hace parpadear.
- Una señal de 1 bit se dibuja alta o baja, Z como línea punteada al medio y un cortocircuito en rojo; un bus, como segmentos con su valor en hexadecimal (`0x3F`). Si se borra el componente de un canal, la fila dice «(sin conexión)» y la traza se raya; deshacer lo reconecta.
- Rueda = mover el tiempo, Ctrl+rueda = acercar o alejar alrededor del puntero, arrastrar la regla = desplazar. «Seguir» mantiene el borde derecho en el tiempo actual (se apaga al mover a mano); mientras sigue, Ctrl+rueda acerca o aleja alrededor del borde derecho, no del puntero. «Ver retardos» muestra 20 píxeles por unidad, para contar el retardo de 1 unidad de cada compuerta; «Ver todo», «Acercar», «Alejar», «Limpiar trazas» y «Ocultar». La barra del panel muestra solo íconos (cada uno dice qué hace al pasar el ratón; «Seguir» se ve presionado mientras está activo) y a la derecha la escala, por ejemplo «1 div = 50 t»: el tiempo de simulación entre dos marcas de la regla.
- Un clic en la traza pone el cursor (se pega al flanco más cercano); la columna de nombres lee el valor de cada canal en el cursor y al pasar el ratón se ve «t = … · Δt = …». Con la traza enfocada, ← y → saltan al flanco anterior o siguiente de la fila elegida.
- Clic derecho en un nombre: «Renombrar…», «Subir», «Bajar», «Quitar», «Quitar todos» y «Mostrar en el circuito» (también con doble clic). Un canal que nunca se renombró muestra un nombre automático que sigue a la etiqueta de su componente (poner la etiqueta «A» convierte `c3.q` en `A.q`); «Renombrar…» fija un nombre y renombrar a vacío vuelve al automático. Cada cambio de canales se deshace en un paso y se guarda con el circuito; las trazas no se guardan. Cada canal recuerda sus últimos 65 536 cambios: lo anterior se descarta y se ve rayado.

## Sesión y recuperación (M6)

- Cada minuto VSim guarda en silencio una copia de cada circuito con cambios sin guardar, en la carpeta `recovery` dentro de la carpeta de datos de VSim (`~/.local/share/vsim2`, `~/Library/Application Support/VSim2` o `%APPDATA%\VSim2`). Las copias se borran al guardar, al cerrar sin guardar, al deshacer hasta lo guardado y al salir normalmente: solo quedan si VSim se cae o se mata.
- Si la vez anterior VSim no se cerró bien, al iniciar aparece «VSim 2 no se cerró correctamente…» con una fila por circuito («contador — guardado a las 14:32»; «⚠ el archivo cambió después» si el archivo es más nuevo que la copia). «Recuperar» abre los marcados como modificados y con su ruta original (Guardar sobrescribe el original; uno sin nombre pide dónde guardarlo); «Descartar» borra los marcados; «Decidir después» los guarda para el próximo inicio. Una copia dañada dice «no se puede leer» y solo se puede descartar. Las copias de más de 30 días se borran solas.
- Archivo ▸ Recientes (después de «Abrir…») lista los últimos 10 circuitos abiertos o guardados como «nombre — carpeta» (el tooltip muestra la ruta completa), y «Limpiar lista». Un archivo que ya no existe se quita con un aviso. Al abrir un circuito —desde Recientes, Abrir, arrastrándolo o desde la línea de comandos— su ventana vuelve al tamaño, posición, maximizado, zoom y centro que tenía.
- Continuar donde quedaste: al salir (Salir, Cmd+Q o cerrando la última ventana) VSim recuerda los circuitos con archivo que estaban abiertos, con cada una de sus vistas, y los vuelve a abrir al iniciar sin archivos en la línea de comandos; los que ya no existen se avisan una vez. Se desactiva en Archivo ▸ Preferencias… ▸ General ▸ «Al iniciar, abrir los circuitos de la última sesión». Los circuitos sin nombre no se recuerdan (vuelven solo por la recuperación).
- Dos VSim abiertos a la vez no se confunden: cada uno guarda sus copias en su propia carpeta y nunca ofrece recuperar las de otro que sigue abierto.

## Atajos y vistas (M6)

- Ventana ▸ «Nueva vista» abre otra ventana del mismo circuito, 30 px más abajo y a la derecha, con el mismo zoom y el mismo centro. Con más de una vista, los títulos y el menú Ventana dicen «contador (1)», «contador (2)»… y se renumeran al cerrar una. Deshacer, la simulación y el archivo son compartidos; la selección y lo que muestra cada vista, no. Arrastrar entre vistas mueve; con Ctrl, copia.
- Archivo ▸ Preferencias… ▸ «Atajos de teclado» lista las acciones por menú con su atajo y un buscador. «Cambiar…» (o doble clic) espera la combinación (Esc cancela); si otra acción ya la usa, aparece «Ctrl+S ya lo usa «Guardar».» con «Reasignar» y «Cancelar». «Quitar atajo», «Restaurar» y «Restaurar todos» hacen lo que dicen. Esc, Enter, Espacio, Tab, Mayús+Tab, Retroceso y los atajos fijos del zoom (Ctrl con +, teclado numérico) no se pueden asignar. Los cambios se ven al instante en todas las ventanas y se recuerdan al reiniciar.
- Además de los atajos de siempre se puede dar uno a Selección, Cable, Mover vista, Centrar circuito, Configurar…, Iniciar, Pausar, Avanzar 10 t, Preferencias… y Nueva vista. Las teclas del lienzo (Supr, R, Mayús+R, F2, F8, Mayús+F8) también se cambian ahí; Retroceso sigue borrando.
- El cursor dice qué hará un clic: flecha sobre el lienzo vacío o algo sin seleccionar, cruz sobre un pin o un cable (empieza un cable), con la herramienta Cable y al colocar un componente, mover sobre lo seleccionado y al arrastrarlo, mano sobre el cuerpo de un interruptor o de un pin de entrada, mover mientras se desplaza la vista (Espacio, botón del medio o «Mover vista»).

## Ejemplos (M6.1)

- Archivo ▸ Ejemplos (después de «Recientes») abre doce circuitos clásicos armados solo con compuertas, flip-flops, interruptores, LED, relojes, buses y displays: Sumador completo, Sumador de 4 bits, Multiplexor 4:1, Decodificador 2 a 4, Paridad de 3 bits, Multiplicador 2×2, Registro de 4 bits, Registro de desplazamiento, Contador de 4 bits, Contador Johnson, Bus de 8 bits y ALU de 4 bits. Al pasar el mouse sobre cada uno se ve qué enseña.
- Cada ejemplo se abre en una ventana nueva como una copia sin nombre y sin modificar, llamada como el ejemplo: Guardar pide dónde guardarla y el ejemplo incluido nunca cambia. No se agrega a Recientes; abrirlo dos veces da dos copias independientes y, si la ventana desde la que se pide está vacía y sin tocar, la reemplaza.
- Cada ejemplo se explica solo: un título arriba, una línea «Prueba: …» abajo que dice qué probar, y un nombre corto junto a cada entrada, salida y parte (A, B, Cin, S, Cout, «selector», «acarreo», «banderas»…). Ningún texto tapa un cable ni un componente.
- «Sumador de 4 bits» usa cuatro instancias del componente «Sumador completo», que viene dentro del archivo: aparece en «Componentes de este circuito» con «Agregar a mi librería» y «Desarmar» muestra su interior. La ALU usa cuatro «Bit de ALU» (cada uno un «Sumador completo», una AND, una OR, una XOR y un multiplexor 4:1); S1 S0 eligen la operación: 00 suma, 01 AND, 10 OR, 11 XOR.
- Los flip-flops D empiezan en 0 (como al encender un circuito real): «Contador de 4 bits» es el contador en cascada clásico (cada flip-flop invierte su salida y le da el reloj al siguiente) y cuenta desde 0; el «Contador Johnson» parte en 0000; los LED de «Registro de 4 bits» se ven en 0 hasta la primera carga. Una entrada sin conectar sigue en Z.
- Los archivos de los ejemplos se generan con `CombinationalExamples`, `SequentialExamples` y `BusAndAluExamples` (pruebas de `core`, listados por `BundledCircuits`): después de cambiar uno, `VSIM_WRITE_BUNDLED=1 ./gradlew :core:test --tests '*BundledCircuitsTest.writeTheResources'` los reescribe; las pruebas comprueban que coinciden con lo generado, que ningún cable pasa sobre un pin, que ningún texto tapa nada y que cada ejemplo funciona.

## Paso a paso (M6.1)

## Tabla de verdad (M6.1)

## Preferencias y distribución

- Archivo → Preferencias… (en macOS también VSim 2 → Ajustes…): tema Claro, Oscuro o Seguir sistema; esquema de color del circuito (Según el tema, VSim clásico, Claro, Oscuro y los otros esquemas del VSim original: Pastel, CSim y Twilight); idioma (Sistema, Español, English; se aplica al reiniciar); directorio de la librería de componentes (Elegir… / Restaurar predeterminado). El tema y el esquema cambian al instante en todas las ventanas.
- "Seguir sistema" consulta el modo oscuro del sistema al iniciar y al elegir esa opción (macOS, Windows y GNOME); si no puede saberlo, usa el tema claro.
- `-Dvsim.language=en` (o `./gradlew :ui:run -PvsimLanguage=en`) manda sobre el idioma guardado.
- Las preferencias se guardan con `java.util.prefs` (nodo `cl/almejo/vsim2`).

Jar ejecutable, con todas las dependencias adentro:

    ./gradlew :ui:executableJar
    java -jar ui/build/libs/vsim2-2.0.0-SNAPSHOT.jar

`./gradlew check` construye el jar y verifica su manifiesto y contenido (`:ui:executableJarTest`).

## Demo de consola (M1)

    ./gradlew :core:run --args="../examples/vsim1/4-bit-adder.json"

Licencia: GPL (ver `license.txt`).
