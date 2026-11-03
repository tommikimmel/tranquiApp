package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fase 5: la copia del monolito de lo que administra el servicio de profesionales (el perfil, la
 * disponibilidad y las tarifas). Turnos, recetas, facturación y los mails siguen leyendo de las tablas de
 * siempre ({@code usuario}, {@code disponibilidad}, {@code tarifa_medico}); esto las mantiene al día con
 * los eventos del servicio y con la resincronización periódica. Aplicar la copia no publica nada.
 */
@Service
public class CopiaProfesionales {

    private static final Logger log = LoggerFactory.getLogger(CopiaProfesionales.class);

    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadRepository disponibilidadRepository;
    private final TarifaMedicoRepository tarifaRepository;

    public CopiaProfesionales(UsuarioRepository usuarioRepository, DisponibilidadRepository disponibilidadRepository,
                              TarifaMedicoRepository tarifaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.tarifaRepository = tarifaRepository;
    }

    /**
     * Copia el perfil. Además deja la versión del usuario por encima de la del evento: el servicio sigue la
     * numeración de {@code profesional.actualizado} que usaba el monolito, y en una vuelta atrás los eventos
     * que vuelva a publicar el monolito no pueden quedar por debajo (los consumidores los descartarían).
     */
    @Transactional
    public void aplicarPerfil(Long profesionalId, int version, PerfilProfesionalData perfil) {
        Usuario u = usuarioRepository.findById(profesionalId).orElse(null);
        if (u == null) {
            log.warn("Perfil de un profesional que no existe en el monolito: {}", profesionalId);
            return;
        }
        copiarPerfil(perfil, u);
        int actual = u.getVersion() != null ? u.getVersion() : 0;
        u.setVersion(Math.max(actual, version));
        usuarioRepository.save(u);
    }

    /** Reemplaza la disponibilidad entera del profesional (las dos modalidades y las franjas viejas). */
    @Transactional
    public void aplicarDisponibilidad(DisponibilidadProfesionalData data) {
        Usuario u = usuarioRepository.findById(data.profesionalId()).orElse(null);
        if (u == null) {
            log.warn("Disponibilidad de un profesional que no existe en el monolito: {}", data.profesionalId());
            return;
        }
        disponibilidadRepository.deleteAll(disponibilidadRepository.findByMedicoId(u.getId()));
        disponibilidadRepository.flush();
        List<Disponibilidad> nuevas = data.franjas().stream()
                .map(f -> Disponibilidad.builder()
                        .medico(u)
                        .diaSemana(f.diaSemana())
                        .horaInicio(LocalTime.parse(f.horaInicio()))
                        .horaFin(LocalTime.parse(f.horaFin()))
                        .modalidad(f.modalidad() != null ? Modalidad.valueOf(f.modalidad()) : null)
                        .build())
                .toList();
        disponibilidadRepository.saveAll(nuevas);
    }

    /**
     * Actualiza las tarifas por {@code servicioId} (conserva los ids del monolito) y borra las que el
     * profesional eliminó, igual que {@link MedicoService#actualizarPerfil}.
     */
    @Transactional
    public void aplicarTarifas(TarifasProfesionalData data) {
        Usuario u = usuarioRepository.findById(data.profesionalId()).orElse(null);
        if (u == null) {
            log.warn("Tarifas de un profesional que no existe en el monolito: {}", data.profesionalId());
            return;
        }
        Map<String, TarifaMedico> existentes = tarifaRepository.findByMedicoId(u.getId()).stream()
                .collect(Collectors.toMap(TarifaMedico::getServicioId, Function.identity(), (a, b) -> a));
        Set<String> recibidas = data.tarifas().stream().map(TarifasProfesionalData.Tarifa::servicioId).collect(Collectors.toSet());
        for (TarifasProfesionalData.Tarifa t : data.tarifas()) {
            TarifaMedico tarifa = existentes.get(t.servicioId());
            if (tarifa == null) {
                tarifa = TarifaMedico.builder().medico(u).servicioId(t.servicioId()).build();
            }
            tarifa.setLabel(t.label());
            tarifa.setPrecio(t.precio());
            tarifa.setHabilitado(t.habilitado());
            tarifa.setRequiereObraSocial(t.requiereObraSocial());
            tarifa.setObraSocial(t.obraSocial());
            tarifa.setPrecioOnline(t.precioOnline());
            tarifa.setPrecioPresencial(t.precioPresencial());
            tarifa.setRequiereAgenda(t.requiereAgenda());
            tarifa.setEsReceta(t.esReceta());
            tarifaRepository.save(tarifa);
        }
        List<TarifaMedico> eliminadas = existentes.values().stream()
                .filter(t -> !recibidas.contains(t.getServicioId()))
                .toList();
        if (!eliminadas.isEmpty()) {
            tarifaRepository.deleteAll(eliminadas);
        }
    }

