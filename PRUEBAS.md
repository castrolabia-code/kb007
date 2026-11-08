# Pruebas manuales en el teléfono — KB-007

Marcá cada casilla a medida que pruebes. Si algo falla, anotá el **modelo de teléfono**, la
**versión de Android** y qué hiciste justo antes.

Preparación: instalá el APK (ver README §1) y aceptá micrófono y notificaciones.

---

## 1. Primer arranque y permisos
- [ ] Antes de cada pedido de permiso aparece una pantalla que explica para qué es.
- [ ] Si rechazás el micrófono 2 veces, aparece el botón «Abrir ajustes de la app». Al activarlo a mano y tocar «Ya lo activé», entra al aparato.
- [ ] Sin cassette se ve el compartimiento vacío (ejes y cabezal), con el display en «SIN CASSETTE».

## 2. Grabación manual
- [ ] Pulsá **REC**: REC y PLAY quedan hundidas, el LED rojo parpadea, los carretes giran y el contador avanza.
- [ ] Al hablar, la **aguja del VU** se mueve con inercia (no salta). Con un aplauso llega a la zona roja.
- [ ] Un cassette entra deslizándose con el nombre automático en la etiqueta («KB007_fecha_hora»).
- [ ] **PAUSA**: queda trabada, se enciende el LED PAUSA, los carretes frenan con inercia y el cronómetro se detiene. Otra vez PAUSA → sigue.
- [ ] **STOP**: aparece «Guardado: …», el cassette queda cargado con la cinta al final (carrete derecho lleno).
- [ ] **PLAY**: rebobina sola (REW) y reproduce desde el principio. Se escucha bien, sin cortes al inicio ni al final.
- [ ] Un segundo **STOP** con la cinta detenida → «clack» y el cassette sale.

## 3. Grabación en segundo plano
- [ ] Empezá a grabar, apretá Inicio y **apagá la pantalla 5 minutos**. Al volver, sigue grabando y el tiempo es correcto.
- [ ] La notificación KB-007 muestra LED rojo, «● Grabando», cronómetro y botones **Detener** y **Pausa**.
- [ ] Tocá **Pausa** y **Reanudar** desde la notificación con la pantalla bloqueada.
- [ ] Tocá **Detener** desde la notificación: la grabación aparece en la LISTA con la duración correcta.

## 4. Robustez
- [ ] **Llamada entrante** mientras graba (pedile a alguien que te llame):
  - con «Pausar y reanudar», se pausa durante la llamada y sigue al cortar;
  - con «Guardar y detener», se guarda y para.
- [ ] Lo mismo con una **llamada de WhatsApp**.
- [ ] Mientras graba, abrí la app de cámara en modo video, o una nota de voz de WhatsApp: la grabación se **pausa**, y al cerrar esa app se reanuda (Android 10+).
- [ ] **Cierre forzado**: grabá 1 minuto. Ajustes de Android → Apps → KB-007 → *Forzar detención*. Abrí KB-007: la grabación aparece en la LISTA marcada **RECUPERADA** y se reproduce casi completa. Probalo con M4A, OGG y WAV.
- [ ] Con el micrófono ocupado por otra app (por ejemplo, grabadora del sistema grabando), pulsá REC: aparece un aviso de micrófono ocupado y no se cuelga.
- [ ] (Opcional) Con el teléfono casi lleno, con menos de 30 MB libres, aparece «Espacio insuficiente».

## 5. Palabra clave con la app abierta
- [ ] Ajustes → Palabra clave: con «grabadora» aparece «✓ Todas las palabras están en el vocabulario».
- [ ] Escribí una palabra inventada («zorglub»): aparece «✗ El modelo no conoce: zorglub».
- [ ] **Probar palabra clave**:
  - la primera vez muestra «Cargando el modelo…» unos segundos;
  - decí la frase varias veces y aparece con ✓ y el porcentaje;
  - decí otras frases y no aparecen con ✓.
- [ ] Ajustá la sensibilidad hasta que detecte bien a 1–2 m y no reaccione a charla normal.
- [ ] En el aparato, activá el switch **PALABRA CLAVE**:
  - la primera vez pide quitar la optimización de batería → aceptá;
  - el LED ámbar parpadea mientras carga y después queda fijo;
  - el display dice «ESPERANDO PALABRA CLAVE».
- [ ] Decí la frase:
  - vibra;
  - empieza a grabar con LED rojo y carretes girando, y el display dice «GRABANDO · CLAVE»;
  - la grabación incluye los ~3 s anteriores (escuchala: tiene que oírse la palabra clave al principio).
- [ ] Quedate en silencio: a los 8 s (valor por defecto) corta solo y queda guardada.
- [ ] Configurá la palabra de detención «basta». Decí la frase, hablá y decí «basta»: corta la grabación.
- [ ] En la LISTA, esas grabaciones dicen **CLAVE «grabadora»**.

