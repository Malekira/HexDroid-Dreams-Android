# HexDreams IRC 1.0.16 (55)

Un certificado IRC no reconocido, con nombre incorrecto o distinto del aprobado detiene la conexión y muestra su servidor/puerto, titular, emisor, validez y huella SHA-256. No se envían comandos ni credenciales IRC antes de validar o aceptar el certificado.

«Aceptar certificado» guarda exclusivamente la huella presentada para ese perfil, servidor y puerto, y vuelve a conectar. Un certificado distinto requiere otra autorización. Cancelar mantiene la conexión detenida. La aceptación se guarda antes de reintentar; los cambios de servidor/puerto y las solicitudes obsoletas no pueden autorizar otro destino.

Se reutiliza la infraestructura TLS/TOFU existente. Los certificados válidos siguen pasando por la validación normal; las políticas IRCv3 STS no permiten excepciones. El nuevo aviso no modifica la verificación HTTPS de fotos y avatares.

Pruebas con un servidor TLS local y certificado de prueba: rechazo inicial sin envío IRC, conexión con la huella exacta, cambio de huella y STS. Pruebas Android de confirmación/cancelación, datos del aviso y persistencia ligada al destino.
