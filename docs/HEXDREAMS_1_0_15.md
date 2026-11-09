# HexDreams IRC 1.0.15 (54)

Nombre y logo actualizados. El hexágono conserva el diseño aportado y transparencia exterior. El identificador Android se mantiene para conservar compatibilidad con los datos existentes.

Resolución: consulta explícita, METADATA avatar (incluida retirada vacía), avatar-url, cuarto campo del GECOS Base64 UTF-8 y plantilla HTTPS configurable. Acepta campos adicionales de GECOS y oculta el bloque codificado en los nombres reales de WHO/WHOIS. No inventa nombres reales.

Fuentes IRC: WHO 352, WHOX 354 según los campos de la consulta enviada y su token (incluido %tuhsnfar,42), WHOIS 311, JOIN extendido, SETNAME, METADATA y 761. Suscripción/GET/SYNC exclusivamente cuando se negocia draft/metadata-2; se conserva el tratamiento especial de soju. La recuperación de fotos no abre ventanas ni inicia WHO/WHOIS adicionales.

Plantilla en Ajustes → Multimedia: {nick}, {nickLower}, %nick% y %s. Predeterminada: https://chat-avatars.chateagratis.net/avatar/Chat/{nick}.png. El nick se codifica como componente UTF-8 después de quitar rangos. Estado y caché por red; identidad con CASEMAPPING.

Descarga: IO, tres tareas simultáneas, caché de 64 miniaturas durante 60 segundos, máximo 96×96 con proporción conservada. Dos MiB automáticos, cinco MiB manuales, límite previo de 32 millones de píxeles. Control de versiones para impedir respuestas tardías tras invalidación; reintento renueva v en los dominios oficiales, excepto default.png. Los fallos muestran iniciales.

Se respetan multimedia, solo Wi-Fi, carga automática, iconos de usuarios y avatares en mensajes. HTTPS verificado, sin credenciales, destinos públicos y dominios automáticos oficiales o configurados. OkHttp conecta a las direcciones DNS verificadas; cada redirección vuelve a pasar por las mismas reglas. No se suben imágenes locales.

Pruebas: GECOS UTF-8, prioridades, retirada, codificación, nicks especiales, destinos privados, protocolo real con servidor simulado, aislamiento entre redes, renovación y respuestas tardías; se ejecutan también las regresiones previas.
