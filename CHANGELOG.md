# Cambios

Los cambios de VSim 2 que se notan al usarlo, del más nuevo al más antiguo. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones publicadas están en
[GitHub Releases](https://github.com/almejo/vsim2/releases); antes de la 0.1, cada sección es un conjunto de
funciones, con las fechas en que se hizo. El detalle de cada función está en
[docs/funciones.md](docs/funciones.md).

## Sin publicar — Memoria (RAM y ROM)

### Agregado

- Dos piezas nuevas en la paleta, en la categoría «Memoria»: **RAM** y **ROM**, de 2 a 16M palabras de 1 a 64 bits, con
  pines `A`, `D`, `Q`, `WE`, `CS` y reloj. Lectura combinacional, escritura en el flanco de bajada del reloj (o «Sin
  reloj»), conexión por bus o con pines sueltos, «Un solo bus D», nombre y retardo. Cambiar el tamaño nunca corta el
  contenido. Una memoria vacía casi no ocupa espacio, ni siquiera de 16M × 64 bits.
- En el lienzo, la memoria muestra su nombre, «RAM · 256 × 8», una luz de actividad (azul al leer, naranja al escribir)
  y, con «Mostrar contenido», una ventana con las palabras alrededor de la dirección actual. Los colores de lectura y
  escritura son dos roles nuevos de cada esquema de colores.
- Editor de contenido: doble clic sobre la pieza (o «Editar contenido…» en el clic derecho) abre una grilla de
  direcciones y palabras que se escribe sobre las celdas, en hexadecimal, decimal o binario, con Ctrl+C y Ctrl+V de
  texto hexadecimal, Ctrl+Z y Ctrl+Y dentro del editor, «Llenar…», «Ir a…» (Ctrl+G), «Importar…», «Exportar…» y
  «Volcar…» (RAM) en el formato de VSim y en el de Logisim (v2.0 raw), con ícono y texto en cada botón. Aceptar es un
  solo paso de deshacer. Con la simulación corriendo, o en pausa, la RAM muestra sus valores vivos y su última lectura
  (azul) y escritura (naranja). «Exportar…» y «Volcar…» nunca escriben sobre un archivo enlazado de ninguna memoria del
  circuito; «Llenar…» propone la selección o las filas que se ven, y pide confirmar sobre 65.536 palabras; «Enlazar…»
  pregunta antes de descartar cambios sin aceptar.
  «Restaurar contenido inicial» y «Volcar…» también están en el clic derecho de la RAM.
- El inspector de una RAM o una ROM dice dónde está su contenido («En el circuito» o «Enlazado: …»), con «Editar
  contenido…» y «Abrir en editor externo», y avisa cuando el contenido tiene más palabras (o bits) de las que se ven,
  con «Agrandar a …» y «Ver en el editor». Los botones y avisos se acomodan al ancho de la barra lateral sin
  cortarse, y un aviso nuevo no toca lo que estás escribiendo en un campo.
- Memorias enlazadas a un archivo de texto: «Enlazar…» en el editor de contenido; el circuito guarda la ruta relativa a
  su carpeta y una copia del contenido. VSim recarga el archivo cuando lo guardas en tu editor (un paso de deshacer),
  espera a que termine de guardarse, avisa si falta o no se puede leer y sigue con la copia, y nunca lo escribe. «Abrir en editor externo» usa el comando
  de Preferencias ▸ «Editor externo» o el editor del sistema.
- Arrastrar un archivo `.hex` (u otro archivo de memoria) al lienzo, o Archivo ▸ Importar ▸ «Memoria desde archivo…»,
  crea una RAM o una ROM enlazada a ese archivo donde se soltó; con encabezado se crea de inmediato, sin él un diálogo
  pregunta RAM o ROM con los valores ya llenos.
- Al crear un componente propio con una memoria enlazada, la memoria entra con su contenido pero sin el enlace, y un
  mensaje lo dice.
- Pestaña «Mapa de memoria»: la RAM o ROM seleccionada completa como una matriz que muestra en vivo cada lectura
  (azul) y escritura (naranja); en pausa queda encendido lo que tocó el último paso. También un mapa de calor de
  accesos desde que partió la simulación y los valores de cada palabra, zoom con la rueda en memorias grandes, una
  ayuda con dirección, lecturas, escrituras y valor, y «Restaurar» para volver una RAM a su contenido inicial. Un clic
  en un cuadrito abre el editor de contenido en esa dirección y «Volcar…» guarda lo que tiene la RAM. Los colores de
  lectura y escritura son los del esquema activo, iguales en el mapa, el editor y el lienzo. En memorias de más de 64K
  palabras, con el mapa acercado, la ayuda habla del bloque de 256 palabras y el cuadrito no se enciende por los
  accesos de otras palabras del bloque.
- La IA (MCP) lee y escribe el contenido de las memorias RAM y ROM: `vsim_read_memory` (contenido inicial o el de la
  simulación ahora, hasta 256 palabras) y la operación `SetMemoryContents` de `vsim_edit` (palabras en hexadecimal,
  todo o nada, un solo paso de deshacer). Al leer el circuito, el contenido de una memoria sale como «N words set» y no
  entero, y el enlace solo como el nombre del archivo. No puede enlazar archivos ni escribir en una memoria enlazada.
- Ejemplo nuevo en Archivo ▸ Ejemplos: «Programa en ROM» (un contador recorre una ROM de 16 palabras con Fibonacci y un
  display las muestra en hexadecimal).

## Sin publicar — Notas adhesivas

### Agregado

- Notas adhesivas: recordatorios tuyos pegados sobre el circuito como un papel (amarillo, rosa, azul, verde o naranja,
  con la esquina doblada y sombra), de texto libre o con una lista de tareas que se marcan con un clic. Se pegan con la
  herramienta «Nota adhesiva» (justo después de «Marco»): un clic deja una nota de 8 × 6 cuadros, arrastrar le da otro
  tamaño, y se abre su editor. Doble clic las edita en el lugar, la esquina doblada les cambia el tamaño, la ✕ las
  despega después de preguntar, y el clic derecho cambia el color, el tipo (texto o lista) o las pliega en un
  papelito que muestra su texto al pasar el mouse. También se cambian en Propiedades. Dentro de un marco cerrado se
  mueven con el bloque y no se pueden cambiar hasta abrir el candado. No salen en las imágenes ni en los PDF salvo que
  marques «Incluir notas adhesivas», y Ctrl+F busca en ellas.
- Botón «–» en la esquina de arriba a la izquierda de cada nota para plegarla (la ✕ sigue arriba a la derecha): la nota
  plegada queda como un papelito anclado en esa misma esquina, y un solo clic sobre él la despliega. Es un paso de
  deshacer, no se puede dentro de un marco cerrado y los botones no salen en imágenes ni PDF.
- Los papeles son de colores vivos, como un post-it, también en el tema oscuro (amarillo `#FFF176` en el claro, `#F0D44A` en
  el oscuro); el texto y la esquina doblada se eligen según el brillo del papel y no según el tema.
- Pestaña «Notas» del panel lateral: lista las notas del circuito en orden de lectura, con su color, la primera línea o
  el avance de la lista («2/4», «✓ todo listo») y «(plegada)»; un clic o Enter en una te lleva a ella y la selecciona
  (y la despliega, salvo dentro de un marco cerrado).
- La IA (MCP) lee tus notas adhesivas, pega notas de texto o de tareas, marca tareas y cambia su texto; no toca las
  tuyas si no se lo pides, y las deja fuera de sus exportaciones salvo que pida incluirlas.

## Versión 0.4 — 2026-10-26

### Agregado

- Favoritos en la paleta: al pasar el mouse por un componente aparece una estrella; un clic la llena y copia el
  componente a «Favoritos», la primera sección de la paleta, sin empezar a colocarlo ni sacarlo de su categoría. Otro
  clic, o el menú del clic derecho, lo quita. Se comparten entre todas las ventanas y circuitos, incluyen los
  componentes propios y un componente propio que falta por un rato no pierde su estrella.
- Recientes en la paleta: justo debajo de «Favoritos», los últimos 5 componentes distintos que colocaste en la sesión
  (con un clic, arrastrándolos, con la inserción rápida o un componente propio), el más reciente arriba. Es una lista
  para todas las ventanas, no repite los favoritos y no aparece mientras está vacía.
- Inserción rápida: doble clic en un espacio vacío del lienzo, o Insert, abre «Insertar componente», un buscador que
  sin texto muestra los favoritos y los recientes y al escribir busca en toda la paleta, incluidos los componentes
  propios. Desde el doble clic coloca lo elegido en ese punto (un solo paso de deshacer); desde Insert empieza la
  colocación flotante.
- Textos de varias líneas en el editor de formas, escritos en el mismo editor en el lugar del editor principal: Mayús+Enter
  agrega una línea, Enter (o un clic fuera) lo da por listo y Esc lo cancela; cada línea conserva la alineación y el
  editor crece con las líneas.
- En el editor de formas, los pines se dibujan más grandes y, al pasar el mouse con Seleccionar, llevan un aro, el
  cursor de mover y su nombre («A · entrada»).
- En el editor de formas, un texto elegido se rodea con un rectángulo de todo su bloque (todas sus líneas, a su tamaño
  real); al pasar el mouse se perfila el bloque, y todo él es zona de clic.
- Colores en las formas con la paleta de las notas: «Letra» y «Borde» para los textos, «Relleno» (sin rojo) y «Línea»
  para las figuras; se adaptan al esquema, salen en blanco y negro al imprimir y se guardan como campos opcionales (los
  archivos sin colores no cambian).
- Un texto de una forma que queda vacío se quita, en el mismo paso de deshacer de la edición; un texto nuevo en el que
  no escribiste desaparece al salir.
- Texto del editor de formas con alineación «Derecha»: el punto donde hiciste clic es el borde derecho del texto.
- Editor de formas para los componentes propios («Cambiar forma…», clic derecho en la pieza o en la paleta): dibuja con líneas, rectángulos, elipses, triángulos, polígonos y textos, edita nodos y curvas, mueve los pines y cambia el tamaño de la caja y de la figura por separado. «Formas ▾» da puntos de partida (predeterminada, rectángulo, cuadrado, círculo, triángulo, trapecios ALU y MUX con su texto, AND, OR, XOR). El cambio vale para «Solo esta pieza», «Todas las piezas» o la librería.
- En el editor de formas, Mayús al mover limita el movimiento a horizontal o vertical; las flechas mueven la selección (Mayús+flecha, más lejos) y Supr la borra. Mover vista (H) y Agregar nodo (A) son herramientas; una muesca marca cada punto entero del borde de la caja y, al arrastrar un pin, se resaltan los puntos libres y los ocupados.
- En el editor de formas, «Quitar nodo» (Q) es una herramienta, como «Agregar nodo» (A): el nodo bajo el cursor se marca en rojo y un clic lo quita, sin elegirlo antes. El cursor lleva un «+» o un «−», y con Ctrl apretado cada una hace lo de la otra. Una figura no baja de 2 nodos (3 si es cerrada) y la línea de ayuda dice por qué.
- En el editor de formas, Multilínea (K) dibuja una línea abierta de varios tramos y Polígono (P) una figura siempre cerrada: un clic por nodo; doble clic, Enter o Esc terminan (Esc conserva los nodos puestos) y un clic en el primer nodo cierra la figura. Ctrl+Z mientras dibujas quita el último nodo. «Cerrar / Abrir figura» convierte una figura elegida de abierta a cerrada y al revés.
- En el editor de formas, «Curvar tramo» (C) reemplaza al botón «Recto / Curvo»: resalta el tramo bajo el cursor, un clic en un tramo recto lo curva y, al arrastrar la curva desde cualquier punto, ese punto sigue al cursor. Ctrl+clic lo deja recto.
- En el editor de formas, cada tramo curvo tiene dos manijas y los nodos son suaves o esquinas: con Nodos se ven las manijas, unidas a su nodo por una línea; en un nodo suave, arrastrar una manija gira la otra al lado opuesto con el mismo largo, y con Alt se mueve sola (el nodo queda en esquina). Doble clic en el nodo cambia entre suave y esquina. Los tramos curvos se guardan como curvas cúbicas (`C`); los archivos con curvas anteriores (`Q`) se abren igual.
- En el editor de formas, Ctrl mientras arrastras el control de la caja escala todo el dibujo con ella (y los pines, en la cuadrícula).
- Los cables siguen a los pines que cambian de lugar al cambiar la forma o al «Actualizar componente…» / «Actualizar desde la librería». Si un cable no puede seguir sin tocar otra red, una hoja lo explica y solo «Aplicar y conectar a mano» sigue adelante; los pines sueltos quedan marcados y en Problemas.
- Avisos de conexiones pendientes dentro de los componentes propios: la pieza rota por dentro brilla en rojo y Problemas da la ruta completa hasta el pin suelto («A2 › A1 › AND: la entrada «B» está desconectada»); el terminal exterior cuyo pin de interfaz está suelto por dentro se marca en rojo. Se actualizan al editar, deshacer y reabrir, y no impiden guardar ni simular.
- La IA (MCP) puede cambiar la forma de un componente propio, en una pieza o en todas: elige una de las formas predefinidas y la posición de los pines, en un solo paso de deshacer, y no lo hace si un cable no puede seguir a un pin sin tocar otra red. Al leer el circuito sabe qué piezas tienen forma propia.
- Cada componente propio guarda su versión: al abrir un circuito, Problemas avisa si una pieza usa una versión anterior que la librería cambió y que, al actualizarla, dejaría conexiones sueltas o no se podría actualizar por usarse dentro de otro componente (la línea lo dice). Nada se actualiza solo.

### Cambiado

- En el editor de formas, el zoom funciona como en el editor principal: va de 25 % a 400 % (antes llegaba a 600 %), Ctrl
  + rueda acerca o aleja un paso por muesca dejando quieto el punto bajo el cursor (antes se acercaba hacia la esquina
  de la caja) y los botones de zoom de la barra dejan quieto el centro del lienzo. El rectángulo que elige figuras al
  arrastrar en vacío se ve como el del editor principal: un relleno tenue del color de selección y un borde de hormigas
  en marcha.
- En el editor de formas, las manijas de todos los tramos curvos se ven siempre con Nodos y con «Curvar tramo» y se
  toman directamente: ya no hay que pasar el mouse por el tramo ni elegir antes el nodo. Al curvar un tramo con
  «Curvar tramo» sus manijas quedan a la vista y se arrastran en seguida; arrastrar la curva sigue funcionando. Un nodo
  agregado en una curva nace siempre suave, con sus dos manijas.
- En el editor de formas, un texto escrito se dibuja siempre con el tamaño elegido (6, 9 o 14), sin achicarse ni crecer
  con la figura o con lo largo del texto, y puede sobresalir de ella; solo el texto que sigue el nombre del componente
  se ajusta al espacio. El tamaño «Grande» pasa de 13 a 14.
- Un componente propio solo queda con los pines bloqueados (y su actualización se rechaza) si una pieza colocada usa a otro componente que lo contiene; una copia sobrante de ese otro componente en el circuito, que nada usa, ya no bloquea ni dice «usado dentro de…».
- Problemas nombra el pin entre comillas: «la salida «out» está desconectada» en lugar de «salida out desconectada».
- En el editor de formas, cambiar el tamaño de la caja (con su control o con Ancho y Alto) sigue una sola regla: lo que toca el borde de la caja se mueve con ese borde (el rectángulo crece con la caja, la punta de un triángulo sigue en el borde derecho, el punto de control de una curva acompaña a su nodo) y lo demás queda centrado, moviéndose la mitad del cambio. Los pines de arriba y de abajo también se mueven la mitad, redondeado para seguir en la cuadrícula; los de los lados siguen en su borde. Antes el dibujo quedaba fijo en la esquina superior izquierda.
- La IA (MCP) distingue un terminal exterior cuyo pin de interfaz está suelto por dentro (`INTERFACE_UNCONNECTED_INSIDE`) de un pin de compuerta suelto, y si pide mover un pin de un componente que se usa dentro de otro, VSim le dice dentro de cuál y que solo el dibujo puede cambiar.
- Los textos «ALU» y «MUX» de los trapecios (y los textos de un dibujo en general) se ven en negrita, y el nombre de un pin del borde superior o inferior ya no se pisa con el contorno ni con el nombre de otro pin: se achica y, si no cabe dentro de la caja, se dibuja fuera, junto a su pin (nunca se oculta).
- En el editor de formas, los textos de la forma predeterminada crecen con el zoom como los de las figuras; solo los trazos y las marcas de los pines mantienen un tamaño constante en pantalla.
- En el editor de formas, la forma predeterminada aparece desde el principio como figuras de verdad (el rectángulo y el nombre), que se eligen y se editan con el primer clic; ya no se vuelve de golpe un dibujo aparte al tocar un nodo, y la línea de ayuda ya no habla de «forma predeterminada» y «dibujo». Al aceptar, si sigue igual a la forma predeterminada para la caja final, se guarda como forma predeterminada y sigue sus cambios futuros.
- Al «Actualizar componente…», un pin nuevo se ubica en un punto libre del borde, nunca en una esquina; si no queda ninguno, la caja crece hacia abajo.
- «Actualizar componente…» deja en su lugar los pines que siguen existiendo (sus cables quedan conectados) y ubica los nuevos en un punto libre; con «Actualizar desde la librería», los cables siguen a los pines que se movieron (en 0.3 se volvían a ubicar todos los pines y los cables quedaban sueltos). Si el componente se usa dentro de otro y sus pines o su tamaño cambian, no se actualiza nada: se conserva la copia anterior y VSim dice dentro de cuál componente se usa. Tampoco se actualiza si un pin nuevo quedaría sobre un cable o un pin.

### Arreglado

- En el editor de formas, después de seleccionar con un recuadro, un clic con Polígono, Multilínea, Texto, Mover vista o
  los nodos ya no dibuja de nuevo el recuadro anterior.
- Un pin fuera del borde de la caja se marca con un aro ámbar en el lienzo, su fila de «Pines» explica en un aviso al pasar el mouse por qué (el cable tendrá que entrar al componente) y qué hacer (arrastrarlo al borde punteado), y el aviso bajo el lienzo ya no se corta: se parte en varias líneas. «Bits» queda alineado a la derecha con el margen del resto del panel y el aviso de un pin dice «1 bit» en singular.
- La línea de ayuda del editor de formas ya no se corta con varias figuras o un nodo elegidos: los textos son más cortos.
- El diálogo «Actualizar componente…» dice que los pines que ya existen conservan su lugar en la caja y que los nuevos van a un punto libre del borde.
- La hoja «Conexiones al cambiar la forma» pone sus botones a la derecha, como en el diseño.
- El triángulo de advertencia de un pin marcado en rojo se dibuja del lado de su patita, fuera de la pieza, y ya no tapa el nombre del pin.

## Versión 0.3 — 2026-10-24

### Agregado

- Esquema de colores «Placa» (Preferencias ▸ General ▸ Esquema de color): el circuito como una placa de circuito
  impreso, con fondo verde, cables de cobre en 0 y dorados en 1, contornos y textos blancos como la serigrafía,
  piezas negras como chips y la cuadrícula como vías de un verde apenas más claro. Todos los estados de los cables,
  los textos, los marcos, los túneles y los brillos se leen sobre el verde.
- En «Placa» los pines son dorados, como los contactos de una placa (las patas de las piezas siguen blancas). El
  editor de esquemas tiene un color nuevo, «Pines»: en los demás esquemas es el mismo color del borde de las piezas,
  así se ven igual que antes, y un esquema propio guardado antes lo toma de su borde.
- El estado de la IA donde miras: la barra de estado de cada ventana termina con un punto de color y «IA: apagada»,
  «IA: escuchando», «IA: conectada», «IA: trabajando» o «IA: error»; al pasar el mouse dice la dirección y un clic
  abre Herramientas ▸ «IA (MCP)…». Un aviso largo ya no lo saca de la barra (se corta, con el texto completo al pasar
  el mouse). Preferencias ▸ «IA» muestra arriba el mismo estado con Encender / Apagar y Reiniciar.
- Las notas pueden tener fondo, color de letra y un borde con su propio color, desde Propiedades («Fondo», «Letra»,
  «Borde») o el menú contextual «Estilo ▸», también para varias notas a la vez; por omisión se ven como siempre. Los
  colores se leen en todos los esquemas (también en «Placa»), salen al exportar e imprimir, y la IA puede usarlos en
  sus notas. Una nota con fondo del mismo color que el lienzo (negro en Twilight, blanco en Claro o al exportar con
  «Fondo blanco para imprimir») muestra su caja con una línea suave, y al pegarla o arrastrarla su vista previa es un
  contorno que deja ver lo que hay debajo.
- Ver ▸ «Ver todo el circuito» (Mayús+1, también con un botón en el control de zoom de la barra de estado) acerca o
  aleja la vista hasta que se ve el circuito entero, con un margen, y Ver ▸ «Zoom a la selección» (Mayús+2) hace lo
  mismo con lo seleccionado. Llegan con un viaje suave (o de un salto si «Animar los movimientos de la vista» está
  desactivado) y actúan sobre el panel activo.
- Herramienta «Zoom» (botón después de «Mover vista», Ver ▸ «Zoom» o la tecla Z): arrastra un recuadro y la vista
  viaja hasta que ese recuadro llena el panel; mientras arrastras, el recuadro dice el zoom al que llegará («250 %»).
  Un clic acerca un paso alrededor del punto, y Alt + clic, Mayús + clic o el botón derecho alejan un paso. El cursor
  es una lupa con «+» o, con Alt o Mayús apretada, con «−». Esc suelta el recuadro y, sin recuadro, vuelve a
  Selección. El recuadro nunca sale al exportar ni al imprimir.
- Control de zoom al final de la barra de estado: «−», un deslizador de 25 % a 400 % con 100 % al medio (cada vez
  que el zoom se duplica avanza lo mismo y cerca del medio se queda en 100 %), «+», el porcentaje, «100 %», «Ver todo
  el circuito» y «Zoom a la selección». El porcentaje se escribe («150», «62,5») o se elige de una lista y la vista
  llega con un viaje suave (en la lista, las flechas solo la recorren: el zoom cambia al elegir con clic o Enter); si
  no es un número se marca en rojo y Esc lo devuelve. Usar el deslizador o la lista con el mouse deja el teclado en
  el lienzo. Ver ▸ «Escribir el zoom…» lleva el teclado ahí. Muestra y mueve el panel activo; en una ventana angosta el deslizador se achica primero.

### Cambiado

- Volver a 100 % (Ver ▸ «Zoom 100 %», Ctrl+0 o el botón «100 %» de la barra de estado) ya no salta: viaja suave
  alrededor del centro de la vista, salvo que «Animar los movimientos de la vista» esté desactivado. Cuando la vista
  cambia de zoom hacia un lugar que está en pantalla, ese lugar ya no resbala hacia un lado: se siente como entrar en
  él. Mover la vista o hacer zoom a mano a mitad de camino lo detiene donde está.
- El porcentaje de la barra de estado ya no vuelve a 100 % con un clic: ahora se escribe o se elige, y «100 %» es
  un botón al lado. El zoom pasó al final de la barra, después de «IA: …».
- Los botones «Alejar», «Acercar» y «Centrar circuito» salieron de la barra de herramientas, que conserva solo la
  herramienta Zoom (la lupa, Z): el zoom se maneja desde el control de la barra de estado y desde el menú Ver, con los
  mismos atajos.

### Arreglado

- Un marco que arma la IA alrededor de una nota ahora la encierra entera, con todas sus líneas (antes una nota de
  varias líneas quedaba medio afuera), y también la etiqueta larga de un componente.
- El 0, 1 o Z de un interruptor se lee en todos los esquemas: sobre un color claro (el 1 de CSim, de Claro y de
  Oscuro, la Z verde del VSim original) va en negro en vez de blanco.
- «Apagar» o «Reiniciar» la IA mientras espera «Siguiente» le responde de inmediato que se detuvo: antes, a veces la
  respuesta se perdía al cerrar la conexión y Claude Code se quedaba esperando hasta agotar su tiempo.
- Encender la IA: si Claude Code pedía esperar «Siguiente» apenas el puerto respondía, a veces le contestaba que la IA
  estaba apagada. Al cerrar la última ventana, VSim ya no se queda unos segundos esperando a que terminen las
  respuestas de la IA.

## Versión 0.2 — 2026-10-22

### Cambiado

- Ícono nuevo de VSim 2 en la ventana y en la barra de tareas: un chip con una señal verde que dibuja una V.
- La carpeta `examples/` tiene ahora solo ejemplos para abrir y probar: contador de 4 bits con display, luz de
  pasillo (interruptor de escalera), semáforo, sumador de 4 bits con semisumador, y un circuito del VSim original.
  Los 14 ejemplos de Archivo ▸ Ejemplos siguen igual.
- README renovado: logo, de qué se trata VSim 2, una captura, cómo descargarlo y usarlo, y la historia (CSim, JCSim,
  VSim).

## Versión 0.1 — 2026-10-22

Primera versión publicada (jar en GitHub Releases). Incluye todo lo de abajo, hasta «IA.3: cierre de la IA».
Desde ahora cada versión sube de a 0.1, normalmente al terminar un hito.

## 2026-10-22 — Cierre de la IA (MCP)

### Agregado

- Comandos para aprender a usar la IA en Claude Code: `/mcp__vsim__guia`, `armar`, `exponer`, `explicar`,
  `revisar`, `probar`, `componente` y `exportar` redactan el pedido por ti (el texto después del comando es el
  pedido; el ritmo y el formato se reconocen en cualquier parte), y el recurso `vsim://ayuda` explica qué hace y qué
  no hace la IA, sus dos modos y los comandos.
- Un puerto solo para esta sesión: `-Dvsim.ai.port=<n>` o `VSIM_AI_PORT=<n>`. El panel y Preferencias ▸ «IA» dicen
  «Puerto <n> (solo esta sesión, desde la línea de comandos)», «Copiar configuración» lo registra como `vsim-<n>` y
  el puerto recordado no cambia.
- El panel de la IA, rehecho: el estado con un punto de color y una palabra (Apagada, Encendiendo…, Escuchando,
  Conectado, Trabajando, Error), la dirección, desde cuándo y cuántos pedidos; «Encender» / «Apagar» y «Reiniciar»;
  ante un error, qué hacer; las tareas con «Detener» y «Deshacer lo que hizo la IA»; la configuración plegada.
  Iconos (detener, deshacer de la IA, encender, reiniciar, portapapeles, carpeta, cerrar) en el panel, la barra de
  estado y la ventanita de «Presentar», en tema claro y oscuro.
- Subtítulos en «Presentar»: lo que la IA cuenta antes de una etapa o de un paso (`vsim_say`, o `narration` en
  `vsim_begin_stage` y `vsim_edit`) aparece abajo al centro del panel, nunca bajo la ventanita, y en gris cursiva en
  la tarjeta de la etapa; en «De corrido» va con su pausa. En modo trabajo queda en el globo de ayuda de «IA
  trabajando…».
- Preguntas de la IA en VSim (`vsim_ask_user`, `vsim_await_answer`, `vsim_withdraw_question`): en la ventanita de
  «Presentar», o en la barra de estado con «Responder…», con un botón por opción y una respuesta corta.
- Aviso «Claude está pensando o te preguntó algo en Claude Code» cuando la IA lleva 20 s sin hablar y nada espera por
  ti.
- Si Claude Code deja de responder con un paso esperando «Siguiente», VSim detiene la tarea a los 2 minutos y lo dice
  («La IA dejó de responder: se detuvo la tarea»); el paso en transparente se va.
- Notas de varias líneas: Mayús+Enter agrega una línea al editar una nota (en el lienzo y en Propiedades); se ven,
  seleccionan, buscan y exportan línea por línea, y la IA escribe una nota de varias líneas con `\n`.

### Cambiado

- «Activar» / «Desactivar» del panel de la IA ahora son «Encender» / «Apagar», y encender ya no congela la ventana
  (el puerto se abre en segundo plano mientras dice «Encendiendo…»).
- «De corrido» se detiene al final de una etapa solo si falló una comprobación de funcionamiento (tabla de verdad,
  señales o Problemas), no por un defecto de orden; cuando se detiene, la ventanita lo dice claro («Me detuve: falló
  la comprobación de «<etapa>» · Siguiente para seguir») y la tarjeta muestra la comprobación que falló.
- Los marcos de las etapas quedan alineados arriba aunque la IA empiece en otra fila, miden las etiquetas de las
  piezas y no se ponen dentro de otro marco; el primer lugar donde se arma una etapa queda una fila más abajo, para
  que quepan las etiquetas.
- Los marcos que pone VSim se deshacen como «Deshacer marco de etapa».
- Los comandos `/mcp__vsim__…` nunca fallan si el pedido llega cortado o vacío: la IA pregunta qué quieres.
- «Ajustes que tocó la IA» en una segunda ventana del circuito muestra los de su primera ventana.
- VSim le cuesta menos a la IA: las instrucciones van de 7 427 a 3 419 caracteres, las 31 herramientas tal como las
  lee la IA de 49 084 a 40 961 (`vsim_edit` de 14 782 a 10 371) y la respuesta típica de `vsim_edit` de 1 157 a 590:
  sin campos vacíos ni valores por omisión, y solo cuatro herramientas con un ejemplo.
- Si la IA te hizo una pregunta y lleva 2 minutos sin preguntar por tu respuesta, la tarjeta dice «Respóndele en
  Claude Code; la IA no ha preguntado por tu respuesta en N min» y la barra deja de decir «IA trabajando»; la tarea
  sigue (solo tú la detienes).

### Arreglado

- Guardar mientras un paso de la IA espera «Siguiente» ya no lo descarta.
- Una nota con saltos de línea (como la «Prueba: …» de la IA) se dibujaba como una sola línea gigante.
- Seleccionar una nota de varias líneas con Propiedades abierto la dejaba en una sola línea.
- Las instrucciones para la IA tenían líneas pegadas y un salto de línea donde debía ir `\n`.
- Claude Code corta las instrucciones de un servidor a 4 096 caracteres, así que la IA no leía las reglas de
  comprobación, de «Presentar» ni los límites (los textos del circuito son datos, nunca guarda archivos); ahora caben
  enteras, con lo esencial primero.
- «Apagar» y «Reiniciar» congelaban la ventana unos 2,5 s mientras la IA esperaba «Siguiente», y la IA recibía una
  conexión cortada en vez de «detenido».
- La frase que la IA manda con un cambio se mostraba aunque el cambio fuera rechazado.

## 2026-10-21 — Presentar lo que hace la IA (MCP)

### Agregado

- Ver ▸ «Presentar lo que hace la IA» (y su botón en la barra de herramientas): la IA arma el circuito paso a paso y
  a la vista. También se lo puedes pedir en el chat; la ventanita dice «La IA pasó a Presentar» y la IA no lo apaga
  por su cuenta durante una tarea.
- Una ventanita flotante con la tarea, «Etapa n / m», las etapas (las terminadas con «✓ …» o «✗ …» de su
  comprobación) y sus pasos, y los botones «Siguiente ▸» (Enter), «Hasta fin de etapa», «Detener», «Deshacer etapa»
  y, al final, solo «Deshacer etapa», «Dejar solo el resultado» y «×». Se arrastra por su agarradera «⋮⋮» (doble clic
  la vuelve arriba a la derecha; ⋯ ▸ «Mover a» cada esquina) y nunca sale del panel.
- «Paso a paso»: cada paso se ve en transparente con un globo que lo señala («Paso 4 de 6 · propuesto», el texto y
  el porqué) y no se aplica hasta «Siguiente»; si tocas el circuito mientras espera, se descarta y la IA lo vuelve a
  proponer. «De corrido»: los pasos van solos con una pausa (1 s por defecto, de 0,3 a 5 s). El ritmo se elige en la
  ventanita o en Ver ▸ «Ritmo de Presentar», y la IA puede pedir uno para su tarea (se devuelve al terminar).
- La vista sigue a la IA como una cámara: cada paso y cada etapa nueva quedan centrados en la parte del panel que deja
  libre la ventanita (de un salto con menos movimiento); si mueves la vista a mano, deja de seguir hasta la etapa
  siguiente. El globo nunca queda bajo la ventanita, sigue al zoom y se ve en cada panel de una vista dividida que
  muestra el cambio. En «De corrido» la ventanita dice «Siguiente paso en …». Nada de esto sale al exportar.
- Cada etapa se arma a la derecha de la anterior y al terminar queda en su propio marco con su título (bloqueado
  cuando se volvió componente: «cómo se hizo»); un marco nunca toma piezas tuyas. Cada paso y cada marco es un
  Ctrl+Z; «Dejar solo el resultado» deja solo la última etapa, con los componentes creados, en un solo Ctrl+Z.
- Para la IA: `vsim_get_presentation`, `vsim_set_presentation` (con ritmo y pausa opcionales para su tarea), `vsim_begin_stage` (el lugar libre donde armar la
  etapa) y `vsim_await_step` (espera «Siguiente»: aplicado, descartado o detenido); `vsim_edit` acepta el porqué de
  cada paso.

### Arreglado

- «Dejar solo el resultado» ya no rompe el circuito: solo se ofrece cuando cada etapa intermedia está en su propio
  marco y nada de ellas está conectado con lo que queda (antes, si la última etapa cableaba las piezas de la primera,
  las borraba); si no, VSim lo rechaza sin tocar nada.
- «Deshacer etapa» va etapa por etapa: cada vez deshace la última etapa que todavía tiene pasos (se salta una etapa
  que solo comprobó), dice en la ventanita qué hizo o dónde se detuvo y se apaga cuando no queda nada.
- Bloquear el marco de una etapa cuando la siguiente hizo un componente de ella es un paso de esa etapa siguiente:
  «Deshacer etapa» ya no se detiene ahí ni dice que fue un cambio tuyo.
- «Detener» deja la ventanita con «Deshacer etapa» y «×» («Detuviste a la IA») en vez de cerrarla.
- El ritmo que eliges en una segunda ventana del circuito gana sobre el que pidió la IA, y «Mantener» / «Volver a
  los míos» del panel de la IA ya no le quitan a la tarea el ritmo que pidió.
- «Siguiente paso en …» desaparece cuando ese paso no se aplicó porque cambiaste el circuito durante la pausa, y una
  guía de alineación de la IA ya no descarta el paso que espera «Siguiente».

## 2026-10-21 — La IA trabaja (MCP)

### Agregado

- Claude Code se puede conectar al VSim abierto (por MCP, solo en tu computador) y trabajar en tus ventanas: le
  pides «arma un flip-flop D», «conviértelo en componente», «prueba su tabla de verdad», «exporta a PDF» o «mira la
  ventana» y lo ves aparecer. Lee el catálogo, el circuito y sus Problemas; coloca, conecta por nombre de pin,
  etiqueta, mueve, borra, hace marcos, notas y guías, ordena cables, crea componentes e instancias; simula con un
  límite de eventos, calcula tablas de verdad en una copia y pide imágenes de una zona o de la ventana. Cada pedido se
  aplica entero o nada.
- Herramientas ▸ «IA (MCP)…»: «Activar» / «Desactivar» y el estado en palabras y color («Apagada», «Escuchando en
  127.0.0.1:47017», «Claude Code conectado», «IA trabajando: …»), los avisos de puerto ocupado («Usar el siguiente
  libre») o de otro VSim con la IA, «Ajustes que tocó la IA» y las propuestas de la IA con «Aceptar» / «No».
- Archivo ▸ Preferencias… ▸ «IA»: «Iniciar con VSim», el puerto (47017 por defecto), «Generar un secreto nuevo» y
  «Copiar configuración para Claude Code», que copia la línea `claude mcp add …` para ejecutar una vez. «Lo que la
  IA lee del circuito se envía a su proveedor».
- Cada cambio de la IA es un Ctrl+Z («Deshacer cambio de la IA») que no se junta con los tuyos; la barra de estado
  dice «IA trabajando: …» con «Detener» y «Deshacer lo que hizo la IA», que se detiene en tu primer cambio. Un cambio
  preparado antes de que tú editaras se rechaza sin aplicar nada.
- Al terminar una tarea, si lo que cambió no estaba a la vista, la vista viaja hasta ahí (de un salto con menos
  movimiento) y el aviso «La IA terminó: …» ofrece «Ver lo que hizo»; las guías que la IA puso solo para alinear se
  quitan solas, salvo que pidas conservarlas.
- La IA cambia los ajustes del editor (ajustar, cuadrícula, guías, cables que siguen) solo dentro de una tarea, en esa
  ventana y de forma temporal; tus preferencias solo las propone; exporta solo a las carpetas de exportar que VSim
  recuerda (o a `VSim` en tu carpeta personal), sin sobrescribir, y el aviso «La IA guardó …» tiene «Abrir carpeta»;
  nunca guarda, abre ni borra archivos.
- En las tablas de verdad de la IA el límite de eventos se reparte entre las filas: la fila de un latch que mantiene
  su estado ya no se come el límite de las demás.

### Agregado

- El lienzo de una ventana se divide en 2 paneles (arriba y abajo, o a los lados) o en 4, como en Excel: arrastrando
  la pestañita de arriba del borde derecho hacia abajo o la de la punta del borde de abajo hacia la izquierda, o
  desde Ventana ▸ «Dividir ▸» («Arriba y abajo», «A los lados», «Quitar división»). Cada panel muestra el mismo
  circuito con su propio zoom y centro; los divisores se arrastran (el cruce mueve los dos) y se quitan llevándolos
  al borde o con doble clic.
- El panel activo (el último donde hiciste clic) tiene un borde fino de color y Ventana ▸ «Siguiente panel» pasa al
  otro. La selección y la herramienta son las mismas en todos los paneles; el zoom, «Centrar circuito», la búsqueda,
  F8 y Nueva vista actúan en el panel activo, y los brillos, problemas, guías y marcas se ven en todos.
- La división se recuerda por archivo en las preferencias, nunca en el .vsim: al volver a abrir un circuito la
  ventana vuelve dividida como estaba, y dividir no lo deja «modificado» ni agrega un paso de deshacer.
- Lo que empiezas a arrastrar en un panel sigue en otro panel de la misma ventana: un cable (con Cable o desde un
  pin), un recuadro de selección, mover o copiar con Ctrl piezas, deslizar un tramo con Alt, mover una etiqueta,
  dibujar, mover (con su bloque si está bloqueado) o agrandar un marco. La punta sigue al puntero en el otro panel,
  con su zoom para «Ajustar»; la vista previa se ve en todos los paneles; al soltar es un solo paso de deshacer, y Esc
  lo cancela en todos.

## 2026-10-21 — Ajustar, guías y cuadrícula

### Agregado

- Ver ▸ «Cuadrícula ▸» dibuja detrás del circuito puntos (como siempre), líneas como papel cuadriculado (una fina
  cada 2 unidades y una más marcada cada 10) o nada. Vale para todas las ventanas y se recuerda; «Cambiar el estilo
  de la cuadrícula» pasa de uno a otro y acepta un atajo propio. Las líneas no salen en las imágenes ni en el PDF.
- Cada esquema de colores tiene un color nuevo, «Líneas de la cuadrícula», que se puede cambiar en el editor de
  esquemas; los esquemas propios ya guardados lo toman de sus puntos.
- Ver ▸ «Cuadrícula ▸ Separación ▸» elige cada cuántas unidades va una línea de la cuadrícula: 2 (como antes), 4, 5
  o 10, con una más marcada cada cinco. «Ajustar a ▸ Líneas de la cuadrícula» usa la misma separación, así el ajuste
  se nota más. Es una preferencia del editor: el circuito y la cuadrícula de pines y cables no cambian.
- Reglas arriba y a la izquierda del lienzo: arrastrando desde ellas salen guías horizontales y verticales (líneas
  cian discontinuas) que se mueven arrastrándolas y se quitan devolviéndolas a su regla. También desde el menú
  contextual («Agregar guía … aquí», «Quitar guía»), desde Ver («Mostrar guías», «Agregar guía horizontal/vertical»,
  «Borrar guías») y desde la esquina de las reglas. Las guías se guardan con el circuito, se ven en todas sus vistas,
  se deshacen en un paso y no salen en las imágenes ni en el PDF.
- «Ajustar» (el imán de la barra, Ver ▸ «Ajustar», Ctrl+Mayús+M): al mover, pegar, colocar o soltar piezas, sus
  bordes y centros saltan a alinearse con los de las piezas vecinas y con las guías, y una línea magenta muestra cada
  alineación mientras arrastras. Ver ▸ «Ajustar a ▸» elige piezas, guías y líneas de la cuadrícula (con la
  separación elegida, siempre a la línea más cercana). Apagado, todo se mueve como antes.
- Con «Ajustar», el inicio y la punta de un cable que dibujas y las esquinas de un marco nuevo caen en las guías (o en
  las líneas de la cuadrícula) cercanas, salvo cuando ya están sobre un pin o un cable: la conexión manda. La guía a la
  que se ajustó lo que arrastras se ve más marcada mientras arrastras.
- Mientras mueves, copias con Ctrl, pegas o colocas piezas, un punto rojo marca cada lugar donde un pin o la punta
  de un cable quedaría tocando un cable o un pin de otra red, con imán o sin él y sigan o no los cables. Al lado
  de los 4 puntos más cercanos al puntero se lee el nombre del pin que llega y el del pin que toca («Cin → S»),
  sin tener que acercar el puntero. Al soltar no pasa nada nuevo: es solo un aviso en la vista previa, y no sale
  en las imágenes ni en el PDF.

### Cambiado

- Un marco nuevo dibujado hacia arriba o hacia la izquierda y más chico que el mínimo (6 × 4) crece desde la esquina
  donde empezaste, hacia donde arrastraste, en vez de pasarse al otro lado de ella.

## 2026-10-21 — Tareas cortas

### Agregado

- Los archivos de VSim 2 (circuitos, componentes de la librería y el portapapeles) dicen qué son y con qué versión se
  guardaron (`"format" : "vsim2"`, `"savedWith"`). Los archivos anteriores se siguen abriendo.

### Cambiado

- El color de un LED y el de un marco se eligen con la misma fila de muestras que la entrada de un túnel, cada una
  con su nombre al pasar el mouse; «Sin color» es la muestra tachada. Las muestras usan los colores del esquema
  elegido en Preferencias, como el lienzo.
- El anillo de la muestra elegida es azul (oscuro en el tema claro, claro en el oscuro) y se distingue del fondo en
  todos los temas. Con el teclado la fila es una sola parada de Tab, las flechas pasan de una muestra a otra,
  Espacio elige y Enter elige y acepta «Configurar…»; los lectores de pantalla anuncian la fila como un grupo.
- Abrir un archivo de una versión más nueva de VSim 2 dice «Este archivo es de una versión más nueva de VSim 2
  (formato N). Actualiza VSim para abrirlo.», y abrir un JSON que no es de VSim dice «Este archivo no es de VSim.»,
  en vez de un error técnico.

### Arreglado

- Deslizar dos dedos hacia los lados en un touchpad (en Linux) mueve el circuito hacia los lados. Antes no lo movía y
  además llegaba al lienzo como un clic izquierdo, que podía quitar la selección.
- Los mensajes de error del asistente de Karnaugh, de la importación de tablas y de las expresiones contrastaban
  poco con el fondo (3,7:1 en el tema claro, 4,2:1 en el oscuro): ahora van en un rojo con contraste de al menos
  4,5:1 en los dos temas.
- Los demás textos de error (el valor no válido de «Configurar…» y de Propiedades, los cortocircuitos y la falla de
  la simulación en la barra de estado) usan ese mismo rojo legible: en el tema oscuro se leían apenas (1,4:1), y al
  cambiar de tema con la ventana abierta conservaban el rojo del tema anterior.
- La vista previa de un túnel de salida que estás poniendo o pegando muestra el color de la entrada de su nombre,
  como se verá al soltarlo, y no siempre el primer color.

## 2026-10-19 — 2026-10-20 — Exportar a PDF

### Agregado

- Archivo ▸ «Exportar a PDF…» guarda un PDF vectorial de todo el circuito, de la selección o de un marco, en A4,
  Carta, Oficio o A3, vertical u horizontal.
- «Ajustar a una hoja» lo achica a una sola hoja (avisa si el texto queda muy chico y ofrece «Dividir en varias
  hojas»); con un porcentaje se reparte en varias hojas que se solapan 1 cm, con marcas de corte y «Fila · Columna —
  Hoja n de N» para pegarlas.
- «Blanco y negro» para imprimir en láser (marcos sin relleno, estados de los cables por gris y trazo, con leyenda;
  un LED encendido va relleno y uno apagado solo con su contorno), «Con valores» y «Encabezado y pie de página» con
  título, archivo, fecha y número de hoja.
- Vista previa igual al PDF, con un mapa de hojas y la hoja elegida en grande; el diálogo cuenta las hojas, pide
  confirmación sobre 50 y no deja pasar de 1.000.
- Se escribe sin congelar la ventana, con barra de avance y «Cancelar»; un PDF anterior con el mismo nombre no se
  pierde si cancelas o falla. Las opciones y la carpeta se recuerdan solo al guardar.

## 2026-10-20 — Túneles de cables

### Agregado

- «Túnel (entrada)» y «Túnel (salida)» en Entrada/Salida: unen cables o buses por nombre, sin dibujar el cable y sin
  retardo. Cada nombre tiene una entrada y las salidas que quieras.
- El color lo pone la entrada, con una fila de 8 muestras en Propiedades; las salidas lo toman solas y una salida sin
  entrada se ve gris. Nombres y colores nuevos se proponen solos (T1, T2…; el primer color libre).
- El nombre se edita en el lugar; renombrar una entrada renombra sus salidas en un solo paso de deshacer, y una salida
  ofrece los nombres que tienen entrada.
- Al pasar el mouse se marcan todos los túneles del mismo nombre y la barra de estado cuenta entradas y salidas; el
  menú contextual tiene «Ir a la entrada» y «Siguiente salida».
- La pestaña Problemas avisa de entradas repetidas, salidas sin entrada, entradas sin salidas y salidas que reciben
  señal por su propio cable. «Crear componente» y «Desarmar» cuidan que los nombres no se mezclen con los de afuera.
- La búsqueda encuentra los túneles por su nombre.

### Cambiado

- La salida tiene el pin a la derecha (la entrada lo tiene a la izquierda), así se distinguen de un vistazo.
- El rojo es el primer color de los túneles, y los colores se nombran en el orden de la fila de muestras.
- La muestra de color elegida se marca con un anillo bien visible.

## 2026-10-19 — 2026-10-20 — Modo práctica de Karnaugh

### Agregado

- Herramientas ▸ «Practicar Karnaugh…»: el asistente en modo práctica, donde el alumno dibuja sus propios grupos en
  el paso 3 (con el mouse o el teclado) y VSim los revisa en vivo: grupos que no son grupos, que pueden crecer o que
  sobran, y unos que faltan por agrupar.
- «Pista», «Revisar» (mínima, correcta pero no mínima, o no equivalente, con la solución de VSim al lado) y «Me rindo ·
  Ver la solución».
- «Usar mis grupos» en el paso 4: la expresión, el costo y el circuito que se crea salen de los grupos del alumno.
- Las pestañas de cada salida dicen si está lista, pendiente o rendida, y antes de pasar al paso 4 VSim pregunta si
  quedan mapas sin terminar.
- El grupo elegido se marca con el borde animado de la selección.
- En el asistente, un clic en un grupo deja fija su explicación mientras se lee «¿Por qué?».

## 2026-10-19 — Circuito desde expresiones

### Agregado

- Herramientas ▸ «Circuito desde expresiones…»: una salida por línea (`F = A·B + C'`), revisada mientras se escribe,
  con la lista de entradas encontradas y los problemas del conjunto.
- Notación amplia (`'`, `¬`, `!`, `~`, `·`, `*`, `&`, espacio, `+`, `|`, `^`, `⊕`, paréntesis, 0 y 1) y nombres de
  varias letras (`Cin`, `carry_in`), con un aviso cuando `AB` parece querer decir `A·B`.
- Panel lateral con la notación y botones que escriben cada operador.
- «Simplificar» como en el asistente de Karnaugh, o armar el circuito tal como está escrito.
- «Ver en Karnaugh…» abre el asistente con la tabla de las expresiones, sin cerrar la ventana.

### Cambiado

- Las ventanas que crean circuitos se cierran solas después de crear con éxito, y el resultado aparece en la barra
  de estado de la ventana que recibió el circuito.
- Las compuertas generadas reciben sus entradas de arriba hacia abajo.

## 2026-10-19 — Buscar en el circuito

### Agregado

- Editar ▸ «Buscar…» (Ctrl+F): una barra bajo la barra de herramientas que busca etiquetas, tipos, textos, títulos
  de marcos y pines mientras escribes, también dentro de los componentes propios, con «3 de 7», F3 / Mayús+F3 y
  opciones de mayúsculas y expresión regular.
- Un campo «Filtrar componentes» en la pestaña Componentes, con las mismas reglas.
- La vista viaja suave hacia lo que VSim muestra (árbol, problemas, formas de onda, búsqueda); se puede desactivar
  en Preferencias.

### Arreglado

- Una expresión regular que tardaría demasiado se abandona en vez de congelar la ventana.

## 2026-10-18 — 2026-10-19 — Circuito desde tabla de verdad

### Agregado

- Herramientas ▸ «Circuito desde tabla de verdad…»: un asistente en cinco pasos (tabla, mapa de Karnaugh, grupos,
  expresión y crear) que explica cada grupo y arma el circuito, en un marco cerrado, en un circuito nuevo o en el
  actual, y si se quiere también como componente.
- Las celdas X de un grupo se explican como 1 o 0; cada mapa usa colores distintos y tiene ayuda de teclado.
- Archivo ▸ Importar ▸ «Tabla de verdad desde Excel/CSV…»: lee `.xlsx`, `.csv` o celdas pegadas, de hasta 10
  entradas y 16 salidas, adivina qué columnas son entradas, acepta coma decimal y muestra la tabla tal como la leyó,
  con los problemas en rojo.
- La importación sugiere el asistente de Karnaugh cuando la tabla cabe en él, y la ventana «Tabla de verdad…» puede
  abrir la tabla calculada en el asistente.

### Cambiado

- «Importar componente…» pasó al submenú Archivo ▸ Importar (como «Componente…»).
- El selector de archivos de la importación parte en la misma carpeta que Archivo ▸ Abrir.

### Arreglado

- Cerrar el asistente o la importación mientras se arma el circuito cancela la creación sin dejar nada a medias.

## 2026-10-18 — Piezas de prueba al crear componentes

### Agregado

- «Crear componente desde selección…» convierte en pines los interruptores, constantes, relojes, LED, displays y
  diagramas de tiempo marcados, dice qué pin da cada uno y los deja conectados afuera para seguir probando.
- Los pines se pueden renombrar en el mismo diálogo.

### Arreglado

- Esc cancela la colocación de un componente aunque el foco esté en la paleta o en otra parte de la ventana.

## 2026-10-17 — 2026-10-18 — Bloques

### Agregado

- Sección «Bloques» en la paleta: sumador, ALU, comparador, multiplexor, decodificador, registro y contador de N bits.
- «Decimal con signo» en «Mostrar como», también para la Constante (acepta valores negativos).
- Los pines de entrada y salida de varios bits muestran su valor en una caja, y el de un pin de entrada se escribe
  con doble clic.
- Los displays de 7 segmentos tienen una entrada de bus.
- Dos ejemplos nuevos hechos con bloques: «Mini ALU» y «Acumulador».

### Cambiado

- Los bloques y sus ejemplos parten en 8 bits.

## 2026-10-16 — 2026-10-17 — Pulido del editor

### Agregado

- Doble clic en un cable selecciona su tramo; triple clic, todos los cables conectados.
- La etiqueta de un componente se puede arrastrar y volver a su lugar automático.
- Preferencias ▸ «Letra de etiquetas y textos» elige la letra y el tamaño.
- Esquemas de colores propios, con un editor y vista previa en vivo.
- La pestaña Componentes muestra el ícono de cada componente y los marcos como carpetas.
- Con Mayús o Ctrl, el cursor dice si el clic agrega o quita, y Ctrl+Mayús dibuja un rectángulo que quita de la
  selección.

### Cambiado

- Propiedades muestra todo en las mismas dos columnas.
- Una racha de flechas que vuelve al punto de partida no deja paso de deshacer.
- La vista previa de un arrastre cambia al instante al apretar o soltar Mayús, Ctrl o Alt.
- El campo de una etiqueta girada crece hacia la izquierda; cerrar la ventana confirma un texto a medio editar.

### Arreglado

- Un cable movido sigue seleccionado cuando se funde con otro.
- La repetición de una tecla mantenida ya no confunde un arrastre.

## 2026-10-16 — Cables que siguen al mover

### Agregado

- Al mover una selección, los cables conectados se estiran y no se pierde ninguna conexión; la vista previa marca en
  rojo los contactos nuevos y la barra de estado los cuenta.
- El botón «Los cables siguen al mover» y una tecla (Mayús por defecto) para invertirlo durante un arrastre.
- Alt + arrastrar un cable seleccionado lo desplaza de costado.

### Cambiado

- El borde de un marco cerrado no deja sacar lo que tiene dentro.

## 2026-10-16 — Edición de texto en el lugar

### Agregado

- Etiquetas, textos libres, títulos de marcos y el valor de una Constante se editan justo donde están dibujados, con ✓
  y ✗, en un solo paso de deshacer.
- Las flechas mueven la selección un cuadro (Mayús+flecha, cinco), en un solo paso de deshacer por racha.

### Cambiado

- Esc deja la herramienta «Mover vista».
- Los errores de un campo se ven en blanco sobre rojo, legibles en cualquier tema.

## 2026-10-15 — Constante

### Agregado

- Componente «Constante»: un valor fijo de 1 a 32 bits, escrito en binario, hexadecimal o decimal, que se valida
  antes de aplicarse y nunca se trunca en silencio.

## 2026-10-14 — 2026-10-15 — Marcos

### Agregado

- Marcos con título y color alrededor de una parte del circuito: Ctrl+G sobre la selección o la herramienta «Marco».
- Un marco cerrado (candado) mueve, copia, rota y borra su bloque completo; uno abierto se puede redimensionar.
- Menú del marco con «Cambiar título…», «Color», candado, «Convertir en componente…» y «Quitar marco»; campos X, Y,
  Ancho y Alto en Propiedades.
- El ejemplo «ALU de 4 bits» trae marcos.

### Arreglado

- Centrar el circuito y exportar imágenes cuentan las etiquetas de los componentes, que antes podían quedar cortadas.

## 2026-10-14 — Exportar imagen y ordenar cables

### Agregado

- Archivo ▸ «Exportar imagen…» guarda lo seleccionado o todo el circuito en PNG (1×, 2× o 4×) o SVG, con fondo blanco
  para imprimir.
- Editar ▸ «Ordenar cables» (Ctrl+Alt+L) y «Ordenar cables…» con opciones: borrar puntas sueltas, unir tramos
  alineados y aplanar vueltas en U, sin cambiar nunca las conexiones y en segundo plano, con «Cancelar».

### Cambiado

- «Guardar» se desactiva cuando no hay nada que guardar.

## 2026-10-10 — 2026-10-13 — Ejemplos, tabla de verdad y paso a paso

### Agregado

- Archivo ▸ Ejemplos con doce circuitos clásicos que se explican solos, abiertos como copias sin nombre.
- Simulación ▸ «Tabla de verdad…» de lo seleccionado, de todo el circuito o de un componente propio, con copia, CSV,
  la fila actual marcada y doble clic para probar una fila.
- Simulación ▸ «Paso a paso»: avanzar al próximo evento (F7) o al próximo flanco de reloj (Mayús+F7), con brillo en los
  cables que cambiaron y un contorno en lo que cambia a continuación.
- Simulación ▸ «Reiniciar» (Ctrl+F5) vuelve a t = 0, y el tamaño de «Avanzar 10 t» se puede elegir.
- F5 inicia o pausa la simulación.
- Un corte en el cable vertical donde dos cables se cruzan sin unirse.
- Un anillo hueco en cada entrada sin conectar, y un globo con el nombre, el bit, el ancho y el valor de cada pin.
- La barra de formas de onda muestra íconos y la escala; los nombres de los canales siguen a las etiquetas, y el
  lienzo marca lo que está en formas de onda.
- El tooltip de cada botón termina con su atajo; un clic en el zoom de la barra de estado vuelve a 100 %.

### Cambiado

- Los flip-flops D parten en Q = 0.
- «Paso» pasó a llamarse «Avanzar 10 t», y retardos y tiempos dicen que se miden en unidades de tiempo «(t)».

## 2026-10-09 — 2026-10-10 — Formas de onda, sesión y atajos

### Agregado

- Ver ▸ Formas de onda: un panel tipo analizador lógico con un canal por red, cursor, zoom, «Seguir» y «Ver
  retardos»; los canales se guardan con el circuito.
- Copias de recuperación cada minuto y un diálogo para recuperarlas si VSim no se cerró bien.
- Archivo ▸ Recientes, y cada circuito vuelve a abrir con el tamaño, la posición, el zoom y el centro de su ventana.
- Continuar donde quedaste: al iniciar se vuelven a abrir los circuitos de la última sesión.
- Preferencias ▸ «Atajos de teclado» para cambiar cualquier atajo.
- «Nueva vista» numera las vistas y abre al lado con el mismo zoom y centro.
- El cursor dice qué hará un clic.

### Arreglado

- VSim sigue iniciando aunque un circuito no se pueda abrir.
- Se recupera solo la copia más nueva de un archivo.
- Una ventana guardada fuera de la pantalla vuelve a quedar a la vista.

## 2026-10-09 — Buses

### Agregado

- Buses de varios bits: cable grueso con su valor en hexadecimal (y en binario en el globo), «Separador de bus» y
  «Unión de bus».
- «Pin de entrada» y «Pin de salida» de 1 a 64 bits, para componentes propios con buses.
- Unir pines de anchos distintos se marca como problema.
- Esquemas de colores Pastel, CSim y Twilight del VSim original.
- LED naranjo, blanco, morado y cian, con un aspecto tenue cuando están apagados.
- Ayuda sobre los pines de componente en la paleta y en el diálogo de crear componente.

### Cambiado

- Los cortocircuitos y anchos incompatibles pulsan con un brillo rojo suave.
- Los pines de bus se marcan con un cuadrado y los sin conectar con un anillo hueco.

## 2026-10-09 — Componentes propios, temas y preferencias

### Agregado

- Componentes propios: «Crear componente desde selección…», una librería en una carpeta que VSim vigila, «Mis
  componentes» en la paleta, renombrar, exportar, importar, «Desarmar» y «Actualizar componente…».
- Cada circuito guarda una copia de los componentes que usa.
- Pestaña Componentes con el árbol del circuito y los valores en vivo.
- Archivo ▸ Preferencias…: tema claro, oscuro o del sistema, esquema de color del circuito, idioma y carpeta de la
  librería.
- Íconos en menús, barra y menú contextual, y un ícono propio para la ventana.
- Arrastrar la selección a otra vista (mueve) o a otro circuito (copia), con Ctrl para copiar; la vista se desplaza
  sola cerca del borde.
- Jar ejecutable con todas las dependencias.

### Cambiado

- Los textos de los componentes se leen derechos en cualquier rotación.
- El display de 7 segmentos tiene segmentos biselados y un guion para un dígito indefinido; el diagrama de tiempo se
  dibuja como un osciloscopio.
- Los puntos de unión aparecen solo donde un cable se ramifica.

## 2026-10-08 — 2026-10-09 — Selección, portapapeles y ventanas

### Agregado

- Selección múltiple con Mayús/Ctrl y con rectángulo; rotar y borrar un grupo en un paso.
- Cortar, copiar, pegar (flotando bajo el mouse) y duplicar, también entre circuitos.
- Etiquetas de componentes, textos libres y la pestaña Propiedades.
- «Borrar tramo» de un cable.
- Varias vistas de un circuito, el menú Ventana y abrir archivos soltándolos sobre la ventana.
- Pestaña Problemas, navegación de cortocircuitos con F8 y «Pausar al detectar cortocircuito».
- Los contornos de selección se animan como hormigas.

### Arreglado

- El diálogo «Configurar…» queda abierto ante un valor no válido.

## 2026-10-08 — Editor

### Agregado

- El editor: paleta con búsqueda, compuertas con formas IEEE, colocar, mover y configurar componentes, dibujar
  cables, zoom, menús, barra de herramientas, barra de estado y archivos, en español e inglés.
- Dibujar cables arrastrando desde un pin o un cable, colocar un componente por clic y arrastrar desde la paleta.

### Arreglado

- Deshacer y rehacer en el buscador de la paleta ya no deshacen el circuito.
- Un cable ya no tiembla al empezar a arrastrarlo y el anillo de un pin no queda marcado al salir del lienzo.

## 2026-10-08 — Núcleo de simulación

### Agregado

- Simulación por eventos con valores de varios bits (0, 1, Z y conflicto), compuertas, flip-flops, relojes,
  interruptores, LED y displays.
- Formato de archivo propio y apertura de circuitos del VSim original.
- Demo de consola: `./gradlew :core:run`.
