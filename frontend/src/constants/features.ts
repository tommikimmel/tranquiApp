// Flags de build del frontend (variables VITE_* que Vite fija al compilar).

// Login con Google: visible salvo que el build lo apague explícitamente. Solo el entorno local
// (docker-compose.local.yml) usa VITE_GOOGLE_LOGIN=false; producción no la define.
export const GOOGLE_LOGIN_ENABLED = import.meta.env.VITE_GOOGLE_LOGIN !== 'false'
