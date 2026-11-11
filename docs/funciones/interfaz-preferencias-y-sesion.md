# Interfaz, preferencias y sesión

Parte de las [funciones de VSim 2](../funciones.md).

## Pantalla de inicio

- Al abrir VSim aparece una pantalla de inicio oscura: a la izquierda, un trozo del lienzo con un semisumador (una XOR y una AND, con A = 1 y B = 0, la suma encendida y el acarreo apagado); a la derecha, el logo y el nombre. Abajo dice «Cargando…» (en el idioma de VSim) y la versión.
- Sale centrada en la pantalla donde está el mouse, también con dos monitores.
- Se queda al menos 3 segundos: si VSim abre más rápido, espera lo que falta; si tarda más (por ejemplo, al abrir un circuito pesado), no suma demora. Se cierra sola cuando aparece la primera ventana. En pantallas con escala 2× usa una imagen del doble de resolución.

## Ventanas y pestañas

- Una ventana tiene varios circuitos en **pestañas**, debajo de la barra de herramientas. Las pestañas de un mismo circuito forman un **grupo**: arriba llevan una franja gruesa (4 px) del color del circuito y su cuerpo lleva un tinte de ese color, más fuerte en la pestaña activa (cada circuito abierto recibe uno de seis colores, en el orden en que se abrió, más claros en el tema oscuro); dos circuitos con el mismo nombre se distinguen: el archivo suma su carpeta («4-bit adder — clase1») y un circuito sin guardar se numera desde el segundo («4-bit adder 2»). La pestaña del circuito muestra un ícono de documento, su nombre, «●» si tiene cambios sin guardar y una × para cerrarla. Al pasar el mouse dice «contador · doble clic: plegar sus componentes».
- **Abrir en la misma ventana**: Archivo ▸ Abrir, Recientes, Ejemplos, Nuevo, importar un circuito de Logisim, crear un circuito desde una tabla de verdad o soltar un archivo `.vsim` sobre la ventana lo abren como un grupo nuevo en esa ventana, y su pestaña queda activa (un `.vsimcomponent.json` soltado se agrega a la librería). Abrir un archivo ya abierto lleva a su pestaña, en la ventana donde esté. Al abrir VSim, la sesión anterior vuelve con sus ventanas y pestañas (ver [Sesión y recuperación](#sesión-y-recuperación)); los archivos de la línea de comandos (o de un doble clic en un `.vsim`) se suman a ella como grupos nuevos de la ventana activa, y uno que ya estaba en la sesión solo se enfoca. Las copias recuperadas que no estaban en la sesión abren cada una en su ventana. Si la ventana tenía solo un circuito nuevo sin tocar, ese circuito se cierra al abrir otro.
- **Cambiar de pestaña**: un clic en la pestaña, Ctrl+Tab y Ctrl+Mayús+Tab (también Ctrl+Av Pág y Ctrl+Re Pág) recorren las pestañas de la ventana desde cualquier parte de ella (el lienzo, el panel derecho, el árbol, la paleta); Tab y Mayús+Tab siguen moviendo el foco de un panel a otro, y Ventana ▸ «Pestaña siguiente» / «Pestaña anterior» hacen lo mismo. El menú y la barra de herramientas de la ventana son los de la pestaña activa; el título de la ventana dice el circuito de la pestaña activa.
- **Cerrar**: la × de la pestaña, el clic con el botón del medio o Archivo ▸ «Cerrar pestaña» (Ctrl+W, también en el menú Ventana) cierran la pestaña. Cerrar la última pestaña de un circuito cierra su grupo, y si ninguna otra ventana lo muestra, pregunta una vez si guardar los cambios. Archivo ▸ «Cerrar ventana» (Ctrl+Mayús+W) cierra todas las pestañas de la ventana y pregunta una vez por cada circuito que solo estaba en ella. Una ventana sin pestañas se cierra, y al cerrar la última ventana VSim termina, como antes.
- **Más vistas del mismo circuito**: Ventana ▸ «Nueva pestaña del mismo circuito» abre otra pestaña del circuito en su grupo, con el mismo zoom y el mismo centro; Ventana ▸ «Nueva ventana con este circuito» (Ctrl+Mayús+N) abre otra ventana con el circuito, 30 px más abajo y a la derecha. Con más de una vista, las pestañas, los títulos y el menú Ventana dicen «contador (1)», «contador (2)»… y se renumeran al cerrar una. Deshacer, la simulación y el archivo son compartidos; la selección y lo que muestra cada vista, no. Arrastrar entre vistas mueve; con Ctrl, copia.
- **Los grupos se mueven enteros**: arrastrar cualquier pestaña arrastra todo su grupo (una imagen del grupo sigue al mouse, translúcida donde el sistema lo permite, y una raya muestra dónde quedará). Soltarlo en la misma fila de pestañas cambia el orden de los grupos; soltarlo en las pestañas de otra ventana lo pasa a esa ventana (si ya tenía ese circuito, se juntan en un solo grupo); soltarlo fuera de toda fila de pestañas abre una ventana nueva con el grupo, del mismo tamaño, donde lo soltaste. Ventana ▸ «Mover el grupo a una ventana nueva» hace lo mismo desde el menú (con un solo grupo en la ventana no hace nada: la ventana ya es solo de ese circuito).
- **Plegar**: un doble clic en la pestaña de un circuito pliega su grupo (también Ventana ▸ «Plegar / desplegar el grupo»): las pestañas de sus componentes se esconden con una animación corta (sin animación con «[Animaciones](#animaciones): No») y la pestaña del circuito muestra «⬚ 2», cuántas hay escondidas; otro doble clic o un clic en «⬚ 2» las vuelve a mostrar. Si la pestaña activa era una de las que se esconden, pasa a ser la del circuito. Ventana ▸ «Cerrar los componentes de este circuito» cierra las pestañas de componentes del grupo sin preguntar nada.
- **Pestaña nueva («+»)**: un botón «+» justo después de la última pestaña abre un circuito nuevo en una pestaña de la ventana, igual que Archivo ▸ Nuevo; un doble clic en la parte vacía de la fila hace lo mismo. Su tooltip dice «Nuevo circuito» con la tecla que tenga Nuevo ahora (si la cambiaste en Preferencias ▸ «Atajos de teclado», se ve la nueva). Cuando las pestañas no caben, el «+» queda fijo junto a «▾».
- **Muchas pestañas**: cuando no caben, aparecen ‹ › para desplazarlas, el «+» y «▾» («Todas las pestañas»), que lista las pestañas de todas las ventanas agrupadas por ventana; la pestaña activa siempre queda a la vista.
- **La IA en la pestaña**: la pestaña donde la IA lee, cambia o corre lleva un robot chico verde que se mece mientras trabaja y queda gris un rato al terminar; en un grupo plegado sube a la pestaña del circuito, y la lista de Ventana y «▾» lo muestran igual. Ver [La IA en la pestaña](ia-mcp.md#la-ia-en-la-pestaña).
- El menú Ventana termina con la lista de las pestañas de todas las ventanas («Ventana 1», «Ventana 2»… cuando hay más de una), con la activa marcada; elegir una la trae al frente. Los atajos nuevos (Ctrl+Tab, Ctrl+Mayús+Tab, Ctrl+W, Ctrl+Mayús+W y Ctrl+Mayús+N) se pueden cambiar en Preferencias ▸ «Atajos de teclado»; «Mover el grupo…», «Plegar / desplegar el grupo» y «Cerrar los componentes…» no traen atajo, pero se les puede dar uno.

## Dividir la vista

- Como en Excel, el lienzo de una ventana se puede dividir en 2 paneles (arriba y abajo, o a los lados) o en 4 (las dos cosas). Cada panel muestra el mismo circuito con su propio zoom y su propio centro, así se puede mirar las entradas en uno y el resultado en otro sin abrir otra ventana.
- **Dividir arrastrando**: arriba del borde derecho del lienzo y en la punta derecha del borde de abajo hay dos pestañitas (suaves; al pasar el mouse dicen «Arrastra para dividir la vista»; cada una desaparece cuando la vista ya está dividida en esa dirección). La de arriba, arrastrada hacia abajo, deja un divisor horizontal (un panel arriba y otro abajo); la de abajo, arrastrada hacia la izquierda, un divisor vertical (paneles a los lados). Mientras se arrastra se ve dónde quedará el divisor; a menos de 24 px del borde la vista previa se esconde, porque soltar ahí no divide nada, y apretar Esc mientras se arrastra tampoco.
- **Dividir desde el menú**: Ventana ▸ «Dividir ▸» tiene «Arriba y abajo», «A los lados» y «Quitar división». Desde el menú el divisor queda al medio. Ninguno trae atajo, pero se les puede dar uno en Preferencias ▸ «Atajos de teclado».
- Los paneles nuevos empiezan con el mismo zoom y el mismo centro que el panel que se dividió (como «Nueva pestaña del mismo circuito»): se ve el mismo lugar dos veces y se mueve uno de ellos. Cada panel se desplaza por su cuenta; no se mueven juntos.
- **Cambiar el tamaño**: se arrastra el divisor (una barra gris como los otros divisores de la ventana; al pasar el mouse dice «Arrastra para cambiar el tamaño; doble clic para quitar la división»). Con los dos divisores, se cruzan, y arrastrar el punto donde se cruzan mueve los dos a la vez; un doble clic ahí quita los dos.
- **Quitar la división**: arrastrar el divisor hasta un borde (a menos de 24 px) cierra los paneles de ese lado; un doble clic en el divisor cierra los de abajo o los de la derecha. Si el panel activo se cierra, pasa a serlo su vecino. Ventana ▸ Dividir ▸ «Quitar división» quita los dos divisores y deja solo el panel activo.
- **Panel activo**: es el último en el que se hizo clic o que tomó el foco. Con dos paneles o más tiene un borde fino del color de foco del tema. Ventana ▸ «Siguiente panel» pasa al siguiente (no trae atajo: F6 ya es «Avanzar un paso fijo»).
- **Qué se comparte**: la selección es una sola para toda la ventana: lo elegido en un panel se ve elegido en todos, Propiedades lo muestra y Supr, copiar o girar funcionan desde cualquiera. También la herramienta: elegir «Mover vista» en la barra hace que todos los paneles muevan la vista. Los brillos, los problemas, las marcas de la búsqueda, las sondas, las guías y lo que va a cambiar se dibujan en todos los paneles, y cada panel tiene sus propias reglas.
- **Arrastrar de un panel a otro**: lo que empiezas a arrastrar en un panel sigue en otro panel de la misma ventana, así se unen dos lugares lejanos sin arrastrar hasta allá: un cable (desde un pin o un cable; uno de corrido sigue en el otro panel también entre clics), una selección por recuadro, mover piezas (con Ctrl, copiarlas), deslizar un tramo de cable con Alt, mover una etiqueta, dibujar un marco y mover un marco por su título (si está bloqueado, con su bloque) o cambiarle el tamaño por sus manijas. Mientras el puntero está sobre el otro panel, la punta sigue al puntero ahí, con el zoom de ese panel para el imán de «Ajustar», y el primer panel deja de desplazarse solo. Lo que se arrastra (la vista previa del cable, el recuadro, las piezas fantasma, los puntos de contacto y las líneas de alineación) se ve en todos los paneles que muestran ese lugar. Al soltar se hace una sola cosa, un solo paso de deshacer, como en un panel. Volver sobre el primer panel sigue igual, y Esc lo cancela en todos. Mover la vista (Mover vista, el botón del medio o Espacio) y el recuadro de la herramienta Zoom se quedan en su panel. Arrastrar a otra ventana sigue siendo copiar o mover entre ventanas, como antes.
- **Qué actúa en el panel activo**: el control de zoom de la barra de estado (muestra el zoom del panel activo y, al pasar a otro panel, salta al zoom de ese panel sin mover ninguno), el zoom del menú Ver, la herramienta Zoom (el recuadro y el clic acercan o alejan el panel donde los haces, que pasa a ser el activo), «Ver todo el circuito», «Zoom a la selección», «Centrar circuito», los saltos de la barra de búsqueda, los saltos a cada problema (F8 y Mayús+F8), los saltos desde las formas de onda y «Nueva pestaña del mismo circuito» (que abre donde estaba el panel activo). Archivo ▸ Exportar ▸ «Imagen…» y «PDF…» salen sin bordes de panel, divisores ni pestañas.
- **Se recuerda por archivo**: al volver a abrir un circuito guardado, la ventana vuelve dividida como estaba, con el zoom y el centro de cada panel. Se guarda en las preferencias de VSim (para los últimos 20 archivos), **nunca en el .vsim**: dividir, mover un divisor o quitar la división no marca el circuito como modificado, no cuenta como paso de deshacer y el archivo no cambia. Un circuito sin guardar abre con un solo panel, y si lo recordado ya no cabe en la ventana, también.
- Como mucho hay un divisor de cada dirección, así que hay a lo más 4 paneles, como en Excel.

## Zoom y desplazamiento

- Ctrl+rueda o Ctrl + / Ctrl − (también en el teclado numérico): zoom; rueda / Mayús+rueda: desplazar (en un touchpad, dos dedos hacia arriba, abajo o los lados); botón del medio o Espacio+arrastrar: mover la vista.
- **Control de zoom** al final de la barra de estado (después de «IA: …»): «−» (Alejar), un deslizador, «+» (Acercar), el porcentaje, «100 %», «Ver todo el circuito» y «Zoom a la selección» (deshabilitado sin nada seleccionado). El deslizador va de 25 % a 400 % con 100 % justo al medio, y cada vez que el zoom se duplica avanza lo mismo (50 % y 200 % quedan a un cuarto de cada punta); cerca del medio se queda en 100 % (una marca chica lo indica). Moverlo acerca o aleja al instante alrededor del centro del panel; usarlo con el mouse no le quita el teclado al lienzo (Tab sí llega a él). Con el foco en el deslizador, ← / ↓ alejan un paso, → / ↑ acercan un paso, Re Pág duplica, Av Pág divide por dos, Inicio va a 25 % y Fin a 400 %; la rueda del mouse sobre él da un paso por muesca. En el porcentaje se escribe un número y Enter («150», «150 %», «62,5»; uno fuera de rango queda en 25 % o 400 %) o se elige uno de la lista (25, 50, 75, 100, 150, 200, 300 y 400 %; las flechas la recorren y el zoom cambia al elegir con clic o Enter): la vista llega con un [viaje suave](buscar.md#viaje-suave-de-la-vista) alrededor del centro y el foco vuelve al lienzo. Si lo escrito no es un número, el campo se marca en rojo y dice «Escribe un porcentaje entre 25 y 400»; Esc devuelve el zoom actual. «100 %» vuelve a 100 % sin mover el centro, como Ctrl+0. Ver ▸ «Escribir el zoom…» lleva el teclado al porcentaje, ya seleccionado para escribir encima (no trae atajo, pero se le puede dar uno). En una ventana angosta el deslizador se achica primero; el porcentaje y «IA: …» no se salen de la barra. El zoom no marca el circuito como modificado ni cuenta como paso de deshacer.
- Ver ▸ «Ver todo el circuito» (Mayús+1, o el botón «Ver todo» del control de zoom de la barra de estado) acerca o aleja la vista hasta que el circuito entero, con sus etiquetas y textos, ocupa nueve décimos del panel, centrado; con un circuito chico acerca hasta 400 %, y con un circuito vacío no hace nada. Ver ▸ «Zoom a la selección» (Mayús+2) hace lo mismo con lo seleccionado, así una sola compuerta se ve grande; sin nada seleccionado está deshabilitado. Las dos llegan con un [viaje suave](buscar.md#viaje-suave-de-la-vista) (o de un salto si «Animar los movimientos de la vista» está desactivado), actúan sobre el panel activo, no marcan el circuito como modificado y no cuentan como paso de deshacer. «Centrar circuito» sigue igual: centra sin cambiar tu zoom.
- **Herramienta Zoom** (su botón, una lupa, es la última de las herramientas, después de «Mover vista»; también Ver ▸ «Zoom» y la tecla Z, que se puede cambiar en Atajos de teclado): arrastra un recuadro y, al soltar, la vista viaja hasta que el recuadro llena el panel justo, tocando dos bordes opuestos (un recuadro angosto se ajusta por su lado largo); hasta 400 %, así un recuadro muy chico llega a 400 % con su centro en el medio. Mientras arrastras, el recuadro se pinta como el de la selección y al lado de su esquina inferior derecha dice el zoom al que llegará («250 %», o «400 %» si llega al tope). Si el puntero sale del panel, el recuadro queda en el borde y soltar afuera igual acerca; el panel no se desplaza solo. Un clic (moviendo menos de 5 píxeles) acerca un paso (×1,25) alrededor del punto, y Alt + clic, Mayús + clic o el botón derecho alejan un paso, al instante, como un paso de Ctrl+rueda; en 400 % o en 25 % no se mueve nada. Alt o Mayús durante un arrastre no cambian nada: un recuadro siempre acerca. El cursor es una lupa con «+», con «−» mientras tienes Alt o Mayús apretada, y una cruz mientras arrastras. Esc mientras arrastras suelta el recuadro sin hacer zoom y la herramienta sigue; Esc sin recuadro vuelve a Selección. El botón del medio, Espacio y la rueda funcionan como siempre, el clic derecho no abre el menú contextual, y nada de esto marca el circuito como modificado ni cuenta como paso de deshacer. El recuadro y su porcentaje nunca salen al exportar una imagen o un PDF ni al imprimir.

## Frase del gesto

- Mientras haces algo con el mouse, la barra de estado dice qué hace el próximo clic y qué teclas sirven en ese momento, solo las que de verdad actúan ahí. Está a la izquierda, después de los avisos y antes de los problemas (⚠), que siguen siempre visibles; si la barra es corta, la frase se corta primero y el tooltip la muestra entera.
- **Dibujando un cable** (continuo): «clic: esquina · clic en un pin o un cable: termina · doble clic: termina aquí · Espacio: cambia el codo · Retroceso: quita la última esquina · Esc: cancela». En «Por trozos», mientras arrastras la «L»: «suelta para terminar · Espacio: cambia el codo · Esc: cancela» (ahí Retroceso no hace nada, y no se dice).
- **Moviendo**: aparece con el primer arrastre, no al apretar: «suelta para dejarlas», lo que hace la tecla de invertir («Mayús: los cables no siguen», o «los cables siguen» si el modo está desactivado; con «Ninguna» esa parte no se dice), «Ctrl al soltar: copia», «Esc: cancela» y, si lo que arrastras es un solo cable, la tecla que lo desliza hacia el lado («Alt: desliza el cable», o Mayús si la tecla de invertir es Alt). La tecla de invertir es la de Preferencias ▸ «Tecla para invertir al arrastrar»: si la cambias, la frase dice la nueva.
- **Pegando** («clic o Enter: lo deja · Esc: cancela»; un componente propio arrastrado desde la paleta dice «Colocando …») y **Colocando** un componente de la paleta («clic: coloca · Mayús+clic: sigue colocando · tus teclas de Girar (por defecto R / Mayús+R): gira · Esc: cancela», con el nombre de la pieza).
- Termina el gesto (al soltar, con Esc, al elegir otra herramienta, al cambiar de pestaña o cuando la IA empieza una tarea) y la frase se va; también se va si el lienzo pierde el foco (otra ventana, un diálogo) y vuelve cuando lo recupera. Sin gesto no dice nada, tampoco al pasar sobre un pin o una pieza. Con vistas divididas habla la vista activa. El modo presentación no tiene barra de estado.

## Cuadrícula

- Ver ▸ «Cuadrícula ▸» elige qué se dibuja detrás del circuito: «Puntos» (los de siempre, la opción por defecto), «Líneas» (papel cuadriculado: una línea fina cada 2 unidades y una un poco más marcada cada 10) o «Nada». La elección vale para todas las ventanas y vistas, cambia al instante y se recuerda al reiniciar. «Cambiar el estilo de la cuadrícula», en el mismo submenú, pasa de Puntos a Líneas, a Nada y de vuelta a Puntos; no trae atajo, pero se le puede dar uno en Preferencias ▸ «Atajos de teclado».
- Ver ▸ «Cuadrícula ▸ Separación ▸» elige cada cuántas unidades va una línea: 2 (la opción por defecto), 4, 5 o 10. Cada quinta línea es la más marcada (cada 10 unidades con 2, cada 20 con 4, cada 25 con 5 y cada 50 con 10). La misma separación es la que usa «Ajustar a ▸ Líneas de la cuadrícula», aunque la cuadrícula se vea como puntos o no se vea. Vale para todas las ventanas y vistas y se recuerda al reiniciar; no se guarda en el circuito. Los pines y los cables siguen en la cuadrícula de una unidad, como siempre, y los puntos no cambian.
- Al alejar, las líneas se ralean como los puntos: cuando las finas quedarían a menos de unos 6 px solo se dibujan las más marcadas, y si también esas quedan muy juntas no se dibuja ninguna.
- Las líneas tienen su propio color en cada esquema («Líneas de la cuadrícula» en el editor de esquemas, muy suave sobre el fondo); la de cada 10 usa ese color con el doble de opacidad. Un esquema propio guardado antes de esta opción toma el color de sus puntos, más suave.
- La cuadrícula es solo del editor: las imágenes exportadas (PNG, SVG) y el PDF salen iguales con puntos, líneas o nada.

## Ajustar

- «Ajustar» (el botón del imán en la barra, justo después de las herramientas; también Ver ▸ «Ajustar» y Ctrl+Mayús+M, ⌘+Mayús+M en macOS) hace que lo que mueves salte a alinearse cuando queda cerca: viene prendido. Apagado, todo se mueve como antes, solo por la cuadrícula. Vale para todas las ventanas y se recuerda al reiniciar.
- Ver ▸ «Ajustar a ▸» elige a qué se ajusta: «Piezas» (prendido), «Guías» (prendido) y «Líneas de la cuadrícula» (apagado). Se puede cambiar aunque «Ajustar» esté apagado, y lo elegido se recuerda. Si no hay nada elegido, el botón lo dice: «Ajustar: nada elegido». Ninguno de los tres trae atajo, pero se les puede dar uno en Preferencias ▸ «Atajos de teclado».
- **Piezas**: el borde izquierdo, el centro o el borde derecho de lo que mueves (y arriba, centro, abajo) se alinea con cualquiera de esas líneas de los componentes y marcos que se ven en pantalla. Cada eje se ajusta por su cuenta. Solo cuentan los componentes y marcos: los cables, textos y etiquetas que van en la selección se mueven con ella sin decidir nada; una selección de solo textos no se ajusta.
- **Guías**: las guías del circuito atraen igual que las piezas y, a la misma distancia, ganan. Si solo mueves cables, se ajustan los extremos de los cables a las guías (y a las líneas de la cuadrícula, si están elegidas). Con Ver ▸ «Mostrar guías» apagado, las guías no atraen.
- **Líneas de la cuadrícula**: el borde izquierdo y el de arriba de lo que mueves (o los extremos de los cables) caen siempre en la línea más cercana, con la separación elegida en «Cuadrícula ▸ Separación ▸» y a cualquier zoom, se vea la cuadrícula como puntos, líneas o nada. Solo actúan en un eje donde no hay una pieza o guía cerca, para no tapar la alineación.
- El imán alcanza unos 12 px en la pantalla y nunca menos de una unidad: al 25 % se ajusta desde 6 unidades, al 50 % desde 3 y desde el 100 % desde una. Las mitades de unidad no cuentan: lo que mueves sigue siempre en la cuadrícula.
- Mientras arrastras aparece una línea magenta fina, con una marquita en cada punta, por cada alineación que se cumple (como mucho tres por eje): va de lo que mueves a la pieza más lejana alineada, una unidad más allá de cada lado. Una columna de muchas entradas alineadas muestra una sola línea larga.
- Se ajusta al arrastrar con Selección (mover, copiar con Ctrl —la copia se alinea también con el original—, mover un marco por su título o un bloque bloqueado), al deslizar un tramo de cable de lado (solo guías y líneas de la cuadrícula, siempre de lado), al pegar (mientras sigue al puntero; un pegado de solo cables no se ajusta), al colocar un componente (también al girarlo con R) y al soltar algo de la paleta o de otra ventana. Las paredes de un marco bloqueado siguen mandando. Las flechas del teclado, X/Y en Propiedades y cambiar el tamaño de un marco no se ajustan.
- **Cables y marcos nuevos**: al dibujar un cable (desde un pin o un cable), cada esquina y su punta caen en la guía más cercana a la distancia del imán, o en una línea de la cuadrícula si están elegidas; al dibujar un marco, lo mismo con sus dos esquinas. Las piezas no atraen estos puntos. Un punto que ya está sobre un pin, la punta de un cable o el medio de un cable nunca se mueve: la conexión manda, así que un cable que empieza en un pin sigue empezando ahí aunque haya una guía al lado.
- La guía a la que se ajustó lo que arrastras (una pieza, un pegado, un componente por colocar, la punta de un cable o el borde de un marco nuevo) se dibuja más marcada, continua en vez de discontinua, mientras dura el arrastre. En un marco nuevo solo se marca si el marco, con su tamaño mínimo, de verdad llega a ella.
- Cambiar «Ajustar», lo elegido en «Ajustar a», el zoom o las guías en medio de un arrastre lo recalcula al instante, y soltar justo después deja lo que se ve. Si el imán devuelve lo arrastrado a donde estaba, no se mueve nada y tampoco cuenta como clic: la selección queda igual y un interruptor no cambia.
- **Contactos nuevos**: mientras arrastras con Selección (mover, copiar con Ctrl, un marco por su título, deslizar un tramo), pegas, colocas un componente o traes de vuelta algo que sacaste del lienzo, un punto rojo con borde del color del fondo marca cada lugar donde un pin o la punta de un cable quedaría tocando un cable, un pin o la punta de un cable de otra red, es decir, donde el movimiento uniría dos redes que hoy están separadas. Sale con el imán prendido o apagado y sigan o no los cables (si siguen, se suman los contactos de los tramos que se estiran o se agregan). Una copia con Ctrl también avisa si toca a su original. Al lado de cada punto se lee, sin acercar el puntero, el nombre del pin que llega ahí y, si toca un pin con nombre de otra pieza, «→» y el nombre de ese pin (por ejemplo «Cin → S»); si solo llega un cable a un pin, «→ S». Para no llenar el lienzo, solo los 4 puntos más cercanos al puntero llevan nombre, y un pin sin nombre deja solo el punto. El punto mide lo mismo con cualquier zoom, cambia de color al cambiar de esquema y se dibuja bajo las líneas de alineación, así ninguna lo tapa. Al soltar no pasa nada nuevo: se hace el mismo movimiento de siempre, en un paso.
- Las líneas de alineación y los puntos de contacto son solo del editor: no salen en las imágenes exportadas (PNG, SVG) ni en el PDF.

## Lienzo vacío

Cuando el circuito no tiene nada (ni piezas, ni cables, ni textos, ni notas, ni marcos), el lienzo muestra al centro, en gris suave, «Arrastra una pieza desde la paleta para empezar», «o abre un ejemplo en Archivo ▸ Ejemplos» y «Doble clic: insertar un componente · Ctrl+K: ejecutar cualquier acción», que nombra la [paleta de comandos](edicion.md#paleta-de-comandos) con el atajo que tenga «Buscar una acción…» en ese momento (si le quitas el atajo, la línea termina en «Ayuda ▸ Buscar una acción…»). Si el panel es muy chico para el texto entero, no muestra nada. Desaparece apenas pones cualquier cosa y vuelve si borras todo. Es solo una ayuda de la pantalla: no es parte del circuito, así que no sale en PNG, SVG, PDF ni al imprimir, no se selecciona y no estorba los clics ni al soltar piezas. Tiene tamaño fijo en pantalla (no cambia con el zoom), usa el color de texto atenuado del tema (se lee en claro y en oscuro) y se centra en lo que se ve de cada panel cuando divides la vista.

## Reglas y guías

- Arriba y a la izquierda del lienzo hay dos reglas finas (sin números: solo marcas que siguen el zoom y el desplazamiento). Al pasar el mouse por una regla el cursor cambia a una línea con dos flechas y aparece «Arrastra para crear una guía».
- Arrastrando desde la regla de arriba sale una **guía horizontal**; desde la de la izquierda, una **vertical**: una línea cian discontinua que cruza todo el lienzo y cae en la cuadrícula. Al soltar sobre el lienzo queda la guía y la barra de estado dice «Guía agregada»; soltar de vuelta sobre la regla, sobre la otra regla o fuera del lienzo, o apretar Esc, no crea nada.
- Cada guía tiene un triángulo en la regla que cruza. Para moverla se arrastra su línea con la herramienta de selección (a menos de 3 px y sin Shift ni Ctrl, que siguen siendo de la selección; si hay algo del circuito debajo —una pieza, un cable, un pin que empezaría un cable o el borde de un marco—, eso gana) o su triángulo; el cursor cambia a una flecha doble encima de ella. Arrastrarla de vuelta a su regla la quita («Guía quitada»); soltarla sobre otra guía las junta en una.
- Sin arrastrar: clic derecho en el lienzo vacío da «Agregar guía horizontal aquí» y «Agregar guía vertical aquí»; clic derecho sobre una guía, «Quitar guía» (con las guías escondidas, el menú no las ofrece). En Ver están «Mostrar guías», «Agregar guía horizontal» y «Agregar guía vertical» (donde está el puntero si está sobre el lienzo, si no en el centro) y «Borrar guías» (desactivado si no hay guías, y en un solo paso de deshacer); agregar una guía con las guías escondidas vuelve a mostrarlas. Ninguno trae atajo; se les puede dar uno en Preferencias ▸ «Atajos de teclado». Un clic en el cuadrado donde se juntan las reglas abre un menú con esas mismas opciones (las guías nuevas van al centro del lienzo).
- Las guías son del circuito: se guardan en el archivo, se ven en todas las vistas del circuito (Ventana ▸ «Nueva pestaña del mismo circuito») y cada cambio se deshace con Ctrl+Z en un paso. Si una guía cambia en otra vista (o con deshacer) mientras se arrastra, el arrastre se cancela.
- Ver ▸ «Mostrar guías» (encendido por defecto, vale para todas las ventanas y se recuerda) esconde las reglas y las guías; el lienzo vuelve a llegar hasta el borde y las guías siguen guardadas en el circuito.
- Las reglas y las guías son solo del editor: las imágenes exportadas (PNG, SVG) y el PDF salen iguales con o sin guías.

## Íconos, barra de herramientas y cursores

- Íconos nuevos en menús, barra de herramientas y menú contextual (se adaptan al tema oscuro); la barra muestra solo íconos y el nombre aparece al pasar el mouse. La ventana tiene ícono propio. El tooltip de cada botón termina con su atajo actual, por ejemplo «Deshacer agregar (Ctrl+Z)», y cambia si se cambia el atajo. «Avanzar 10 t» (antes «Paso») avanza la simulación 10 unidades de tiempo; el selector de velocidad tiene un ícono y dice «Velocidad ×10» (×1 = 1 unidad de tiempo por milisegundo); va de ×0,1 a ×1000 y «Máxima» (×1000 desde 2026-11-05). En la barra de estado, «t = …» es el tiempo de simulación y un clic en «▶ corriendo» o «⏸ en pausa» pausa o inicia. «Retardo», «Tiempo en alto», «Tiempo en bajo» e «Intervalo de muestreo» dicen «(t)»: se miden en unidades de tiempo de simulación.
- El cursor dice qué hará un clic: flecha sobre el lienzo vacío o algo sin seleccionar, cruz sobre un pin o un cable (empieza un cable), mientras dibujas un cable y al colocar un componente, mover sobre lo seleccionado y al arrastrarlo, mano sobre el cuerpo de un interruptor o de un pin de entrada, mover mientras se desplaza la vista (Espacio, botón del medio o «Mover vista»), una lupa con «+» o «−» con la herramienta Zoom.
- Con la herramienta «Mover vista» activa, Esc vuelve a la flecha de Selección (como con Zoom).

## Panel derecho

- El panel derecho tiene cuatro secciones: Propiedades, Problemas, Notas y Mapa de memoria. Sus íconos están en una
  tira fija al borde derecho de la ventana, que queda aunque el panel se cierre (con sus insignias: los problemas en
  rojo o ámbar, ver [Insignia de problemas](simulacion-y-paso-a-paso.md#insignia-de-problemas), y la cuenta de notas
  en gris). Un clic en otro ícono cambia de sección; un clic en el ícono de la sección abierta, la ✕ del encabezado o
  un doble clic en el divisor cierran el panel, y el botón de abajo de la tira lo abre o lo cierra. Al reabrirlo
  vuelve con la misma sección y el mismo ancho (el último al que arrastraste el divisor; si la ventana es angosta, el
  lienzo conserva su lugar y el panel se angosta solo mientras no cabe). Es uno por ventana: todas sus pestañas lo
  muestran igual. Abierto o cerrado, su sección y su ancho se recuerdan al continuar donde quedaste (ver
  [Sesión y recuperación](#sesión-y-recuperación)).
- Sus cuatro secciones tienen tecla: Alt+3 «Propiedades», Alt+4 «Problemas», Alt+5 «Notas» y Alt+6 «Mapa de memoria»
  (también Ver ▸, justo después de «Árbol del circuito» y «Paleta»; se pueden cambiar en Atajos de teclado, y el
  globo de cada ícono de la tira dice la tecla: «Propiedades · Alt+3»). Con el panel cerrado, la tecla lo abre en esa
  sección y le da el foco; si esa sección ya está abierta, cierra el panel y el foco vuelve al lienzo; si hay otra
  abierta, cambia a esta.

## Modo presentación

- Ver ▸ «Modo presentación (pantalla completa)» (F11) o el botón de cuatro esquinas en el extremo derecho de la barra de
  herramientas (a la derecha de «Presentar lo que hace la IA») ponen la ventana en toda la pantalla del monitor donde está, sin la barra de título del sistema, y
  esconden las barras detrás de los bordes de la pantalla para que se vea solo el circuito, por ejemplo al proyectarlo
  en una clase. El circuito se dibuja igual que en la ventana (con sus valores, brillos y selección) y, al entrar y
  al salir, cada panel de la vista queda centrado en el mismo punto del circuito y con el mismo zoom (si haces zoom o
  mueves la vista justo después, VSim respeta lo que hiciste).
- Al entrar, un aviso arriba dice durante 2 segundos «Modo presentación · Esc o F11 para salir · lleva el puntero a
  un borde para ver sus barras».
- Cada borde guarda sus barras: arriba, el menú, las pestañas y la barra de herramientas; a la izquierda, el árbol del
  circuito y la paleta (con el ancho que tenían); a la derecha, el panel derecho con su tira de íconos. Una barra
  aparece solo si el puntero se queda 1 segundo en su borde (mientras tanto crece una línea de color en el borde), así
  no se abre al pasar de largo, por ejemplo hacia un segundo monitor, ni al arrastrar algo hasta el borde. Entra
  deslizándose por encima del circuito, sin moverlo, y se esconde 0,4 segundos después de que el puntero sale de ella,
  nunca mientras uno de sus menús está abierto o estás escribiendo en uno de sus campos de texto (la búsqueda de la
  paleta o del árbol, un campo de Propiedades); el foco en un árbol, una lista o un encabezado no la mantiene abierta.
  Al entrar, el teclado pasa al circuito. Si después una tecla le da el foco (Alt+2 para la paleta, Alt+3 para
  Propiedades) o abre uno de sus menús (Alt y la letra del menú), aparece de inmediato. La barra de estado no se
  muestra en este modo.
- Si el circuito tiene alguna sonda, el borde de abajo guarda las formas de onda, aunque su panel estuviera cerrado:
  al entrar se esconden de inmediato y suben desde abajo por encima del circuito, a todo el ancho, como las otras
  barras. Su botón «Ocultar» solo las baja (las sondas siguen y el borde sigue funcionando). En el modo, su barra de
  herramientas muestra un pin («Fijar las formas de onda»; inclinado y gris, o derecho y rojo cuando está apretado):
  apretado, el panel queda abajo fijo y el circuito se acorta para dejarle espacio; soltándolo, el panel vuelve a esconderse cuando el puntero sale de él. VSim recuerda el pin para la
  próxima vez. Abrir el panel en el modo (Ver ▸ Formas de onda, su botón de la barra de herramientas, «Ver en formas
  de onda» o un clic en la marca de una sonda) lo muestra un momento desde abajo, o fijo si el pin está apretado;
  cerrarlo con el ítem o el botón lo esconde detrás del borde aunque esté fijado (el pin se sigue recordando). Sin sondas ni el panel abierto, el borde de abajo no hace nada.
- Tampoco se ven las marcas para editar: las reglas de las guías (y su esquina), los «⋮» para dividir la vista y el
  borde que marca el panel activo en una vista dividida. Las guías ya puestas siguen a la vista (según Ver ▸ Guías) y
  las divisiones que ya tiene la vista se quedan.
- Para salir: F11, Esc, Ver ▸ «Modo presentación (pantalla completa)» (marcado con ✓ mientras está activo) o el botón
  de la barra de herramientas, que queda apretado y cuyo globo dice «Salir del modo presentación (F11 o Esc)». Esc
  sale solo cuando nada más lo usa: antes cierra un menú abierto o la lista de la velocidad, termina la herramienta en
  uso (colocar una pieza, un cable a medias), cierra la barra de búsqueda o la vista de «Mirar adentro», y no borra la
  selección. Con un Terminal o un Teclado capturando el
  teclado, Esc sale del modo y termina la captura, como siempre; F11 va a la pieza si tiene «Flechas como
  secuencias» (que envía las teclas F), y si no, sale del modo.
- Al salir, todo vuelve como estaba: la ventana a su lugar y su tamaño (o maximizada; en GNOME con XWayland a veces
  no vuelve a quedar maximizada, y hay que maximizarla a mano), las barras, los anchos de la
  paleta y del panel derecho y las formas de onda abiertas o cerradas, con la altura que tenían antes de entrar. Es por ventana (cada una en su monitor) y no se recuerda al reiniciar VSim. La ventanita
  de «Presentar» de la IA (ver [Presentar](ia-mcp.md#presentar)) sigue a la vista. El atajo F11 se cambia en
  [Atajos de teclado](#atajos-de-teclado).

## Mostrar los atajos al usarlos

- Ver ▸ «Mostrar los atajos al usarlos» (apagado de entrada; se recuerda al reiniciar y vale para todas las
  ventanas). Funciona igual dentro y fuera del [modo presentación](#modo-presentación): sirve para que en una clase o
  un video se vea qué atajo tiene lo que se va haciendo.
- Cada acción que haces con su atajo, con un ítem de menú o con un botón de la barra de herramientas muestra, abajo en
  el centro del circuito, una etiqueta como «Ctrl+Z · Deshacer». Si la acción no tiene atajo, solo su nombre. Dura
  1,5 segundos (aparece y se va suavemente), con hasta tres apiladas, la más nueva abajo. Es grande a propósito (casi el doble que el texto de la interfaz, para un proyector o un video), con cada tecla en su casilla y el nombre de la acción al lado.
- El atajo que se muestra es el que tienes ahora (los cambios de [Atajos de teclado](#atajos-de-teclado) se ven al
  instante), con los nombres de las teclas en tu idioma (en macOS, ⌃ ⌥ ⇧ ⌘ y la tecla: «⌘Z»).
- No muestra nada de lo que escribes en un campo de texto, ni de lo que va a un Terminal o un Teclado que tiene el
  teclado, ni los clics del ratón sobre el circuito. Con una tecla mantenida apretada no repite la misma etiqueta.
  Tampoco la muestran, por ahora, los ítems de las listas que cambian solas: Archivo ▸ Recientes, Archivo ▸ Ejemplos y
  la lista de ventanas abiertas de Ventana.

## Hoja de atajos

- «?» oscurece el área del circuito (no los menús; el circuito se sigue viendo detrás, más oscuro) y muestra encima
  una tarjeta centrada, de hasta unos 900 px de ancho, con una selección de unos 30 atajos en cinco grupos, cada tecla
  dibujada en su casilla («Ctrl» + «Mayús» + «Z»): Archivo (Nuevo, Abrir, Guardar, Guardar como, Copiar captura),
  Editar (Deshacer, Rehacer, Copiar, Cortar, Pegar, Duplicar, Seleccionar todo, Borrar, Rotar, Etiqueta, Ordenar
  circuito, Enmarcar selección, Insertar componente), Ver (Zoom, Ver todo el circuito, Zoom a la selección, Buscar,
  Siguiente problema, Modo presentación y Formas de onda si le diste una tecla), Simular (Iniciar o pausar, Avanzar un
  paso fijo, Avanzar al próximo evento, Reiniciar) y Ayuda (Buscar una acción y la propia «?»). Si la tarjeta no cabe
  (una pantalla de 1366×768, la letra al 125 %), los grupos se desplazan dentro de ella con la rueda y el pie queda a
  la vista. Otra vez «?», Esc o un clic en cualquier parte del circuito oscurecido la cierran, sin hacer nada más (Esc
  la cierra antes que cualquier otra cosa; en el [modo presentación](#modo-presentación) cierra solo la hoja, sin salir
  del modo). Cualquier otra tecla cierra la hoja y hace lo suyo de siempre, así ves el resultado: Supr borra, una
  flecha mueve, Espacio cambia el codo del cable en curso, Ctrl+Z deshace; Ctrl, Mayús o Alt solas no la cierran.
  Mantener «?» apretada no la hace parpadear: abre o cierra una sola vez hasta que sueltas la tecla.
  También se abre y se cierra con Ayuda ▸ «Atajos de teclado», que muestra «?» como su tecla, y aparece en la
  [paleta de comandos](edicion.md#paleta-de-comandos).
- «?» es el carácter escrito, así que es la misma tecla con el teclado español (Mayús+') y con el de EE. UU.
  (Mayús+/), igual en Windows, Linux y macOS. Mientras escribes en un campo de texto (las propiedades, la búsqueda de
  la paleta, la barra Buscar) o un Terminal o un Teclado tiene el teclado, «?» se escribe. Es una tecla fija, como Esc:
  no se cambia en [Atajos de teclado](#atajos-de-teclado).
- Las teclas son las que tienes ahora, con los nombres en tu idioma: si cambias un atajo, la hoja muestra el nuevo al
  instante, y un atajo que dejaste sin tecla sale de la hoja.
- Abajo en la tarjeta, mientras haces un gesto (dibujar un cable, mover piezas, pegar, colocar un componente), una
  franja repite la [frase del gesto](#frase-del-gesto): qué hace el próximo clic y qué teclas sirven ahora. Sin gesto en curso no aparece.
  El circuito conserva el teclado mientras la hoja está abierta: esas teclas cierran la hoja y actúan.
- Al pie de la tarjeta, a la izquierda, el interruptor Sí/No «Mostrar los atajos al usarlos» es la misma opción de
  Ver ▸ «Mostrar los atajos al usarlos»: encenderlo aquí la marca en el menú y al revés, y cambiarlo no cierra la
  hoja. Mientras la hoja está abierta no aparecen las etiquetas de
  [Mostrar los atajos al usarlos](#mostrar-los-atajos-al-usarlos), para que no se tapen. Al medio dice «? o Esc
  cierra la hoja», y a la derecha el enlace «Ver y cambiar todos los atajos…» cierra la hoja y abre Preferencias en la
  pestaña «Atajos de teclado».
- Usa los colores del tema (claro u oscuro; en los dos el circuito queda más oscuro detrás) y aparece con un fundido
  breve, salvo con Preferencias ▸ [Animaciones](#animaciones) en «No». Al cerrarla, el teclado vuelve al circuito si
  lo tenía al abrirla.

## Qué hay de nuevo

- La primera vez que abres una versión nueva de VSim (también la primera vez que lo instalas), unas burbujas te
  muestran, una por una, lo nuevo de esa versión: la ventana se oscurece salvo lo que la burbuja apunta, que queda
  enmarcado en el color de la burbuja, y la flecha de la burbuja apunta a su centro. En esta versión son ocho: los
  cables de corrido (en el botón Selección), la captura al portapapeles (su botón), el botón «Formas de onda», el
  modo presentación (su botón), «Mostrar los atajos al usarlos» (en el título del menú Ver, sin abrirlo),
  «Animaciones» (en el título del menú Archivo), «Buscar una acción» (Ctrl+K) y la hoja de atajos («?»), las dos
  en el título del menú Ayuda; la de «?» muestra siempre esa tecla, porque no se puede cambiar.
- Cada burbuja tiene su título, su texto (las teclas dibujadas como teclas, con los nombres en tu idioma y las que
  tienes ahora) y, cuando lo que apunta tiene atajo, una línea «Atajo» con esa tecla (si lo cambiaste, sale el tuyo);
  si no tiene atajo, la línea dice dónde está («En el menú: Ver ▸ Mostrar los atajos al usarlos», «En el menú:
  Archivo ▸ Preferencias ▸ General»). Abajo, «n de m» dice en cuál vas, y «Listo», ‹ y › las recorren; con el teclado,
  → o Enter pasa a la siguiente, ← vuelve y Esc es «Listo». ‹ está apagado en la primera y › en la última termina.
  Los botones de la burbuja no se quedan con el teclado: el circuito lo conserva y lo recupera al terminar.
- «Listo», Esc, › en la última o empezar a editar (cambiar el circuito o hacer un gesto en él) terminan la ronda de
  esa versión: no vuelve a salir sola hasta la próxima versión, que muestra sus propias burbujas. Ayuda ▸ «Qué hay de
  nuevo» (justo después de «Atajos de teclado»; también en la [paleta de comandos](edicion.md#paleta-de-comandos)) las
  vuelve a mostrar desde la primera cuando quieras.
- Salen solas solo en la primera ventana, cuando ya se terminó de abrir; nunca en el
  [modo presentación](#modo-presentación) ni mientras la IA está trabajando: esperan a que salgas del modo o a que la
  IA termine. Si cierras VSim antes de verlas, salen la próxima vez. Entrar al modo presentación las termina.
- Si lo que una burbuja apunta no se ve (por ejemplo, un botón que no cabe en una ventana angosta), esa burbuja se
  salta y «n de m» cuenta solo las que se muestran; si desaparece mientras su burbuja está abierta, pasa a la
  siguiente. La burbuja se pone sola donde cabe: bajo la barra de herramientas y los títulos de los menús, sobre la
  barra de estado, a la derecha de la franja izquierda y a la izquierda de la derecha; si no cabe, al otro lado y
  después a los demás, siempre dentro de la ventana y nunca sobre los menús; en una ventana muy chica va centrada y
  sin flecha. Sigue a la ventana cuando cambia de tamaño.
- Usa el color de acento del tema (claro u oscuro) y pasa de un lugar a otro deslizándose; con Preferencias ▸
  [Animaciones](#animaciones) en «No», salta.

## Consejo del día

- Al abrir VSim, como mucho una vez al día, una tarjeta abajo a la derecha del circuito enseña algo que ya existe y
  que no se descubre solo, con un dibujo y tus teclas de verdad. No bloquea nada: puedes seguir trabajando con ella
  abierta, y no se queda con el teclado (un clic en ella se lo da; al cerrarla, el circuito lo recupera).
- Arriba, la ampolleta prendida, «Consejo del día» y ×. Después, el dibujo, el título y el texto, con las teclas
  dibujadas como teclas. Si cambiaste un atajo en Preferencias, el consejo muestra el tuyo, en el texto y en la tecla
  grande del dibujo, al instante; si una acción quedó sin atajo, el texto dice dónde está en los menús
  («Ventana ▸ Dividir ▸ A los lados») y el dibujo no muestra tecla. Abajo, «Mostrar al iniciar», «Probarlo» y ‹ ›.
- Hay cuatro tipos de dibujo: un circuito pequeño que VSim dibuja con tu esquema de colores (claro u oscuro); «antes y
  después», dos dibujos lado a lado y a la misma escala, «Antes → Después» debajo y la tecla grande abajo; un
  circuito en dos paneles con una división, entero a la izquierda y de cerca a
  la derecha, como se ve una vista dividida; o una imagen (una captura de VSim, la misma en todos los idiomas).
  Todos los dibujos de circuitos los hace VSim con tus
  colores y a un tamaño parecido en todos los consejos, y la tecla grande también: nunca viene en una imagen. Las
  teclas tienen el nombre en el color del texto, así se leen en el tema claro y en el oscuro. Un título largo pasa a
  una segunda línea en vez de cortarse.
- «Probarlo» abre el circuito del consejo (o uno vacío) en una pestaña nueva (una copia sin nombre de archivo; el
  circuito que tienes abierto no se toca) y, si el consejo habla de un diálogo o de una acción, la hace ahí («Dividir la vista» lo
  abre ya dividido; «Buscar una acción» abre la búsqueda de acciones). Solo sale en los consejos que tienen algo que
  abrir.
- ‹ y › recorren los consejos y dan la vuelta; la tarjeta nunca dice cuántos hay. VSim recuerda cuáles ya viste (no un
  número): el próximo es el primero de la lista que no has visto, así un consejo que trae una versión nueva no espera
  a que des toda la vuelta. Cuando ya los viste todos, vuelve a empezar desde el primero.
- Se queda hasta que la cierras tú: con ×, o con Esc cuando la tarjeta tiene el foco (si hiciste clic en ella). Editar
  el circuito, hacer clic en él o mover el foco a otra parte u otra ventana no la cierran. Entrar al [modo presentación](#modo-presentación) o cambiar de pestaña también la cierra.
- Sale sola solo en la primera ventana, cuando ya se terminó de abrir, y nunca: la primera vez que abres VSim, el
  mismo día que salen las burbujas de [Qué hay de nuevo](#qué-hay-de-nuevo) (ellas ganan), en el modo presentación ni
  mientras la IA está trabajando (espera a que salgas del modo o a que la IA termine). Si sale, no vuelve a salir sola
  hasta el día siguiente; si cierras VSim antes de que salga, sale la próxima vez.
- «Mostrar al iniciar» la apaga o la prende; es la misma opción que Preferencias ▸ General ▸ «Mostrar el consejo del
  día al iniciar» (cambiar una cambia la otra al instante). Ayuda ▸ «Consejo del día» (justo después de «Qué hay de
  nuevo»; también en la [paleta de comandos](edicion.md#paleta-de-comandos)) la abre cuando quieras.
- Aparece deslizándose y aclarándose; con Preferencias ▸ [Animaciones](#animaciones) en «No», aparece de una vez.
- Por ahora trae treinta y siete consejos, en este orden: «Mirar adentro» (Alt sobre un componente tuyo), «Ordenar circuito»
  (Ctrl+Alt+L: un circuito desordenado, con puntas de cable sueltas, y cómo lo deja), «Insertar rápido» (doble clic
  en un lugar vacío abre «Insertar componente»; también Insert), «Cable continuo y el codo» (Espacio), «Doble clic:
  un tramo; triple: la red», «Duplicar sin el portapapeles» (Ctrl+D), «Saltar al próximo problema» (F8 y Mayús+F8),
  «Limpiar cables» (Ctrl+Alt+L también borra puntas sueltas y une tramos; Ctrl+Alt+Mayús+L muestra antes qué
  cambia), «Buscar» (Ctrl+F, F3), «Buscar una acción» (Ctrl+K), «Dividir la vista», «Colocar varias seguidas» (Mayús+clic sigue colocando la misma pieza; Girar la gira antes), «Deslizar un cable de costado» (Alt y arrastrar un cable seleccionado), «Túneles», «Ver todo y la selección» (Mayús+1 y Mayús+2), «Mover con las flechas» (un cuadro; con Mayús, cinco), «Editar el texto en el lugar» (doble clic o F2), «Marcos» (Ctrl+G) y «Notas adhesivas» (Ver ▸ Nota adhesiva), «Buses y selector de bits», «El ancho sigue al bus» (Ctrl+Z deshace el cable y el ancho), «Separador y unión de bus» (clic derecho en un pin de bus), «Mostrar como» (Propiedades), «Paso a paso» (F7 y Mayús+F7), «Reiniciar la simulación» (Ctrl+F5), «Punto de parada», «Cortocircuitos», «Formas de onda de una cadena», «Ver retardos», «Entrar a un componente» (doble clic o Enter), «Componentes propios con forma» (Cambiar forma…), «Atajos al usarlos» (en el modo presentación), «Tabla de verdad», «Circuito desde tabla de verdad», «Circuito desde expresiones», «Práctica de Karnaugh» e «Importar de Logisim» (Archivo ▸ Importar).

## Atajos de teclado

- Archivo ▸ Preferencias… ▸ «Atajos de teclado» lista las acciones por menú con su atajo y un buscador. «Cambiar…» (o doble clic) espera la combinación (Esc cancela); si otra acción ya la usa, aparece «Ctrl+S ya lo usa «Guardar».» con «Reasignar» y «Cancelar». «Quitar atajo», «Restaurar» y «Restaurar todos» hacen lo que dicen. Esc, Enter, Espacio, Tab, Mayús+Tab, Retroceso y los atajos fijos del zoom (Ctrl con +, teclado numérico) no se pueden asignar. Los cambios se ven al instante en todas las ventanas y se recuerdan al reiniciar.
- Además de los atajos de siempre se puede dar uno a Selección, Mover vista, los dos modos de Editar ▸ «Dibujar cables», Escribir el zoom…, Centrar circuito, Configurar…, Iniciar, Pausar, Avanzar 10 t, Preferencias… y Nueva vista. Las teclas del lienzo (Supr, R, Mayús+R, F2, F8, Mayús+F8, Z para la herramienta Zoom, Mayús+1 para «Ver todo el circuito» y Mayús+2 para «Zoom a la selección») también se cambian ahí; Retroceso sigue borrando. Las teclas del lienzo actúan mientras el lienzo tiene el foco, así no molestan al escribir en un campo de texto.

- Las teclas se nombran en el idioma de VSim, igual en todas partes donde se muestra un atajo (la tabla de atajos, la
  [paleta de comandos](edicion.md#paleta-de-comandos), las ayudas de los botones, las etiquetas de
  [Mostrar los atajos al usarlos](#mostrar-los-atajos-al-usarlos)): «Ctrl+Mayús+S», «Supr», «Retroceso», «Espacio»,
  «Insert», «Inicio», «Fin», «RePág», «AvPág» y las flechas «↑ ↓ ← →»; en inglés «Ctrl+Shift+S», «Delete»,
  «Backspace». En macOS se escriben con ⌃ ⌥ ⇧ ⌘ y la tecla, sin «+» («⇧⌘S»). Los atajos que se ven a la derecha de
  los ítems de los menús los escribe el sistema y pueden salir con sus nombres («Shift»).

## Preferencias

- Archivo → Preferencias… (en macOS también VSim 2 → Ajustes…): tema Claro, Oscuro o Seguir sistema; esquema de color del circuito (Según el tema, VSim clásico, Claro, Oscuro, los otros esquemas del VSim original: Pastel, CSim y Twilight, Placa, Logisim y Chipmunk); idioma (Sistema, Español, English; se aplica al reiniciar); directorio de la librería de componentes (Elegir… / Restaurar predeterminado). El tema y el esquema cambian al instante en todas las ventanas.
- El esquema «Placa» dibuja el circuito como una placa de circuito impreso: fondo verde de máscara de soldadura, cables de cobre en 0 y dorados brillantes en 1 (Z en celeste lila, conflicto en rosado rojizo, desconocido en gris), contornos, patas y textos en blanco como la serigrafía, pines dorados como los contactos de una placa, piezas negras como los chips, y la cuadrícula como vías: puntos de un verde apenas más claro (las líneas, del mismo verde, muy suaves). Todos los estados de un cable se leen sobre el verde (al menos 3:1) y los textos también (al menos 4.5:1); la selección, el brillo de «Avanzar» y de los cambios de la IA son celestes, distintos de cualquier cable, y los colores de los marcos y de los túneles usan su variante oscura, que se lee sobre la placa. Funciona con el tema claro y el oscuro (la placa es el lienzo; el resto de la ventana sigue al tema). Al imprimir o exportar con «Fondo blanco para imprimir» se usan, como con los otros esquemas oscuros, los colores claros sobre blanco.
- Los esquemas «Logisim» y «Chipmunk» recuerdan los colores de esos simuladores; el dibujo de las piezas sigue siendo el de VSim. «Logisim» toma los colores por defecto de Logisim-evolution: lienzo blanco con puntos grises, piezas negras sobre blanco, 0 en verde oscuro, 1 en verde brillante, Z (cable flotante) en azul, conflicto en rojo y desconocido en gris, y el display de 7 segmentos rojo con los segmentos apagados gris claro. «Chipmunk» toma los de diglog, el simulador digital de Chipmunk: pantalla gris oscuro, compuertas cian, pines rojos, etiquetas amarillo oscuro y selección blanca; diglog dibuja todos los cables verdes, así que un circuito en reposo se ve como en diglog: 0 en su verde, 1 en el rojo de sus LED encendidos y Z en un verde apagado (diglog no tenía colores para conflicto ni desconocido: aquí son magenta y gris claro), y el diagrama de tiempos con la traza amarilla de su osciloscopio. Como en diglog, la cuadrícula casi no se ve. Las notas con fondo de color siguen leyéndose sobre su gris (y sobre cualquier lienzo gris de un esquema propio).
- El 0, 1 o Z escrito en un interruptor es blanco, salvo cuando el color del interruptor es tan claro que el blanco no se leería (por ejemplo el dorado del 1 en «Placa», el blanco del 1 en CSim o el verde claro del 1 en Claro y Oscuro): ahí es negro.
- «Editor externo»: el comando que abre los archivos de memoria enlazados (por ejemplo `code` o `gedit`; el archivo va al final); vacío usa el editor del sistema. Ver [Memoria: archivos enlazados](componentes.md#memoria-archivos-enlazados).
- "Seguir sistema" consulta el modo oscuro del sistema al iniciar y al elegir esa opción (macOS, Windows y GNOME); si no puede saberlo, usa el tema claro.
- «Mostrar el consejo del día al iniciar» (encendido de entrada) es la misma opción que «Mostrar al iniciar» de la
  tarjeta del [Consejo del día](#consejo-del-día).
- `-Dvsim.language=en` (o `./gradlew :ui:run -PvsimLanguage=en`) manda sobre el idioma guardado.
- Las preferencias se guardan con `java.util.prefs` (nodo `cl/almejo/vsim2`).
- Preferencias ▸ General ▸ «Letra de etiquetas y textos» elige la letra (Predeterminada o cualquiera instalada) y el tamaño (8 a 24, 10 por defecto) de las etiquetas de los componentes, los textos libres y los títulos de los marcos (estos en negrita, un punto más grandes hasta llenar la franja del título: con letras grandes dejan de crecer para no cortarse). Cambia al instante en todas las ventanas (una edición en el lugar que esté abierta se confirma), en lo que se puede seleccionar con un clic y en las imágenes exportadas. Si la letra guardada no está instalada en este computador (preferencias copiadas de otro), se usa la predeterminada sin perder la elección. Si la letra no tiene algún carácter (Fira Code no tiene «‑», el guion que no corta la línea), ese carácter se toma de la letra predeterminada en vez de dibujar un cuadradito, en el lienzo, en las imágenes, en el PDF y en papel. El texto dentro de las piezas (como el valor de una Constante) no cambia.
- Esquemas de colores propios: en Preferencias ▸ General, «Personalizar…» (junto al esquema de color) abre un editor que parte de una copia del esquema elegido (con «Según el tema», del que da el tema actual): un nombre («Oscuro (copia)» por defecto), un botón por cada uno de los 22 colores (muestra y código hexadecimal; abre el selector de colores, con transparencia) y una vista previa con un circuito de muestra, sobre las líneas y los puntos de la cuadrícula, que cambia al instante, dibujada con la misma letra que el lienzo. «Guardar» lo agrega a la lista de esquemas (después de los incluidos, por nombre) y lo elige; «Cancelar» no guarda nada. Con un esquema propio elegido, «Personalizar…» lo edita en su lugar (las ventanas cambian al guardar) y «Borrar» lo elimina después de pedir confirmación; si era el que estaba en uso, se vuelve a «Según el tema». Los esquemas se guardan en las preferencias; un esquema guardado que ya no existe se muestra como «Según el tema». El color «Pines» es el del punto (o el cuadrado, en un bus) que marca cada pin; las patas, los bordes, las uniones de cables y los pines sin conectar usan el del borde de las piezas. Un esquema propio guardado antes de que existiera «Pines» lo toma de su borde, así se ve igual.

## Animaciones

- Archivo ▸ Preferencias… ▸ General ▸ «Animaciones» (Sí por defecto) es una sola opción para todas las animaciones de VSim: un interruptor (la pista con su perilla y «Sí» o «No» al lado, el mismo de las propiedades de sí o no de las piezas). Es solo de VSim: no lee ninguna opción del sistema (ni el interruptor de animaciones de GNOME ni otro). Cambia al instante, sin reiniciar: lo que se está moviendo termina de una vez en su estado final.
- «Animar los movimientos de la vista» queda justo debajo y solo cuenta con animaciones: con «No» se ve desactivada (guarda tu elección para cuando vuelvas a «Sí») y la vista salta en vez de [viajar](buscar.md#viaje-suave-de-la-vista). El botón «Viaje suave» de la barra Buscar también queda desactivado, y su ayuda dice por qué.
- Con «No»:
  - la vista salta de una vez (buscar, ir a un problema, «Ver todo el circuito», lo que hizo la IA, entrar a un componente);
  - las barras del [modo presentación](#modo-presentación) aparecen sin deslizarse;
  - el cursor de los Terminales queda encendido fijo, y el cursor y la estrella de Automan quietos;
  - el brillo que se apaga de una Matriz LED se apaga de una vez, y el brillo de una pieza no se desvanece;
  - los avisos sobre el circuito, las etiquetas de [Mostrar los atajos al usarlos](#mostrar-los-atajos-al-usarlos), el globo y el subtítulo de [Presentar](ia-mcp.md#presentar) aparecen y se van sin fundido;
  - plegar un grupo de pestañas es instantáneo y el robot de la IA en la pestaña no se mece (vuelve a mecerse apenas eliges «Sí», si la IA sigue trabajando);
  - las listas, los árboles y los paneles se desplazan con la rueda del mouse de un salto, sin suavizado;
  - lo que informa queda quieto, nunca escondido: un cortocircuito queda en rojo fijo (sin pulso), y lo mismo el resplandor rojo bajo un cable con problema; ir a un problema deja el resaltado fijo 1,5 s en vez de parpadear (también la fila de una sonda en las formas de onda); lo que cambió en un paso a paso queda con un contorno fijo hasta el próximo paso, la próxima edición, «Reiniciar» o hasta que la simulación vuelve a correr («Iniciar» o salir del paso a paso), porque desde ahí los valores ya no son los de ese paso; lo que cambió la IA queda fijo 1,5 s y se apaga de una vez; en el modo «Reciente» del [mapa de memoria](simulacion-y-paso-a-paso.md#mapa-de-memoria) cada acceso queda encendido entero un segundo y se apaga de una vez; la captura muestra su destello entero 0,35 s y se va sin desvanecerse;
  - la lupa «Mirar adentro» aparece y se va de una vez, sin crecer ni achicarse (lo que muestra por dentro sigue en vivo);
  - el contorno de la selección, el rectángulo de selección del editor de formas y lo que se marca en los mapas de la [práctica de Karnaugh](tabla-de-verdad-karnaugh-y-expresiones.md#modo-práctica-de-karnaugh) (el grupo elegido, el rectángulo que se dibuja, el cursor del teclado) quedan punteados y quietos: las hormigas no marchan.
- Si eliges «No» mientras algo se desvanece (el brillo de lo que cambió la IA, el de una pieza), se apaga en ese momento: nunca vuelve a encenderse más fuerte.
- Nunca cambia la simulación: los LED, el reloj, las formas de onda que avanzan, lo que escribe un Terminal y lo que muestra la lupa siguen igual. «No» quita adornos y movimiento, nunca valores.

## Acerca de VSim 2

- Ayuda ▸ «Acerca de VSim 2…» abre un diálogo fijo (no se puede redimensionar ni minimizar) titulado «Acerca de VSim 2». Arriba, el logo a la izquierda y a su lado el nombre, la versión y una línea de qué es; debajo, una grilla con Autor (Alejandro Vera), Licencia (GPL 3.0), Código y Versiones (github.com/almejo/vsim2 y su página de versiones), e Historia (JCSim 2001, VSim 2013, VSim 2 2026); y una línea con los créditos de las bibliotecas que usa y de las tipografías incluidas («IBM Plex Mono y VT323, licencia SIL OFL 1.1»). Las dos direcciones son enlaces (color de enlace del tema, subrayados al pasar el mouse) donde el sistema puede abrir un navegador, y texto que se puede seleccionar donde no. Abajo, una franja con «Copiar información» y «Cerrar» (Enter y Esc cierran). Los colores siguen al tema claro u oscuro.
- «Copiar información» copia la versión de VSim, la de Java, el sistema operativo y la apariencia, para pegarlos en un reporte de error.
- En macOS, el mismo diálogo se abre desde «Acerca de» del menú de la aplicación.

## Reportar un problema

- Ayuda ▸ «Reportar un problema…» (después de «Acerca de VSim 2…») abre en el navegador el formulario de problemas de GitHub (`github.com/almejo/vsim2/issues`), sin diálogo intermedio. Va con la versión de VSim, el sistema operativo y el formato de instalación (msi, zip, deb, rpm, appimage, flatpak, snap, dmg o jar) ya escritos; nada más (ni la versión de Java ni tus archivos). Falta escribir qué pasó y, si quieres, adjuntar el circuito.
- El formulario sale en el idioma de VSim: en español si el programa está en español, en inglés en cualquier otro caso. También hay un formulario para proponer ideas, y los issues en blanco están desactivados.
- Los formularios avisan que los circuitos y las capturas pueden tener datos personales (Ley 21.719): sube solo lo necesario.
- Si el sistema no puede abrir un navegador, el comando no hace nada; la dirección de los issues es github.com/almejo/vsim2/issues.
- «Copiar información» de Acerca de VSim 2 ahora incluye también una línea «Formato: …» con cómo se instaló (jar si se ejecuta el .jar suelto).

## Sesión y recuperación

- Cada minuto VSim guarda en silencio una copia de cada circuito con cambios sin guardar, en la carpeta `recovery` dentro de la carpeta de datos de VSim (`~/.local/share/vsim2`, `~/Library/Application Support/VSim2` o `%APPDATA%\VSim2`). Las copias se borran al guardar, al cerrar sin guardar, al deshacer hasta lo guardado y al salir normalmente: solo quedan si VSim se cae o se mata.
- Si la vez anterior VSim no se cerró bien, al iniciar aparece «VSim 2 no se cerró correctamente…» con una fila por circuito («contador — guardado a las 14:32»; «⚠ el archivo cambió después» si el archivo es más nuevo que la copia). «Recuperar» abre los marcados como modificados y con su ruta original (Guardar sobrescribe el original; uno sin nombre pide dónde guardarlo); «Descartar» borra los marcados; «Decidir después» los guarda para el próximo inicio. Una copia dañada dice «no se puede leer» y solo se puede descartar. Las copias de más de 30 días se borran solas.
- Archivo ▸ Recientes (después de «Abrir…») lista los últimos 10 circuitos abiertos o guardados como «nombre — carpeta» (el tooltip muestra la ruta completa), y «Limpiar lista». Un archivo que ya no existe se quita con un aviso. Al abrir un circuito —desde Recientes, Abrir, arrastrándolo o desde la línea de comandos— su ventana vuelve al tamaño, posición, maximizado, zoom y centro que tenía.
- Continuar donde quedaste: al salir (Salir, Cmd+Q o cerrando la última ventana) VSim recuerda cada ventana con sus pestañas de circuitos con archivo: su lugar y tamaño, el orden de los grupos, el zoom y el centro de cada pestaña, la pestaña activa, los grupos plegados, el panel derecho de la ventana (abierto o cerrado, su sección y su ancho) y, en una pestaña de componente, el componente y la pieza cuyos valores mostraba. Al iniciar vuelven las mismas ventanas con las mismas pestañas, y cada pestaña vuelve dividida como estaba (una pestaña de componente recuerda su propia división, aparte de la del circuito). Abrir VSim con un archivo (en la línea de comandos o con un doble clic en un `.vsim`) no pierde la sesión: vuelve igual y el archivo se suma como un grupo nuevo de la ventana activa (la última que vuelve); si el archivo ya estaba en la sesión, solo se enfoca su pestaña. Los archivos que ya no existen y los componentes que ya no están en su circuito se avisan una vez, en un solo mensaje, y sus pestañas no vuelven; si la pieza cuyos valores mostraba ya no está, la pestaña muestra los de la primera pieza de ese componente, o «Sin valores» si no queda ninguna. Una sesión guardada por una versión anterior abre cada vista en su propia ventana, como antes. Se desactiva en Archivo ▸ Preferencias… ▸ General ▸ «Al iniciar, abrir los circuitos de la última sesión». Los circuitos sin nombre no se recuerdan (vuelven solo por la recuperación).
- Dos VSim abiertos a la vez no se confunden: cada uno guarda sus copias en su propia carpeta y nunca ofrece recuperar las de otro que sigue abierto.
