---
name: cron-notification-whatsapp
description: "Motor de tareas programadas (Cron Jobs), recordatorios automáticos de turnos e integración con WhatsApp Business API."
---

# Skill: Scheduling & WhatsApp Notifications

Esta habilidad proporciona pautas para gestionar recordatorios automáticos e integraciones con mensajería externa.

## 1. Responsabilidades del Agente
*   Configurar tareas programadas (`@Scheduled`) seguras, asegurando que se ejecuten en la zona horaria correcta de Argentina.
*   Conectar el backend con APIs de WhatsApp Business utilizando clientes HTTP o SDKs dedicados de Twilio/Meta.
*   Implementar un control estricto de envío de mensajes (`recordatorio_enviado`) para garantizar que la tarea sea idempotente y no notifique más de una vez al paciente por error.

## 2. Pautas de Código
*   Usar expresiones cron descriptivas y evitar que tareas simultáneas compitan por recursos o causen bloqueos de base de datos.
*   Capturar errores del servicio de mensajería externa sin interrumpir la iteración sobre la lista de recordatorios pendientes del día.
