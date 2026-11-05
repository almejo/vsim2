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

- **Ruta**: `▤ circuito › ⬚ Ram (c5) › ⬚ Selector (c2)`. Cada paso es el componente con la referencia de la pieza (su
  etiqueta, o su número si no tiene). Un clic en el circuito trae su pestaña; un clic en un paso entra a ese
  componente con la ruta anterior como valores. El último paso es donde estás y no hace nada.
- **Valores de**: lista todas las piezas por las que se llega a este componente, como «c3 en Doble inversor (c2)» o,
  si está puesto en el circuito mismo, «c1 en lrv32im» (con el nombre del circuito). Elegir una cambia los valores que se ven y la ruta.
  «Sin valores (no se simula)» deja la pestaña sin valores; es lo único que hay cuando ninguna pieza usa el componente.
- **Se usa en N lugares**: dice cuántas piezas usan el componente («Se usa en 1 lugar», «No se usa en ninguna pieza»).
  Con más de un lugar el texto va en ámbar, para recordar que lo que cambias adentro cambia todos.
- **Ver la pieza ↗**: lleva a la pestaña que contiene la pieza de la ruta (la del circuito, o la de otro componente),
  la selecciona y mueve la vista hasta ella. Está apagado sin valores.
- **Guardar en mi librería / Agregar a mi librería**: «Agregar» aparece cuando la librería no tiene el componente
  (pregunta lo mismo que la paleta si el id ya existe); «Guardar» aparece cuando la copia del circuito es distinta de
  la de la librería y escribe el componente con los que usa. Después de guardar, el botón desaparece. Nada se escribe
  en la librería por sí solo.
- **Pines bloqueados**: si el componente está dentro de otro componente que el circuito usa, la barra lo explica:
  «Este componente se usa dentro de Doble inversor: sus pines no se pueden cambiar todavía».
- **Un cambio que no se hizo**: si intentas adentro un cambio que no se puede hacer (borrar un pin bloqueado, por
  ejemplo), no cambia nada y la barra lo dice en ámbar durante unos segundos; la barra de estado también lo dice.

## Panel Proyecto

Arriba de la paleta, en la columna de la izquierda, el panel «Proyecto» lista el circuito y todas las piezas de
componentes propios que tiene, de afuera hacia adentro.

- **Las filas**: la primera es el circuito, con su nombre y cuántas piezas tiene («1 pieza», «12 piezas»). Debajo va cada pieza de un componente propio,
  con el nombre de su componente y, a la derecha, el nombre de la pieza (su etiqueta, o su identificador si no tiene).
  Las piezas que están dentro de otros componentes van más a la derecha, un nivel por componente. Un circuito sin
  componentes propios muestra solo la primera fila.
- **Entrar**: un doble clic en una fila, o Enter con la fila elegida, entra a esa pieza (ver
  [Entrar a un componente](#entrar-a-un-componente)): la pestaña del componente muestra los valores de esa pieza.
- **La fila activa**: en la pestaña de un componente, la fila de la pieza cuyos valores se ven se pinta con el color
  del grupo.
- **Clic derecho en una fila**:
  - «Entrar».
  - «Ver la pieza»: igual que en la [barra de lugar](#la-barra-de-lugar), lleva a la pestaña que contiene la pieza (la
    del circuito, o la del componente que la tiene adentro, con la ruta de esa pieza), la selecciona y mueve la vista
    hasta ella. Funciona desde cualquier pestaña.
  - «Cambiar forma…» y «Propiedades»: de una pieza de la pestaña actual; apagados para las demás.
- **El alto del panel**: se arrastra con el divisor que lo separa de la paleta, y VSim lo recuerda.
- **Clic derecho en el árbol del inspector**: en la pestaña «Componentes» del inspector, una pieza de un componente
  propio, a cualquier profundidad, también tiene «Entrar»; entra con la ruta de esa pieza.

## Adentro de un componente: qué cambia

En la pestaña de un componente se edita con las mismas herramientas que en el circuito. Estas son las diferencias:

- **La paleta**: el mismo componente y los componentes que lo contienen aparecen en gris y no se pueden poner adentro.
  Al pasar el mouse dicen por qué: «No se puede usar dentro de sí mismo» o «Contiene al componente que estás
  editando». En la sección «Componentes de este circuito» están todos los componentes del circuito, no solo los que ya
  hay adentro.
- **Un componente que se usa dentro de otro**: por ahora sus pines no se pueden cambiar desde adentro. «Pin de
  entrada» y «Pin de salida» aparecen en gris en la paleta. Si se intenta borrar un pin, cambiarle el nombre o el
  ancho, no cambia nada y la barra de lugar y la barra de estado dicen por qué: «Este componente se usa dentro de Debugger: sus pines no se
  pueden cambiar todavía». Mover un pin adentro sí se puede, porque no cambia el componente por fuera.
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
- **Un pin nuevo que no cabe**: si al agregar un pin adentro su lugar en el borde de una pieza toca un cable o un pin,
  esa pieza pone el pin nuevo en otro lugar libre de su borde y mantiene el resto como estaba, sin unir nada.
  Problemas lo dice: «Inversor: el pin b quedó en otro lugar (su lugar estaba ocupado)».
- **Memorias**: una RAM abierta desde la pestaña muestra su contenido en la pieza cuyos valores se ven. El contenido de
  una ROM es del componente, el mismo en todas sus piezas, y su editor lo dice en el título: «Contenido de ROM1 de Ram —
  todas las piezas». El «Mapa de memoria» de la pestaña sigue a la pieza que se muestra.
- **Terminales**: la ventana de un Terminal de adentro muestra el de la pieza cuyos valores se ven. Un Terminal con el
  mismo nombre en el circuito y adentro de un componente son dos ventanas distintas, y cada una recuerda su lugar y su
  tamaño. Si la pestaña pasa a mostrar otra pieza, la ventana de la pieza anterior se cierra. Abrirla desde otra
  pestaña de la misma pieza trae al frente la que ya estaba abierta.
