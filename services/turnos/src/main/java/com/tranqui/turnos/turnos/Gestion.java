package com.tranqui.turnos.turnos;

import com.tranqui.turnos.config.Fechas;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * Lo que pasa con un turno después de reservarlo: cancelar, abandonar el pago, asistencia, reprogramar,
 * documentos y recetas, y los links de los mails. Misma lógica que {@code TurnoService} del monolito; los
 * avisos y mails los manda notificaciones con los eventos, el calendario se entera por los eventos y el
 * reembolso lo hace pagos ({@code turno.cancelado} con {@code EN_PROCESO}).
 */
@Service
public class Gestion {

    private static final Logger log = LoggerFactory.getLogger(Gestion.class);
    private static final Set<String> ASISTENCIAS = Set.of("ESPERANDO", "LLEGO", "AUSENTE", "COMPLETADA");
    // 8MB del archivo decodificado: alcanza para un certificado o informe escaneado.
    private static final long MAX_DOCUMENTO_BYTES = 8L * 1024 * 1024;

    private final Turnos turnos;
    private final Personas personas;
    private final Profesionales profesionales;
    private final Publicador publicador;
    private final ClienteInterno interno;
    private final String publicUrl;

    public Gestion(Turnos turnos, Personas personas, Profesionales profesionales, Publicador publicador, ClienteInterno interno,
                   @Value("${app.public-url:http://localhost:8081}") String publicUrl) {
        this.turnos = turnos;
        this.personas = personas;
        this.profesionales = profesionales;
        this.publicador = publicador;
        this.interno = interno;
        this.publicUrl = publicUrl;
    }

    Turno buscar(Long id) {
        return turnos.porIdParaActualizar(id).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
    }

    /**
     * La política de cancelación de siempre ({@code ReembolsoService#correspondeReembolso}): corresponde reembolso
     * si cancela el profesional o si faltan más de 48 horas.
     */
    static boolean correspondeReembolso(Turno t, boolean cancelaProfesional) {
        LocalDateTime fechaTurno = LocalDateTime.of(t.fecha, t.horaInicio);
        return cancelaProfesional || !LocalDateTime.now().plusHours(48).isAfter(fechaTurno);
    }

    @Transactional
    public void cancelar(Long turnoId, String emailQuienCancela) {
        Turno t = buscar(turnoId);
        String emailMedico = personas.porId(t.medicoId).map(Personas.Persona::email).orElse(null);
        boolean cancelaProfesional = emailQuienCancela != null && emailMedico != null && emailMedico.equalsIgnoreCase(emailQuienCancela);
        String resultado = "SIN_PAGO";
        if (t.tienePagoAprobado()) {
            resultado = correspondeReembolso(t, cancelaProfesional) ? "EN_PROCESO" : "SIN_REEMBOLSO";
        }
        t.estado = Turno.CANCELADO;
        turnos.guardar(t);
        publicador.cancelado(t, cancelaProfesional ? "PROFESIONAL" : "PACIENTE", "Cancelado por usuario", resultado);
        publicador.actualizado(t);
    }

    /** Solo una reserva todavía sin pagar, con el token de la reserva (sin sesión). */
    @Transactional
    public void abandonarPago(Long turnoId, String token) {
        Turno t = buscar(turnoId);
        if (token == null || t.tokenReserva == null || !t.tokenReserva.equals(token)) {
            throw new IllegalStateException("La reserva no corresponde a este token.");
        }
        if (!Turno.PENDIENTE_PAGO.equals(t.estado)) {
            return;
        }
        t.estado = Turno.CANCELADO;
        turnos.guardar(t);
        publicador.actualizado(t);
    }

    @Transactional
    public void asistencia(Long turnoId, String asistencia) {
        Turno t = buscar(turnoId);
        String valor = asistencia != null ? asistencia.toUpperCase() : null;
        if (valor == null || !ASISTENCIAS.contains(valor)) {
            throw new IllegalArgumentException("Estado de asistencia inválido: " + asistencia);
        }
        t.asistencia = valor;
        turnos.guardar(t);
        publicador.actualizado(t);
    }

