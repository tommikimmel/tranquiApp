package com.tranqui.app.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ModelEntitiesUnitTest {

    @Test
    void testUsuarioHelperMethodsAndLifecycle() {
        Usuario u = new Usuario();
        u.setEmail("  Test@Example.COM  ");
        u.onCreate();
        assertEquals("test@example.com", u.getEmail());
        assertNotNull(u.getFechaRegistro());

        u.setEmail("  Updated@Example.COM  ");
        u.onUpdate();
        assertEquals("updated@example.com", u.getEmail());

        // CanPrescribe checks
        u.setProfession("psiquiatra");
        u.setLicenseVerifiedAt(LocalDateTime.now());
        assertTrue(u.isCanPrescribe());

        u.setProfession("medico");
        u.setLicenseVerifiedAt(null);
        u.setVerificadoAdmin(true);
        assertTrue(u.isCanPrescribe());

        u.setProfession("psicologo");
        u.setRol(Rol.PSIQUIATRA);
        assertTrue(u.isCanPrescribe());

        u.setProfession("psicologo");
        u.setRol(Rol.PACIENTE);
        assertFalse(u.isCanPrescribe());

        // Effective Tax ID
        Usuario uTax = new Usuario();
        assertNull(uTax.getEffectiveTaxId());

        uTax.setNumeroDocumento(12345678);
        assertEquals("12345678", uTax.getEffectiveTaxId());

        uTax.setDni("87654321");
        assertEquals("87654321", uTax.getEffectiveTaxId());

        uTax.setCuil(20876543210L);
        assertEquals("20876543210", uTax.getEffectiveTaxId());

        uTax.setCuit("20-87654321-0");
        assertEquals("20-87654321-0", uTax.getEffectiveTaxId());

        uTax.setTaxId("TAX-999");
        assertEquals("TAX-999", uTax.getEffectiveTaxId());

        // Effective Legal Name
        Usuario uName = new Usuario();
        uName.setEmail("fallback@email.com");
        assertEquals("fallback@email.com", uName.getEffectiveLegalName());

        uName.setNombre("Juan");
        uName.setApellido("Perez");
        assertEquals("Juan Perez", uName.getEffectiveLegalName());

        uName.setLegalName("Clinica S.A.");
        assertEquals("Clinica S.A.", uName.getEffectiveLegalName());

        // Effective IVA condition
        Usuario uIva = new Usuario();
        assertEquals(6, uIva.getEffectiveIvaConditionId());
        uIva.setIvaConditionId(1);
        assertEquals(1, uIva.getEffectiveIvaConditionId());
    }

    @Test
    void testSubscriptionLifecycle() {
        Subscription sub = new Subscription();
        sub.setCreatedAt(null);
        sub.setUpdatedAt(null);
        sub.onCreate();
        assertNotNull(sub.getCreatedAt());
        assertNotNull(sub.getUpdatedAt());

        sub.onUpdate();
        assertNotNull(sub.getUpdatedAt());
    }

    @Test
    void testMensajeLifecycle() {
        Mensaje m = new Mensaje();
        m.setFechaEnvio(null);
        m.setLeido(null);
        m.onCreate();
        assertNotNull(m.getFechaEnvio());
        assertFalse(m.getLeido());
    }

    @Test
    void testTurnoLifecycle() {
        Turno t = new Turno();
        t.setFechaCreacion(null);
        t.onCreate();
        assertNotNull(t.getFechaCreacion());
    }

    @Test
    void testOtherEntitiesAndDtos() {
        // InformeClinico
        InformeClinico inf = new InformeClinico();
        inf.setId(1L);
        inf.setTipoInforme("Evaluativo");
        inf.setContenido("Observaciones clinicas");
        inf.setPlanTrabajo("Plan de trabajo");
        inf.onCreate();
        assertNotNull(inf.getFechaCreacion());
        assertEquals("Evaluativo", inf.getTipoInforme());
        assertEquals("Observaciones clinicas", inf.getContenido());

        // Ticket & TicketMensaje
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setAsunto("Ayuda");
        ticket.setEstado(TicketEstado.RESUELTO);
        ticket.onCreate();
        assertNotNull(ticket.getFechaCreacion());
        assertNotNull(ticket.getFechaActualizacion());
        assertEquals(TicketEstado.RESUELTO, ticket.getEstado());

        TicketMensaje tm = new TicketMensaje();
        tm.setId(20L);
        tm.setContenido("Detalle del problema");
        tm.onCreate();
        assertNotNull(tm.getFechaEnvio());
        assertEquals("Detalle del problema", tm.getContenido());

        // Invoice & InvoiceSequence
        Invoice inv = new Invoice();
        inv.setId(30L);
        inv.setPaymentId(40L);
        inv.setCbteTipo(11);
        inv.setPuntoVenta(1);
        inv.setCbteNumero(101L);
        inv.setCae("74123456789012");
        inv.setImporteTotal(new BigDecimal("15000.00"));
        inv.onCreate();
        assertNotNull(inv.getCreatedAt());

        InvoiceSequence seq = new InvoiceSequence();
        seq.setPuntoVenta(1);
        seq.setCbteTipo(11);
        seq.setLastNumber(50L);
        seq.onUpdate();
        assertNotNull(seq.getUpdatedAt());

        // SubscriptionPayment & SubscriptionEvent
        SubscriptionPayment sp = new SubscriptionPayment();
        sp.setId(60L);
        sp.setSubscriptionId(70L);
        sp.setAmountArs(new BigDecimal("20000.00"));
        sp.onCreate();
        assertNotNull(sp.getCreatedAt());

        SubscriptionEvent se = new SubscriptionEvent();
        se.setId(80L);
        se.setSubscriptionId(70L);
        se.setEventType("STATUS_CHANGE");
        se.onCreate();
        assertNotNull(se.getCreatedAt());

        // SeguimientoDiario
        SeguimientoDiario seg = new SeguimientoDiario();
        seg.setId(90L);
        seg.setFecha(LocalDate.now());
        seg.setEstadoAnimo("Bueno");
        seg.setSintomas("Ansiedad leve");
        seg.setNotas("Buen dia");
        seg.onCreate();
        assertNotNull(seg.getFechaCreacion());
        assertEquals("Bueno", seg.getEstadoAnimo());
        assertEquals("Buen dia", seg.getNotas());

        // DTOs
        com.tranqui.app.model.dto.UserResponseDto uDto = new com.tranqui.app.model.dto.UserResponseDto();
        uDto.setId(1L);
        uDto.setNombre("Carlos");
        uDto.setEmail("carlos@tranqui.com");
        uDto.setRol(Rol.PSIQUIATRA);
        uDto.setCanPrescribe(true);
        assertEquals("Carlos", uDto.getNombre());
        assertTrue(uDto.getCanPrescribe());

        com.tranqui.app.model.dto.CrearTicketDto ctDto = new com.tranqui.app.model.dto.CrearTicketDto();
        ctDto.setAsunto("Consulta");
        ctDto.setMensaje("Mensaje inicial");
        assertEquals("Consulta", ctDto.getAsunto());

        com.tranqui.app.model.dto.NuevoMensajeTicketDto nmDto = new com.tranqui.app.model.dto.NuevoMensajeTicketDto();
        nmDto.setContenido("Respuesta");
        assertEquals("Respuesta", nmDto.getContenido());
    }
}
