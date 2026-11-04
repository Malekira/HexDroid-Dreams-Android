# HexDroid Dreams 1.0.9 (48) — desconexión manual

Autorización: «Implementa», referida al último pedido: al desconectar, cerrar el servidor y volver a Redes. Rama `feat/dreams-1.0.9`, creada desde la entrega 1.0.8 (`eda5014`).

## Plan tras revisar el código

- Usar el cierre real existente de la conexión; cerrar sus vistas y retirarlo del selector de salas/pestañas durante la sesión una vez terminada la desconexión manual. El perfil sigue en Redes; al abrirlo o conectarlo explícitamente vuelve a mostrarse.
- Aplicarlo al menú Desconectar, al botón de Redes y a `/quit` y `/disconnect`.
- Conservar perfiles, credenciales, foto por URL, borradores e historial en disco. No desconectar otras redes.
- Mantener las caídas de conexión y reconexiones automáticas con sus vistas abiertas. Las desconexiones internas de restauración/eliminación no navegan como una acción del usuario.
- Evitar volver a un chat vacío al salir del menú Redes después de cerrar el último servidor.

Avatares automáticos de ChatZona y separación de salas/PV siguen pendientes de autorización; no se implementan en esta entrega.

## Validación y entrega

Compilación local debug y APK de pruebas Android: **BUILD SUCCESSFUL**. **113 pruebas JVM ejecutadas y aprobadas**, sin fallos, errores ni omisiones. APK verificada con `apksigner`/`aapt2`: firma debug, versión 1.0.9 (48), identidad personalizada y sin claves privadas empaquetadas.

Se compilaron seis pruebas nuevas con el ViewModel real (menú de la app, comandos, protección de datos, caídas, reapertura y cambio de red durante el cierre). La ejecución Android queda pendiente de Actions; la APK solo se publica si aprueban estas pruebas y las suites anteriores.

## Comprobación manual en el teléfono

1. Desde una sala, abrir el menú general y pulsar Desconectar: regresar a Redes y cerrar las vistas de ese servidor.
2. Repetir desde un privado y desde Status; probar también `/quit` y `/disconnect`.
3. Con dos redes abiertas, desconectar una y verificar que la otra sigue conectada y conserva sus salas y privados.
4. Volver a conectar la red cerrada: perfil, foto por URL, scripts y configuración deben conservarse; al abrir una conversación, recuperar su historial y borrador.
5. Comprobar que una caída accidental de conexión mantiene los buffers para reconectar.

## Instalación y firma

APK debug con el mismo paquete `com.malekira.hexdroid.personal.debug`, nombre HexDroid Dreams, GPLv3 y créditos originales. La descarga se realiza desde el artifact `HexDroid-Dreams-debug` de Actions (30 días): iniciar sesión en GitHub, abrir el ZIP con Mis archivos de Samsung e instalar la APK.

Se conserva el mecanismo de firma debug existente. Si Android rechaza actualizar por diferencia de firma, no desinstalar ni borrar datos antes de exportar configuración, historial y scripts. No se publica ninguna clave privada.
