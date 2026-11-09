# HexDreams IRC 1.0.17 (56)

Vuelve al chat sin tira de pestañas. Una migración desactiva la tira superior en instalaciones anteriores, conservando el árbol de salas/privados, Tab para completar nicks, borradores y gestos. Si posteriormente se eligen pestañas en Ajustes, esa elección se respeta.

Corrige las recargas continuas de avatares: elimina los temporizadores de cada miniatura y el reinicio global ante cambios de cualquier usuario. Los anuncios METADATA idénticos ya no invalidan la caché. Cambiar de conversación o salir y volver al chat no vacía la caché de la red.

Cada miniatura carga al mostrarse (reutilizando la caché) o al cambiar su fuente. La actualización manual del visor afecta únicamente a ese usuario; durante una actualización fallida se conserva la imagen visible. Se mantienen la caducidad de caché, los límites de descarga y las comprobaciones de seguridad existentes.

Conserva la aceptación de certificados de 1.0.16, el logo y el visor de logs. Pruebas nuevas: ausencia de pestañas por defecto y migración, conservación de los árboles, ausencia de recargas periódicas/globales y de parpadeo al fallar una actualización.
