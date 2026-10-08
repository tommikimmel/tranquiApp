# Centro de ayuda (FAQ y videos)

Se abre desde el botón "Ayuda / FAQ" de la landing y desde "Ayuda" en el encabezado del panel del
profesional. Implementado en octubre de 2026 (v1.0.0).

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| Preguntas y respuestas | `frontend/src/constants/helpFaq.ts` |
| Modal, buscador y reproductor | `frontend/src/components/HelpFaqModal.tsx` |
| Estilos | `frontend/src/styles/help.css` |
| Tests | `frontend/src/components/HelpFaqModal.test.tsx` |
| Videos (`.webm`) y subtítulos (`.vtt`) | `frontend/public/videos/` |
| Tipos MIME de videos y subtítulos | `frontend/nginx.conf` (bloque `location /videos/`) |

- Pestañas "Soy paciente" (21 preguntas) y "Soy profesional" (22). La pestaña inicial depende del
  rol del usuario.
- Las respuestas usan un markup mínimo: `**negrita**`, líneas con `- ` (viñetas) y `1. ` (listas).
- Una pregunta con video muestra "Ver video explicativo". Un video puede cubrir varias preguntas;
  el campo `start` hace que arranque en un segundo puntual.

## Videos publicados

Todos en formato escritorio (1280×720), con narración en voz rioplatense y subtítulos en español.

| Video | Contenido | Preguntas |
|---|---|---|
| V-P01 | Crear la cuenta y recuperar la contraseña | P01, P02 (arranca en el segundo 75) |
| V-P05 | Reservar y pagar un turno | P05, P06 |
| V-P07 | Cerré la ventana de pago, ¿perdí el turno? | P07 |
| V-R01 | Registrarse como profesional | R01 |
| V-R02 | Qué hace falta para aparecer en el buscador | R02 |

Se eligieron los 5 de mayor uso esperado; los demás se descartaron para no inflar el repositorio
(los 5 suman ~31 MB).

## Cómo se produjeron

- Grabación guionada con Playwright sobre la app real, en un entorno aislado con datos ficticios
  y pagos simulados. Los textos del simulador de pagos se ocultaron en la grabación; la narración
  aclara que en la app real se abre Mercado Pago.
- Voz: Piper TTS (`es_AR-daniela-high`), generada localmente.
- Video armado con ffmpeg (VP8 + Opus en WebM); subtítulos WebVTT sincronizados con la narración.
- Los scripts de grabación no están en el repositorio.

## Cuándo hay que regrabar

Cuando cambia una pantalla que aparece en un video:

| Video | Pantallas de las que depende |
|---|---|
| V-P01, V-R01 | `LoginPage.tsx` |
| V-P05, V-P07 | `LandingPage.tsx`, `CheckoutFlow.tsx`, `MisTurnosModal.tsx` |
| V-R02 | `SettingsView.tsx` |

Si un video queda desactualizado y no se puede regrabar, conviene quitarle el `video` a la
pregunta en `helpFaq.ts` hasta reemplazarlo.
