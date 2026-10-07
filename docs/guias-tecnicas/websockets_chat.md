---
name: websockets-chat
description: "Implementación de WebSockets STOMP, mensajería en tiempo real, priorización clínica y notificaciones internas."
---

# Skill: WebSockets Real-Time Messaging & Chat

Esta habilidad detalla las directrices para implementar el sistema de chat y alertas instantáneas entre el médico y el paciente.

## 1. Responsabilidades del Agente
*   Configurar el Message Broker en Spring Boot utilizando STOMP sobre SockJS para asegurar compatibilidad con navegadores antiguos.
*   Implementar interceptores de sesión para validar los tokens JWT antes de autorizar subscripciones a tópicos privados.
*   Diseñar la lógica de priorización del canal ordenando las conversaciones según la cercanía de las citas agendadas (< 72 hs).
*   Garantizar la transmisión en tiempo real de pop-ups de notificaciones de pago o nuevas solicitudes de recetas.

## 2. Pautas de Código
*   No saturar las conexiones WebSocket persistentes con payloads excesivamente grandes.
*   Implementar el mapeo de usuarios autorizados mediante `Principal` en los controladores de sockets (`@MessageMapping`).
*   Configurar logs detallados de conexión, desconexión y latido (`Heart-Beat`) para depurar problemas de red en la VPS.