    static void copiarPerfil(PerfilProfesionalData p, Usuario u) {
        u.setEmailContacto(p.emailContacto());
        u.setDomicilioAtencion(p.domicilioAtencion());
        u.setDomicilioLat(p.domicilioLat());
        u.setDomicilioLng(p.domicilioLng());
        u.setDomicilioAtencionTorre(p.domicilioAtencionTorre());
        u.setDomicilioAtencionPiso(p.domicilioAtencionPiso());
        u.setDomicilioAtencionDepto(p.domicilioAtencionDepto());
        u.setDomicilioAtencionBarrio(p.domicilioAtencionBarrio());
        u.setMatricula(p.matricula());
        u.setMatriculaTipo(p.matriculaTipo());
        u.setMatriculaProvincia(p.matriculaProvincia());
        u.setMatriculaNumero(p.matriculaNumero());
        u.setMatriculaEspecialidad(p.matriculaEspecialidad());
        u.setMatriculaAsocTipo(p.matriculaAsocTipo());
        u.setMatriculaAsocProvincia(p.matriculaAsocProvincia());
        u.setMatriculaAsocNumero(p.matriculaAsocNumero());
        u.setTitulo(p.titulo());
        u.setSpecialty(p.especialidad());
        u.setCuit(p.cuit());
        u.setPrecio(p.precio());
        u.setColor(p.color());
        u.setOfreceOnline(p.ofreceOnline());
        u.setOfrecePresencial(p.ofrecePresencial());
        u.setExperiencia(p.experiencia());
        u.setPublicaciones(p.publicaciones());
        u.setInstagramUrl(p.instagramUrl());
        u.setFacebookUrl(p.facebookUrl());
        u.setLinkedinUrl(p.linkedinUrl());
        u.setSitioWebUrl(p.sitioWebUrl());
        u.setCodigoRefeps(p.codigoRefeps());
        u.setSelloLinea1(p.selloLinea1());
        u.setSelloLinea2(p.selloLinea2());
        u.setSelloLinea3(p.selloLinea3());
        u.setTags(p.tags());
        u.setDescripcionPerfil(p.descripcionPerfil());
        u.setInstitucionFormacion(p.institucionFormacion());
        u.setAniosExperiencia(p.aniosExperiencia());
        u.setPacientesAtiende(p.pacientesAtiende());
        u.setDuracionTurnoMinutos(p.duracionTurnoMinutos());
        u.setIntervaloEntreTurnosMinutos(p.intervaloEntreTurnosMinutos());
    }

    /** El perfil del usuario tal cual está en el monolito, para la carga inicial del servicio. */
    public static PerfilProfesionalData perfilDe(Usuario u) {
        return new PerfilProfesionalData(u.getEmailContacto(), u.getDomicilioAtencion(), u.getDomicilioLat(),
                u.getDomicilioLng(), u.getDomicilioAtencionTorre(), u.getDomicilioAtencionPiso(),
                u.getDomicilioAtencionDepto(), u.getDomicilioAtencionBarrio(), u.getMatricula(), u.getMatriculaTipo(),
                u.getMatriculaProvincia(), u.getMatriculaNumero(), u.getMatriculaEspecialidad(), u.getMatriculaAsocTipo(),
                u.getMatriculaAsocProvincia(), u.getMatriculaAsocNumero(), u.getTitulo(), u.getSpecialty(), u.getCuit(),
                u.getPrecio(), u.getColor(), u.isOfreceOnline(), u.isOfrecePresencial(), u.getExperiencia(),
                u.getPublicaciones(), u.getInstagramUrl(), u.getFacebookUrl(), u.getLinkedinUrl(), u.getSitioWebUrl(),
                u.getCodigoRefeps(), u.getSelloLinea1(), u.getSelloLinea2(), u.getSelloLinea3(), u.getTags(),
                u.getDescripcionPerfil(), u.getInstitucionFormacion(), u.getAniosExperiencia(), u.getPacientesAtiende(),
                u.getDuracionTurnoMinutos(), u.getIntervaloEntreTurnosMinutos());
    }

    /** Hash de la foto (SHA-256 del texto base64), para que el servicio la pida solo cuando cambia. */
    public static String hashFoto(String fotoUrl) {
        if (fotoUrl == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(fotoUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
