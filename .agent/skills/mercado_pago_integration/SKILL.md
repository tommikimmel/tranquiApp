---
name: mercado-pago-integration
description: "Directrices para la pasarela de Mercado Pago Marketplace, cifrado AES-256 de tokens, webhooks y reembolsos programáticos."
---

# Skill: Mercado Pago Gateway Integration

Esta habilidad contiene directrices para implementar de manera segura y multitenant los flujos de cobro en Mercado Pago.

## 1. Responsabilidades del Agente
*   Implementar cifrado AES-256 simétrico para salvaguardar los tokens del psiquiatra en la base de datos local.
*   Inicializar dinámicamente el SDK de Mercado Pago con las credenciales específicas del médico dueño del turno antes de generar la Preferencia.
*   Diseñar y asegurar el endpoint de Webhook validando firmas (`x-signature` u otro mecanismo provisto por MP) para registrar acreditaciones de pago reales.
*   Gestionar devoluciones de dinero consumiendo la API de Reembolsos de Mercado Pago si la cancelación ocurre con más de 48 horas de anticipación.

## 2. Pautas de Código
*   La lógica de cifrado debe usar vectores de inicialización (`IV`) o llaves robustas inyectadas por variables de entorno Docker.
*   Los webhooks deben procesarse de manera asíncrona o con mecanismos de protección de reintentos para no bloquear la pasarela de Mercado Pago en caso de demoras en la base de datos local.
