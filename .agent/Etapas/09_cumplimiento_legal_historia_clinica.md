# Etapa 9: Cumplimiento Legal — Historia Clínica e Informes Clínicos (Argentina)

Este documento releva el marco legal argentino que alcanza a los informes clínicos y a la historia clínica de **Tranqui App** (registros de salud mental generados por el rol `PSIQUIATRA` sobre el rol `PACIENTE`), audita el estado actual del código contra esas exigencias, y define un plan de acción priorizado.

> Este documento es investigación legal aplicada a producto, no asesoramiento legal formal. Antes de publicar cambios de este alcance, hacerlos revisar por un abogado especializado en derecho sanitario (idealmente junto al psiquiatra a cargo del contenido clínico).

---

## 1. Marco legal aplicable

| Norma | Qué regula | Por qué aplica a Tranqui App |
|---|---|---|
| **Ley 26.529** (Derechos del Paciente, Historia Clínica y Consentimiento Informado, modif. por Ley 26.742) | Define historia clínica, derechos del paciente, quiénes pueden acceder, plazos de guarda y entrega | Es la norma madre: todo lo que Tranqui App llama "informe clínico" es legalmente parte de la historia clínica |
| **Ley 26.657** (Ley Nacional de Salud Mental) | Régimen especial para registros de **salud mental**: confidencialidad reforzada, abordaje interdisciplinario, derechos del "asistido" | Tranqui App es específicamente una app de salud mental (rol `PSIQUIATRA`), así que este es el estándar más estricto, no el genérico |
| **Ley 25.326** (Protección de Datos Personales) + Disposiciones de la AAIP | Datos "sensibles" (salud), principios de finalidad/proporcionalidad, medidas de seguridad, derecho de acceso/rectificación | Los informes y seguimientos diarios son datos sensibles de máxima protección |
| **Ley 27.706** (Programa Federal de Historia Clínica Electrónica) + Decreto 393/2023 | Requisitos técnicos de una Historia Clínica Electrónica (HCE): integridad, autenticidad, **inalterabilidad**, trazabilidad, accesos restringidos | Tranqui App ya es una HCE de facto (registro digital exclusivo) — le aplican estos requisitos técnicos aunque el psiquiatra no esté federado al sistema único nacional |
| **Ley 25.506** (Firma Digital) | Validez legal de firma electrónica/digital sobre documentos | Relevante para que cada informe quede "firmado" y sea oponible como documento auténtico |
| **Ley 27.553** (Recetas electrónicas / telemedicina) | Requisitos de teleconsulta y registro de consentimiento informado específico | Si en el futuro Tranqui App habilita videollamada o recetas, aplica directamente |
| **Código Penal, art. 156** | Violación de secreto profesional | Marco penal que protege el contenido de los informes frente a divulgación sin justa causa |
| **Código Civil y Comercial, arts. 55-61** | Consentimiento informado, actos de disposición sobre el propio cuerpo, derechos personalísimos | Base civil del consentimiento y de la intimidad del paciente |
| **Ley 26.061** (Protección Integral de Niños, Niñas y Adolescentes) + CCyC art. 26 | Autonomía progresiva de menores sobre su salud y sus datos | Solo aplica si Tranqui App llega a atender pacientes menores de edad — a tener en cuenta si se planea |
| Normativa provincial (ej. Ley 14.464 PBA, Ley 5.669 CABA) | Adhesiones/reglamentaciones locales de historia clínica electrónica | Verificar según dónde estén matriculados los psiquiatras que usan la plataforma |

---

## 2. Qué exige cada ley, en concreto

### 2.1 Titularidad y acceso del paciente (Ley 26.529, arts. 14 y 19)
- El **paciente es el titular** de su historia clínica (no el profesional, no la institución).
- A su simple pedido, tiene derecho a copia autenticada **dentro de las 48 horas**.
- Si se le niega, demora o guarda silencio, puede iniciar una acción directa de **hábeas data**.
- También están legitimados: representante legal, cónyuge/conviviente, herederos forzosos (si el paciente falleció), y el médico que el paciente autorice.
- **Confirma lo que ya intuías**: el paciente debe poder ver sus informes en todo momento. Tranqui App ya lo resuelve parcialmente con el portal (`GET /api/pacientes/me/informes`), lo cual de hecho **excede** el mínimo legal (48 hs) — está bien encaminado.

### 2.2 Inmutabilidad y corrección por anexo (Ley 26.529 art. 15 + reglamentación de HCE + Ley 27.706)
- La historia clínica debe ser **cronológica, foliada y completa**: no se editan ni se borran entradas.
- Si hay un error, **se agrega un nuevo asiento** (fecha, hora y responsable de la corrección) **sin suprimir lo corregido**. Esto es exactamente el mecanismo de "anexo" que mencionás.
- Ley 27.706 lo refuerza para historia clínica electrónica: debe ser "íntegra, auténtica, **inalterable**, perdurable, única, inviolable y recuperable".
- **Confirma tu segunda intuición**: ni el backend debería permitir un `UPDATE` que pise el contenido original, ni mucho menos un `DELETE`.

