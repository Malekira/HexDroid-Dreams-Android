# HexDroid Dreams 1.0.13

Base: Dreams 1.0.12, commit `ae84aa9`. Versión 1.0.13 (52).

- El botón de completar nicks muestra **Tab** a la izquierda del campo de escritura.
  Permanece visible; se desactiva cuando no hay nicks para completar.
- Las pestañas de salas y privados aparecen arriba, debajo de la barra principal.
  La fila mide 28 dp y usa etiquetas ajustadas al texto, sin el ancho mínimo de
  las pestañas anteriores. Se desplaza horizontalmente y mantiene los contadores.
- Para cerrar una conversación desde su pestaña, mantenerla pulsada y elegir Cerrar.
  Un toque normal únicamente selecciona la conversación.
- El botón ☰ permanece accesible con las pestañas activadas. Los árboles de salas
  y privados conservan su expansión independiente y sus opciones anteriores.
- Se mantienen los gestos, borradores, Retroceso e historial de la 1.0.12.

Al actualizar se activa la fila superior. Las opciones posteriores se respetan.
La clave interna de preferencias anterior se conserva para compatibilidad con
copias de ajustes; su etiqueta en Ajustes indica ahora su posición superior.

Las pruebas Android verifican la ubicación y altura de las pestañas, el botón Tab
y la compleción de nicks en salas y privados, el acceso simultáneo a ambos árboles
y el cierre mediante una elección explícita. El workflow ejecuta también las
regresiones anteriores.
