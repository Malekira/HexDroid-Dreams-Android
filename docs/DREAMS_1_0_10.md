# HexDroid Dreams 1.0.10 (49)

Rama `feat/dreams-1.0.10`, desde la entrega 1.0.9 (`6e27643`). Autorización: «Implementa», referida a dos árboles independientes de Salas y Mensajes privados en cada red. Sin fusionar ni modificar ramas anteriores.

## Cambio previsto

La vista con pestañas de redes mostraba una lista plana, aunque la vista de redes en árbol ya tenía títulos de grupo. Ambas utilizarán la misma clasificación y los mismos controles desplegables. Cada grupo conservará su contador de no leídos, se podrá plegar por separado y mantendrá su estado al cerrar el panel o cambiar de red. WHOIS y Notices permanecerán en Otras ventanas. Se conservarán la selección y el cierre de conversaciones, el filtro Solo salas y la reordenación de redes.

No se cambian los avatares en esta actualización. Se registra aparte el caso comunicado por el usuario: la foto propia existe en la web, pero no aparece automáticamente en Identidad; la URL de referencia permanece en la conversación, sin repetir nicks particulares en la documentación.

## Comprobación manual en Samsung

1. Abrir el panel con el menú izquierdo, manteniendo las pestañas de redes. Comprobar Salas y Mensajes privados como grupos separados.
2. Plegar Salas: sus canales se ocultan y los privados permanecen. Plegar privados: WHOIS y Notices permanecen en Otras ventanas.
3. Cambiar de red y volver: cada red conserva sus propios grupos plegados. Cerrar y abrir el panel conserva esos estados.
4. Recibir mensajes en un grupo plegado: su contador se actualiza sin desplegar ni marcar las conversaciones como leídas.
5. Abrir una conversación y cerrarla con su cruz: conservan las mismas acciones. El filtro Solo salas oculta los privados y las otras ventanas.
6. Comprobar el mismo comportamiento desactivando las pestañas de redes. El historial, perfiles y scripts siguen disponibles.

## Validación y APK

Compilación local debug y APK de pruebas Android correctas. Se ejecutaron 121 pruebas unitarias JVM: cero fallos, errores u omisiones. Firma e identidad de la APK local verificadas (paquete personalizado, versión 1.0.10 / 49). Pendientes de ejecutar las cuatro nuevas pruebas Android y las suites anteriores en Actions, y verificar la publicación del artifact antes de dar la entrega por terminada.