### 2.3 Confidencialidad reforzada de salud mental (Ley 26.657, arts. 12-16)
- Va más allá de la confidencialidad médica genérica: la información solo puede darse a terceros **con consentimiento fehaciente del paciente**, salvo riesgo cierto e inminente para sí o terceros.
- El registro debe reflejar la intervención de un **equipo interdisciplinario**, evitando diagnósticos basados exclusivamente en un juicio aislado.
- El paciente, su abogado o la persona que designe tienen derecho a acceder a antecedentes, fichas e historias clínicas.

### 2.4 Datos sensibles y seguridad (Ley 25.326)
- Los datos de salud (y en especial salud mental) son "datos sensibles": exigen consentimiento expreso para su tratamiento y medidas de seguridad reforzadas (cifrado, control de accesos, logs de auditoría).
- El titular tiene derecho a saber **quién accedió** a sus datos y a pedir rectificación/supresión de lo que sea inexacto (sin que eso habilite borrar la historia clínica legal, que tiene su propio régimen de conservación).
- Si los datos se alojan fuera de Argentina (VPS/cloud en el exterior), hay que revisar transferencia internacional de datos — Argentina exige que el país destino tenga protección "adecuada" o cláusulas contractuales específicas.

### 2.5 Plazo de guarda (Ley 26.529 art. 18)
- Conservación mínima: **10 años desde el último acto médico registrado** (no desde la creación del documento ni desde que el paciente deja de usar la app). Esto debe ser una política de retención activa, no un simple "no borrar nunca".

### 2.6 Requisitos técnicos de HCE (Ley 27.706 + Decreto 393/2023)
- Integridad, autenticidad, trazabilidad de todos los accesos y modificaciones, control de usuarios por rol, respaldo seguro.
- Cada asiento debería quedar, en la práctica, "firmado" por el profesional (firma digital/electrónica o al menos un mecanismo de sellado con hash + timestamp verificable) para sostener su valor probatorio.

---

## 3. Qué pasa si un juez pide los informes clínicos

1. **El secreto profesional (CP art. 156) es la regla, no la excepción.** El psiquiatra/la plataforma no puede entregar información por un simple pedido informal (abogado de una de las partes, familiar, aseguradora, empleador, etc.) sin consentimiento del paciente.
2. **La excepción válida es una orden judicial fundada** ("oficio judicial") de un juez competente, dirigida formalmente a la institución/profesional. Ahí el secreto cede porque hay "justa causa" y un mandato de autoridad competente.
3. Aun con oficio judicial, el criterio de buena práctica (y lo que recomienda doctrina y colegios profesionales) es:
   - Entregar **solo lo estrictamente requerido** por el oficio (principio de minimización), no la historia clínica completa salvo que el oficio lo pida expresamente.
   - Verificar la autenticidad del oficio (membrete, firma, número de causa) antes de responder.
   - Dejar **registro de la divulgación**: qué se entregó, a quién, cuándo, bajo qué causa judicial — esto es además un derecho del paciente bajo Ley 25.326 (saber quién accedió a sus datos).
   - Si es razonable y no está prohibido por el propio oficio (ej. causas de violencia de género/familia pueden restringirlo), **notificar al paciente** que se recibió el pedido.
   - En salud mental, el criterio de la Ley 26.657 es todavía más restrictivo: se prioriza no revictimizar y limitar la información a lo relevante para la causa.
4. Si el pedido llega directamente al profesional y no a "la empresa", igual conviene que la plataforma tenga un **canal único y trazable** para este tipo de solicitudes (no que cada psiquiatra responda por su cuenta desde el chat de la app).
5. Guardar copia del oficio y de la respuesta enviada como parte del expediente, con plazo de conservación igual o mayor al de la propia historia clínica.

---

## 4. Auditoría del código actual vs. lo exigido

Reviité `backend/src/main/java/com/tranqui/app/{controller,service}/Clinical*.java` y `model/InformeClinico.java`. Hallazgos concretos:

