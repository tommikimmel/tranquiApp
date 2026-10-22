package com.tranqui.app.eventos;

import com.tranqui.eventos.consumo.InboxHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InboxHelperTest {

    @Autowired
    private InboxHelper inboxHelper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM eventos_procesados");
    }

    @Test
    void procesar_primerIntento_ejecutaAccionYRetornaTrue() {
        UUID eventId = UUID.randomUUID();
        String consumidor = "mail-turno-confirmado";
        AtomicBoolean ejecutado = new AtomicBoolean(false);

        boolean procesado = inboxHelper.procesar(eventId, consumidor, () -> ejecutado.set(true));

        assertThat(procesado).isTrue();
        assertThat(ejecutado.get()).isTrue();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void procesar_duplicado_noEjecutaAccionYRetornaFalse() {
        UUID eventId = UUID.randomUUID();
        String consumidor = "mail-turno-confirmado";
        AtomicInteger ejecuciones = new AtomicInteger(0);

        boolean primeraVez = inboxHelper.procesar(eventId, consumidor, () -> {
            ejecuciones.incrementAndGet();
        });
        assertThat(primeraVez).isTrue();
        assertThat(ejecuciones.get()).isEqualTo(1);

        // Segundo intento con el mismo evento y consumidor (duplicado)
        boolean segundaVez = inboxHelper.procesar(eventId, consumidor, () -> {
            ejecuciones.incrementAndGet();
        });
        assertThat(segundaVez).isFalse();
        assertThat(ejecuciones.get()).isEqualTo(1); // No volvió a ejecutarse

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void procesar_diferenteConsumidorParaMismoEvento_ejecutaAmbos() {
        UUID eventId = UUID.randomUUID();
        AtomicBoolean c1 = new AtomicBoolean(false);
        AtomicBoolean c2 = new AtomicBoolean(false);

        boolean r1 = inboxHelper.procesar(eventId, "consumidor-notificaciones", () -> c1.set(true));
        boolean r2 = inboxHelper.procesar(eventId, "consumidor-calendario", () -> c2.set(true));

        assertThat(r1).isTrue();
        assertThat(r2).isTrue();
        assertThat(c1.get()).isTrue();
        assertThat(c2.get()).isTrue();

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ?",
                Integer.class, eventId
        );
        assertThat(total).isEqualTo(2);
    }

    @Test
    void procesar_siAccionFalla_haceRollbackYPermiteReintento() {
        UUID eventId = UUID.randomUUID();
        String consumidor = "consumidor-fallido";

        assertThatThrownBy(() -> inboxHelper.procesar(eventId, consumidor, () -> {
            throw new RuntimeException("Fallo de red simulado");
        })).isInstanceOf(RuntimeException.class).hasMessage("Fallo de red simulado");

        // Al haber hecho rollback, la fila no debe quedar guardada
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_procesados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor
        );
        assertThat(count).isZero();

        // Ahora reintenta y debe procesar exitosamente
        AtomicBoolean reintentoOk = new AtomicBoolean(false);
        boolean resultado = inboxHelper.procesar(eventId, consumidor, () -> reintentoOk.set(true));
        assertThat(resultado).isTrue();
        assertThat(reintentoOk.get()).isTrue();
    }
}
