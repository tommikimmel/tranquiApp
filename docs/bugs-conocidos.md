# Bugs conocidos

Errores detectados y todavía sin arreglar. Al arreglar uno, se saca de esta lista (y queda en el
`CHANGELOG.md`).

## 1. El checkout muestra servicios que el profesional no ofrece

- **Dónde:** `frontend/src/components/CheckoutFlow.tsx`, lista `services`.
- **Qué pasa:** siempre se muestran los 5 servicios por defecto (Consulta particular, Obra Social
  OSDE, Receta fuera de turno, Certificado, Sobre turno) con precios fijos de respaldo, aunque el
  profesional no los tenga cargados o los haya deshabilitado. A una psicóloga le aparece "Receta
  fuera de turno".
- **Arreglo probable:** mostrar solo las tarifas presentes y habilitadas en `professional.tariffs`.

## 2. Un turno más tarde en la hora actual desaparece del panel del profesional

- **Dónde:** `frontend/src/components/DashboardHome.tsx` (próximo turno y "Próximos eventos").
- **Qué pasa:** la API devuelve `hour` sin minutos (por ejemplo `"21"`, con los minutos en
  `horaInicio`). El filtro hace `hour.split(':')`, toma los minutos como 0 y descarta el turno si
  la hora coincide con la actual. Ejemplo: a las 10:05, un turno de las 10:30 no aparece, justo
  cuando el profesional necesita el botón de Meet.
- **Arreglo probable:** usar `horaInicio` para comparar.

## 3. A una psicóloga se le ofrecen acciones de recetas

- **Dónde:** "Acciones rápidas" del Inicio (`DashboardHome.tsx`, "Emitir receta" e "Historia
  clínica") y la ficha del paciente en `PatientsView.tsx` ("Emitir Receta").
- **Qué pasa:** se muestran aunque el plan del profesional no incluye recetas (y la emisión de
  recetas está deshabilitada en general).
- **Arreglo probable:** ocultarlas con el mismo criterio que el menú lateral (`canUseRecetas`).
