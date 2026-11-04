# HexDroid Dreams 1.0.10 (49)

Rama `feat/dreams-1.0.10`, desde la entrega 1.0.9 (`6e27643`). Autorización: «Implementa», referida a dos árboles independientes de Salas y Mensajes privados en cada red. Sin fusionar ni modificar ramas anteriores.

## Cambio implementado

La vista con pestañas de redes mostraba una lista plana, aunque la vista de redes en árbol ya tenía títulos de grupo. Ambas utilizan ahora la misma clasificación y los mismos controles desplegables. Cada grupo conserva su contador de no leídos, se puede plegar por separado y mantiene su estado al cerrar el panel o cambiar de red. WHOIS y Notices permanecen en Otras ventanas. Se conservan la selección y el cierre de conversaciones, el filtro Solo salas y la reordenación de redes.

No se cambian los avatares en esta actualización. Se registra aparte el caso comunicado por el usuario: la foto propia existe en la web, pero no aparece automáticamente en Identidad; la URL de referencia permanece en la conversación, sin repetir nicks particulares en la documentación. Se intentó comprobar su peso y dimensiones: el proxy del entorno rechazó CONNECT con 403 antes de descargar la imagen. No se pudo medir ni afirmar que excediera el límite de la app: 2 MiB (2.097.152 bytes). Este bloqueo del entorno no demuestra un fallo de acceso en el teléfono.

## Comprobación manual en Samsung

1. Abrir el panel con el menú izquierdo, manteniendo las pestañas de redes. Comprobar Salas y Mensajes privados como grupos separados.
2. Plegar Salas: sus canales se ocultan y los privados permanecen. Plegar privados: WHOIS y Notices permanecen en Otras ventanas.
3. Cambiar de red y volver: cada red conserva sus propios grupos plegados. Cerrar y abrir el panel conserva esos estados.
4. Recibir mensajes en un grupo plegado: su contador se actualiza sin desplegar ni marcar las conversaciones como leídas.
5. Abrir una conversación y cerrarla con su cruz: conservan las mismas acciones. El filtro Solo salas oculta los privados y las otras ventanas.
6. Comprobar el mismo comportamiento desactivando las pestañas de redes. El historial, perfiles y scripts siguen disponibles.

## Validación y APK

Compilación local debug y APK de pruebas Android correctas. Se ejecutaron 121 pruebas unitarias JVM: cero fallos, errores u omisiones. Firma e identidad de la APK local verificadas (paquete personalizado, versión 1.0.10 / 49). Las cuatro nuevas pruebas Android y las suites anteriores obligatorias aprobaron en [Actions 41861118536](https://github.com/Malekira/HexDroidVip/actions/runs/41861118536). Se verificaron como SUCCESS los pasos de compilación/pruebas unitarias, pruebas Android, comprobación de todas las suites obligatorias, firma/identidad y publicación de APK. Código compilado: `7a76c1d865d8362c25dc267b69f9342e889a85cf`.

Los casos nuevos comprueban la vista del teléfono con pestañas, plegado independiente, clasificación de WHOIS/Notices, selección y cierre, estado por red, actualización de contadores mientras un grupo está plegado, filtro Solo salas, vista de redes en árbol y restauración de estado Android. No se omitieron estos casos para publicar.

Artifacts publicados y visibles: **HexDroid-Dreams-debug**, 34,5 MB, ID `13339694133`; **Phase3-test-reports**, 292 KB. Digest del ZIP de la APK: `6917ae5a7f53ae84b50b2992ed8d7cb5b97f9f6ee867793d36483564e92a63f8`. El SHA de la APK individual se incluye en SHA256SUMS.txt dentro del ZIP.

Al registrar esta evidencia GitHub aún mostraba la ejecución general en curso, aunque todos los pasos anteriores y Complete job ya estaban completados correctamente. No se afirma un estado global SUCCESS sin que GitHub lo muestre.

## Descargar e instalar

[Descargar HexDroid-Dreams-debug (ZIP)](https://github.com/Malekira/HexDroidVip/actions/runs/41861118536/artifacts/13339694133). Iniciar sesión en GitHub, descargar el ZIP, extraerlo con Mis archivos de Samsung y abrir `HexDroid-Dreams-debug.apk`. No hace falta una computadora. El artifact dura 30 días.

Se mantiene nombre, paquete `com.malekira.hexdroid.personal.debug`, GPLv3, créditos originales y firma debug solicitada. Si Android rechaza actualizar por una diferencia de firma, conservar la instalación anterior y exportar configuración, historial y scripts antes de desinstalar o borrar datos.

[Archivos completos para revisión](https://github.com/Malekira/HexDroidVip/compare/feat/dreams-1.0.9...feat/dreams-1.0.10). Sin fusionar ramas.
