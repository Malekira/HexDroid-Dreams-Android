# HexDroid Dreams 1.0.11 (50)

Rama `feat/dreams-1.0.11` desde Android 1.0.10 (`0196e83`), separada de Windows. Autorización: «Implementa lo pendiente de la versión de Android».

## Inspección y alcance

El registro y el código confirman que los árboles de salas/PV, Registro, Scripts y contadores ya están implementados. Queda el informe de la foto propia que existe en web pero no aparece automáticamente en Identidad. Identidad solo tenía un campo de URL sin imagen ni comprobación. Se conserva la fuente HTTPS, sin carga de fotos locales.

Se implementó resolución automática por red y nick, preferencia por URL explícita/metadatos, vista previa ampliable en Identidad, dirección automática visible y copiable, adopción explícita de esa dirección para guardarla en el perfil/respaldo, actualización/reintento y diagnóstico de errores. Se conservan el límite de 2 MiB y los controles de privacidad (opciones de avatares y proxy). Se evita borrar la foto publicada por el servidor al guardar una identidad cuya URL no cambió.

Se añadieron seis pruebas JVM y cinco Android. La compilación local debug y las 127 pruebas JVM aprobaron; las cinco nuevas pruebas Android y las suites anteriores requeridas aprobaron en Actions antes de publicar la APK. No se modifican ni fusionan ramas anteriores.

## Medición de la imagen comunicada

El 5 de noviembre se reintentó HTTPS a la dirección proporcionada en la conversación. El proxy rechazó CONNECT con 403, sin descargar bytes de imagen. Su peso y dimensiones siguen sin conocerse; no se afirma que exceda el límite ni que falle la red del teléfono.

## Uso en el teléfono

1. Abrir Redes, editar la red y entrar en Identidad.
2. Con el campo vacío, ver la dirección automática: primero metadatos de la red y, en ChatZona, alternativa por el nick actual. La imagen se muestra si la descarga está habilitada.
3. Tocar la vista previa para ampliarla. «Actualizar / reintentar foto» realiza una nueva comprobación sin esperar la caché de errores.
4. «Usar esta dirección en mi perfil» copia la URL al campo. Guardar conserva esa dirección explícita en el perfil y en los respaldos compatibles. Dejarla automática evita fijar una dirección antigua al cambiar de nick.
5. Para habilitar fotos: Ajustes → Vistas previas multimedia → Avatares automáticos de ChatZona; la opción junto a mensajes sigue siendo independiente. En otras redes se utiliza la opción de vistas previas de imágenes. Con proxy no se realizan descargas externas.
6. Si falla, revisar el aviso: límite de 2 MiB, respuesta no disponible del servidor, formato no compatible, tiempo de espera, conexión segura o redirecciones. No se afirma que una respuesta rechazada por el servidor signifique que el archivo pesa demasiado.

Las miniaturas visibles se actualizan cada cinco minutos en primer plano; después de un fallo reintentan a los treinta segundos. La vista previa de Identidad espera brevemente mientras se edita la URL para no iniciar una petición por cada tecla. No hay cambios en historial, scripts, WHOIS, formato de escritura ni identidad del paquete Android.

## Comprobaciones manuales pendientes

- En el Samsung, verificar la foto concreta que funciona en web: comprobar la dirección detectada, imagen, ampliación y actualización. Si el servidor entrega otra URL en metadatos, esa URL tiene prioridad. La descarga de esa imagen no pudo comprobarse desde este entorno.
- Guardar sin cambiar la dirección no debe borrar la foto del servidor; adoptar una URL y reiniciar debe conservarla, también en el respaldo.
- Desactivar avatares o usar un perfil con proxy debe impedir su descarga. Revisar fotos de otros usuarios y que salas/PV, WHOIS, menciones, Tab e historial sigan funcionando.

## Validación y entrega

- Compilación local de APK debug y de pruebas Android correcta; **127 pruebas JVM aprobadas**, sin fallos, errores ni omisiones.
- [Ejecución final de GitHub Actions](https://github.com/Malekira/HexDroidVip/actions/runs/41945688999), código compilado [`a21d8817a6f89f564a47085e228b56eda8faff98`](https://github.com/Malekira/HexDroidVip/commit/a21d8817a6f89f564a47085e228b56eda8faff98).
- Aprobados los pasos de compilación/pruebas JVM, pruebas en emulador Android API 35, comprobación obligatoria de todas las suites requeridas, firma/identidad y publicación de APK. Las cinco pruebas nuevas verifican Identidad con foto automática y adopción de URL, reintento después de fallo, opciones desactivadas/proxy sin descargas, prioridad de los metadatos sin guardar una URL implícitamente y foto propia del chat con perfil vacío. No se omitió ninguna prueba requerida para publicar.
- Las seis pruebas JVM nuevas comprueban prioridad de fuentes, validación/cambios de nick, guardado vacío sin eliminación remota y el límite de descarga tanto por Content-Length como por lectura acotada del cuerpo. Se rechaza el exceso sin truncar una imagen y presentarla como completa.
- APK local firmada e identidad verificadas: **HexDroid Dreams 1.0.11 (50)**, `com.malekira.hexdroid.personal.debug`, minSdk 26. Se mantienen GPLv3 y créditos originales.
- Artifacts publicados y visibles: **HexDroid-Dreams-debug**, 34,5 MB, ID `13378423922`; **Phase3-test-reports**, 314 KB, ID `13377894307`.
- SHA-256 del ZIP de la APK: `5eb6ccb5e8feb93a58f8af81c16890d96b8499f7c62dcc52034717750990cb99`. El hash de la APK individual está en `SHA256SUMS.txt` dentro del ZIP.

El estado global de Actions se verificó como **SUCCESS**, incluida la limpieza del runner. La prueba de la imagen real queda expresamente pendiente del teléfono por el bloqueo del proxy del entorno.

## Descargar e instalar en Samsung

[Descargar HexDroid-Dreams-debug (ZIP)](https://github.com/Malekira/HexDroidVip/actions/runs/41945688999/artifacts/13378423922). Iniciar sesión en GitHub, descargar el ZIP, extraerlo con Mis archivos y abrir `HexDroid-Dreams-debug.apk`. No hace falta una computadora. El artifact dura 30 días.

Se conserva la firma de tipo debug solicitada y el paquete personalizado. En esta ejecución no había secretos de firma debug estable configurados: Actions generó una clave de desarrollo nueva, según la advertencia real del job. Puede no permitir actualizar una instalación anterior. Si Android rechaza actualizar por firma, conservar la instalación anterior y exportar configuración, historial y scripts antes de desinstalar o borrar datos. No se ha publicado ninguna clave privada.

[Archivos completos para revisión](https://github.com/Malekira/HexDroidVip/compare/feat/dreams-1.0.10...feat/dreams-1.0.11). No se modificaron ni fusionaron ramas anteriores, incluida Windows.
