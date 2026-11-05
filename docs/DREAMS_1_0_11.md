# HexDroid Dreams 1.0.11 (50)

Rama `feat/dreams-1.0.11` desde Android 1.0.10 (`0196e83`), separada de Windows. Autorización: «Implementa lo pendiente de la versión de Android».

## Inspección y alcance

El registro y el código confirman que los árboles de salas/PV, Registro, Scripts y contadores ya están implementados. Queda el informe de la foto propia que existe en web pero no aparece automáticamente en Identidad. Identidad solo tenía un campo de URL sin imagen ni comprobación. Se conserva la fuente HTTPS, sin carga de fotos locales.

Se implementó resolución automática por red y nick, preferencia por URL explícita/metadatos, vista previa ampliable en Identidad, dirección automática visible y copiable, adopción explícita de esa dirección para guardarla en el perfil/respaldo, actualización/reintento y diagnóstico de errores. Se conservan el límite de 2 MiB y los controles de privacidad (opciones de avatares y proxy). Se evita borrar la foto publicada por el servidor al guardar una identidad cuya URL no cambió.

Se añadieron seis pruebas JVM y cinco Android. La compilación local debug y las 127 pruebas JVM aprobaron; las pruebas Android se compilaron y se ejecutarán en Actions antes de publicar la APK. Los resultados y enlaces de entrega se registrarán después de comprobarlos. No se modifican ni fusionan ramas anteriores.

## Medición de la imagen comunicada

El 5 de noviembre se reintentó HTTPS a la dirección proporcionada en la conversación. El proxy rechazó CONNECT con 403, sin descargar bytes de imagen. Su peso y dimensiones siguen sin conocerse; no se afirma que exceda el límite ni que falle la red del teléfono.

## Estado

Implementación en curso. No hay todavía APK de esta versión publicada.
