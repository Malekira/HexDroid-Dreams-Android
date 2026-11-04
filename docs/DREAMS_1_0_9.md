# HexDroid Dreams 1.0.9 (48)

Rama `feat/dreams-1.0.9`, desde la entrega 1.0.8 (`eda5014`). Autorizaciones: «Implementa» (desconexión), «Implementa también las últimas anotaciones» y confirmación «todooooo». Sin fusiones de ramas anteriores.

## Funciones

- **Desconectar:** el menú, el botón de Redes y `/quit`/`/disconnect` cierran las vistas del servidor y vuelven a Redes. Se retira del selector durante la sesión; su perfil sigue disponible. Abrirlo o conectarlo lo muestra de nuevo. No se interrumpe otra red ni se descarta su conversación si el usuario cambió de red mientras terminaba QUIT. Las caídas accidentales conservan las vistas para reconectar.
- **Salas y Privados:** secciones separadas dentro de cada red en el panel lateral. WHOIS, Notices y ventanas especiales quedan en Otras ventanas; no se convierten en privados.
- **Registro:** elegir una carpeta con el selector de Android, o almacenamiento privado; archivos por sala/PV o todas las conversaciones juntas, con división diaria opcional. Las fechas se obtienen del mensaje y de la zona horaria del dispositivo. Las líneas combinadas identifican red y conversación. Controles y botones se adaptan al ancho del teléfono.
- **Scripts:** seleccionar Scripts en Ajustes abre el gestor directamente; desaparece la pantalla intermedia «Gestionar». Se conservan los scripts importados y el motor .hex.
- **No leídos:** JOIN/PART/QUIT siguen visibles y registrados, pero no aumentan el contador ni los destacados. Los mensajes de conversación mantienen su comportamiento.
- **ChatZona:** avatares en usuarios y privados; opcionalmente junto a los mensajes. La foto de perfil y `METADATA avatar` tienen prioridad. Como alternativa se utiliza `https://chat-avatars.chateagratis.net/avatar/Chat/NICK`, codificando el nick como un único segmento. Se reconoce ChatZona por sus dominios o `NETWORK=ChatZona`, sin usar el nombre editable del perfil ni coincidencias parciales de dominio. El sistema no se aplica a otras redes y respeta el uso de proxy. No se incorpora selección de fotos locales.
- **Actualización de fotos:** caché compartida y limitada, descargas simultáneas limitadas, imagen predeterminada ante errores; se revisan las fotos visibles cada cinco minutos mientras la app está en primer plano. Cambiar el nick cambia la URL alternativa. El visor conserva ampliación y añade actualización manual. Se conserva la suscripción `draft/metadata-2`; en privados y fichas se solicita GET avatar cuando el servidor lo admite, con límite de frecuencia.

## Ajustes

- **Ajustes → Registro:** Elegir carpeta de historial, Usar almacenamiento interno, organización por conversación/todo junto y Dividir archivos por día. La carpeta usa permisos persistentes de SAF; no se solicita acceso a todo el almacenamiento. Si Android rechaza el permiso se conserva el destino anterior.
- **Ajustes → Registro → Exportar archivos TXT organizados internos:** ZIP con la presentación diaria/agrupada guardada en privado. El botón aparece al elegir una organización interna alternativa. La exportación/restauración compatible y el respaldo con historial siguen usando el archivo canónico completo.
- **Ajustes → Vistas previas multimedia:** Avatares automáticos de ChatZona y Avatares de ChatZona junto a los mensajes. Desactivar los avatares automáticos oculta las fotos en esa red sin borrar el perfil ni los metadatos guardados.

## Conservación y privacidad del historial

El archivo privado canónico permanece organizado por red/conversación para consulta, búsqueda, deduplicación y copias compatibles. La carpeta y la organización seleccionadas reciben los mensajes nuevos como archivos TXT adicionales. Los archivos anteriores no se borran ni mueven. En modo interno sin organización alternativa se utiliza directamente el archivo canónico.

