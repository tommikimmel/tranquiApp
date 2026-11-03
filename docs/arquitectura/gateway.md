# API Gateway — Spring Cloud Gateway

El API Gateway es el punto de entrada unificado para todos los clientes (navegadores web, aplicaciones móviles y clientes externos) hacia la plataforma Tranqui App.

Se ubica delante del monolito (y de los futuros microservicios) para centralizar la autenticación perimetral, limitación de tasa (rate limiting), resiliencia (circuit breakers y timeouts), observabilidad (correlación distribuida) y protección de rutas internas.

---

## 1. Responsabilidades

1. **Ruteo perimetral:** Despacho de solicitudes según prefijo de ruta hacia el servicio correspondiente o hacia el monolito.
2. **Identificador de correlación (`X-Correlation-Id`):** Generación automática si no viene en la solicitud entrante y propagación reactiva tanto al backend como en la respuesta al cliente.
3. **Control de tasa (Rate Limiting):** Algoritmo token bucket implementado sobre Redis para prevenir abusos y ataques de denegación de servicio.
4. **Protección de endpoints internos (`/internal/**`):** Bloqueo con 404 ante tráfico perimetral con cabeceras `X-Forwarded-*` y verificación de firma criptográfica mediante JWT de servicio (`ServiceJwt`).
5. **Resiliencia perimetral:** Circuit Breakers basados en Resilience4j con respuestas de contingencia (fallback 503 JSON) estandarizadas, timeouts estrictos y reintentos automáticos para operaciones seguras (GET).
6. **Manejo de CORS sin duplicación:** El Gateway no deriva los preflight `OPTIONS`: los responde con `spring.cloud.gateway.server.webflux.globalcors`, que replica la política del monolito (`SecurityConfig.corsConfigurationSource`) y que `CorsConfig` aplica también a los controladores propios del Gateway (`/health`, `/api/admin/**`). En las respuestas derivadas, el filtro `DedupeResponseHeader` (`RETAIN_FIRST`) evita encabezados CORS duplicados con los que agrega el monolito. En producción el frontend y `/api` comparten dominio, así que CORS solo actúa en local (frontend en `:5173`, Gateway en `:8080`).
7. **Monitoreo y Salud:** Endpoint público `/health` que expone estado binario (`UP` / `DOWN`) sin filtrar detalles internos. El Actuator de Spring Boot y las métricas Prometheus se exponen únicamente en el puerto de gestión interna (8082), igual que en el monolito; el chequeo de Consul usa `/actuator/health` en ese puerto.

---

## 2. Tabla de Rutas

Configuradas en `gateway/src/main/resources/application.yml` (y sobreescritas por perfil):

| Ruta Gateway | Destino inicial (Fase 0) | Servicio futuro | Timeouts (connect / response) | Rate Limit (req/min) | Circuit Breaker |
|---|---|---|---|---|---|
| `/api/auth/**` | `http://backend:8080` | Auth Service | 2 s / 10 s | 10 req/min (IP) | `auth-cb` |
| `/api/turnos/reservar` | `http://backend:8080` | Turnos Service | 2 s / 10 s | 5 req/min (User/IP) | `turnos-cb` |
| `/api/turnos/**` | `http://backend:8080` | Turnos Service | 2 s / 10 s | 60 req/min (User/IP) | `turnos-cb` |
| `/api/medicos/**` | `http://backend:8080` | Profesionales Service | 2 s / 10 s | 60 req/min (User/IP) | `medicos-cb` |
| `/api/pacientes/**` | `http://backend:8080` | Pacientes Service | 2 s / 10 s | 60 req/min (User/IP) | `pacientes-cb` |
| `/api/recetas/**` | `http://backend:8080` | Recetas Service | 2 s / 10 s | 30 req/min (User/IP) | `recetas-cb` |
| `/api/pagos/webhook` | `http://backend:8080` | Pagos Service | 2 s / 30 s | 120 req/min (IP) | `pagos-cb` |
| `/api/pagos/**` | `http://backend:8080` | Pagos Service | 2 s / 30 s | 30 req/min (User/IP) | `pagos-cb` |
| `/api/chat/**` | `http://backend:8080` | Chat Service | 2 s / 10 s | 120 req/min (User/IP) | `chat-cb` |
| `/ws-tranqui/**` | `http://backend:8080` | WebSocket / Chat | 2 s / 10 s | 60 req/min (User/IP) | — |
| `/internal/**` | `http://backend:8080` | Servicios internos | 2 s / 10 s | 600 req/min (Service) | `internal-cb` |
| `/api/**` | `http://backend:8080` | Monolito (catch-all) | 2 s / 10 s | 120 req/min (User/IP) | `monolito-cb` |

---

## 3. Limitación de Tasa (Rate Limiting)

