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

## Panel Proyecto

Arriba de la paleta, en la columna de la izquierda, el panel «Proyecto» lista el circuito y todas las piezas de
componentes propios que tiene, de afuera hacia adentro.

- **Las filas**: la primera es el circuito, con su nombre y «N piezas». Debajo va cada pieza de un componente propio,
  con el nombre de su componente y, a la derecha, el nombre de la pieza (su etiqueta, o su identificador si no tiene).
  Las piezas que están dentro de otros componentes van más a la derecha, un nivel por componente. Un circuito sin
  componentes propios muestra solo la primera fila.
- **Entrar**: un doble clic en una fila, o Enter con la fila elegida, entra a esa pieza (ver
  [Entrar a un componente](#entrar-a-un-componente)): la pestaña del componente muestra los valores de esa pieza.
- **La fila activa**: en la pestaña de un componente, la fila de la pieza cuyos valores se ven se pinta con el color
  del grupo.
- **Clic derecho en una fila**:
  - «Entrar».
  - «Ver la pieza»: selecciona la pieza y viaja hasta ella cuando es una pieza de la pestaña actual; si está dentro de
    otro componente, abre la pestaña de ese componente. Está apagado para una pieza del circuito cuando la pestaña
    actual es la de un componente.
  - «Cambiar forma…» y «Propiedades»: de una pieza de la pestaña actual; apagados para las demás.
- **El alto del panel**: se arrastra con el divisor que lo separa de la paleta, y VSim lo recuerda.
- **Clic derecho en el árbol del inspector**: en la pestaña «Componentes» del inspector, una pieza de un componente
  propio, a cualquier profundidad, también tiene «Entrar»; entra con la ruta de esa pieza.
