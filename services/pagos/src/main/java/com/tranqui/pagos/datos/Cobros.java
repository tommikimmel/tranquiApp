package com.tranqui.pagos.datos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Un cobro por {@code external_reference} de Mercado Pago: el id del turno, o {@code doc-<id>} para un
 * documento de la API de conceptos. Nace con la reserva (o el pedido del documento) y guarda el link del
 * checkout, que el frontend pide a {@code GET /api/payments/checkout/{referencia}}.
 */
@Repository
public class Cobros {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String LISTO = "LISTO";
    public static final String ERROR = "ERROR";
    public static final String PAGADO = "PAGADO";
    public static final String VENCIDO = "VENCIDO";

    public record Cobro(String referencia, Long turnoId, Long solicitudId, Long profesionalId, Long pacienteId,
                        BigDecimal monto, String titulo, String tipoConcepto, String checkoutUrl, String estado,
                        String error) {

        public boolean esDocumento() {
            return referencia.startsWith("doc-");
        }
    }

    private static final RowMapper<Cobro> FILA = (rs, i) -> new Cobro(rs.getString("referencia"),
            rs.getObject("turno_id", Long.class), rs.getObject("solicitud_id", Long.class),
            rs.getLong("profesional_id"), rs.getObject("paciente_id", Long.class), rs.getBigDecimal("monto"),
            rs.getString("titulo"), rs.getString("tipo_concepto"), rs.getString("checkout_url"), rs.getString("estado"),
            rs.getString("error"));

    private final JdbcTemplate jdbc;

    public Cobros(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String referenciaTurno(Long turnoId) {
        return String.valueOf(turnoId);
    }

    public static String referenciaDocumento(Long solicitudId) {
        return "doc-" + solicitudId;
    }

    public Optional<Cobro> buscar(String referencia) {
        return jdbc.query("SELECT * FROM cobro WHERE referencia = ?", FILA, referencia).stream().findFirst();
    }

    /** Alta del cobro; si ya existe (evento repetido o carga inicial) no lo toca. */
    public boolean crear(Cobro c) {
        return jdbc.update("INSERT INTO cobro (referencia, turno_id, solicitud_id, profesional_id, paciente_id, monto, titulo, "
                        + "tipo_concepto, checkout_url, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (referencia) DO NOTHING",
                c.referencia(), c.turnoId(), c.solicitudId(), c.profesionalId(), c.pacienteId(), c.monto(), c.titulo(),
                c.tipoConcepto(), c.checkoutUrl(), c.estado()) > 0;
    }

    public void listo(String referencia, String checkoutUrl) {
        jdbc.update("UPDATE cobro SET checkout_url = ?, estado = ?, error = NULL, actualizado_en = now() WHERE referencia = ?",
                checkoutUrl, LISTO, referencia);
    }

    public void error(String referencia, String error) {
        jdbc.update("UPDATE cobro SET estado = ?, error = ?, actualizado_en = now() WHERE referencia = ?", ERROR, error, referencia);
    }

    /** Pagado o vencido; uno pagado no vuelve atrás. */
    public void cambiarEstado(String referencia, String estado) {
        jdbc.update("UPDATE cobro SET estado = ?, actualizado_en = now() WHERE referencia = ? AND estado <> ?",
                estado, referencia, PAGADO);
    }
}