### 🔴 Crítico — viola la ley tal como está hoy
- **`DELETE /api/pacientes/{pacienteId}/informes/{informeId}`** (`ClinicalController.java:176-187`, `ClinicalService.eliminarInforme`) hace un **hard delete** (`informeClinicoRepository.delete(informe)`). Esto es ilegal: la historia clínica no puede perder asientos, y menos aún antes de cumplirse el plazo de guarda de 10 años.
- **`PUT /api/pacientes/{pacienteId}/informes/{informeId}`** (`ClinicalController.java:189-207`, `ClinicalService.editarInforme`) hace un **overwrite en el lugar** de `tipoInforme`, `planTrabajo` y `contenido`, sin dejar rastro del valor anterior. Esto viola directamente el mecanismo de corrección por anexo (no se puede "suprimir lo corregido").
- Lo mismo aplica a **`DELETE /api/pacientes/{pacienteId}/seguimientos/{seguimientoId}`** sobre `SeguimientoDiario`.
- `InformeClinico` (`model/InformeClinico.java`) no tiene ningún campo de trazabilidad más allá de `fechaCreacion`: no hay `fechaModificacion`, no hay referencia a un informe "padre" u "original", no hay estado (`VIGENTE`/`ANULADO_POR_ANEXO`), no hay firma ni hash de integridad.

### 🟡 Importante — falta, pero no es una violación activa
- No hay endpoint ni tabla de **auditoría de accesos** (quién vio o exportó qué informe, cuándo) — lo exige tanto Ley 27.706 (trazabilidad) como el derecho del paciente bajo Ley 25.326 a saber quién accedió a sus datos.
- No hay **política de retención** (los 10 años desde el último contacto) ni proceso de expurgo/archivado — hoy los datos simplemente viven mientras exista la fila en la base.
- No hay un flujo dedicado para **solicitudes judiciales** (recepción del oficio, validación, exportación acotada, registro de la entrega).
- No hay **firma digital/electrónica** del profesional sobre cada informe (Ley 25.506 / Ley 27.706), solo la relación `medico` en la entidad.
- No hay manejo diferenciado para **pacientes menores de edad** (representante legal, autonomía progresiva) — a evaluar si el producto los va a atender.
- No se ve una capa de **cifrado en reposo** para el contenido clínico en la base (a confirmar contra `docker-compose.yml`/config de Postgres) — recomendado dado que es un dato "sensible" de máxima protección.
- El consentimiento informado para el tratamiento de datos de salud (Ley 25.326) y para el uso de la plataforma no aparece modelado explícitamente (aceptación de términos con fecha/versión).

### 🟢 Ya está bien encaminado
- El paciente puede ver sus propios informes en cualquier momento vía `GET /api/pacientes/me/informes` — cumple y excede el mínimo de 48 hs de la Ley 26.529.
- Hay verificación de propiedad (`informe.getMedico().getEmail().equals(medicoEmail)`) antes de editar/eliminar, lo que al menos evita que un psiquiatra toque informes de otro (buena práctica de seguridad, aunque no alcanza para el problema de fondo).
- Los roles `PACIENTE`/`PSIQUIATRA` con `@PreAuthorize` ya dan una base de control de accesos por rol, requisito de Ley 27.706.

---

## 5. Plan de acción propuesto

### Fase A — Corregir violaciones activas (bloqueante, hacer primero)
1. **Eliminar el hard delete de informes y seguimientos.** Reemplazar `DELETE` por un *soft state* (`estado = ANULADO`, con motivo y autor) que nunca borra la fila. Si el requisito de negocio es "el psiquiatra se equivocó al crear un borrador", solo debería poder anularse dentro de una ventana muy corta (ej. minutos) y aun así dejar rastro — no borrar silenciosamente.
2. **Reemplazar el `PUT` de edición por un flujo de anexo/corrección:**
   - El informe original se marca `VIGENTE_CORREGIDO` pero conserva su contenido intacto.
   - Se crea un nuevo `InformeClinico` (o una entidad `AnexoInforme`) de tipo `ANEXO_CORRECCION`, con referencia al informe original (`informeOriginalId`), motivo de la corrección, autor, fecha/hora.
   - En la vista del paciente y del profesional, el anexo se muestra **junto al original**, no en su reemplazo — ambos visibles, en orden cronológico.
3. Migrar `SeguimientoDiario` al mismo patrón (o, si su naturaleza es más informal tipo diario, al menos loguear la corrección aunque el registro visual se simplifique).

### Fase B — Trazabilidad e integridad
4. Agregar tabla/entidad de **auditoría de accesos** (`AccesoInforme`: usuario, informeId, acción [`VER`,`EXPORTAR`,`ENTREGAR_JUDICIAL`], timestamp, IP) para cumplir Ley 27.706 y el derecho de acceso de Ley 25.326.
5. Agregar **hash de integridad** (ej. SHA-256 del contenido + timestamp) por asiento, y evaluar firma electrónica del profesional al crear/anexar un informe — sostiene el valor probatorio ante un juez.
6. Job de retención: calcular automáticamente la fecha mínima de guarda (10 años desde el último acto registrado del paciente) y bloquear cualquier purga automática antes de ese plazo.

