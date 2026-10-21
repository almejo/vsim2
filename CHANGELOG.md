# Cambios

Los cambios de VSim 2 que se notan al usarlo, del más nuevo al más antiguo. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). VSim 2 todavía no tiene versiones publicadas: cada
sección es un conjunto de funciones, con las fechas en que se hizo. El detalle de cada función está en
[docs/funciones.md](docs/funciones.md).

## 2026-10-21 — Tareas cortas

### Cambiado

- El color de un LED y el de un marco se eligen con la misma fila de muestras que la entrada de un túnel, cada una
  con su nombre al pasar el mouse; «Sin color» es la muestra tachada.
- El anillo de la muestra elegida usa el color de acento del tema y se ve bien también en el tema claro y en el
  oscuro; las flechas pasan de una muestra a otra y Enter o Espacio elige.

### Arreglado

- Los mensajes de error del asistente de Karnaugh, de la importación de tablas y de las expresiones contrastaban
  poco con el fondo (3,7:1 en el tema claro, 4,2:1 en el oscuro): ahora van en un rojo con contraste de al menos
  4,5:1 en los dos temas.
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
