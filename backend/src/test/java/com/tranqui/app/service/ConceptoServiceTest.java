package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.TipoConcepto;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.SolicitarConceptoDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptoServiceTest {

    @Mock
    private SolicitudDocumentoRepository solicitudRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MercadoPagoService mercadoPagoService;

    @InjectMocks
    private ConceptoService conceptoService;

    @Test
    void whenCreateRequest_shouldSaveAndReturn() {
        SolicitarConceptoDto dto = SolicitarConceptoDto.builder()
                .medicoId(2L)
                .tipoConcepto(TipoConcepto.RECETA_CONTROL)
                .precio(BigDecimal.valueOf(1500))
                .build();

        Usuario paciente = Usuario.builder().id(1L).email("pac@test.com").nombre("Paciente").rol(Rol.PACIENTE).build();
        Usuario medico = Usuario.builder().id(2L).email("med@test.com").nombre("Doctor").rol(Rol.PSIQUIATRA).build();

        when(usuarioRepository.findByEmail("pac@test.com")).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico));
        when(solicitudRepository.save(any(SolicitudDocumento.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitudDocumento result = conceptoService.crearSolicitud(dto, "pac@test.com");

        assertNotNull(result);
        assertEquals(paciente, result.getPaciente());
        assertEquals(medico, result.getMedico());
        assertEquals(TipoConcepto.RECETA_CONTROL, result.getTipoConcepto());
        assertEquals(BigDecimal.valueOf(1500), result.getPrecio());
        assertEquals(EstadoPago.PENDIENTE, result.getEstado());
        assertFalse(result.isEmitido());
    }

    @Test
    void whenCreateRequestWithInvalidUser_shouldThrowException() {
        SolicitarConceptoDto dto = SolicitarConceptoDto.builder().medicoId(2L).build();
        when(usuarioRepository.findByEmail("invalid@test.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> conceptoService.crearSolicitud(dto, "invalid@test.com"));
    }

    @Test
    void whenEmitDocumentWithWrongDoctor_shouldThrowSecurityException() {
        Usuario medico = Usuario.builder().id(2L).email("med@test.com").nombre("Doctor").rol(Rol.PSIQUIATRA).build();
        SolicitudDocumento doc = SolicitudDocumento.builder()
                .id(10L)
                .medico(medico)
                .estado(EstadoPago.APROBADO)
                .build();

        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(doc));

        assertThrows(SecurityException.class, () -> conceptoService.emitirDocumento(10L, "http://link.com", "hacker@test.com"));
    }

    @Test
    void whenEmitUnpaidDocument_shouldThrowIllegalStateException() {
        Usuario medico = Usuario.builder().id(2L).email("med@test.com").nombre("Doctor").rol(Rol.PSIQUIATRA).build();
        SolicitudDocumento doc = SolicitudDocumento.builder()
                .id(10L)
                .medico(medico)
                .estado(EstadoPago.PENDIENTE) // unpaid
                .build();

        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(doc));

        assertThrows(IllegalStateException.class, () -> conceptoService.emitirDocumento(10L, "http://link.com", "med@test.com"));
    }

    @Test
    void whenEmitValidDocument_shouldUpdateAndReturn() {
        Usuario paciente = Usuario.builder().id(1L).email("pac@test.com").nombre("Paciente").rol(Rol.PACIENTE).build();
        Usuario medico = Usuario.builder().id(2L).email("med@test.com").nombre("Doctor").rol(Rol.PSIQUIATRA).build();
        SolicitudDocumento doc = SolicitudDocumento.builder()
                .id(10L)
                .paciente(paciente)
                .medico(medico)
                .estado(EstadoPago.APROBADO)
                .tipoConcepto(TipoConcepto.RECETA_CONTROL)
                .build();

        when(solicitudRepository.findById(10L)).thenReturn(Optional.of(doc));
        when(solicitudRepository.save(any(SolicitudDocumento.class))).thenAnswer(inv -> inv.getArgument(0));

        SolicitudDocumento result = conceptoService.emitirDocumento(10L, "http://link.com/receta.pdf", "med@test.com");

        assertNotNull(result);
        assertTrue(result.isEmitido());
        assertEquals("http://link.com/receta.pdf", result.getUrlDescarga());
        assertNotNull(result.getFechaEmision());
    }
}
