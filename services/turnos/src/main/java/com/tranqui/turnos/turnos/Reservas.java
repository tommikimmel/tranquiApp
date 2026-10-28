package com.tranqui.turnos.turnos;

import com.tranqui.eventos.data.TarifasProfesionalData.Tarifa;
import com.tranqui.turnos.agenda.Horarios;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import com.tranqui.turnos.web.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La reserva: {@code TurnoService#reservarTurno} del monolito con la misma lógica (tarifa, precio por
 * modalidad, horario libre, sobreturno, documentos que no ocupan agenda). El paciente lo busca o crea el
 * monolito (identidad, decisión 1) y el link de pago lo arma el servicio de pagos con {@code turno.reservado}.
 */
@Service
public class Reservas {

    private static final Set<String> TIPOS = Set.of("PARTICULAR", "OSDE", "OBRA_SOCIAL", "RECETA", "CERTIFICADO", "SOBRETUNO");
    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "ONLINE");
    private static final Set<String> SERVICIOS_CONOCIDOS = Set.of("particular", "sobreturno", "obra_social", "osde", "receta-fuera",
            "certificado");

    private final Turnos turnos;
    private final Personas personas;
    private final Profesionales profesionales;
    private final Horarios horarios;
    private final ClienteInterno interno;
    private final Publicador publicador;
    private final TransactionTemplate tx;
    private final boolean mercadoPagoHabilitado;
    private final boolean pagoSimulado;

    public Reservas(Turnos turnos, Personas personas, Profesionales profesionales, Horarios horarios, ClienteInterno interno,
                    Publicador publicador, TransactionTemplate tx, @Value("${mercadopago.enabled:false}") boolean mercadoPagoHabilitado,
                    @Value("${payment.simulation.enabled:false}") boolean pagoSimulado) {
        this.turnos = turnos;
        this.personas = personas;
        this.profesionales = profesionales;
        this.horarios = horarios;
        this.interno = interno;
        this.publicador = publicador;
        this.tx = tx;
        this.mercadoPagoHabilitado = mercadoPagoHabilitado;
        this.pagoSimulado = pagoSimulado;
    }

    public Dtos.TurnoResponseDto reservar(Dtos.ReservaTurnoDto dto) {
        if (dto.tipo() != null && !TIPOS.contains(dto.tipo())) {
            throw new IllegalArgumentException("Tipo de turno inválido: " + dto.tipo());
        }
        if (dto.modalidad() != null && !MODALIDADES.contains(dto.modalidad())) {
            throw new IllegalArgumentException("Modalidad inválida: " + dto.modalidad());
        }
        String emailPaciente = dto.emailPaciente() != null ? dto.emailPaciente().trim().toLowerCase() : null;
        if (emailPaciente != null && turnos.tieneTurnoActivo(emailPaciente, LocalDate.now())) {
            throw new IllegalStateException("Ya tenés un turno activo o pendiente de pago. No podés reservar más de un turno a la vez.");
        }
        Long medicoId = dto.medicoId();
        Profesionales.Agenda agenda = medicoId != null ? profesionales.porId(medicoId).orElse(null) : null;
        Personas.Persona medico = medicoId != null ? personas.porId(medicoId).orElse(null) : null;
        if (agenda == null || medico == null) {
            throw new NoEncontradoException("Médico no encontrado");
        }

        // La tarifa se resuelve primero: si el servicio ocupa agenda o no decide el resto.
        String tipo = dto.tipo();
        boolean esObraSocial = "OBRA_SOCIAL".equals(tipo) || "OSDE".equals(tipo);
        String servicioId = "particular";
        boolean esServicioCustom = dto.servicioId() != null && !dto.servicioId().isBlank() && !SERVICIOS_CONOCIDOS.contains(dto.servicioId());
        Optional<Tarifa> tarifaCustom = esServicioCustom ? profesionales.tarifa(medicoId, dto.servicioId()) : Optional.empty();
        boolean usaTarifaCustom = tarifaCustom.isPresent() && tarifaCustom.get().habilitado();
        if (usaTarifaCustom) {
            servicioId = dto.servicioId();
        } else if (esObraSocial) {
            servicioId = "obra_social";
        } else if ("RECETA".equals(tipo)) {
            servicioId = "receta-fuera";
        } else if ("CERTIFICADO".equals(tipo)) {
            servicioId = "certificado";
        } else if ("SOBRETUNO".equals(tipo)) {
            servicioId = "sobreturno";
        }
        Optional<Tarifa> tarifa = usaTarifaCustom ? Optional.empty() : profesionales.tarifa(medicoId, servicioId);
        if (!usaTarifaCustom && tarifa.isEmpty() && esObraSocial) {
            tarifa = profesionales.tarifa(medicoId, "osde");
        }

        boolean requiereAfiliado;
        boolean requiereAgenda;
        boolean esRecetaServicio;
        if (usaTarifaCustom) {
            requiereAfiliado = tarifaCustom.get().requiereObraSocial();
            requiereAgenda = tarifaCustom.get().requiereAgenda();
            esRecetaServicio = tarifaCustom.get().esReceta();
        } else {
            requiereAfiliado = esObraSocial || (tarifa.isPresent() && tarifa.get().requiereObraSocial());
            requiereAgenda = tarifa.map(Tarifa::requiereAgenda).orElse(true);
            esRecetaServicio = tarifa.isPresent() && tarifa.get().esReceta();
        }

        String modalidad;
        LocalDate fecha;
        LocalTime horaInicio;
        LocalTime horaFin;
        if (!requiereAgenda) {
            // Un documento: sin consultorio ni videollamada, no reserva horario (la fecha y hora son las de la compra).
            modalidad = null;
            fecha = LocalDate.now();
            horaInicio = LocalTime.now();
            horaFin = horaInicio;
        } else {
            modalidad = dto.modalidad();
            if (modalidad == null) {
                if (agenda.ofrecePresencial() && agenda.ofreceOnline()) {
                    throw new IllegalArgumentException("Debés indicar si el turno es presencial u online.");
                }
                modalidad = agenda.ofrecePresencial() ? "PRESENCIAL" : "ONLINE";
            }
            if (!"SOBRETUNO".equals(tipo)) {
                List<LocalTime> libres = horarios.disponibles(medicoId, dto.fecha(), modalidad);
                if (!libres.contains(dto.hora())) {
                    throw new IllegalStateException("El horario seleccionado ya no está disponible");
                }
            } else {
                boolean ocupado = turnos.delDia(medicoId, dto.fecha()).stream()
                        .anyMatch(t -> t.ocupaAgenda && t.horaInicio.equals(dto.hora()));
                if (ocupado) {
                    throw new IllegalStateException("El horario del sobreturno seleccionado ya se encuentra ocupado por otro turno");
                }
            }
            fecha = dto.fecha();
            horaInicio = dto.hora();
            horaFin = dto.hora().plusMinutes(45);
        }

        BigDecimal precio;
        if (usaTarifaCustom) {
            precio = precioPorModalidad(tarifaCustom.get(), modalidad);
        } else if (tarifa.isPresent() && tarifa.get().habilitado()) {
            precio = precioPorModalidad(tarifa.get(), modalidad);
        } else if (esObraSocial) {
            precio = new BigDecimal("10500");
        } else if ("RECETA".equals(tipo)) {
            precio = new BigDecimal("45000");
        } else if ("CERTIFICADO".equals(tipo)) {
            precio = new BigDecimal("55000");
        } else if ("SOBRETUNO".equals(tipo)) {
            precio = new BigDecimal("90000");
        } else {
            precio = agenda.precio() != null ? agenda.precio() : new BigDecimal("60000");
        }
        if (requiereAfiliado && (dto.metadataAfiliado() == null || dto.metadataAfiliado().isBlank())) {
            throw new IllegalStateException("Este servicio requiere número de afiliado.");
        }
        // Fase 4: sin la cuenta vinculada se corta acá, con el mismo 409 de siempre (el monolito lo hacía
        // después de guardar, dentro de la misma transacción: no quedaba nada).
        if (mercadoPagoHabilitado && !pagoSimulado && !medico.mpVinculada()) {
            throw new IllegalStateException("El profesional todavía no vinculó su cuenta de Mercado Pago.");
        }

        String metaAfiliado = dto.metadataAfiliado();
        if (dto.obraSocial() != null && !dto.obraSocial().isBlank()) {
            if (metaAfiliado != null && !metaAfiliado.isBlank() && !metaAfiliado.startsWith(dto.obraSocial())) {
                metaAfiliado = dto.obraSocial() + " - " + metaAfiliado;
            } else if (metaAfiliado == null || metaAfiliado.isBlank()) {
                metaAfiliado = dto.obraSocial();
            }
        }

        // El paciente es de identidad (el monolito): lo busca o lo crea, y guarda la obra social y el afiliado.
        ClienteInterno.PacienteMonolito paciente;
        try {
            paciente = interno.buscarOCrearPaciente(new ClienteInterno.BuscarOCrearPaciente(emailPaciente, dto.nombrePaciente(),
                    dto.telefonoPaciente(), dto.obraSocial(), dto.metadataAfiliado()));
        } catch (RestClientException e) {
            throw new ServicioNoDisponibleException("No se pudo reservar el turno en este momento. Probá de nuevo en unos minutos.", e);
        }

        Turno t = new Turno();
        t.medicoId = medicoId;
        t.pacienteId = paciente.id();
        t.fecha = fecha;
        t.horaInicio = horaInicio;
        t.horaFin = horaFin;
        t.tipo = tipo;
        t.modalidad = modalidad;
        t.estado = Turno.PENDIENTE_PAGO;
        t.tokenReserva = UUID.randomUUID().toString();
        t.precio = precio;
        t.metadataAfiliado = metaAfiliado;
        t.servicioId = servicioId;
        t.idFinanciador = dto.idFinanciador();
        t.ocupaAgenda = requiereAgenda;
        t.esReceta = esRecetaServicio;
        Turno reservado = tx.execute(estado -> {
            personas.guardar(paciente.persona(personas.porId(paciente.id()).map(Personas.Persona::mpVinculada).orElse(false)));
            Turno nuevo = turnos.crear(t);
            publicador.reservado(nuevo, Etiquetas.tituloCobro(medico.nombre()));
            if (!nuevo.ocupaAgenda || nuevo.esDeReceta()) {
                publicador.documentoSolicitado(nuevo, nuevo.servicioId != null ? nuevo.servicioId : "documento",
                        nuevo.tipo != null ? nuevo.tipo : "DOCUMENTO");
            }
            publicador.actualizado(nuevo);
            return nuevo;
        });
        return new Dtos.TurnoResponseDto(reservado.id, reservado.estado, reservado.asistencia, reservado.fecha, reservado.horaInicio,
                reservado.precio, "", Boolean.TRUE, reservado.tokenReserva, null);
    }

    /** Un precio por modalidad solo pisa el precio si el profesional lo cargó para esa modalidad. */
    private static BigDecimal precioPorModalidad(Tarifa tarifa, String modalidad) {
        if ("ONLINE".equals(modalidad) && tarifa.precioOnline() != null) {
            return tarifa.precioOnline();
        }
        if ("PRESENCIAL".equals(modalidad) && tarifa.precioPresencial() != null) {
            return tarifa.precioPresencial();
        }
        return tarifa.precio();
    }
}
