---
name: appointment-scheduler
description: "Lógica de cálculo de bloques horarios de 45 minutos, sincronización de Google Calendar y flujo administrativo OSDE."
---

# Skill: Appointment Scheduler & Agenda

Esta habilidad define las reglas y el procesamiento algorítmico de la reserva de turnos en **Tranqui App**.

## 1. Responsabilidades del Agente
*   Implementar el cálculo matemático de bloques de 45 minutos basado en la disponibilidad declarada por el psiquiatra.
*   Evitar la sobreposición horaria de reservas concurrentes utilizando técnicas de bloqueo pesimista en JPA (`PESSIMISTIC_WRITE`) o restricciones de base de datos a nivel SQL.
*   Conectar el backend con la API de Google Calendar utilizando OAuth2 flow/Service Accounts para agendar la cita y obtener el enlace de Google Meet.
*   Administrar los campos adicionales de OSDE (Número de afiliación y estado pendiente de copago).

## 2. Pautas de Código
*   Validar siempre que las horas de inicio de los turnos coincidan con bloques lógicos válidos del médico.
*   Controlar las excepciones al conectar con las APIs de Google y registrar logs estructurados en lugar de interrumpir el flujo del usuario si la agenda del calendario falla tras haberse cobrado con éxito.
*   Utilizar la zona horaria unificada `America/Argentina/Cordoba` para todos los cálculos.