Implementado en `TranquiRateLimiter` mediante un script Lua ejecutado atómicamente en Redis con el algoritmo de token bucket:

- **Resolución de clave (`KeyResolver`):**
  - **Verificación de usuario (`UserJwtVerifier`):** Extrae el token de autenticación desde la cookie `SESSION-TOKEN` o el encabezado `Authorization: Bearer <token>`. Valida la firma HMAC-SHA256 con `jwt.secret` y la fecha de expiración. Extrae el identificador de usuario (`userId`, `id`, `usuarioId` o `sub`) para limitar por cuenta (`user:<id>`). Sin defaults hardcodeados en el código: en perfiles distintos a local/test, la ausencia de la variable `JWT_SECRET` impide el arranque del gateway con un mensaje de error explícito.
  - **Resolución de IP real (`RealIpResolver`):** Utiliza la propiedad `tranqui.gateway.trusted-proxies` (redes privadas por defecto: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` y `127.0.0.1/32`). Si la IP remota es un proxy confiable (ej. Traefik) y existe encabezado `X-Forwarded-For`, se selecciona la IP más a la derecha que NO sea un proxy confiable. Si la IP remota no es confiable, se ignora cualquier cabecera `X-Forwarded-For` para evitar suplantaciones (spoofing) y se utiliza la IP remota directa. Se aplica en `UserOrIpKeyResolver` (anónimos) y en `IpKeyResolver` (login, registro y webhooks de Mercado Pago).
  - **Rutas internas:** Para `/internal/**`, se limita por la cabecera del servicio emisor (`service:<nombre>`).
- **Respuesta 429 Too Many Requests:**
  - Cuando se agotan las fichas disponibles en la ventana, la respuesta HTTP 429 incluye obligatoriamente la cabecera `Retry-After: <segundos>`, indicando al cliente cuánto debe esperar antes de reintentar.
  - Se adjuntan cabeceras estándar de tasa: `X-RateLimit-Remaining: 0`.
- **Política de Falla Abierta (Fail-Open):**
  - Si la conexión a Redis se interrumpe, se agota por timeout o arroja error, el gateway **no bloquea el tráfico de los usuarios**.
  - Emite un log de advertencia (`WARN`), incrementa el contador de métrica Micrometer `gateway.ratelimit.redis.errors` y permite que la solicitud continúe su curso hacia el backend.

---

## 4. Seguridad en Rutas Internas (`/internal/**`)

Las rutas con prefijo `/internal/**` están estrictamente reservadas para la comunicación privada entre servicios:

1. **Detección de accesos perimetrales no autorizados:** Si la petición entrante contiene cualquier encabezado que comience con `X-Forwarded-` (como `X-Forwarded-For`, `X-Forwarded-Proto`, etc.), se asume que proviene del perímetro público y se responde de inmediato con `404 Not Found` para no filtrar la existencia del endpoint.
2. **Validación de JWT de Servicio (`ServiceJwt`):**
   - Requiere una cabecera `Authorization: Bearer <token>`.
   - El token es verificado criptográficamente utilizando HMAC-SHA256 con el secreto compartido configurado en `app.service-jwt.secret`.
   - Se valida fecha de expiración y formato. Si no está presente o no es válido, se rechaza con `401 Unauthorized`.
   - Si es válido, se extrae el identificador del servicio emisor (`issuer` / `sub`) y se inyecta la cabecera descendente `X-Service-Name`.

---

## 5. Resiliencia y Fallbacks

- **Timeouts:**
  - Timeout de conexión HTTP de 2 segundos para todos los destinos.
  - Timeout de respuesta por defecto de 10 segundos.
  - Timeout de respuesta extendido a 30 segundos para endpoints de pagos y webhooks (`/api/pagos/**`).
- **Reintentos:**
  - Exclusivamente para peticiones idempotentes con método HTTP `GET`.
  - Máximo 2 intentos con backoff exponencial inicial de 100 ms ante errores de conexión (`ConnectTimeoutException`, `IOException`).
- **Circuit Breaker:**
  - Si un servicio acumula fallas consecutivas o supera el umbral de lentitud, el Circuit Breaker de Resilience4j se abre.
  - Toda solicitud derivada al fallback responde con código HTTP `503 Service Unavailable` y un cuerpo JSON estructurado:
    ```json
    {
      "error": "servicio_no_disponible",
      "servicio": "<nombre_servicio>"
    }
    ```

---

## 6. Correlación y Trazabilidad

El filtro global reactivo `CorrelationIdGlobalFilter` asegura la observabilidad de cada interacción:
1. Inspecciona la cabecera `X-Correlation-Id`.
2. Si no viene provista por el cliente, genera un UUID aleatorio.
3. Lo inyecta en la petición hacia el backend.
4. Lo incorpora en las cabeceras de respuesta al cliente antes del commit HTTP.