### Fase C — Flujo de solicitudes judiciales
7. Crear un módulo administrativo **"Solicitudes judiciales"** separado del uso clínico normal:
   - Carga del oficio judicial (PDF) por un rol administrativo/legal (no el psiquiatra tratante directamente).
   - Selección acotada de qué informes/rango de fechas se entregan (principio de minimización).
   - Generación de un export certificado (PDF con hash y metadata de la causa) + registro automático en la tabla de auditoría con motivo `ENTREGA_JUDICIAL`.
   - Notificación al paciente (salvo que el oficio la prohíba expresamente).
8. Documentar internamente (para los psiquiatras usuarios de la plataforma) que **nunca deben responder pedidos de historia clínica por fuera de este flujo** — ni por chat, ni por mail directo — para mantener la trazabilidad y no incurrir personalmente en violación de secreto profesional.

### Fase D — Consentimiento y datos sensibles
9. Modelar el **consentimiento informado** para tratamiento de datos de salud como un registro versionado (texto aceptado, fecha, IP) al alta del paciente, distinto del simple "acepto términos y condiciones".
10. Revisar cifrado en reposo para las columnas `contenido`/`planTrabajo` (o cifrado de disco a nivel de base) y cifrado en tránsito (ya debería estar si todo corre sobre HTTPS — confirmar en `devops/`).
11. Si en algún momento se apunta a pacientes menores de edad: agregar rol/relación de representante legal con reglas de acceso distintas a las de un adulto (Ley 26.061 + CCyC art. 26).

### Fase E — Alcance nacional (opcional, más largo plazo)
12. Evaluar si conviene interoperar con el **Sistema Único de Registro de Historias Clínicas Electrónicas** (Ley 27.706) — hoy es progresivo/por adhesión, no obligatorio de inmediato, pero es la dirección de la política pública en salud digital en Argentina.

---

## 6. Checklist de cumplimiento

- [ ] El paciente puede ver todos sus informes en todo momento (ya cumplido).
- [ ] Ningún informe se puede editar en el lugar; toda corrección genera un anexo visible junto al original.
- [ ] Ningún informe ni seguimiento se puede eliminar físicamente de la base.
- [ ] Existe registro de auditoría de accesos/exportaciones/entregas.
- [ ] Existe un flujo separado y documentado para responder oficios judiciales, con minimización de datos y registro de la entrega.
- [ ] Existe política de retención mínima de 10 años desde el último acto clínico.
- [ ] El consentimiento informado para datos de salud está modelado y versionado, no implícito.
- [ ] Los datos sensibles están cifrados en reposo y en tránsito.
- [ ] (Si aplica) Hay un flujo diferenciado para pacientes menores de edad.

---

## Fuentes
- [Ley 26.529 — texto actualizado (legisalud.gov.ar)](http://www.legisalud.gov.ar/pdf/ley26529_ta.pdf)
- [Justicia Cerca — Mi historia clínica (Argentina.gob.ar)](https://www.argentina.gob.ar/justicia/derechofacil/aplicalaley/mi-historia-clinica)
- [Ley Nacional de Salud Mental 26.657 — texto original](https://www.argentina.gob.ar/normativa/nacional/ley-26657-175977/texto)
- [Ley 25.326 — Protección de Datos Personales (texto actualizado, HCDN)](https://www3.hcdn.gob.ar/dependencias/secparl/dgral_info_parlamentaria/dip/archivos/Ley_25326.pdf)
- [Ley de Protección de Datos Personales para psicólogos — Ley 25.326 (Brauni)](https://brauni.io/blog/ley-proteccion-datos-personales-psicologos)
- [Ley 27.706 — Historia Clínica Electrónica (texto oficial)](https://www.argentina.gob.ar/sites/default/files/2024/04/ley_27706_historia_clinica_electronica.pdf)
- [Comentarios a la Ley 27.706 (Abeledo Gottheil)](https://abeledogottheil.com.ar/comentarios-a-la-ley-27-706-unificacion-de-historias-clinicas-electronicas-en-argentina/)
- [Historia clínica electrónica en Argentina: qué exige la Ley 26.529 (psik.com.ar)](https://www.psik.com.ar/blog/historia-clinica-electronica-ley-26529)
- [Art. 156 Violación de Secreto Profesional (Pensamiento Penal)](https://www.pensamientopenal.com.ar/cpcomentado/37763-art-156-violacion-secreto-profesional)
- [Secreto profesional en psicología: alcances y excepciones (Brauni)](https://brauni.io/blog/secreto-profesional-psicologia)
- [Ley 27.553 — disposiciones de telemedicina en Argentina (Beccar Varela)](https://beccarvarela.com/novedades/ley-27-553-con-que-disposiciones-legales-cuenta-la-telemedicina-en-argentina/)
