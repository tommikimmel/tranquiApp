# Cambios a futuro

Cambios detectados durante la migración a microservicios que quedaron afuera de cada fase a propósito. No son
errores visibles hoy (esos van en [bugs-conocidos.md](bugs-conocidos.md)), sino trabajo pendiente o decisiones
abiertas. Al hacer uno, se saca de esta lista y queda en el `CHANGELOG.md`.

## Suscripciones (anotados en la Fase 8)

### 1. Webhooks de preapproval sin deduplicar
- **Dónde:** el procesamiento de `subscription_preapproval` (hoy `SubscriptionService.processMercadoPagoPreapprovalWebhook`;
  desde la Fase 8, en el servicio de pagos).
- **Qué pasa:** cada notificación vuelve a escribir el estado de la suscripción, aunque Mercado Pago la reenvíe o
  lleguen fuera de orden. Los pagos sí se deduplican (`mp_payment_id` e `idempotency_key` únicos).
- **Cambio:** guardar el id de la notificación (o la fecha del preapproval) e ignorar las repetidas o más viejas.

### 2. Posible cobro doble al cambiar de plan
- **Dónde:** el checkout de la suscripción (`iniciarCheckout`).
- **Qué pasa:** antes de crear un preapproval nuevo cancela el anterior "si puede"; si esa cancelación falla, el
  profesional queda con dos débitos automáticos activos en Mercado Pago.
- **Cambio:** no crear el preapproval nuevo si no se pudo cancelar el anterior, o reintentar la cancelación desde
  la reconciliación diaria.

### 3. El link al PDF de la factura no tiene ruta
- **Dónde:** `Invoice.pdfUrl` se guarda como `/api/subscriptions/invoices/{id}/pdf`, pero esa ruta no existe (el
  PDF está en `invoices.pdf_base64`).
- **Cambio:** agregar la ruta (solo el dueño de la factura o un admin) y mostrar el link en Mi Cuenta.

### 4. Facturación con ARCA sin definir
- **Estado:** la integración está implementada pero apagada (`ARCA_ENABLED=false`) y todavía no está definida ni
  detallada. En la Fase 8 se movió tal cual al servicio de pagos.
- **Antes de prenderla, definir:**
  - la numeración: hoy sale solo de `invoice_sequences` y nunca se compara con el último comprobante autorizado
    en ARCA (`FECompUltimoAutorizado` está implementado pero no se usa);
  - un CAE fallido quema un número y el reintento arma otra factura con otro número;
  - un solo emisor por punto de venta;
  - dónde viven el certificado y la clave en producción;
  - el control del tope del Monotributo.

## Seguridad

### 5. Token de QBI2 en `.env.template`
- **Qué pasa:** `QBI2_RECIPE_TOKEN` (homologación de Innovamed) está versionado en `main` y `develop`, y el
  repositorio es público.
- **Cambio:** pedir un token nuevo, dejar la variable vacía en la plantilla y cargar el valor solo en el `.env`
  del VPS.

## Microservicios

### 6. Verificaciones de punta a punta postergadas
Las Fases 5 (R5), 6 (T5), 7 (H5) y 8 (S5) se implementaron sin levantar el stack. Falta recorrerlas en local
(ver cada spec en [specs/](specs/)) antes de desplegarlas.

### 7. Memoria del VPS
Con la Fase 7 el consumo proyectado es ~5,0 GB contra un techo de ~4,8 GB (ver
[entornos/produccion.md](entornos/produccion.md)). Medirlo en las verificaciones de punta a punta y decidir si
se agranda el VPS o se juntan servicios. La Fase 8 no suma contenedores, pero la facturación agrega trabajo al
servicio de pagos.

### 8. Copias del estado de la suscripción
En la Fase 8, el portón del gateway y el servicio de profesionales siguen leyendo el estado de la suscripción del
monolito (de una copia alimentada por eventos). En la Fase 9, al retirar el monolito, tienen que pasar a consumir
`suscripciones.suscripcion.cambio-estado.v2` directo.

### 9. Arreglos de la migración que conviene llevar a `main` antes
- Solo el dueño del turno puede cancelarlo, marcar la asistencia o reprogramarlo (#59).
- El profesional solo carga en la historia clínica, edita la ficha o receta si atiende al paciente (#61).
