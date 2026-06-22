# Etapa 3: Disponibilidad Horaria, Agenda y Generación de Turnos

Esta etapa abarca la configuración horaria del psiquiatra y la reserva automatizada de turnos por parte de los pacientes. Incluye el cálculo de bloques de 45 minutos y la distinción de flujos entre turnos particulares y cobertura de obra social (OSDE).

---

## 1. Objetivos de la Etapa
1.  Permitir que el psiquiatra defina sus rangos de disponibilidad semanal (ej. lunes de 09:00 a 13:00).
2.  Implementar un servicio en el backend que divida dinámicamente dichos rangos en bloques fijos de **45 minutos**.
3.  Establecer la reserva temporal de turnos (bloqueo de 10 minutos) con la máquina de estados del Turno (`PENDIENTE_PAGO`, `PENDIENTE_VALIDACION`, `CONFIRMADO`, `CANCELADO`).
4.  Integrar Google Calendar API para agendar el turno de forma automática e incluir el enlace de Google Meet.

---

## 2. Definición Técnica y Código de Soporte

### A. Algoritmo de Cálculo de Bloques de Turnos (45 Minutos)
Este servicio calcula la lista de franjas horarias disponibles para un día en base a las horas configuradas del médico y los turnos ya agendados en esa fecha.

```java
@Service
public class AgendaService {

    private static final int DURACION_TURNO_MINUTOS = 45;

    public List<LocalTime> calcularBloquesDisponibles(List<Disponibilidad> disponibilidades, List<Turno> turnosExistentes, LocalDate fecha) {
        List<LocalTime> bloquesDisponibles = new ArrayList<>();

        for (Disponibilidad disp : disponibilidades) {
            LocalTime inicio = disp.getHoraInicio();
            LocalTime fin = disp.getHoraFin();

            while (inicio.plusMinutes(DURACION_TURNO_MINUTOS).isBefore(fin) || inicio.plusMinutes(DURACION_TURNO_MINUTOS).equals(fin)) {
                LocalTime finalBloque = inicio.plusMinutes(DURACION_TURNO_MINUTOS);
                
                // Verificar si choca con algún turno existente
                boolean ocupado = comprobarChoqueTurno(inicio, finalBloque, turnosExistentes);
                if (!ocupado) {
                    bloquesDisponibles.add(inicio);
                }
                inicio = finalBloque;
            }
        }
        return bloquesDisponibles;
    }

    private boolean comprobarChoqueTurno(LocalTime inicio, LocalTime fin, List<Turno> turnosExistentes) {
        return turnosExistentes.stream().anyMatch(turno -> {
            LocalTime tInicio = turno.getHoraInicio();
            LocalTime tFin = turno.getHoraFin();
            return (inicio.isBefore(tFin) && fin.isAfter(tInicio));
        });
    }
}
```

### B. Estructura del Flujo de OSDE (Validación en Diferido)
El paciente introduce su número de afiliado de OSDE. La reserva del turno se valida calculando el copago fijo establecido, cobrando mediante Mercado Pago, y una vez abonado, se agenda en Google Calendar bajo el título `[OSDE] Consulta - {Paciente}`.

```java
@Service
public class TurnoService {

    @Autowired
    private TurnoRepository turnoRepository;
    @Autowired
    private GoogleCalendarService calendarService;

    @Transactional
    public Turno confirmarTurnoOsde(Long turnoId, String numeroAfiliado) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        turno.setTipo(TipoTurno.OSDE);
        turno.setMetadataAfiliado(numeroAfiliado);
        
        // Muta a pendiente de validación manual una vez que el copago es procesado (Etapa 4)
        turno.setEstado(EstadoTurno.CONFIRMADO);
        
        // Sincronizar agenda en Google Calendar
        String meetUrl = calendarService.crearEventoReunion(turno);
        turno.setTelemedicinaUrl(meetUrl);

        return turnoRepository.save(turno);
    }
}
```

---

## 3. Estrategia de Testing y Verificación

### A. Prueba Unitaria para Cálculo de Bloques
*   **Prueba Unitaria (`AgendaServiceTest.java`):**
```java
@SpringBootTest
class AgendaServiceTest {

    @Autowired
    private AgendaService agendaService;

    @Test
    void shouldGenerateCorrectBlocksOf45Minutes() {
        Disponibilidad disp = new Disponibilidad();
        disp.setHoraInicio(LocalTime.of(9, 0));
        disp.setHoraFin(LocalTime.of(10, 30)); // Debería dar exactamente 2 bloques (09:00 y 09:45)

        List<Disponibilidad> disponibilidades = List.of(disp);
        List<Turno> turnosExistentes = new ArrayList<>(); // Vacío

        List<LocalTime> bloques = agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, LocalDate.now());
        
        assertEquals(2, bloques.size());
        assertEquals(LocalTime.of(9, 0), bloques.get(0));
        assertEquals(LocalTime.of(9, 45), bloques.get(1));
    }
}
```

### B. Simulación de Interrupción de Calendario
Probar qué ocurre si la API de Google Calendar falla (Rate Limits o Token Expirado). El backend debe hacer un Rollback de la transacción del turno y guardar un log detallado del error para evitar discrepancias en la agenda de la base de datos local y externa.

---

## 4. Cuestiones a Tener en Cuenta / Buenas Prácticas
*   **Zona Horaria:** Asegúrate de normalizar todas las fechas y horas a `UTC-3` (zona horaria oficial de Argentina) tanto en el servidor, base de datos y Google Calendar API para evitar desfases horarios de citas.
*   **Bloqueo de Calendarios Concurrentes:** Cuando dos pacientes intenten reservar el mismo bloque a la misma décima de segundo, se debe implementar una clave primaria compuesta o un bloqueo pesimista (`LockModeType.PESSIMISTIC_WRITE`) en Spring JPA a fin de evitar reservas dobles accidentales.
