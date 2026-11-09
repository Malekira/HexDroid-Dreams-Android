# HexDreams IRC 1.0.18 (57)

Corrige la carga inicial de fotos sobre 1.0.17, manteniendo el chat sin pestañas y sin recargas periódicas/globales.

Los avatares dejan de depender del interruptor de vistas previas de enlaces, que viene apagado por defecto. La carga automática de avatares y su opción «Avatares solo por Wi-Fi» son controles independientes en Multimedia; se conserva la preferencia Wi-Fi de las vistas previas de enlaces sin modificarla. Las conexiones con proxy siguen sin descargar avatares.

Las direcciones METADATA malformadas no se convierten en retiradas de foto; una dirección no admitida para descarga automática deja paso a una foto GECOS/plantilla permitida. Un avatar vacío explícito sigue bloqueando las fuentes de respaldo.

Las redirecciones siguen su destino HTTPS real, evitando un bucle al redirigir entre los dos dominios oficiales. Se conservan parámetros firmados, validación de dominios y destinos públicos, certificados válidos y límites de descarga. Los fallos iniciales de conexión/lectura tienen un único reintento compartido por descarga, sin temporizadores ni sondeo de miniaturas.

Pruebas nuevas: producción completa de resolución GECOS → carga HTTP → decodificación → imagen visible con ajustes por defecto y vistas previas apagadas; persistencia de preferencias; redirección entre dominios oficiales; bloqueo de redirecciones privadas; reintento acotado y caché; rechazo de metadatos inválidos sin ocultar fotos válidas.
