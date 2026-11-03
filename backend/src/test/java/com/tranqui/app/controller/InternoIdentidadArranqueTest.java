package com.tranqui.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las rutas de la carga de identidad no responden hasta que el monolito terminó de sembrar sus datos. */
class InternoIdentidadArranqueTest {

    @Test
    void antesDelArranqueCompleto_503_yDespuesAtiende() {
        InternoIdentidadController controller = new InternoIdentidadController();

        assertThatThrownBy(controller::exigirArranqueCompleto)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        controller.alArrancar();

        assertThatCode(controller::exigirArranqueCompleto).doesNotThrowAnyException();
    }
}