La copia adicional solo recibe una línea si el archivo canónico la aceptó, evitando duplicación por eco o reproducción de historial. Las conversaciones cifradas obedecen su opción separada; autenticaciones y secretos se excluyen o redactan antes de escribir cualquiera de las copias. No hay vencimiento ni límite artificial de líneas en disco.

Si falla la carpeta elegida, el mensaje continúa en el archivo privado y aparece un aviso en Status y en Registro. Volver a conceder permiso reactiva las escrituras incluso si Android devuelve la misma URI. Al recuperar el permiso se reanudan las copias nuevas; los mensajes conservados durante el fallo siguen disponibles para consulta/exportación desde el historial privado. No se promete una sincronización retroactiva automática de la carpeta externa.

Desconectar conserva perfiles, credenciales, foto por URL, borradores, historial y configuración de las fases anteriores. La eliminación de registros internos continúa exigiendo confirmación; incluye sus copias organizadas privadas y no borra la carpeta externa del usuario.

## Validación

- Compilación local debug y APK de pruebas Android correctas; 121 pruebas JVM ejecutadas y aprobadas, sin fallos, errores ni omisiones.
- Pruebas nuevas: desconexión real desde el menú, comandos, carrera al cambiar de red, protección de historial/borradores/perfil, reapertura; archivos diarios y combinados, persistencia y respaldo de opciones, SAF con proveedor Android aislado, conservación ante carpeta inaccesible, privacidad y cifrado; eventos sin no leídos; controles de Registro, separación de salas/PV y avatares con preferencia por metadatos, opción de mensajes y desactivación.
- Pendiente: ejecución final de todas las pruebas Android en Actions y verificación del artifact correspondiente al código completo. La versión parcial de desconexión ya aprobó sus seis casos Android; no se presenta esa APK como entrega del conjunto completo.

El intento de comprobar directamente el servicio público de fotos desde este entorno fue bloqueado por su proxy (CONNECT 403). No se afirma haber descargado fotos reales de ChatZona aquí. Se prueban la construcción de URL, alcance de red, protocolo y presentación mediante un cargador controlado. La carga real debe comprobarse en el teléfono.

## Comprobación manual en Samsung

1. Desconectar desde sala, PV y Status, y con `/quit`/`/disconnect`: volver a Redes y conservar otra red conectada.
2. Volver a abrir la red: recuperar configuración, foto, scripts, historial y borrador.
3. Revisar secciones Salas/Privados/Otras ventanas, sus contadores y el filtro Solo salas.
4. Elegir una subcarpeta desde Registro, cambiar agrupación/división diaria y recibir mensajes. Abrir los TXT con Mis archivos; comprobar datos de sala/PV y fechas. Los registros anteriores permanecen disponibles en la app.
5. Quitar/reconceder acceso a la carpeta y verificar el aviso y la conservación privada. Probar exportación TXT organizada y respaldo/restauración compatibles.
6. Abrir Scripts desde Ajustes directamente; revisar scripts importados.
7. Mantener una sala sin verla: JOIN/PART/QUIT no deben aumentar el contador; un mensaje normal sí.
8. En ChatZona, comprobar fotos, avatar ampliado, cambio de nick, actualización, opción junto a mensajes y desactivación; verificar otra red sin cambios y WHOIS/menciones/Tab.

## APK y datos

Mismo paquete `com.malekira.hexdroid.personal.debug`, nombre HexDroid Dreams, GPLv3 y créditos originales. Descargar `HexDroid-Dreams-debug` desde Actions, iniciar sesión en GitHub y abrir el ZIP con Mis archivos de Samsung. El artifact dura 30 días.

Se mantiene la firma debug solicitada mediante el mecanismo existente. Si Android rechaza actualizar por diferencia de firma, no desinstalar ni borrar los datos anteriores antes de exportar configuración, historial y scripts. No se publica ninguna clave privada.
