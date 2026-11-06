# Cambios

Los cambios de VSim 2 que se notan al usarlo, del más nuevo al más antiguo. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones publicadas están en
[GitHub Releases](https://github.com/almejo/vsim2/releases); antes de la 0.1, cada sección es un conjunto de
funciones, con las fechas en que se hizo. El detalle de cada función está en
[docs/funciones.md](docs/funciones.md). El primer párrafo de cada versión publicada (bajo su título, antes de
«Agregado») es su «Resumen» de 2 a 4 líneas: es el texto que `packaging/release-notes.sh` pone en la página de la release.

## Sin publicar

Pestañas y editar por dentro: varios circuitos en pestañas agrupadas por circuito, entrar a un componente propio para editarlo en su pestaña, con una barra de lugar, un árbol del circuito, «Mirar adentro» y Ver la pieza; lo que se cambia adentro cambia el componente en todo el circuito.

### Agregado

- Pestañas: una ventana tiene varios circuitos en pestañas, agrupadas por circuito con su color. Abrir, Recientes, Ejemplos, Nuevo o soltar un archivo lo abren como pestaña en la ventana; Ctrl+Tab y Ctrl+Mayús+Tab las recorren, Ctrl+W cierra la pestaña y Ctrl+Mayús+W la ventana. Arrastrar una pestaña lleva su grupo entero a otro lugar de la fila, a otra ventana o a una ventana nueva; un doble clic en la pestaña del circuito pliega su grupo. Ventana ▸ «Nueva ventana con este circuito» (Ctrl+Mayús+N) hace lo que antes hacía «Nueva vista». Ver [Ventanas y pestañas](docs/funciones/interfaz-preferencias-y-sesion.md#ventanas-y-pestañas).
- Entrar a un componente propio: un doble clic en su pieza, el botón «Entrar ↘» que aparece al pasar el mouse, «Entrar» en el clic derecho o Enter con la pieza seleccionada abren el componente en una pestaña del grupo de su circuito. La pestaña muestra los valores de esa pieza mientras simula, y lo que cambias adentro cambia el componente en todo el circuito, en un solo paso de deshacer. Entrar de nuevo trae la misma pestaña con la pieza nueva. Si el componente sale del circuito, sus pestañas se cierran con un aviso. Ver [Entrar a un componente](docs/funciones/pestanas-y-componentes.md#entrar-a-un-componente).
- Barra de lugar en las pestañas de componente: la ruta («circuito › Ram › Selector 2») con cada paso clicable, «Valores de» para elegir qué pieza se simula (o «Sin valores»), cuántos lugares usan el componente, «Ver la pieza ↗» y «Guardar en mi librería» / «Agregar a mi librería». Un cambio que no se pudo hacer adentro se explica ahí y en la barra de estado. Ver [La barra de lugar](docs/funciones/pestanas-y-componentes.md#la-barra-de-lugar).
- Árbol del circuito arriba de la paleta: todas las piezas del circuito, con el ícono de la paleta, y las de adentro de cada componente propio a cualquier profundidad. Un clic lleva a la pieza (primero a su pestaña, abriendo la del componente con los valores de esa pieza si hace falta), un doble clic o Enter entra a un componente, y el clic derecho ofrece «Entrar», «Ver la pieza», «Cambiar forma…» y «Propiedades». Un buscador encuentra piezas en todo el circuito, con su ruta. El árbol sigue a la pestaña activa y marca su lugar con el color del grupo. El árbol y la paleta se pliegan como acordeón (con un clic en su cabecera, Alt+1 y Alt+2, o Ver ▸ «Árbol del circuito» y «Paleta») y lo plegado se recuerda por ventana. Ver [Árbol del circuito](docs/funciones/pestanas-y-componentes.md#árbol-del-circuito).
- Reglas adentro de un componente: la paleta pone en gris el mismo componente y los que lo contienen, diciendo por qué; los pines del componente toman su valor de afuera; una pestaña sin pieza que mostrar dice «Sin valores»; un cambio que no se puede hacer adentro no cambia nada y la barra de lugar y la barra de estado dicen por qué. Todas las pestañas de un circuito muestran los mismos Problemas, con el camino de cada uno, y F8 entra al componente donde está el problema; una pieza cuyo pin nuevo no cabía en su lugar aparece en Problemas. Los pines de un componente que está dentro de otro (el «Medio sumador» del «Sumador completo», la ALU del LRV32IM) se cambian desde adentro como los de cualquiera: las piezas de adentro de los otros componentes toman el pin nuevo y sus cables lo siguen, en el mismo paso de deshacer, y lo que no puede seguir aparece en Problemas con su camino («Sumador completo › Medio sumador 2: pin b sin conectar»). Deshacer y Rehacer dicen dónde fue el paso («Deshacer agregar (en Ram)»). Las memorias, el Mapa de memoria y las ventanas de Terminal siguen a la pieza que muestra la pestaña. Ver [Adentro de un componente](docs/funciones/pestanas-y-componentes.md#adentro-de-un-componente-qué-cambia).
- Ir a… en la barra de búsqueda (Ctrl+F): bajo el campo ofrece las pestañas abiertas y los componentes del circuito cuyo nombre coincide («⬚ Muldiv — componente, 3 usos»); un clic activa la pestaña o entra al componente con los valores de su primer uso, y Ctrl+Intro (o Intro sin coincidencias en el circuito) va al primero. Ver [Ir a…](docs/funciones/pestanas-y-componentes.md#ir-a-desde-la-barra-de-búsqueda).
- Mirar adentro: el botón del ojo de la barra (o Ver ▸ «Mirar adentro») hace que, al pasar el mouse sobre una pieza de un componente propio, se destaque con un contorno y un panel flotante crezca desde ella mostrando su interior, con los valores de esa pieza. También con Alt apretada, o con «Mirar adentro» en el clic derecho, sin activar el modo. El panel se encoge de vuelta al sacar el mouse o con Esc. Ver [Mirar adentro](docs/funciones/pestanas-y-componentes.md#mirar-adentro-ver-el-interior-sin-entrar).
- IA por dentro de un componente: `vsim_list_places` lista el circuito y cada componente propio con sus usos, `vsim_open_place` abre el lugar en la ventana de la IA, y `vsim_read_circuit`, `vsim_edit`, `vsim_read_signals`, `vsim_set_inputs`, `vsim_read_memory` y `vsim_picture` aceptan un `place` (el componente y el camino de una pieza): leen el cuerpo con los valores y la memoria de esa pieza, y un cambio adentro es un paso de deshacer y cambia el componente en todo el circuito. Sin `place` siguen actuando sobre el circuito, sea cual sea la pestaña a la vista. Ver [IA (MCP)](docs/funciones/ia-mcp.md#qué-puede-hacer-la-ia).

- Velocidad ×1000 en el selector de la barra, entre ×100 y «Máxima»: un circuito grande, como el LRV32IM importado de Logisim, corre lo bastante rápido para que su Terminal escriba de a poco. Ver [Interfaz, preferencias y sesión](docs/funciones/interfaz-preferencias-y-sesion.md).

### Cambiado

- Cada pieza de un componente tiene su propio contenido de memoria: en la pestaña de un componente, el editor de una RAM o una ROM (y su «Importar…») cambia solo la pieza de «Valores de» («Contenido de RAM1 de Memoria chica — solo Memoria 1»); antes cambiaba el componente y con él todas sus piezas. «Usar para todas las piezas» lo pasa al componente y «Volver al del componente» lo quita; con «Sin valores» se edita el del componente. Cada pieza parte de su contenido al simular y con «Reiniciar». Los circuitos se guardan en el formato 6, que VSim 0.6 no abre (lo dice al intentarlo); los archivos anteriores se abren igual. Ver [Adentro de un componente](docs/funciones/pestanas-y-componentes.md#adentro-de-un-componente-qué-cambia).
- Continuar donde quedaste recuerda cada ventana con sus pestañas: el orden de los grupos, el zoom de cada pestaña, la activa, los grupos plegados y, en una pestaña de componente, la pieza cuyos valores mostraba; una pestaña de componente recuerda su propia división de la vista. Un componente que ya no está en su circuito se avisa junto con los archivos que faltan. Ver [Sesión y recuperación](docs/funciones/interfaz-preferencias-y-sesion.md#sesión-y-recuperación).
- «Crear componente desde selección…» y «Convertir en componente…» de un marco llevan adentro del componente los marcos de la selección, en el mismo lugar (antes se perdían); las notas adhesivas se siguen quedando en el lienzo. Ver [Componentes propios](docs/funciones/componentes.md#componentes-propios).
- El doble clic en una pieza de un componente propio entra al componente en vez de abrir sus Propiedades, que siguen en el inspector y en el clic derecho. Ver [Entrar a un componente](docs/funciones/pestanas-y-componentes.md#entrar-a-un-componente).
- Ventana ▸ «Nueva vista» ahora se llama «Nueva pestaña del mismo circuito» y abre una pestaña en la misma ventana; Archivo ▸ «Cerrar ventana» pasó a Ctrl+Mayús+W y Ctrl+W es ahora «Cerrar pestaña». Ver [Ventanas y pestañas](docs/funciones/interfaz-preferencias-y-sesion.md#ventanas-y-pestañas).
- La pestaña «Componentes» del inspector y el panel «Proyecto» se van: su lugar lo toma el [árbol del circuito](docs/funciones/pestanas-y-componentes.md#árbol-del-circuito) de la columna izquierda, que no agrupa por tipo, no muestra los marcos ni los valores, y en vez de filtrar busca en todo el circuito. Las piezas sin etiqueta del mismo tipo en un mismo lugar se numeran («Interruptor 1», «Interruptor 2») en el árbol, la barra de lugar y Problemas, y la ruta de la barra ya no muestra los identificadores («circuito › Ram › Selector 2» en vez de «Ram (c5) › Selector (c2)»).

### Arreglado

- En la pestaña de un componente, «Editar descripción…», «Cambiar forma…» para todas las piezas y «Actualizar desde la librería» sobre una pieza de adentro ya no quedan solo en la pestaña: cambian ese componente en el circuito (y en el archivo al guardar), en un solo paso de deshacer. Ver [Adentro de un componente](docs/funciones/pestanas-y-componentes.md#adentro-de-un-componente-qué-cambia).
- La columna izquierda: el divisor entre «Circuito» y «Paleta» ahora se arrastra (y se recuerda), y plegar y volver a abrir la Paleta (o el Circuito) ya no hace desaparecer el otro panel: vuelven los dos con su altura anterior.
- El «Desplazador» ya no deja toda la salida en Z cuando algún bit de A está en Z: con S definida, cada bit se mueve a su lugar (también los que están en Z) y los que entran valen 0, como en Logisim. El LRV32IM importado de Logisim leía 0 del Teclado (el byte llega con el resto del bus suelto) y ahora lee la tecla. Ver [Bloques de N bits](docs/funciones/componentes.md#bloques-de-n-bits).
- Un archivo de memoria de Logisim (`v2.0 raw`) con una repetición de 0 palabras (`0*0`, como la que escribe el BinaryConverter2 del LRV32IM en `fast-rfact.ram`) ya no se rechaza como «no es una repetición válida»: no agrega nada, como en Logisim. Ver [Memoria: archivos enlazados](docs/funciones/componentes.md#memoria-archivos-enlazados).

## Versión 0.6 — 2026-11-04

Terminal, Teclado y UART 16550 para que un circuito escriba y lea texto; imprimir y exportar a PDF en varias hojas;
importar y exportar circuitos de Logisim (beta); y buses con grupos de bits. Desde esta versión hay instaladores con
Java incluido para Windows y Linux (msi, zip portátil, deb, rpm, AppImage, Flatpak y Snap), y Ayuda ▸ «Reportar un
problema» lleva directo a GitHub.

### Agregado

- Esquemas de colores «Logisim» y «Chipmunk» (Preferencias ▸ General ▸ Esquema de color), con los colores de Logisim-evolution (lienzo blanco, piezas negras, cables verdes) y de diglog de Chipmunk (pantalla gris oscuro, compuertas cian, pines rojos, cables verdes en 0 y rojos en 1); el dibujo de las piezas no cambia. Ver [Preferencias](docs/funciones/interfaz-preferencias-y-sesion.md#preferencias).
- «Exportar a PDF…» e «Imprimir…» tienen el botón «Ver hojas en el circuito» (recuerda las opciones y el margen, enciende «Mostrar hojas de impresión» y cierra el diálogo; solo con «Todo el circuito»), y mientras las hojas se ven en el circuito, «Todo el circuito» sale en esas mismas hojas, con la nota «Se usan las hojas que ves en el circuito.» bajo la línea de estado (solo mientras el papel, la orientación, la escala, el encabezado y pie, las guías, las notas adhesivas y el margen del diálogo son los que ves en el circuito). La IA también empieza las hojas de sus PDF de todo el circuito donde están ancladas las del lienzo (su papel, escala y opciones salen de su pedido), y puede mostrarlas en una ventana con el ajuste «Mostrar hojas de impresión». Ver [Exportar a PDF](docs/funciones/importar-y-exportar.md#exportar-a-pdf).
- Archivo ▸ «Imprimir…» (Ctrl+P) imprime en papel las mismas hojas que muestra la vista previa, con el mismo diálogo que «Exportar a PDF…». Si en el diálogo de la impresora eliges otro papel u orientación, o la impresora necesita un margen mayor, VSim rehace la vista previa y espera a que vuelvas a pulsar «Imprimir…», así nunca sale algo que no viste. En horizontal el papel sale igual que el PDF, con los textos de la Terminal, de las etiquetas y de las piezas giradas derechos, y el texto va en la fuente de VSim, no en la de la impresora. Sin impresoras instaladas queda deshabilitado. Ver [Imprimir](docs/funciones/importar-y-exportar.md#imprimir).
- «Imprimir…» usa el papel por defecto de la impresora (por ejemplo Carta) en vez de A4 y lo avisa en el diálogo; si eliges otra impresora con otro papel por defecto y no habías elegido papel, rehace la vista previa antes de imprimir, para que el borde no salga cortado. Ver [Imprimir](docs/funciones/importar-y-exportar.md#imprimir).
- Ver ▸ «Mostrar hojas de impresión» (y un botón en la barra de herramientas) dibuja sobre el circuito las hojas que saldrían al imprimir o exportar a PDF todo el circuito: el borde de cada hoja a trazos grises, la franja que comparten dos vecinas, «Hoja n» en cada una y un contorno ámbar alrededor de las piezas y marcos que un borde corta aunque cabrían en una hoja. Con varias hojas, su cuadrícula sigue, tenue, por todo el lienzo, y al arrastrar piezas o marcos el contorno ámbar aparece ya en la vista previa si un borde los cortaría donde los sueltes. Mientras se ven, las hojas quedan quietas al editar (si el circuito crece hacia la izquierda o arriba se agrega una columna o fila de ese lado); ocultarlas y mostrarlas de nuevo las vuelve a repartir, y con «Ajustar a una hoja» o una sola hoja se sueltan y se reparten desde la esquina del circuito en cuanto vuelvan a ser varias. Un clic en el rótulo de arriba («A4 vertical · 100 % · 2 × 3 = 6 hojas») cambia el papel, la orientación o la escala, o abre «Más opciones…». Nunca salen en las imágenes, el PDF ni la impresión. Ver [Hojas de impresión en el lienzo](docs/funciones/importar-y-exportar.md#hojas-de-impresión-en-el-lienzo).
- «Saltar hojas vacías» (en Apariencia de «Exportar a PDF…» e «Imprimir…», junto a «Guías para unir hojas», activada por defecto y recordada): las hojas donde no cae nada del circuito no salen en el PDF ni en la impresora; las demás conservan su número («Hoja 5 de 6»), la línea de estado dice «· se imprimen 4», la vista previa y la barra de avance cuentan solo las que salen, y junto a una hoja saltada no van pestaña, marcas ni su número. En el circuito esas hojas se ven tenues con «Hoja 2 · vacía», el rótulo agrega «· se imprimen 4» («· se imprime 1» si es una) y las opciones rápidas tienen la misma casilla, solo con varias hojas; la IA la respeta al exportar (todo el circuito, una selección o un marco). Un marco cuenta por lo que dibuja (borde, barra de título y, solo con color y en color, su interior), así que un marco grande sin color no mantiene hojas vacías; el límite de 1.000 hojas cuenta las que salen, no las de la grilla. Las líneas de la cuadrícula fuera de las hojas se desplazan con el circuito. Ver [Exportar a PDF](docs/funciones/importar-y-exportar.md#exportar-a-pdf).
- El diálogo de «Exportar a PDF…» e «Imprimir…» crece con su contenido (un aviso nuevo, un texto más largo) en vez de cortar controles; nunca se encoge ni se sale de la pantalla.
- En «Imprimir…», los avisos de la impresora destacan: en verde con un icono de información «Papel cambiado a Carta, el de la impresora.» y en ámbar con un icono de advertencia los desajustes de papel. Si eliges un papel distinto del que la impresora tiene por defecto, avisa de inmediato («La impresora tiene Carta por defecto: si en la bandeja hay Carta, una hoja A4 saldrá cortada.») en vez de esperar a que imprimas.
- Cada versión con etiqueta (`vX.Y`) construye sola sus instaladores con Java incluido (msi y zip portátil, deb, rpm, AppImage, Flatpak y Snap; dmg desde la 1.0), los revisa instalados y deja una release en borrador con `SHA256SUMS` y notas sacadas del «Resumen» de la versión en este archivo. Ver [Publicar una versión](packaging/README.md#publicar-una-versión).
- Una guía, [Cómo instalar y probar](docs/instalar.md), explica por sistema y formato cómo instalar VSim 2 (Windows, macOS, Linux y el jar), qué hacer con los avisos de SmartScreen y Gatekeeper, dónde quedan las preferencias y cómo reportar un problema. El README apunta a ella.
- En macOS, un doble clic en un `.vsim` en Finder (o soltarlo sobre el icono del Dock) lo abre en VSim, también si el aviso llega mientras VSim arranca. Ver [Abrir y guardar](docs/funciones/importar-y-exportar.md#abrir-y-guardar).
- Ayuda ▸ «Reportar un problema…» abre en GitHub el formulario de problemas (en el idioma de VSim) con la versión, el sistema y el formato de instalación ya completados; hay también un formulario para proponer ideas. «Copiar información» de Acerca de incluye ahora el formato de instalación. Ver [Reportar un problema](docs/funciones/interfaz-preferencias-y-sesion.md#reportar-un-problema).
- Pantalla de inicio: al abrir, VSim muestra un semisumador en el lienzo, el logo, la versión y «Cargando…», al menos 3 segundos y centrada en la pantalla del mouse. Ver [Pantalla de inicio](docs/funciones/interfaz-preferencias-y-sesion.md#pantalla-de-inicio).
- Cada circuito tiene un **nombre propio**, aparte del nombre de su archivo: Propiedades sin nada seleccionado muestra «Nombre del circuito» y lo cambia en un paso de deshacer. La ventana sigue mostrando el nombre del archivo; un circuito nuevo toma el del archivo al guardarlo por primera vez y «Guardar como…» no lo cambia. Un circuito importado de Logisim conserva el nombre de su circuito principal (por ejemplo «Simple Uprog Lrv32im») y al exportarlo a Logisim vuelve como el nombre del circuito principal; la IA lo ve al leer el circuito. Los archivos sin nombre guardado toman el de su archivo, como antes. Ver [Abrir y guardar](docs/funciones/importar-y-exportar.md#abrir-y-guardar).
- Archivo ▸ Importar ▸ «Circuito de Logisim (.circ) (beta)…»: abre (es beta: no lo hemos probado lo suficiente) un proyecto de Logisim 2.7 como un documento nuevo sin guardar, con el nombre del archivo; los otros circuitos del proyecto quedan como componentes propios incrustados. El trabajo corre fuera del hilo de la ventana, con una ventana de progreso (y «Cancelar») solo si tarda más de 2 segundos. Si algo no queda igual, un informe lo lista por circuito y, dentro de cada uno, en Diferencias reales (una RAM o un contador que no hacen lo mismo con las patas como están, una pata que no se pudo unir), Sin soporte, Aproximado e Inválido; un aviso que se repite se explica una sola vez seguido de las piezas a las que se aplica, y un clic en una pieza la selecciona y la centra en el lienzo (en un componente propio el enlace va una vez en el título del circuito, con el camino «Componente › Interior»; la ventana del informe no bloquea el editor, se puede agrandar y recuerda su tamaño; el de la exportación es igual); un archivo que no es un proyecto de Logisim da un aviso con su propio motivo. Ver [Importar un circuito de Logisim](docs/funciones/importar-y-exportar.md#importar-un-circuito-de-logisim-beta).
- Los túneles que se importan de Logisim llegan «Sin dirección», como en Logisim: el importador ya no adivina cuál es la entrada de cada nombre, así que un nombre que reciben dos lugares (como `D31-D0` del LRV32IM) o ninguno ya no da errores ni avisos, y el informe ya no explica qué túnel quedó como entrada. Los túneles solos con su nombre siguen avisándose. Cada nombre toma su propio color, el mismo en todos sus túneles (no todos rojos). Ver [Importar un circuito de Logisim](docs/funciones/importar-y-exportar.md#importar-un-circuito-de-logisim-beta).
- El importador respeta el dibujo de Logisim: los cables quedan como los dibujó (puedes pasar Herramientas ▸ «Limpiar cables» después) y las posiciones se separan una vez y media para que las piezas de VSim, más grandes, tengan lugar. Los textos conservan su anclaje de Logisim (izquierda, centro o derecha; arriba, base o abajo) y el informe avisa cuando uno queda encima de una pieza; las constantes, el reloj y el botón quedan derechos, con su texto legible, aunque en Logisim miren hacia arriba o hacia abajo.
- Los túneles tienen el parámetro «Dirección»: «Con dirección» (por omisión, como siempre) o «Sin dirección». Un túnel sin dirección es el mismo cable que todos los de su nombre y cualquiera puede recibir la señal o entregarla, como en Logisim; se dibuja sin la flecha y no da los avisos de entrada y salida. Se cambia en cualquier túnel, en los dos sentidos, y un nombre puede mezclar ambos; uno sin dirección solo con su nombre da un aviso, y dos piezas que pelean por él, el cortocircuito de siempre. Los circuitos anteriores se abren y se guardan igual. Ver [Túneles de cables](docs/funciones/cables-buses-y-tuneles.md#túneles-de-cables).
- Archivo ▸ Exportar ▸ «Circuito de Logisim (.circ) (beta)…»: guarda el circuito como un proyecto de Logisim 2.7 que Logisim abre con las mismas conexiones; los componentes propios pasan como circuitos con su forma y sus pines. Lo que Logisim no tiene (la ALU, la UART, piezas de más de 32 bits…) queda fuera, y lo que se aproxima (un reloj, una RAM de flanco de bajada, un Terminal de 8 bits…) se lista en un informe al terminar. Si la escritura falla, el archivo que había queda intacto. Ver [Exportar a Logisim](docs/funciones/importar-y-exportar.md#exportar-a-logisim-beta).
- El registro tiene el parámetro «Disparo»: «Flanco de bajada» (por omisión), «Flanco de subida», «Nivel alto» y «Nivel bajo». Los dos niveles lo hacen funcionar como un latch: mientras el reloj está en ese nivel y EN ≠ 0, Q sigue a D; al salir del nivel guarda el último valor. CLR gana en todos los modos y los circuitos anteriores quedan en «Flanco de bajada»; el dibujo no cambia. La descripción de la pieza explica cada opción. Detalle en [Bloques de N bits](docs/funciones/componentes.md#bloques-de-n-bits).
- La RAM tiene el parámetro «Flanco» («Bajada» por omisión, «Subida»), visible solo con «Escritura: Con reloj»: escribe cuando `CLK` pasa de 1 a 0 o de 0 a 1. Los circuitos anteriores quedan en «Bajada»; el dibujo de la RAM es el mismo con los dos flancos. Detalle en [Memoria: RAM y ROM](docs/funciones/componentes.md#memoria-ram-y-rom).
- «Separador de bus» y «Unión de bus» con **grupos de bits**: cada pin puede llevar varios bits como un bus. «Bits por pin» en Propiedades parte el bus en pines iguales (4 en un bus de 8 da dos nibbles) y «Configuración avanzada…» abre una ventana con la tabla de pines y sus tramos, una tira de bits para pasar bits de un pin a otro con un clic, el texto editable (`7-11,25-31|15-19`), los problemas y una vista previa; Aceptar aplica todo en un paso de deshacer. Un pin puede llevar bits salteados (el inmediato de `sw` de RISC-V) y un bit puede quedar sin usar (en la Unión sale en Z). Los pines de varios bits se dibujan gruesos con sus bits como etiqueta; con ellos los pines van cada dos cuadros y la pieza se ensancha para que la etiqueta más larga se lea entera, sin cortes. Con un bit por pin la pieza es la de siempre y los circuitos de antes se abren igual. En la ventana, Enter en el campo «Como texto» aplica el texto y, si no queda ningún problema, acepta (con un problema lo muestra y la ventana sigue abierta); Aceptar es el botón principal, lee el texto escrito antes de aplicar y los botones quedan fijos abajo; un tramo escrito al revés (`11-7`) vale lo mismo que `7-11` (en la tabla sale de menor a mayor) también en la IA y en los archivos; la ventana se ajusta a su contenido, con una vista previa compacta y ocho colores de pin todos distintos (los bits «sin usar» se reconocen por no tener relleno). «Bits por pin» y el resumen dicen cuándo el último pin lleva menos bits («2 pines de 7 bits (el último de 1)») y se ponen al día al cambiar el «Ancho». Cambiar los grupos o el «Ancho» nunca une en silencio un pin al cable de otro bit del mismo ancho: se rechaza nombrando el pin, igual desde «Bits por pin», la ventana, Propiedades, «Configurar…» y la IA, que además reagrupa al cambiar solo el «Ancho» como Propiedades y recibe los problemas en palabras («el bit 40 no existe en un bus de 8 bits»). Ver [Grupos de bits](docs/funciones/cables-buses-y-tuneles.md#grupos-de-bits).
- Un archivo que no se puede abrir ahora lo dice con un aviso (antes, desde la línea de comandos, solo quedaba en el registro): nombra el archivo y explica por qué, distinguiendo uno guardado con un VSim más nuevo (con ambas versiones), uno que no es de VSim (si parece de Logisim, dice que se importa desde Archivo ▸ Importar) y uno dañado (indica el componente o elemento que falló). El texto técnico está en «Detalles». Al arrancar, varios archivos con problemas dan un solo aviso.
- El monitor de bytes de la ventana del Terminal marca con una fila «… N bytes perdidos …» los bytes que el registro descartó antes de que la ventana los leyera, en vez de saltárselos sin avisar. Ver [Terminal](docs/funciones/componentes.md#terminal).
- Un lienzo completamente vacío muestra al centro un texto suave que dice cómo empezar («Arrastra una pieza desde la paleta para empezar» y «o abre un ejemplo en Archivo ▸ Ejemplos»). Desaparece al poner cualquier cosa, vuelve si borras todo y no sale en las imágenes, el PDF ni la impresión. Ver [Lienzo vacío](docs/funciones/interfaz-preferencias-y-sesion.md#lienzo-vacío).
- «Los cables siguen al mover» tiene el atajo Ctrl+E (se puede cambiar en Atajos de teclado). La ayuda del botón muestra la tecla vigente y la barra de estado dice si quedó activado o desactivado.
- Herramienta **Texto** en la barra de herramientas y en el menú Ver (después de «Nota adhesiva»): un clic en el lienzo abre el campo de texto en ese punto y el editor vuelve a Selección.
- Clic derecho sobre un pin de bus de un componente ofrece «Agregar separador de bus» (en una salida) o «Agregar unión
  de bus» (en una entrada): pone la pieza del ancho del pin, hacia afuera y unida por un cable, en un solo paso de
  deshacer, y la deja seleccionada.
- Simulación ▸ «Avanzar un ciclo de reloj» (Ctrl+F7, y un botón en la barra de «Paso a paso» junto al del flanco)
  avanza hasta justo después del próximo flanco de bajada, donde guardan los flip-flops: lo que antes hacía Mayús+F7.
  Después de un avance por flanco el estado dice qué flanco fue («flanco de subida», «flanco de bajada», o «flanco de
  subida y de bajada» con dos relojes que cambian en sentidos opuestos a la vez).
- La IA también puede avanzar un ciclo de reloj completo, y cuando avanza por flancos la respuesta dice qué flanco fue.
- **Pulsador** en Entrada/Salida, después del Interruptor: vale 1 solo mientras lo mantienes apretado con el mouse y vuelve a 0 al soltarlo (donde sea), con Esc, al perder la ventana el foco y al pausar o reiniciar la simulación. Apretarlo no deja paso de deshacer, no marca el archivo ni lo selecciona; para moverlo, arrástralo desde la manilla de la izquierda. Al reiniciar la simulación empieza suelto. Cuenta como entrada en la tabla de verdad y en «Crear componente», y la IA lo fija con `vsim_set_inputs`. Ver [Pulsador](docs/funciones/componentes.md#pulsador).
- Ver ▸ «Valores de los buses» (y un botón en la barra de herramientas) oculta el valor escrito sobre cada bus; las
  imágenes y el PDF siguen al lienzo. El globo, el árbol y las formas de onda conservan el valor. Se recuerda, no tiene
  tecla por omisión y la IA puede cambiarlo durante una tarea.
- **Terminal** en Entrada/Salida: una pantalla de texto de filas por columnas que el circuito escribe de a un byte en un flanco de reloj (`D`, `CLK`, `WE`, `CLR`), compatible con el TTY de Logisim y con más: D de 7 u 8 bits con UTF-8 o Latin-1, secuencias VT100/xterm (cursor, borrado, 16, 256 y millones de colores, estilos), historial, y tres perfiles («Logisim (LRV32IM)», «VT100‑xterm» y «Teletipo») cuyas diferencias se marcan con «●» y se deshacen con «↺» o «Volver al perfil». Se dibuja con IBM Plex Mono o VT323 (incluidas, licencia SIL OFL 1.1), colores de pantalla (fósforo verde, ámbar, blanco, papel o los del tema), cuatro paletas ANSI, efecto «Suave» o «Fuerte», siete cursores (uno es el de Automan) y la última letra marcada; un globo por celda dice el carácter, su código, sus bytes y cuándo llegó; la animación se detiene sin foco, fuera de vista y con «menos movimiento», y no sale en imágenes ni PDF. Doble clic en la pantalla abre su ventana (monitor de bytes, «Salida esperada», copiar, guardar y limpiar), y arrastrar su pestañita «⤡» cambia columnas y filas. Además: «Detener al recibir» un texto, sonidos (campana y golpeteo de teletipo), «Guardar salida» en un `.txt` que eliges con «Elegir archivo…» (abrir un circuito nunca escribe en un archivo sin que lo confirmes), bytes como caracteres en las formas de onda y un generador de caracteres desde una ROM (`CODE`, `ROW`, `PIX`). Ver [Terminal y teclado](docs/funciones/componentes.md#terminal-y-teclado).
- **Teclado** en Entrada/Salida: una cola de bytes que tecleas desde el PC (un clic captura el teclado, Esc sale, Mayús+Esc envía ESC; entran también los caracteres de AltGr y de Opción en macOS, como «@») y que el circuito lee con `CLK`, `RE`, `AV` y `D`, como el Teclado de Logisim, con capacidad, 7 u 8 bits, UTF-8 o Latin-1, Enter y Retroceso a elección, flechas como secuencias VT, tercer estado, «Texto inicial» (si lo cambias la cola vuelve a empezar con él), «Terminal enlazado» para escribir desde la ventana del Terminal, «Color al escribir» con muestras, «Pegar texto» y «Vaciar cola» en el clic derecho, y una pestañita «↔» para cambiar su ancho arrastrando. Escribir no es una edición.
- **UART 16550** en Entrada/Salida: el puerto serie estándar con sus ocho registros en el bus de una CPU, FIFO de 1 o 16 bytes e interrupción, con pantalla y teclado integrados o con pines TX y RX. Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- Ejemplo «Hola, terminal» en Archivo ▸ Ejemplos: una ROM con un mensaje, un contador y un Terminal que lo escribe, y un Teclado que se repite en la misma pantalla.
- Una pieza puede pausar la simulación (hoy, el Terminal con «Detener al recibir»): la barra de estado dice «Simulación detenida en t = …: «consola» recibió «=120»». Ver [Piezas que detienen la simulación](docs/funciones/simulacion-y-paso-a-paso.md#piezas-que-detienen-la-simulación).
- Una pestañita para cambiar el tamaño de una pieza arrastrando (la primera en el Teclado y en el Terminal): se ve con la pieza elegida sola, los cables siguen, es un paso de deshacer y un doble clic vuelve al tamaño de partida.
- Propiedades agrupa los parámetros de las piezas nuevas en grupos, con perfiles de valores, y un texto de varias líneas se escribe en un recuadro de tres líneas (Ctrl+Enter lo aplica).
- La IA tiene `vsim_read_terminal` (lee la pantalla de un Terminal o de una UART, el historial, los bytes recibidos y si coincide con la salida esperada) y `vsim_type_text` (escribe un texto en un Teclado), ambas sobre la simulación que corre.
- Ayuda ▸ «Acerca de VSim 2…» nombra las tipografías incluidas (IBM Plex Mono y VT323, licencia SIL OFL 1.1), y el README dice su licencia.
- La pestaña «Propiedades» se desplaza cuando su formulario es más alto que la ventana (antes el del Teclado quedaba cortado abajo): barra vertical, sin barra horizontal, y la rueda del mouse funciona también sobre los cuadros de texto.
- La pantalla integrada de la UART 16550 se anima en el lienzo como la del Terminal: parpadeo, cursor de Automan, la última letra iluminada, la imagen anterior que se desvanece y el «Efecto de pantalla» (las imágenes, el PDF y la impresión la muestran quieta). Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- La UART 16550 con pantalla integrada tiene la pestañita «⤡» del Terminal: arrastrarla cambia las columnas y las filas de su pantalla, los cables la siguen y es un solo paso de deshacer. Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- Un doble clic en la pantalla integrada de la UART 16550 abre su ventana como en el Terminal (con pines TX y RX no hay pantalla y el doble clic sigue configurándola). Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- Las celdas escritas de la pantalla integrada de la UART 16550 tienen en el lienzo el globo del Terminal (carácter, código, bytes, instante, fila y columna). Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- La UART 16550 con pantalla integrada suena como el Terminal: «Campana (BEL)» y «Golpeteo de teletipo» en su grupo *Sonido* (solo las del propio circuito). Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- La UART 16550 con pantalla integrada tiene la «Salida esperada» del Terminal: la pestaña de su ventana compara lo que llegó con lo esperado, y la IA también lo lee. Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- Una UART 16550 con «Pines TX y RX» no reserva memoria para una pantalla ni para su historial, y al volver a «Integrada» la pantalla empieza vacía. Ver [UART 16550](docs/funciones/componentes.md#uart-16550).
- Con «Guardar salida» activa, el Terminal muestra bajo el archivo si esta sesión realmente graba o no (falta confirmar, «No grabar», otro Terminal en el mismo archivo o sin archivo).
- VSim se puede empaquetar con su propio Java, sin tener que instalarlo aparte: `./gradlew :ui:nativePackages` arma el deb y el rpm en Linux, el msi y un zip portátil en Windows y el dmg en macOS, con ícono, entrada en el menú y los archivos `.vsim` asociados. Ver [Para desarrolladores](docs/funciones/para-desarrolladores.md#para-desarrolladores).

### Cambiado

- «Exportar a PDF…» e «Imprimir…» parten del margen recordado (1 cm, o el mayor que usó la última impresora; nunca menos de 1 cm) en vez de siempre 1 cm, y un PDF guardado lo conserva: así el diálogo, el circuito y el papel muestran las mismas hojas.
- En «Exportar a PDF…» e «Imprimir…», con varias hojas, cada hoja nombra en el margen junto a cada borde la hoja vecina («◀ Hoja 1», «Hoja 3 ▶», «▲ Hoja 1», «▼ Hoja 4»), además de las marcas de corte y la etiqueta «Fila · Columna — Hoja n de N»; los bordes derecho e inferior con vecina llevan una pestaña de pegado de 1 cm en gris suave con «Pegar aquí» (bajo el circuito), y las marcas de corte van en los bordes izquierdo y superior, en el borde interior del solape: se recorta cada hoja por las marcas y se coloca sobre la pestaña de la hoja de su izquierda o de arriba, sin gris a la vista; «Guías para unir hojas» (activada por defecto, se recuerda) las quita todas, con las pestañas, y devuelve su franja a la ventana. Ver [Exportar a PDF](docs/funciones/importar-y-exportar.md#exportar-a-pdf).
- Con «Encabezado y pie de página», tres casillas dejan fuera por separado la fecha, el nombre del archivo y el número de hoja (activadas por defecto, se recuerdan). Ver [Exportar a PDF](docs/funciones/importar-y-exportar.md#exportar-a-pdf).
- El zoom de la vista previa de «Exportar a PDF…» e «Imprimir…» es ahora una lupa junto a su lista (con la ayuda «Zoom de la vista previa (no cambia la escala del papel)») en vez de una etiqueta larga que dejaba la lista cortada.
- En «Exportar a PDF…» e «Imprimir…», con más de una hoja aparece junto a la cuenta de hojas un botón «Ajustar a una hoja», para volver atrás después de «Dividir en varias hojas», y el zoom de la vista previa se llama «Zoom de la vista previa» para no confundirlo con la «Escala» del papel. Ver [Exportar a PDF](docs/funciones/importar-y-exportar.md#exportar-a-pdf).
- Las pruebas del proyecto corren ahora en GitHub Actions en Linux, Windows y macOS con cada cambio a `main` y cada pull request (`.github/workflows/ci.yml`), y las versiones de las acciones se revisan cada semana (Dependabot). Los archivos de ejemplo y de prueba (`.vsim`, `.json`, `.txt`, `.hex`, `.properties`) se descargan siempre con fin de línea LF, también en Windows.
- Un túnel sin dirección se distingue de un vistazo: en vez de la flecha lleva dos triángulos «◂▸», y se llama «Túnel (sin dirección)» en Propiedades, el buscador, Problemas, el árbol y para la IA, con su propia descripción (las de entrada y salida avisan que se puede quitar la dirección). Al cambiar «Dirección» en Propiedades, la barra de estado dice qué pasó y que Ctrl+Z lo deshace.
- El importador de Logisim avisa de los textos que quedan encima de una pieza con un solo aviso por circuito, que lista cada texto y su pieza (antes era un aviso por texto); ya no lleva un enlace por texto.
- Al pasar el mouse por un túnel sin dirección, la barra de estado cuenta «CLK: 4 túneles sin dirección» (y separa los que tienen dirección de los que no, si el nombre los mezcla), y su menú contextual ofrece «Siguiente túnel del nombre», que recorre todos los túneles del nombre, en vez de «Ir a la entrada» y «Siguiente salida», que suponen una dirección.
- Archivo tiene un submenú **Exportar** (justo después de «Importar», entre separadores) con «Imagen…», «PDF…» y «Circuito de Logisim (.circ) (beta)…», en vez de tres ítems sueltos («Exportar imagen…», «Exportar a PDF…», «Exportar a Logisim…»). Hacen lo mismo, con las mismas teclas asignables en Atajos de teclado y deshabilitados con un circuito vacío; «Imprimir…» (Ctrl+P) sigue en Archivo, justo después de «Guardar como…». Ver [Importar y exportar](docs/funciones/importar-y-exportar.md).
- Dentro de un componente propio rige la misma regla que en el lienzo: una pata suelta de una pieza interna (una entrada de un AND, el EN de un registro) o un cable suelto ya no es un problema, ni hace brillar en rojo la pieza. Siguen siendo problemas un pin del componente que no llega a nada por dentro, los túneles solos o repetidos y los cortocircuitos. Ver [Conexiones pendientes](docs/funciones/componentes.md#conexiones-pendientes).
- Los textos que nombran la entrada de una compuerta (Problemas, el globo de un pin, los nombres por defecto de las sondas, el aviso de conexiones al cambiar la forma) la cuentan desde 1: «entrada 1», «entrada 2», «entrada 3» (en inglés «input 1»…), en vez de `in0`, `in1`, `in2`. Los nombres internos no cambian: el archivo y la IA siguen usando `in0`.
- La descripción del Teclado (su ayuda al pasar el mouse y en Propiedades) dice que Mayús+Esc envía el byte ESC.
- En el Terminal, un `WE` sin conectar vale 1 en todos los modos (antes solo en el modo Logisim: VT100 y Teletipo no escribían hasta cablearlo). Con `WE` conectado todo sigue igual.
- El diálogo «Configurar…» ya no repite la descripción de la pieza: la descripción se lee en Propiedades.
- El lienzo del editor de formas dibuja la cuadrícula como el editor principal: el mismo estilo (puntos o líneas) y la misma separación que eliges en Ver ▸ Cuadrícula, con los colores del tema claro u oscuro y menos puntos cuando el zoom es bajo, y cambia al instante si cambias esa preferencia con el editor abierto. Un botón nuevo en su barra de herramientas, junto al imán «Ajustar», muestra u oculta la cuadrícula (se recuerda aparte del editor principal).
- «Avanzar al próximo flanco de reloj» (Mayús+F7) ahora se detiene en el próximo flanco de cualquier tipo, de subida o
  de bajada: medio ciclo por vez. Para el ciclo completo está «Avanzar un ciclo de reloj» (Ctrl+F7). Lo mismo para la
  IA: su avance por flanco ahora es de medio ciclo.
- El ícono de «Avanzar al próximo flanco de reloj» muestra un pulso con sus dos flancos y la flecha de avance, en vez de un
  solo flanco de bajada; sigue distinto del de «Avanzar un ciclo de reloj».
- En el editor de formas, Línea, Multilínea y Polígono se dibujan igual con clic o con arrastre: arrastrar en
  Multilínea o Polígono pone un nodo al empezar y otro al soltar, y un clic en Línea empieza una línea de dos clics
  (Esc la cancela, Mayús fija el ángulo). Las ayudas de la línea inferior lo dicen.
- Ayuda ▸ «Acerca de VSim 2…» abre un diálogo fijo con el logo y la versión arriba, una grilla (Autor, Licencia,
  Código, Versiones, Historia) con los dos enlaces, los créditos y una franja con «Copiar información» (versión,
  Java, sistema y apariencia, para los reportes de error) y «Cerrar». En macOS también se abre desde el menú de la aplicación.
- Cambiar en Propiedades (o en «Configurar…», o desde la IA) un parámetro que mueve pines ahora lleva sus cables, como al arrastrar el borde: el «Ancho» del Teclado, las filas, columnas, tamaño de letra o perfil de un Terminal, las entradas de una compuerta y cualquier otro parámetro que cambie dónde quedan los pines. Cada cable sigue al pin del mismo nombre y todo es un solo paso de deshacer; el cable de un pin que desaparece queda donde estaba. Ver [Cables que siguen al mover](docs/funciones/cables-buses-y-tuneles.md#cables-que-siguen-al-mover).

### Arreglado

- En macOS, las listas desplegables, los campos numéricos y las pestañas ya no quedan unos píxeles corridos respecto de los demás campos (en Propiedades, el campo de un número empezaba más a la izquierda que el de un texto, y las pestañas de un mapa de Karnaugh se montaban sobre el margen); en Windows, lo mismo con las pestañas. VSim se ve igual en los tres sistemas, así que ya no corrige los bordes de los controles nativos que no usa.
- En Propiedades, una lista desplegable con un texto largo (como «Datos» de una RAM) ya no se sale del panel cuando la letra del sistema es más ancha o el panel es angosto: se achica hasta el ancho de la columna y termina su texto en «…». Ver [Etiquetas, textos y Propiedades](docs/funciones/edicion.md#etiquetas-textos-y-propiedades).
- En la barra de búsqueda, con una letra de sistema más ancha (como la de algunos Linux o macOS) ✕ ya no queda cortado en una ventana angosta: después del campo, también se achica el espacio que el contador guarda para «9999 de 9999». Ver [Barra de búsqueda](docs/funciones/buscar.md#barra-de-búsqueda).
- En la ventanita de «Presentar» de la IA, con una letra más ancha (como la de algunos Linux) «Dejar solo el resultado» y los demás botones ya no se cortan: si no caben uno al lado del otro con su texto entero, van uno debajo del otro. Ver [Presentar](docs/funciones/ia-mcp.md#presentar).
- En Windows, los circuitos (`.vsim`) y componentes de la librería se guardaban con fin de línea CRLF, distinto de Linux y macOS: ahora las líneas terminan siempre con LF, así que el mismo circuito guardado en cualquier sistema es el mismo archivo. Ver [Abrir y guardar](docs/funciones/importar-y-exportar.md#abrir-y-guardar).
- Sobre un lienzo gris medio (de un esquema propio, por ejemplo) la letra de color de una nota, con o sin fondo, a veces no llegaba al contraste mínimo de lectura; ahora siempre llega.
- Con una «Letra de etiquetas y textos» a la que le falta un carácter (por ejemplo Fira Code, que no tiene «‑», el guion que no corta la línea), las etiquetas, los textos libres, los títulos de los marcos, las notas adhesivas y los nombres de los pines ya no muestran un cuadradito: ese carácter se toma de la letra predeterminada, en el lienzo, en las imágenes, en el PDF y en papel. Ver [Preferencias](docs/funciones/interfaz-preferencias-y-sesion.md#preferencias).
- En el informe de la importación y exportación de Logisim, el enlace de cada componente propio queda junto al título de su sección y no en el medio de la ventana.
- El contorno de selección de un «Separador de bus», una «Unión de bus» y otras piezas de varios bits (un sumador, por ejemplo) vuelve a ajustarse a la pieza: se le sumaba el recuadro de valor de los pines de entrada y salida, y quedaba varios cuadros más ancho que ella.
- Los avisos largos se parten en varias líneas: en Propiedades un error de una sola línea ya no ensancha el panel de la derecha a costa del lienzo, y en la ventana de grupos el rechazo («El pin b4 quedaría unido…») ya no se corta en el borde.
- En la práctica de Karnaugh, un clic sin querer en la tabla ya no pierde lo practicado: aparece «Deshacer» (o Ctrl+Z) que devuelve la tabla y los grupos. Cerrar la práctica con grupos dibujados pregunta antes («¿Cerrar la práctica? Se perderán los grupos dibujados.»; «Seguir practicando» la deja abierta). Ver [Modo práctica de Karnaugh](docs/funciones/tabla-de-verdad-karnaugh-y-expresiones.md#modo-práctica-de-karnaugh).
- Encender «Mostrar Q̅» en un flip-flop D antiguo ya no une Q y Q̅ en silencio cuando un cable que baja desde Q (u otro pin) pasa por el punto de Q̅: Propiedades lo rechaza con un aviso y no cambia nada, y `vsim_edit` responde con un error que pide mover el cable antes. Ver [Secuenciales](docs/funciones/componentes.md#secuenciales).
- «Guardar salida»: la pregunta «¿Grabar ahí?» pertenece a la ventana del editor y se hace una sola vez por archivo en cada vez que usas VSim (antes se repetía al abrir el circuito de nuevo); un enlace simbólico o una ruta con `..` cuentan como el mismo archivo para la regla de que dos Terminales no graban en el mismo.
- «Reiniciar» un Terminal ya no se confunde con reconstruir la pieza al editar un parámetro: el registro de bytes tiene una época propia, así que el monitor y «Guardar salida» empiezan de nuevo solo en «Reiniciar».
- En la barra de estado, el aviso «Simulación detenida en t = …: …» de una pieza que pausó la simulación se borra al avanzar paso a paso más allá de esa pausa, sin esperar a que la simulación vuelva a correr.
- Al achicar la «Capacidad» de un Teclado con la simulación en marcha, la cola ya no conserva medio carácter UTF-8: si el corte cae dentro de un carácter, se descarta entero.
- En `vsim_edit`, los parámetros que la IA pasa al colocar con `nand`, `nor`, `xnor`, `not` o `inverter-controlled` ahora mandan sobre los del atajo (`nand` con `negate: false` da una AND sin negar; `nor` con `inputs: 4` da una OR negada de 4 entradas), y la descripción de la herramienta lista los atajos y lo que fija cada uno.
- Pegar o duplicar una entrada de túnel cuyo nombre ya tiene una entrada en el circuito le pone un nombre libre (`A` pasa a `A_2`, el primer `_N` libre), en el mismo paso de deshacer, en vez de dejar «Hay 2 entradas». Las salidas de ese nombre que vienen en lo pegado se renombran con ella. Una salida sola conserva su nombre, y una entrada pegada en otro circuito donde el nombre está libre también. Ver [Túneles de cables](docs/funciones/cables-buses-y-tuneles.md#túneles-de-cables).
- En el asistente de expresiones booleanas, la leyenda de notación lista todas las marcas de NOT que se entienden (`'`, `’`, `′` y `ʹ`), y los errores de las líneas se pintan con el mismo rojo legible del resto del editor.
- Un doble clic sobre la casilla de una tarea de una nota adhesiva la marca una sola vez (un solo paso de deshacer), en vez de marcarla y desmarcarla, y no abre la edición del texto.
- Una nota adhesiva de menos de 4 cuadros de alto ya no tapa su primera línea de texto con los botones ✕ y –: el mínimo ahora es 4 × 4 cuadros y el texto empieza bajo los botones. Una nota guardada con 3 de alto se abre con 4.
- Los problemas de una pieza sin etiqueta dicen cuál es: una compuerta se nombra con su cantidad de entradas («AND de 3 entradas: la entrada «in2» está desconectada») y el árbol de componentes usa el mismo nombre; con etiqueta, se usa la etiqueta.
- El contorno de selección de un pin de entrada o salida de varios bits ahora rodea también el recuadro con su valor (antes lo cortaba), y el rectángulo de selección lo cuenta como parte del pin: para seleccionarlo hay que encerrar el pin y su recuadro. Los pines de 1 bit no cambian.
- Una memoria RAM o ROM enlazada a un archivo que no se encuentra (o que no se puede leer) ahora es un problema de la pieza, como los demás: aparece en la pestaña «Problemas» con el nombre de la memoria y la ruta, la memoria se marca en el lienzo y la IA lo ve al leer los problemas. Antes solo se veía en la barra de estado y en el inspector. Se va cuando el archivo vuelve o se quita el enlace.
- Cambiar el ancho de un LED o un Interruptor de varios bits (por ejemplo de 8 a 1, o de 8 a 4) mueve la pieza para que su pin siga en el mismo punto de la grilla, y el cable conectado no queda suelto; todo en un solo paso de deshacer.
- La documentación de los buses dice que el globo muestra también el valor en binario.
- Ensanchar un Interruptor de varios bits paso a paso en Propiedades (de 1 a 2, a 3…) ya no lanza un error al dibujarlo: mientras la simulación se pone al día, muestra los bits del parámetro «Valor».
- Un Terminal con «Efecto de pantalla» en «Suave» o «Fuerte» ya no pone lento el editor ni la simulación mientras recibe texto: el lienzo guarda una imagen de la pantalla con su efecto y, mientras nada cambia en ella, solo vuelve a dibujar el cursor, la última letra marcada y la imagen anterior que se desvanece. Ver [Terminal](docs/funciones/componentes.md#terminal).

## Versión 0.5 — 2026-10-28

Memorias RAM y ROM con su editor de contenido y mapa de memoria, interruptores y LED de varios bits, compuertas
con más entradas y ancho, nuevas piezas de aritmética, multiplexores y flip-flops, notas adhesivas, y una descripción
para cada pieza. La paleta se reorganiza en nueve categorías que siguen un curso.

### Agregado

- Bajo el «Valor» de un interruptor de varios bits (en Propiedades y en el diálogo de configurar), un pequeño bloque dice
  cómo lee VSim el número mientras lo escribes, una lectura bajo la otra y alineadas (binario, hexa y decimal; un
  binario muy largo se parte en líneas para no pasar del campo, así el panel no se desplaza de lado). Si no es un
  número o no cabe, lo dice en el color de error, en una línea que se parte si es larga; cambiar el ancho lo actualiza.
- El interruptor de varios bits tiene «Números» (Sin signo / Con signo, por defecto Sin signo; solo se muestra con más
  de un bit). Con signo, «Valor» acepta además decimales negativos (en 8 bits, de −128 a −1: −1 entrega 0xFF), y la
  lectura y el globo dan el decimal con signo en lugar del sin signo. Los archivos de antes se abren sin signo y el
  valor por defecto no se escribe.
- «Todas las piezas», un circuito con cada pieza nueva (un marco por categoría) mostrando su caso interesante. No
  está en Archivo ▸ Ejemplos: se genera como archivo aparte para abrirlo a mano.
- El interruptor puede tener varios bits («Ancho (bits)» de 1 a 32): una fila compacta con una celda por bit, el más
  significativo a la izquierda, y una sola salida de bus. Un clic sobre una celda cambia solo ese bit; el globo muestra el valor en binario, hexadecimal y decimal, y el bit
  bajo el puntero. Con un bit, nada cambia.
- El LED también puede tener varios bits: una fila de luces con la misma caja que el interruptor del mismo ancho (bit
  contra bit) y un solo pin de bus de entrada. Cada luz se enciende solo con 1; con 0, Z o conflicto queda apagada.
- Rotar un interruptor o un LED de varios bits no da vuelta la fila: su pin pasa al lado siguiente en el sentido del
  reloj y el cuerpo sigue derecho, también dentro de un grupo rotado (el pin va adonde lo lleva el giro, así sus cables
  siguen conectados), en la exportación y al imprimir. Un interruptor o un LED de un bit que pasa a varios bits queda
  derecho con el pin del lado donde lo tenía (un LED sin rotar lo tiene abajo).
- La tabla de verdad, «Crear componente» y la IA entienden el interruptor y el LED de varios bits: la tabla los muestra
  como columnas de N bits, «Crear componente» los vuelve un pin de entrada o de salida del mismo ancho (sin marcar, el
  interruptor queda como una constante de ese ancho) y `vsim_set_inputs` acepta `0xA6`, `0b1010_0110` o `166` para un
  interruptor de 8 bits y rechaza lo que no cabe.
  Al volverse una entrada, un interruptor de varios bits parte en todo 1 si su valor era todo 1 y en todo 0 en
  cualquier otro caso (el pin de entrada solo tiene esos valores iniciales); «Crear componente» lee el valor en
  cualquier base (`0xFF` es todo 1).
- Propiedades termina con un recuadro «Descripción» que explica la pieza elegida y sus supuestos: qué vale una
  entrada sin conectar, en qué flanco actúa, qué pasa con una entrada en Z, cómo lee los números con signo. Todas las
  piezas lo tienen, también la RAM y la ROM (escritura en el flanco de bajada, `CS` y `WE` sin conectar, direcciones
  indefinidas, archivos enlazados), y el texto cambia con los parámetros (un AND negado muestra el NAND, la RAM «Sin
  reloj» lo explica). Ocupa todo el espacio que queda bajo los parámetros (se desplaza solo si el texto es más
  largo); también aparece en «Configurar…», con un máximo de 14 líneas.
- Los componentes propios tienen descripción: se escribe al crearlos o actualizarlos («Descripción (opcional)») y
  después con «Editar descripción…» en Propiedades, como un paso de deshacer que también cambia (y al deshacer,
  devuelve) el archivo de la librería.
- La IA recibe la misma descripción en `vsim_describe_component`, y su ayuda (el recurso «ayuda») lista las nueve
  categorías de la paleta con sus piezas; el filtro por categoría de `vsim_describe_components` usa los nombres nuevos
  (gates, wiring, arithmetic, plexers, sequential, memory…).
- Aritmética con seis piezas nuevas de N bits, cada una con su «Descripción»: «Restador» (A − B − Bin, con Bout),
  «Negador» (−A en complemento a dos), «Multiplicador» (producto de 2N bits en dos mitades P y H, con o sin signo),
  «Divisor» (cociente, resto y la salida E para la división por cero: Q = 0, R = A, E = 1), «Desplazador» (lógico,
  aritmético o rotación, a izquierda o derecha; la cantidad entra por S) y «Contador de unos».
- Multiplexores: «Demultiplexor» (D va a la salida que elige S, de 2, 4 u 8 salidas; las demás valen 0) y «Codificador
  con prioridad» (A es el número de la entrada más alta en 1 y V dice si alguna lo está; de 2, 4, 8 o 16 entradas).
- Dos piezas nuevas en Cableado: el **Extensor de bits** (cambia el ancho de un bus, rellenando con ceros o con el
  signo, o cortando a los bits de abajo) y el **Selector de bits** (saca el grupo número `S` de un bus, por ejemplo el
  nibble que toca). Cada una explica sus supuestos en su descripción.
- El **Inversor controlado** en la paleta, justo después del Buffer triestado: la salida es la entrada invertida
  mientras el control vale 1 y Z si no.
- Flip-flops JK, T y SR en Secuenciales, del tamaño del flip-flop D, con Q y Q̅, en el flanco de bajada y partiendo en
  Q = 0. El SR con S = R = 1 muestra el estado prohibido: Q y Q̅ en 0 hasta un flanco con S distinto de R.
- Las compuertas AND, OR y XOR tienen «Entradas» (de 2 a 8) y «Ancho (bits)» (de 1 a 32) en Propiedades: un AND de 6
  entradas de 8 bits hace el AND bit a bit de 6 buses. Con 6 a 8 entradas la compuerta es más alta y la salida
  sigue al medio.
- «Negar salida», una casilla en Propiedades, pone la burbuja en la salida: un AND negado es un NAND, un OR un NOR y
  un XOR un **XNOR**, que es nuevo.
- El **Buffer**: una compuerta de una entrada que copia su entrada (útil para hablar de retardo y de fan-out); negado
  es el NOT. También tiene «Ancho (bits)».
- Las compuertas con 6 a 8 entradas se dibujan con el cuerpo de siempre (4 × 4, centrado en la salida) y una línea
  trasera prolongada hasta las entradas de los extremos; con «Ancho» mayor que 1 los cables de sus pines son gruesos
  (también en el Buffer).
- La línea de costo del asistente de Karnaugh y de «Circuito desde expresiones» cuenta los XNOR.
- Para la IA, una compuerta se coloca con sus parámetros (`inputs`, `width`, `negate`) y `vsim_describe_component` lista los pines según «Entradas»; el ejemplo de la herramienta de edición usa `and` con `negate`. También acepta `nand`, `nor`, `xnor` y `not` como tipo (ponen la compuerta con «Negar salida»), y un id de antes, como `nand2`, da un error que dice con qué reemplazarlo; el resumen del circuito cuenta una NAND como `nand` y no como `and`.
- Las herramientas de prueba con variable de entorno (`VSIM_MIGRATE_PATHS`, `VSIM_WRITE_SHOWCASE`, `VSIM_WRITE_BUNDLED`) ya no quedan «UP-TO-DATE» en Gradle cuando la variable está puesta.
- Para desarrolladores: `VSIM_MIGRATE_PATHS` reescribe tus archivos al formato 4 (ver «Para desarrolladores»). Los ejemplos y fixtures del repositorio ya están en el formato 4.
- Dos piezas nuevas en la paleta, en la categoría «Memoria»: **RAM** y **ROM**, de 2 a 16M palabras de 1 a 64 bits, con
  pines `A`, `D`, `Q`, `WE`, `CS` y reloj. Lectura combinacional, escritura en el flanco de bajada del reloj (o «Sin
  reloj»), conexión por bus o con pines sueltos, «Un solo bus D», nombre y retardo. Cambiar el tamaño nunca corta el
  contenido. Una memoria vacía casi no ocupa espacio, ni siquiera de 16M × 64 bits.
- En el lienzo, la memoria muestra en su barra de título «RAM · 256 × 8», su nombre, dos luces, `RD` (azul, al leer)
  y `WR` (naranja, al escribir; la ROM solo tiene `RD`), cada una encendida por sus propios accesos y al menos 200 ms
  (en pausa, hasta el paso siguiente), así que una escritura seguida de una lectura enciende las dos; apagadas se ven
  con su borde y tenues con «CS = 0» cuando no está conectada; y un cuadrito ▾ / ▸ que
  con un clic muestra u oculta la ventana de contenido. La ventana lista las palabras alrededor de la dirección actual
  («dirección  palabra», letra de ancho fijo, la dirección en un tono apagado y la palabra en el color normal) con la palabra leída y la escrita en un recuadro de su color (gana la escritura si es la misma) y una palabra
  escrita sin definir como «Z». Una memoria vinculada a un archivo muestra una cadenita en la barra de título. Una memoria sin etiqueta se llama RAM1, RAM2, ROM1… (numeradas por tipo), igual en el
  lienzo, el mapa, el editor y «Volcar…». Los colores de lectura y escritura son dos roles nuevos de cada esquema de
  colores.
- Editor de contenido: doble clic sobre la pieza (o «Editar contenido…» en el clic derecho) abre una grilla de
  direcciones y palabras que se escribe sobre las celdas, en hexadecimal, decimal o binario, con Ctrl+C y Ctrl+V de
  texto hexadecimal, Ctrl+Z y Ctrl+Y dentro del editor, «Llenar…», «Ir a…» (Ctrl+G), «Importar…», «Exportar…» y
  «Volcar…» (RAM) en el formato de VSim y en el de Logisim (v2.0 raw), con ícono y texto en cada botón. Aceptar es un
  solo paso de deshacer. Con la simulación corriendo, o en pausa, la RAM muestra sus valores vivos, su lectura
  (azul) y su escritura (naranja) con la misma regla de las luces, y la barra de estado dice «Última lectura … · Última
  escritura …». «Exportar…» y «Volcar…» nunca escriben sobre un archivo enlazado de ninguna memoria del
  circuito; «Llenar…» propone la selección o las filas que se ven, y pide confirmar sobre 65.536 palabras; «Enlazar…»
  pregunta antes de descartar cambios sin aceptar.
  «Restaurar contenido inicial» y «Volcar…» también están en el clic derecho de la RAM.
- Editor de contenido, pulido: la celda que editas mientras la grilla muestra valores vivos (corriendo o en pausa)
  se ve con su valor nuevo, en negrita y con un tinte, y su globo dice «Editado · inicial: FF · en la simulación
  ahora: 0A»; la celda solo acepta los dígitos de la base (hex 0-9 A-F, en mayúscula; Dec 0-9; Bin 0-1) y no más de
  los que cabe en el ancho (las demás teclas se ignoran); la columna de direcciones se ve en un tono más tenue que
  las palabras, también en una memoria enlazada y en el esquema oscuro. La barra de herramientas tiene botones del
  mismo estilo, agrupados (archivo, enlace, edición, restaurar) y con la base al final, y el editor se abre del
  ancho que necesita; la barra «Enlazada a …» ocupa su propia fila con sus botones completos y, con la barra de
  estado, muestra el ícono de enlace y solo el nombre del archivo (la ruta completa en el globo; si aun así no cabe,
  se acorta por el medio). Nueva barra de búsqueda (la lupa de la barra de herramientas, o Ctrl+F): escribes un
  valor en la base actual y ▲ / ▼ (o Enter, Mayús+Enter, F3) van a la palabra anterior o siguiente que lo tiene,
  dando la vuelta en los extremos, con «3 de 12», la celda elegida y a la vista; Esc la cierra. Busca en los valores
  vivos cuando se ven y recorre solo las palabras guardadas, así que una memoria de 16M palabras no se traba (buscar
  0 cuenta las no definidas sin visitarlas).
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
- Ejemplos nuevos en Archivo ▸ Ejemplos: «Programa en ROM» (un contador recorre una ROM de 16 palabras con Fibonacci y un
  display las muestra en hexadecimal) y «RAM que se llena» (un contador de direcciones y otro de pasadas escriben una
  RAM de 16 palabras: la pasada 0 deja 00 en todas, la 1 deja 01 y así hasta FF, y vuelve a 00).
- Al cambiar la base del editor de contenido (Hex, Dec, Bin), las columnas se ajustan al ancho de las palabras en esa
  base; si la fila no cabe, la grilla se desplaza hacia el lado en vez de cortar los valores con «…».
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

### Cambiado

- La paleta se reorganiza en nueve categorías que sigue un curso: Compuertas, Cableado, Aritmética, Multiplexores,
  Secuenciales, Memoria, Entrada/Salida, Depuración y Mis componentes. Desaparecen «Básicas», «Bloques» y «Pines de
  componente» (los pines de entrada y de salida pasan a Entrada/Salida). Los circuitos, favoritos y recientes de antes
  no cambian.
- El multiplexor ofrece 8 entradas además de 2 y 4; D0, D1, Y y los circuitos de antes no cambian.
- El Buffer triestado tiene «Ancho (bits)» (1 por omisión) y «Negar salida»; los triestado de antes se abren y
  funcionan igual.
- El globo de cada pieza de la paleta es ahora el primer párrafo de su descripción, así que todas las piezas tienen
  uno (antes solo los bloques, los pines, los túneles y la memoria).
- El flip-flop D tiene también Q̅, debajo de Q, y el parámetro «Mostrar Q̅» para ocultarlo. Los flip-flops D de los
  circuitos de antes se abren con Q̅ oculto, con sus tres pines de siempre, así que un cable que baja derecho desde Q
  no queda unido a Q̅ y el circuito simula igual.
- El registro es más delgado (12 × 4 en vez de 12 × 7): «REG» chico en la esquina y el valor grande al medio; en
  binario los dígitos van en grupos de 4 y, con más de 16 bits, en dos líneas (el registro mide 12 × 5). Los
  circuitos guardados antes se abren conectados: VSim agrega un tramo corto de cable desde cada pin de abajo hasta
  donde estaba. El contador no cambia.
- Un clic en un interruptor (o en un bit de uno de varios bits) ahora cambia su «Valor»: Propiedades muestra el valor
  nuevo al instante, el archivo queda modificado, al guardar se escribe ese valor y cambiar otra propiedad ya no lo
  devuelve al valor guardado. Se deshace con Ctrl+Z, que también lo devuelve en la simulación; varios clics seguidos
  en el mismo interruptor son un solo paso. Los valores que pone la IA al probar el circuito siguen siendo solo de la
  simulación: no cambian «Valor», el archivo ni la historia de deshacer.
- Al cambiar un parámetro que mueve pines (las «Entradas» de una compuerta, las de un multiplexor), las sondas de esos pines pasan al pin con el mismo nombre y se quitan si ya no existe; deshacer las devuelve.
- Hay un solo tipo por compuerta (AND, OR, XOR y Buffer) en vez de uno por cada número de entradas. Los circuitos,
  componentes, librerías y lo copiado antes se abren igual, con los pines en el mismo lugar; al guardarlos se
  escriben con el formato nuevo (versión 4), que las versiones anteriores de VSim 2 no abren.
- La paleta tiene una entrada por compuerta: AND, OR, XOR, NAND, NOR, XNOR, Buffer y NOT, en vez de una por cada
  número de entradas. NAND, NOR, XNOR y NOT colocan la compuerta con «Negar salida» marcada, también al arrastrarlas,
  desde la inserción rápida, «Favoritos» y «Recientes»; los favoritos de antes aparecen con la entrada nueva.
- El nombre de una compuerta sigue a «Negar salida» en Propiedades, el árbol de componentes y la búsqueda: un AND
  negado se llama NAND y un Buffer negado, NOT.

### Arreglado

- La fila de muestras de color (el LED, las entradas de túnel, los marcos y las notas) ya no corta las últimas muestras en
  un panel Propiedades angosto: se parte en las líneas que hagan falta, alineadas a la izquierda, sin desplazarse de lado.
- Un clic en un interruptor ya no vuelve a armar el circuito: los cables y las etiquetas de bus no pierden su color por
  un instante, la pestaña Problemas no se vacía un momento y en un circuito grande el clic sigue siendo inmediato.
- Un clic justo después de deshacer otro clic parte del valor que dejó el deshacer, no del que se acababa de deshacer.
- Un clic que deja el interruptor en el valor que ya tenía guardado (la tabla de verdad o la IA lo habían cambiado solo
  en la simulación) ya no deja un paso de deshacer vacío ni marca el archivo como modificado.
- En «Paso a paso», deshacer o rehacer un clic en un interruptor hace brillar los cables que cambian, como el clic.
- «Deshacer lo que hizo la IA» y «Deshacer etapa» ya no se detienen en tus clics en interruptores que quedaron encima
  de los pasos de la IA (te culpaban de un cambio que solo movía entradas): los deshacen también y el aviso lo dice.
- Un paso de la IA que espera «Siguiente» ya no falla si mientras tanto haces clic en un interruptor que ese paso
  cambia: se aplica sobre el valor que le diste.
- Escribir en «Ancho (bits)» de un interruptor un número mayor que 32 (un 70 camino a 7) ya no da un error interno: el
  bloque de lecturas bajo «Valor» se oculta hasta que el ancho es posible.
- Cambiar «Números» de un interruptor ya no lo reinicia (perdía el valor de una fila de la tabla de verdad probada en el
  circuito), y volver a «Sin signo» con un decimal negativo ya no queda bloqueado: el valor se reescribe como su patrón
  de bits sin signo (en 8 bits, `-1` pasa a `255`).
- La fila de muestras de color ya no pide acomodarse una y otra vez mientras todavía no tiene ancho.

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
