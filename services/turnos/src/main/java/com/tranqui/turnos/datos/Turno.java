package com.tranqui.turnos.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una fila de {@code turno}: los mismos campos que la entidad {@code Turno} del monolito, más el pago del
 * turno (lo registra el servicio de pagos). Se modifica en memoria y se guarda con {@link Turnos#guardar},
 * que sube la versión.
 */
public class Turno {

    public static final String PENDIENTE_PAGO = "PENDIENTE_PAGO";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String EXPIRADO = "EXPIRADO";
    public static final String PENDIENTE_VALIDACION = "PENDIENTE_VALIDACION";

    public static final String PAGO_APROBADO = "APROBADO";
    public static final String PAGO_RECHAZADO = "RECHAZADO";
    public static final String PAGO_REEMBOLSADO = "REEMBOLSADO";

    public Long id;
    public int version;
    public Long pacienteId;
    public Long medicoId;
    public LocalDate fecha;
    public LocalTime horaInicio;
    public LocalTime horaFin;
    public String tipo;
    public String modalidad;
    public String estado;
    public String asistencia = "ESPERANDO";
    public BigDecimal precio = BigDecimal.ZERO;
    public String metadataAfiliado;
    public String servicioId;
    public boolean ocupaAgenda = true;
    public String idFinanciador;
    public String telemedicinaUrl;
    public String googleEventId;
    public String checkoutUrl;
    public boolean documentoEnviado;
    public boolean esReceta;
    public LocalDateTime fechaCreacion = LocalDateTime.now();
    public boolean confirmacionAsistenciaEmailEnviado;
    public boolean asistenciaConfirmadaPaciente;
    public String tokenConfirmacionAsistencia;
    public String tokenReserva;
    public String pagoTransactionId;
    public String pagoEstado;
    public BigDecimal pagoMonto;
    public LocalDateTime pagoFecha;

    public boolean tienePagoAprobado() {
        return PAGO_APROBADO.equals(pagoEstado);
    }

    /** {@code TurnoService#esReceta}: el flag del servicio, o los ids y tipos viejos de receta. */
    public boolean esDeReceta() {
        return esReceta || "receta-fuera".equals(servicioId) || "RECETA".equals(tipo);
    }
}
