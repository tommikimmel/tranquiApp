---
name: database-migration
description: "Pautas de base de datos relacional PostgreSQL, indexación, diseño DDL y rutinas seguras de backup (pg_dump)."
---

# Skill: Database Migrations & Backups

Esta habilidad detalla los procedimientos recomendados para gestionar la persistencia y la seguridad física de los datos relacionales en **Tranqui App**.

## 1. Responsabilidades del Agente
*   Escribir sentencias SQL DDL compatibles con PostgreSQL 15, utilizando tipos de datos adecuados y llaves foráneas consistentes.
*   Crear e identificar los índices apropiados sobre campos de alta tasa de lectura (búsqueda por e-mail, filtros de agenda y orden de mensajes).
*   Mantener el script de volcado en caliente (`pg_dump`) y asegurar la expiración periódica de las copias de seguridad viejas en el crontab del servidor.

## 2. Pautas de Código
*   Todas las tablas y campos deben ser definidos y manipulados usando nomenclatura `snake_case`.
*   Usar llaves primarias incrementales (`BIGSERIAL`) para mitigar sobrecarga en el índice B-Tree, o `UUID` si se requiere anonimización estricta.
*   Asegurar que todas las llaves foráneas cuenten con restricciones explícitas de eliminación (`ON DELETE CASCADE` o `ON DELETE SET NULL`) para preservar la integridad referencial.
