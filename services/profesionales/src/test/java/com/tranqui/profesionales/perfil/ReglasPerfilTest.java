package com.tranqui.profesionales.perfil;

import com.tranqui.profesionales.ProfesionalesIntegracionTestDatos;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Las reglas de {@code SubscriptionService#isAccessAllowed} e {@code ImageUtils#decodedByteSize}, sin Spring. */
class ReglasPerfilTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 22, 12, 0);

    private static Profesional con(String rol, String estado, LocalDateTime hasta) {
        return ProfesionalesIntegracionTestDatos.profesional(rol, estado, hasta);
    }

    @Test
    void acceso_activaOCanceladaConPeriodoVigente() {
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", "ACTIVE", AHORA.plusSeconds(1)), AHORA)).isTrue();
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", "CANCELLED", AHORA.plusDays(3)), AHORA)).isTrue();
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", "ACTIVE", AHORA), AHORA)).isFalse();
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", "ACTIVE", null), AHORA)).isFalse();
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", "PAST_DUE", AHORA.plusDays(3)), AHORA)).isFalse();
        assertThat(Perfiles.tieneAcceso(con("PSIQUIATRA", null, null), AHORA)).isFalse();
        // El paywall solo aplica a profesionales.
        assertThat(Perfiles.tieneAcceso(con("PACIENTE", null, null), AHORA)).isTrue();
    }

    @Test
    void tamanioDeLaFoto_sinDecodificarElBase64() {
        assertThat(Perfiles.bytesDecodificados("data:image/png;base64,AAAA")).isEqualTo(3);
        assertThat(Perfiles.bytesDecodificados("data:image/png;base64,AAA=")).isEqualTo(2);
        assertThat(Perfiles.bytesDecodificados("no-es-data-uri")).isEqualTo(14);
    }
}