## 6. Palabra clave con la pantalla apagada (crítica)
- [ ] Activá la escucha, salí de la app y **bloqueá el teléfono**.
- [ ] Esperá **10 minutos**. Decí la frase a 1 m: vibra y graba. Al desbloquear, la notificación dice «● Grabando (palabra clave)».
- [ ] Repetí después de **1 hora** con la pantalla apagada. Si dejó de funcionar, revisá los pasos de tu marca en README §4 «Limitaciones por fabricante».
- [ ] Desde la notificación: **Grabar ya** inicia una grabación y **Detener escucha** apaga el LED ámbar y saca la notificación.
- [ ] **Batería**: con la batería cargada, dejá la escucha 2 horas con la pantalla apagada y anotá cuánto bajó. Valor esperado: 4–16 %.
- [ ] **Reinicio**: con la escucha activa, reiniciá el teléfono. Aparece la notificación «KB-007: escucha en pausa». Tocala y vuelve a escuchar.

## 7. Lista de grabaciones
- [ ] Cada cassette muestra nombre manuscrito, fecha y hora, duración, tamaño, formato y calidad, y MANUAL o CLAVE.
- [ ] **Buscar**: escribí parte del nombre, con o sin acentos.
- [ ] **Ordenar**: Fecha, Nombre y Duración. Tocar de nuevo invierte el orden (flecha).
- [ ] **Renombrar**: el nombre cambia en la lista, en la etiqueta del cassette cargado y en el archivo.
- [ ] **Eliminar**: pide confirmación y desaparece. Si estaba cargado, el aparato lo expulsa.
- [ ] **Cargar**: vuelve al aparato con ese cassette adentro.

## 8. Compartir por WhatsApp
- [ ] M4A → **WhatsApp**: abre WhatsApp, elegís el chat y llega como audio reproducible.
- [ ] OGG/Opus → **WhatsApp**: llega y se reproduce.
- [ ] WAV → **WhatsApp**: pregunta «¿Convertir a M4A?».
  - [ ] **Convertir y enviar**: barra de progreso y llega un M4A liviano.
  - [ ] **Enviar WAV igual**: llega como documento.
- [ ] **Compartir** abre el selector del sistema (Gmail, Drive, Telegram…) y el archivo llega con su nombre.
- [ ] Con WhatsApp Business en lugar de WhatsApp, el botón también funciona.

## 9. Calidades y formatos
Para cada combinación grabá 15 s hablando y reproducila en KB-007 y en otra app (por ejemplo, VLC).
- [ ] Preset **Voz / nota rápida** (M4A 22,05 kHz 64 kbps mono).
- [ ] Preset **Reunión** (M4A 44,1 kHz 96 kbps).
- [ ] Preset **Música / ensayo** (M4A 48 kHz 192 kbps estéreo, sin procesar si está disponible). Comprobá en VLC que es estéreo.
- [ ] Preset **Máxima calidad** (WAV 48 kHz estéreo).
- [ ] **OGG/Opus** 48 kHz 64 kbps; y OGG con 44,1 kHz (tiene que mostrar «se grabará a 48 kHz»).
- [ ] **16 kHz** con cualquier formato.
- [ ] La etiqueta de calidad en la LISTA coincide con lo elegido.
- [ ] **Supresión de ruido / AGC / eco**: si dicen «No disponible», el switch está deshabilitado. Si están disponibles, grabá con y sin ellos al lado de un ventilador y compará.
- [ ] **Ganancia**: en Ajustes, la barra de nivel se mueve al hablar. Con +12 dB la barra sube; con -12 dB baja.
- [ ] **Corte por silencio** a 5 s, en una grabación manual: corta solo.
- [ ] **Duración máxima** a 15 min: corta a los 15:00.

## 10. Almacenamiento
- [ ] «Carpeta de la app»: el archivo aparece en Android/data/com.kb007.recorder/files/Music/KB-007 (visible conectando el teléfono a la PC).
- [ ] «Música/KB-007 (pública)»: la grabación aparece en la app de música o de archivos, en Música/KB-007.
- [ ] «Carpeta elegida…»: elegí una carpeta (o la SD). La grabación se guarda ahí y se puede reproducir, renombrar, borrar y compartir desde KB-007.
- [ ] Patrón de nombre «{modo}_{n}_{fecha}»: la vista previa cambia y las grabaciones nuevas lo usan.

## 11. Temas, sonidos y vibración
- [ ] Cambiá entre los 4 temas: cambian el cuerpo (metal cepillado, plástico, negro mate, madera), los colores del cassette, las teclas y las tipografías. Todo es legible.
- [ ] Clic al pulsar cada tecla, «clack» al expulsar, tic en LISTA y AJUSTES.
- [ ] **Zumbido de motor** al reproducir (más agudo en REW/FF). **No suena mientras graba.**
- [ ] Desactivá *Sonidos mecánicos*: silencio total. Desactivá *Vibración*: no vibra.
- [ ] Animaciones fluidas: los carretes giran parejo, sin tirones. El carrete chico gira más rápido que el grande.
- [ ] Girá el teléfono a **horizontal**: el aparato se adapta y entra completo.

## 12. Reproducción
- [ ] REW y FF durante la reproducción: bobina sin audio. PLAY retoma desde la nueva posición.
- [ ] Desconectá los auriculares mientras reproduce: se pausa.
- [ ] Llamada mientras reproduce: se pausa.
- [ ] Mientras reproduce con la escucha activa, decí la palabra clave: la reproducción se detiene y empieza a grabar.
