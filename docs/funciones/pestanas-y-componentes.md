# Pestañas y componentes

Un componente propio se edita en una pestaña de su circuito, con el editor de siempre. Cómo funcionan la ventana, las
pestañas, los grupos y el plegado está en [Ventanas y pestañas](interfaz-preferencias-y-sesion.md#ventanas-y-pestañas).
Cómo se crean y se guardan los componentes propios está en
[Componentes propios](componentes.md#componentes-propios).

## Entrar a un componente

- **Cómo se entra**: con doble clic en el cuerpo de una pieza de un componente propio. Un doble clic en su etiqueta
  sigue editando la etiqueta, y las Propiedades de la pieza siguen en el inspector y en el clic derecho. También se
  entra de estas otras formas:
  - con un clic en el botón **«Entrar ↘»**, que aparece en la esquina de arriba a la derecha de la pieza al pasar el
    mouse;
  - con **«Entrar»**, la primera opción del clic derecho de la pieza, antes de «Desarmar»;
  - con **Enter**, si la única pieza seleccionada es de un componente propio.
- **Qué piezas se pueden entrar**: las de los componentes que están en el circuito, incluidas las copias que vinieron
  de la librería o de un circuito de Logisim. Las piezas de la paleta (compuertas, memorias, Terminal…) no se pueden
  entrar, y su doble clic hace lo de siempre.
- **La pestaña del componente**: se abre al final del grupo de su circuito, con un ícono de chip, el nombre del
  componente, el borde de arriba punteado y el color del grupo. Al pasar el mouse dice «Ram — componente de lrv32im».
  Se abre mostrando el componente entero, como «Ver todo el circuito». Con «Animación» encendida, la vista parte del
  lugar y del tamaño que tenía la pieza en el circuito y crece hasta mostrar todo el componente.
- **Volver a entrar**: si el grupo ya tiene una pestaña de ese componente, entrar de nuevo la trae al frente en vez de
  abrir otra. Esa pestaña pasa a mostrar los valores de la pieza por la que entraste. Si el grupo está plegado, entrar
  lo despliega.
- **Varias pestañas del mismo componente**: Ventana ▸ «Nueva pestaña del mismo circuito», usada en una pestaña de
  componente, abre otra pestaña del mismo componente. Las dos se llaman «Ram (1)» y «Ram (2)», y cada una puede
  mostrar los valores de una pieza distinta. Al entrar de nuevo se usa la última que estuvo activa, y las otras siguen
  mostrando su pieza.
- **Un componente dentro de otro**: dentro de una pestaña de componente también se puede entrar a sus piezas de otros
  componentes. La pestaña nueva muestra la pieza de adentro de la pieza que estabas mirando.

## Editar por dentro

- **Qué se edita**: lo que se cambia en la pestaña de un componente cambia el componente del circuito. Afecta a todas
  sus piezas, y cada cambio es un solo paso de deshacer del circuito, que se puede deshacer desde cualquiera de sus
  pestañas. La librería no cambia sola.
- **Qué valores se ven**: la pestaña muestra los valores de una sola pieza mientras el circuito simula: la pieza por
  la que entraste. Si esa pieza se borra, la pestaña pasa a mostrar otra pieza del mismo componente. Si no queda
  ninguna, la pestaña queda sin valores.
- **Cerrar**: cerrar la pestaña de un componente no pregunta nada, porque sus cambios ya son del circuito.
  - Ventana ▸ «Cerrar los componentes de este circuito» cierra todas las pestañas de componentes del grupo.
  - Cerrar la última pestaña del circuito cierra su grupo, con las pestañas de sus componentes.
- **Un componente que deja de existir**: si el componente sale del circuito (por ejemplo, al deshacer el paso que lo
  agregó), sus pestañas se cierran en todas las ventanas. La barra de estado de la pestaña del circuito avisa una vez:
  «El componente Ram ya no existe en el circuito; se cerró su pestaña».
- **Al volver a abrir VSim**: las pestañas de componentes vuelven con su pieza, como el resto de la sesión. Ver
  [Sesión y recuperación](interfaz-preferencias-y-sesion.md#sesión-y-recuperación).

## La barra de lugar

Debajo de la fila de pestañas, cada pestaña de componente muestra una barra con el color de su grupo. Las pestañas
de circuito no la tienen.

- **Ruta**: `▤ circuito › ⬚ Ram › ⬚ Selector 2`. Cada paso es la pieza con el mismo nombre que tiene en el
  [árbol del circuito](#árbol-del-circuito) (su etiqueta, o su tipo, numerado si hay varias iguales sin etiqueta). Un
  clic en el circuito trae su pestaña; un clic en un paso entra a ese componente con la ruta anterior como valores. El
  último paso es donde estás y no hace nada.
- **Valores de**: lista todas las piezas por las que se llega a este componente, como «Inversor 2 en Doble inversor»
  o, si está puesto en el circuito mismo, «Inversor en lrv32im» (con el nombre del circuito). Elegir una cambia los
  valores que se ven y la ruta.
  «Sin valores (no se simula)» deja la pestaña sin valores; es lo único que hay cuando ninguna pieza usa el componente.
- **Se usa en N lugares**: dice cuántas piezas usan el componente («Se usa en 1 lugar», «No se usa en ninguna pieza»).
  Con más de un lugar el texto va en ámbar, para recordar que lo que cambias adentro cambia todos.
- **Ver la pieza ↗**: lleva a la pestaña que contiene la pieza de la ruta (la del circuito, o la de otro componente),
  la selecciona y mueve la vista hasta ella. Está apagado sin valores.
- **Guardar en mi librería / Agregar a mi librería**: «Agregar» aparece cuando la librería no tiene el componente
  (pregunta lo mismo que la paleta si el id ya existe); «Guardar» aparece cuando la copia del circuito es distinta de
  la de la librería y escribe el componente con los que usa. Después de guardar, el botón desaparece. Nada se escribe
  en la librería por sí solo.
- **Un cambio que no se hizo**: si un cambio adentro no se puede hacer, no cambia nada y la barra lo dice en ámbar
  durante unos segundos; la barra de estado también lo dice.

## Adentro de un componente: qué cambia

En la pestaña de un componente se edita con las mismas herramientas que en el circuito. Estas son las diferencias:

- **La paleta**: el mismo componente y los componentes que lo contienen aparecen en gris y no se pueden poner adentro.
  Al pasar el mouse dicen por qué: «No se puede usar dentro de sí mismo» o «Contiene al componente que estás
  editando». En la sección «Componentes de este circuito» están todos los componentes del circuito, no solo los que ya
  hay adentro. Pegar o soltar una pieza así tampoco la pone adentro: no cambia nada y la barra de lugar dice por qué
  («Doble inversor contiene al componente que estás editando: no se puede poner adentro»).
- **Un componente que se usa dentro de otro** (el «Medio sumador» dentro del «Sumador completo», la ALU dentro del
  LRV32IM): sus pines se cambian desde adentro igual que los de cualquier componente. Agregar, borrar, renombrar o
  cambiar el ancho de un «Pin de entrada» o «Pin de salida» cambia también las piezas que están dentro de los otros
  componentes: el pin nuevo va a un lugar libre del borde y los cables de adentro siguen a los pines, como en el
  circuito. Los componentes que lo contienen no cambian por fuera. Todo es un solo paso de deshacer, que devuelve
  cada componente como estaba. Lo que no puede seguir se lista en Problemas con su camino, en cada lugar donde se usa
  el componente que lo contiene: «Sumador completo › Medio sumador 2: el pin b quedó en otro lugar (su lugar estaba
  ocupado)» o «Sumador completo › Medio sumador 2: pin b sin conectar». Mover un pin adentro no cambia nada por
  fuera.
- **Quitar un pin**: borrar un «Pin de entrada» o «Pin de salida» adentro quita ese pin de todas las piezas. Los
  cables que llegaban a él, en el circuito o dentro de otros componentes, se quedan donde estaban, sueltos, y
  Problemas los lista con la pieza: «Inversor: el pin a ya no existe; su cable quedó suelto». La fila se va al
  deshacer el cambio; como las demás filas de un cambio adentro, no se guarda con el circuito.
- **Cambiar un componente que está adentro**: en la pestaña de un componente, «Editar descripción…», «Cambiar
  forma…» para todas las piezas y «Actualizar desde la librería» sobre una pieza de adentro cambian ese componente en
  el circuito, en un solo paso de deshacer, igual que desde la pestaña del circuito y con sus mismas reglas (una forma
  que movería los pines de un componente usado dentro de otro se rechaza y se dice por qué). «Crear componente desde
  selección…» sin reemplazar la selección agrega el componente al circuito. Un cambio así que llegue por otro camino
  no se hace, y la barra de lugar dice «Inversor no se cambia desde aquí: usa su menú en la pestaña del circuito».
- **Los pines del componente**: su valor viene de afuera, de la pieza cuyos valores muestra la pestaña. Un clic no los
  cambia y no se les puede escribir un valor; la barra de estado dice «Su valor viene de afuera».
- **Interruptores y botones de adentro**: actúan solo sobre la pieza que muestra la pestaña, como una entrada de la
  simulación. No cambian el componente ni marcan el circuito como modificado.
- **Sin valores**: si el componente no tiene piezas en el circuito, o si se eligió no mostrar ninguna, la pestaña no
  simula. Todo se ve sin valor, nada brilla, los clics en interruptores y botones no hacen nada, y la barra de estado
  dice «Sin valores: elige una pieza en “Valores de”».
- **Deshacer**: Edición ▸ Deshacer y Rehacer dicen dónde fue el paso: «Deshacer agregar (en Ram)». Se puede deshacer
  desde cualquier pestaña del circuito, y la vista no salta a la pestaña del componente.
- **Si el componente cambia desde otra parte** (deshacer en la pestaña del circuito, «Actualizar desde la librería»,
  la IA): la pestaña se pone al día. Si en ese momento estabas arrastrando algo o dibujando un cable adentro, ese
  gesto se cancela, como con Esc. La selección conserva lo que sigue existiendo.
- **Problemas**: todas las pestañas de un circuito muestran la misma lista de Problemas. Un problema que está adentro
  de un componente se nombra con su camino («U1 › U3 (NOT)»). F8, o un doble clic en la lista, lleva a donde está: si
  está adentro de un componente, entra a ese componente con los valores de la pieza del camino y selecciona la parte.
  Desde la pestaña de un componente, un problema del circuito lleva a la pestaña del circuito.
- **Memorias: cada pieza tiene su propio contenido**: una RAM o una ROM abierta desde la pestaña muestra el contenido
  de la pieza que se ve en «Valores de», y lo que se escribe ahí (también lo que se importa con «Importar…») cambia
  solo esa pieza: con dos «Memoria chica» en el circuito, escribir la palabra 1 adentro con «Valores de: Memoria 1» no
  toca a Memoria 2. El título lo dice: «Contenido de RAM1 de Memoria chica — solo Memoria 1». Al iniciar la
  simulación o con «Reiniciar», cada pieza parte de su propio contenido; se guarda con el circuito y Deshacer lo
  quita. Dos botones más abajo:
  - **«Usar para todas las piezas»**: lo que se ve pasa a ser el contenido del componente, en todas las piezas que no
    tengan uno propio, y esta pieza deja de tener el suyo. Un solo paso de deshacer.
  - **«Volver al del componente»**: la pieza olvida su contenido propio y vuelve a usar el del componente.

  Con «Sin valores» el editor cambia el contenido del componente, el de todas las piezas, y el título dice «— todas
  las piezas». Un archivo enlazado a una memoria de adentro es del componente, el mismo en todas las piezas, y la
  barra del enlace lo dice; se guarda con el circuito (relativo a su carpeta, como los enlaces del circuito) y sigue
  enlazado al volver a abrirlo. Soltar un archivo de memoria en la pestaña de un componente lo deja enlazado igual.
  VSim vuelve a leer el archivo cuando cambia mientras hay una pestaña del componente abierta. Una pieza que tiene
  su propio contenido de esa memoria sigue partiendo de él, no del archivo, y Problemas lo dice: «Memoria chica 1 ›
  RAM: usa su propio contenido, no el archivo enlazado». F8 lleva a la memoria con los valores de esa pieza (su
  editor tiene «Volver al del componente»), y el clic derecho en la fila ofrece lo mismo. En Propiedades, una memoria de adentro con contenido propio en la pieza que se ve dice
  «Contenido propio de Memoria 1». Si un componente tiene otro adentro, el contenido propio de la pieza de afuera
  manda sobre el de las de adentro. El «Mapa de memoria» de la pestaña sigue a la pieza que se muestra. Al
  desarmar una pieza, sus memorias se quedan con el contenido propio que tenía. Si una pieza guarda contenido propio
  de una memoria que ya no está en el componente (se borró adentro), ese contenido no se usa y Problemas lo dice:
  «Memoria chica: guarda contenido propio de una memoria que ya no está en el componente (c10)»; el clic derecho en la
  fila ofrece «Olvidar ese contenido», un solo paso de deshacer.
- **Terminales**: la ventana de un Terminal de adentro muestra el de la pieza cuyos valores se ven. Un Terminal con el
  mismo nombre en el circuito y adentro de un componente son dos ventanas distintas, y cada una recuerda su lugar y su
  tamaño. Si la pestaña pasa a mostrar otra pieza, la ventana de la pieza anterior se cierra. Abrirla desde otra
  pestaña de la misma pieza trae al frente la que ya estaba abierta.

## Árbol del circuito

En la columna de la izquierda, arriba de la paleta, el árbol «Circuito» muestra todo el circuito: lo que hay en él y
lo que hay dentro de cada componente propio, a cualquier profundidad. Es el mismo en todas las pestañas del circuito.

- **Las filas**: la primera es el circuito, con su nombre y cuántas piezas tiene («1 pieza», «12 piezas»). Debajo va
  cada pieza, en el orden del circuito, con el ícono de la paleta. Una pieza de un componente propio se despliega en
  las piezas de adentro. Los cables, los marcos y las notas no son piezas y no aparecen; las filas no muestran valores.
- **Los nombres**: una pieza se llama por su etiqueta («IR · Registro», con el tipo detrás) o, si no tiene, por su
  tipo («Ram», «AND de 3 entradas»). Si en un mismo lugar hay varias sin etiqueta del mismo tipo, se numeran en el
  orden del circuito: «Multiplexor 1» … «Multiplexor 4». El identificador de la pieza («c5/c2») está en la ayuda al
  pasar el mouse. La [barra de lugar](#la-barra-de-lugar) y Problemas las nombran igual.
- **Clic en una fila**: lleva a la pieza. Si está en otro lugar, primero va a esa pestaña (la del circuito, o la del
  componente que la tiene adentro, con los valores de esa pieza, abriéndola si hace falta); después la selecciona y la
  vista viaja hasta ella, y el inspector muestra sus propiedades. Es el mismo «Ver la pieza» de la barra de lugar y de
  F8. Un clic en la fila del circuito trae su pestaña.
- **Doble clic o Enter** en una pieza de un componente propio entra a ella (ver
  [Entrar a un componente](#entrar-a-un-componente)). En esas filas el clic simple espera un instante, por si viene el
  segundo.
- **Clic derecho**: «Entrar» (en un componente propio), «Ver la pieza», «Cambiar forma…» (en un componente propio) y
  «Propiedades». Las dos últimas llevan primero a la pieza, esté donde esté.
- **Buscar**: el campo de arriba busca en todo el circuito, también dentro de los componentes, con las mismas reglas y
  opciones Aa y .\* que la barra de búsqueda. Los resultados se listan uno debajo de otro con su ruta en gris («en
  Ram › Muldiv») y lo que coincide en negrita; debajo del campo dice cuántos hay, o «Nada se llama así en este
  circuito.». Enter va al primero; Esc vacía el campo y vuelve el árbol como estaba. Ctrl+F con el foco en el árbol va
  al campo.
- **Sigue a la pestaña activa**: cada pestaña abre en el árbol su lugar (el circuito, o la pieza cuyos valores
  muestra) y lo marca con el color del grupo. Elegir una sola pieza en el lienzo elige su fila.
- **Acordeón**: las cabeceras «Circuito» y «Paleta» pliegan su panel con un clic (o con Alt+1 y Alt+2, que se pueden
  cambiar en Atajos de teclado; también en Ver ▸ «Árbol del circuito» y «Paleta»), y el otro ocupa el espacio. Con
  los dos abiertos, el divisor entre ellos se arrastra y VSim lo recuerda. Lo plegado es de cada ventana, vale para
  todas sus pestañas y vuelve al abrir VSim.

## Ir a… desde la barra de búsqueda

La barra de búsqueda (Ctrl+F) también ofrece, en una segunda fila bajo el campo («Ir a:»), las pestañas abiertas de la
ventana y los componentes del circuito cuyo nombre coincide con lo que escribes, con las mismas reglas que la
búsqueda (sin distinguir tildes; «Aa» y «.*» como estén). Cada uno es un botón: «lrv32im» o «⬚ Ram» para una pestaña, y
«⬚ Muldiv — componente, 3 usos» para un componente del circuito (también «1 uso» y «sin usos»).

- Un clic en una pestaña la trae al frente; un clic en un componente lo **entra** (ver
  [Entrar a un componente](#entrar-a-un-componente)) con los valores de su primer uso, o «Sin valores» si el circuito
  no lo usa. La barra se cierra al elegir.
- Con el foco en el campo, **Ctrl+Intro** va al primer botón, y **Intro** también cuando el circuito no tiene ninguna
  pieza que coincida. Sin texto o sin coincidencias, la fila no aparece.

## Mirar adentro: ver el interior sin entrar

Para mirar qué hay dentro de un componente sin abrir su pestaña:

- **Activar «Mirar adentro»**: el botón del ojo de la barra de herramientas o Ver ▸ «Mirar adentro» (sin atajo de
  teclado por defecto; se puede asignar en Atajos de teclado). Con el modo activado no se marca ninguna pieza: solo la
  pieza de un componente propio que está bajo el mouse se destaca con un contorno del color de selección mientras se ve
  su panel.
- **Ver adentro**: al pasar el mouse sobre una de esas piezas el panel flotante crece desde la pieza (unos 200 ms) con
  el interior del componente, dibujado como en el lienzo y ajustado a 320 × 240 píxeles como máximo, con los valores de
  esa pieza mientras se simula (si no hay valores que mostrar, se ve sin ellos). El panel no toma el foco ni se puede
  usar; sigue a la simulación y se encoge de vuelta hacia la pieza al sacar el mouse, al tocar el lienzo (un clic o
  arrastrar) o con Esc (no vuelve hasta que el mouse salga de esa pieza y vuelva).
- **Con Alt**: mantener Alt apretada mientras se pasa el mouse sobre una pieza de un componente propio muestra el
  panel aunque el modo esté desactivado.
- **Desde el clic derecho**: «Mirar adentro», en una pieza de un componente propio, muestra su panel aunque el modo
  esté desactivado, hasta que el mouse salga de la pieza.
- «Mirar adentro» es de cada ventana y no se guarda al cerrarla. No cambia nada del circuito ni deja pasos de deshacer.