    /**
     * Solo para documentos que no son recetas (certificados, informes): el archivo sale como adjunto por mail
     * (lo manda notificaciones, decisión 3) y no se guarda; solo cambia {@code documentoEnviado}.
     */
    @Transactional
    public void documentoEnviado(Long turnoId, String emailMedico, String archivo, String nombreArchivo) {
        Turno t = buscar(turnoId);
        Personas.Persona medico = personas.porId(t.medicoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (medico.email() == null || !medico.email().equalsIgnoreCase(emailMedico)) {
            throw new IllegalStateException("No tenés permiso para modificar este turno.");
        }
        if (t.ocupaAgenda) {
            throw new IllegalStateException("Este turno no corresponde a un documento.");
        }
        if (!Turno.CONFIRMADO.equals(t.estado)) {
            throw new IllegalStateException("El documento todavía no fue pagado.");
        }
        if (t.esDeReceta()) {
            throw new IllegalStateException("Las recetas se marcan como enviadas automáticamente al generarlas en Recetas Electrónicas.");
        }
        if (archivo == null || archivo.isBlank()) {
            throw new IllegalArgumentException("Adjuntá el archivo del documento para poder enviarlo por mail.");
        }
        if (bytesDecodificados(archivo) > MAX_DOCUMENTO_BYTES) {
            throw new IllegalArgumentException("El archivo pesa demasiado: el máximo permitido es 8MB.");
        }
        Personas.Persona paciente = personas.porId(t.pacienteId).orElse(null);
        if (paciente == null || paciente.email() == null || paciente.email().isBlank()) {
            throw new IllegalStateException("El paciente no tiene un email registrado: no se puede enviar el documento.");
        }
        try {
            interno.enviarDocumento(new ClienteInterno.DocumentoAdjunto(paciente.email(), paciente.nombre(), medico.nombre(),
                    Etiquetas.tipo(t), archivo, nombreArchivo));
        } catch (RestClientException e) {
            log.error("No se pudo mandar el documento del turno {}: {}", turnoId, e.getMessage());
            throw new IllegalStateException("No se pudo enviar el mail con el documento. Intentá de nuevo en unos minutos.");
        }
        t.documentoEnviado = true;
        turnos.guardar(t);
        publicador.documentoEnviado(t, Etiquetas.tipo(t), "ADJUNTO");
        publicador.actualizado(t);
    }

    /**
     * El profesional emitió recetas al paciente (evento del monolito, que tiene las recetas): sus "receta fuera
     * de turno" pagas para ese paciente se dan por enviadas, como {@code marcarRecetasEnviadasParaPaciente}.
     */
    @Transactional
    public void recetasEmitidas(Long medicoId, Long pacienteId) {
        for (Turno t : turnos.recetasPendientes(medicoId, pacienteId)) {
            t.documentoEnviado = true;
            turnos.guardar(t);
            publicador.documentoEnviado(t, Etiquetas.tipo(t), "RECETA");
            publicador.actualizado(t);
        }
    }

    @Transactional
    public void reprogramar(Long turnoId, String fechaTexto, String horaTexto) {
        Turno t = buscar(turnoId);
        LocalDate fechaAnterior = t.fecha;
        LocalTime horaAnterior = t.horaInicio;
        LocalDate nuevaFecha = Fechas.leer(fechaTexto);
        LocalTime nuevaHora = LocalTime.parse(horaTexto);
        Profesionales.Agenda agenda = profesionales.porId(t.medicoId).orElse(null);
        int duracion = agenda != null && agenda.duracionTurnoMinutos() != null ? agenda.duracionTurnoMinutos() : 45;
        t.fecha = nuevaFecha;
        t.horaInicio = nuevaHora;
        t.horaFin = nuevaHora.plusMinutes(duracion);
        // La fecha nueva invalida el recordatorio y la confirmación de la fecha vieja: se vuelve a mandar.
        t.confirmacionAsistenciaEmailEnviado = false;
        t.asistenciaConfirmadaPaciente = false;
        t.tokenConfirmacionAsistencia = null;
        turnos.guardar(t);
        publicador.reprogramado(t, fechaAnterior, horaAnterior, "ONLINE".equals(t.modalidad) ? null : direccion(t));
        publicador.actualizado(t);
    }

    /** El botón "Confirmar asistencia" del recordatorio (sin sesión, con el token del mail). */
    @Transactional
    public void confirmarAsistencia(Long turnoId, String token) {
        Turno t = buscar(turnoId);
        validarToken(t, token);
        if (!Turno.CONFIRMADO.equals(t.estado)) {
            throw new IllegalStateException("Este turno ya no está disponible para confirmar.");
        }
        t.asistenciaConfirmadaPaciente = true;
        turnos.guardar(t);
        publicador.asistenciaConfirmada(t);
        publicador.actualizado(t);
    }

    /** El botón "No podré asistir": una cancelación del paciente, con la misma política de reembolso. */
    @Transactional
    public void noAsistira(Long turnoId, String token) {
        Turno t = buscar(turnoId);
        validarToken(t, token);
        if (Turno.CANCELADO.equals(t.estado)) {
            return;
        }
        cancelar(turnoId, personas.porId(t.pacienteId).map(Personas.Persona::email).orElse(null));
    }

    /** Los enlaces del recordatorio, para notificaciones (el token no viaja en el evento). */
    public java.util.Optional<String[]> enlacesAsistencia(Long turnoId) {
        return turnos.porId(turnoId)
                .filter(t -> t.tokenConfirmacionAsistencia != null)
                .map(t -> new String[]{
                        publicUrl + "/api/turnos/" + t.id + "/confirmar-asistencia?token=" + t.tokenConfirmacionAsistencia,
                        publicUrl + "/api/turnos/" + t.id + "/no-asistira?token=" + t.tokenConfirmacionAsistencia});
    }

    String direccion(Turno t) {
        return profesionales.porId(t.medicoId).map(Profesionales.Agenda::domicilioAtencion).orElse(null);
    }

    private static void validarToken(Turno t, String token) {
        if (token == null || token.isBlank() || t.tokenConfirmacionAsistencia == null || !t.tokenConfirmacionAsistencia.equals(token)) {
            throw new IllegalStateException("El enlace no es válido o ya venció.");
        }
    }

    /** El tamaño del archivo de un data: URI sin decodificarlo. */
    static long bytesDecodificados(String dataUri) {
        int coma = dataUri.indexOf(',');
        String b64 = coma >= 0 ? dataUri.substring(coma + 1) : dataUri;
        int relleno = b64.endsWith("==") ? 2 : b64.endsWith("=") ? 1 : 0;
        return (long) (b64.length() / 4.0 * 3) - relleno;
    }
}
