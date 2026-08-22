package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.TipoConcepto;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.SolicitarConceptoDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class ConceptoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    private Usuario paciente;
    private Usuario medico;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .nombre("Paciente")
                .email("pac@test.com")
                .rol(Rol.PACIENTE)
                .build();
        medico = Usuario.builder()
                .nombre("Medico")
                .email("med@test.com")
                .rol(Rol.PSIQUIATRA)
                .build();

        usuarioRepository.save(paciente);
        usuarioRepository.save(medico);

        when(subscriptionService.isAccessAllowed(anyLong())).thenReturn(true);
    }



    @Test
    @WithMockUser(username = "pac@test.com", roles = "PACIENTE")
    void whenSolicitarDocumento_shouldReturnCheckoutUrl() throws Exception {
        SolicitarConceptoDto dto = SolicitarConceptoDto.builder()
                .medicoId(medico.getId())
                .tipoConcepto(TipoConcepto.RECETA_CONTROL)
                .precio(BigDecimal.valueOf(2000.00))
                .build();

        mockMvc.perform(post("/api/conceptos/solicitar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.checkoutUrl").exists());
    }

    @Test
    @WithMockUser(username = "med@test.com", roles = "PSIQUIATRA")
    void whenGetPendientes_shouldReturnApprovedList() throws Exception {
        SolicitudDocumento doc = SolicitudDocumento.builder()
                .paciente(paciente)
                .medico(medico)
                .tipoConcepto(TipoConcepto.CERTIFICADO)
                .precio(BigDecimal.valueOf(1500.00))
                .estado(EstadoPago.APROBADO)
                .emitido(false)
                .build();
        solicitudRepository.save(doc);

        mockMvc.perform(get("/api/conceptos/pendientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doc.getId()))
                .andExpect(jsonPath("$[0].tipoConcepto").value("CERTIFICADO"))
                .andExpect(jsonPath("$[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$[0].emitido").value(false));
    }

    @Test
    @WithMockUser(username = "med@test.com", roles = "PSIQUIATRA")
    void whenEmitirDocumento_shouldUpdateStatusAndReturn() throws Exception {
        SolicitudDocumento doc = SolicitudDocumento.builder()
                .paciente(paciente)
                .medico(medico)
                .tipoConcepto(TipoConcepto.CERTIFICADO)
                .precio(BigDecimal.valueOf(1500.00))
                .estado(EstadoPago.APROBADO)
                .emitido(false)
                .build();
        solicitudRepository.save(doc);

        mockMvc.perform(post("/api/conceptos/" + doc.getId() + "/emitir")
                .param("urlDescarga", "http://docs.com/signed.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doc.getId()))
                .andExpect(jsonPath("$.emitido").value(true))
                .andExpect(jsonPath("$.urlDescarga").value("http://docs.com/signed.pdf"));
    }
}
