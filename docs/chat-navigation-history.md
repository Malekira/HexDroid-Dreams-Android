# Pestañas, navegación e historial

- Las conversaciones aparecen en pestañas sobre el campo de escritura.
- Deslizar hacia la izquierda en el área de mensajes abre la pestaña siguiente;
  hacia la derecha abre la anterior. No se salta del último extremo al primero.
- El gesto requiere al menos 56 dp después de superar el umbral táctil del sistema.
  Los gestos sobre el campo de escritura y sobre las propias pestañas no cambian de sala.
- Retroceso no navega fuera del chat mientras el campo contenga texto, incluidos espacios.
  Con el campo vacío se mantiene el comportamiento habitual de Android.
- Se activa el historial local, incluido Status. Las respuestas de `/whois`, incluso las mostradas solo en
  el panel flotante, se guardan en el historial WHOIS. Las copias dirigidas a Status o a
  una sala conservan también su historial habitual. Los mensajes
  privados con el usuario o servicio `whois` se guardan como cualquier privado.
- Los registros se recuperan al abrir sus conversaciones. El historial permanente de la fase 3
  se conserva hasta que el usuario lo elimine.

Las pestañas y el registro de Status se activan también al actualizar una instalación
anterior. Se respeta el interruptor global de historial si el usuario lo había desactivado. Al guardar
cualquier ajuste se registra la versión de esta configuración; las elecciones posteriores
del usuario, como desactivar el historial o las pestañas, se respetan.

## Validación

`bash ./gradlew :app:testDebugUnitTest :app:assembleDebug`

El workflow existente HexDroid Personal ejecuta las pruebas y genera un APK debug. Las pruebas de navegación cubren ambos sentidos, el umbral, los extremos
y la eliminación de la conversación seleccionada.

Comprobar en un dispositivo:

1. Abrir dos salas y un privado; alternar con pestañas y con gestos en ambos sentidos.
2. Escribir un borrador, cambiar de sala y regresar: comprobar que se conserva.
3. Pulsar Retroceso con texto y con espacios: no debe salir del chat. Vaciar el campo
   y comprobar que vuelve a funcionar. Android puede ocultar primero el teclado.
4. Desplazarse verticalmente por los mensajes y mover el cursor en el campo de escritura:
   ninguna acción debe cambiar de sala.
5. Ejecutar `/whois` con presentación flotante, en ventana propia y en Status; conversar
   además con un usuario `whois`.
   Cerrar y reabrir la app; comprobar que se recuperan las líneas de las conversaciones
   al abrirlas y las de Status.
6. Desactivar pestañas o historial, reiniciar y comprobar que la elección se conserva.
