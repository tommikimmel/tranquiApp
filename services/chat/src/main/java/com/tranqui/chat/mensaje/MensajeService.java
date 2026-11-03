package com.tranqui.chat.mensaje;

import com.tranqui.chat.copias.Copias;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.MensajeEnviadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lógica del chat, igual que el {@code MensajeService} del monolito. */
@Service
public class MensajeService {

    private final MensajeRepository mensajes;
    private final Copias copias;
    private final OutboxPublisher outbox;

    public MensajeService(MensajeRepository mensajes, Copias copias, OutboxPublisher outbox) {
        this.mensajes = mensajes;
        this.copias = copias;
        this.outbox = outbox;
    }

    /**
     * Guarda el mensaje y publica {@code chat.mensaje.enviado.v1} en la misma transacción (sin el
     * contenido: sección 5.3). El destinatario tiene que existir en la copia de participantes.
     */
    @Transactional
    public Mensaje guardar(Long remitenteId, Long destinatarioId, String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        if (copias.participante(destinatarioId).isEmpty()) {
            throw new IllegalArgumentException("Destinatario no encontrado");
        }
        Mensaje mensaje = mensajes.saveAndFlush(new Mensaje(remitenteId, destinatarioId, contenido));
        long menor = Math.min(remitenteId, destinatarioId);
        long mayor = Math.max(remitenteId, destinatarioId);
        outbox.publicar(Topicos.CHAT_MENSAJE_ENVIADO_V1, "mensaje", String.valueOf(mensaje.getId()),
                mensaje.getAggregateVersion(),
                new MensajeEnviadoData(mensaje.getId(), menor * 31 + mayor, remitenteId, destinatarioId, OffsetDateTime.now()));
        return mensaje;
    }

    /** Historial de una conversación (más nuevos primero) y marca como leídos los del otro. */
    @Transactional
    public Page<Mensaje> historial(Long usuarioId, Long otroId, Pageable pagina) {
        mensajes.marcarLeidos(otroId, usuarioId);
        return mensajes.historial(usuarioId, otroId, pagina);
    }

    @Transactional(readOnly = true)
    public boolean tieneNoLeidos(Long usuarioId) {
        return mensajes.tieneNoLeidos(usuarioId);
    }

    /** Pacientes de un profesional, primero los que tienen turno confirmado en los próximos 3 días. */
    @Transactional(readOnly = true)
    public List<MensajeRepository.Canal> canalesDelProfesional(Long profesionalId) {
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        return mensajes.canalesPrioritarios(profesionalId, hoy, ahora, hoy.plusDays(3), ahora);
    }

    @Transactional(readOnly = true)
    public List<MensajeRepository.Canal> canalesConRol(Long usuarioId, String rol) {
        return mensajes.canalesConRol(usuarioId, rol);
    }

    /** Resumen para la lista de pacientes del profesional (ruta interna para el monolito). */
    @Transactional(readOnly = true)
    public Resumen resumen(Long profesionalId) {
        Map<Long, Integer> noLeidos = mensajes.noLeidosPorRemitente(profesionalId).stream()
                .collect(Collectors.toMap(MensajeRepository.NoLeidosPorRemitente::getRemitenteId,
                        MensajeRepository.NoLeidosPorRemitente::getCantidad));
        return new Resumen(mensajes.pacientesConChat(profesionalId), noLeidos);
    }

    /** Mismo formato que {@code ResumenChat} del monolito. */
    public record Resumen(List<Long> pacientesConChat, Map<Long, Integer> noLeidosPorPaciente) {}
}
