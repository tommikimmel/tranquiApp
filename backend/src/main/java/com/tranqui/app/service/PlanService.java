package com.tranqui.app.service;

import com.tranqui.app.model.Feature;
import com.tranqui.app.model.Plan;
import com.tranqui.app.model.PlanFeature;
import com.tranqui.app.repository.FeatureRepository;
import com.tranqui.app.repository.PlanFeatureRepository;
import com.tranqui.app.repository.PlanRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @PostConstruct
    @Transactional
    public void initDefaultPlansAndFeatures() {
        seedFeatures();
        seedPlans();
    }

    private void seedFeatures() {
        createFeatureIfNotExists("recetas_electronicas", "Recetas Electrónicas QBI2", "Emisión legal de recetas y psicofármacos con firma digital integrada");
        createFeatureIfNotExists("bot_whatsapp", "Bot y Notificaciones de WhatsApp", "Recordatorios y confirmaciones automáticas de turnos a pacientes");
        createFeatureIfNotExists("mp_split", "Cobro Anticipado y Mercado Pago Split", "Cobro automático de señas y honorarios con reducción de ausentismo");
        createFeatureIfNotExists("google_meet", "Google Meet & Calendario", "Sincronización bidireccional de agenda y links automáticos de telemedicina");
        createFeatureIfNotExists("historia_clinica", "Historia Clínica Digital", "Registro clínico, evoluciones, diagnósticos y adjuntos de pacientes");
        createFeatureIfNotExists("agenda_compartida", "Agenda y Turnero Online", "Página pública de reserva y autogestión de turnos");
        createFeatureIfNotExists("reportes", "Reportes y Métricas Financieras", "Estadísticas de pacientes, ingresos, cobros y ausentismo");
    }

    private void createFeatureIfNotExists(String key, String name, String desc) {
        if (!featureRepository.existsById(key)) {
            featureRepository.save(Feature.builder()
                    .key(key)
                    .name(name)
                    .description(desc)
                    .build());
        }
    }

    private void seedPlans() {
        // 1. Consultorio (psicólogos)
        Plan consultorio = planRepository.findByCode("consultorio").orElseGet(() -> {
            Plan p = Plan.builder()
                    .code("consultorio")
                    .name("Tranqui Consultorio")
                    .description("Ideal para psicólogos y profesionales que buscan adquisición de pacientes por zona y software de gestión integral.")
                    .priceArs(new BigDecimal("149500.00"))
                    .priceUsdRef(new BigDecimal("99.00"))
                    .billingPeriod("monthly")
                    .minSeats(1)
                    .requiresPrescriber(false)
                    .isActive(true)
                    .effectiveFrom(LocalDateTime.now())
                    .build();
            return planRepository.save(p);
        });

        // 2. Clínico (psiquiatras)
        Plan clinico = planRepository.findByCode("clinico").orElseGet(() -> {
            Plan p = Plan.builder()
                    .code("clinico")
                    .name("Tranqui Clínico")
                    .description("Diseñado para psiquiatras y médicos: incluye recetas electrónicas oficiales QBI2 con firma digital y psicofármacos.")
                    .priceArs(new BigDecimal("225000.00"))
                    .priceUsdRef(new BigDecimal("149.00"))
                    .billingPeriod("monthly")
                    .minSeats(1)
                    .requiresPrescriber(true)
                    .isActive(true)
                    .effectiveFrom(LocalDateTime.now())
                    .build();
            return planRepository.save(p);
        });

        // 3. Equipo (3+ profesionales)
        Plan equipo = planRepository.findByCode("equipo").orElseGet(() -> {
            Plan p = Plan.builder()
                    .code("equipo")
                    .name("Tranqui Equipo")
                    .description("Para clínicas y grupos de 3 o más profesionales: 15% de descuento por volumen con todas las herramientas.")
                    .priceArs(new BigDecimal("382500.00"))
                    .priceUsdRef(new BigDecimal("250.00"))
                    .billingPeriod("monthly")
                    .minSeats(3)
                    .requiresPrescriber(false)
                    .isActive(true)
                    .effectiveFrom(LocalDateTime.now())
                    .build();
            return planRepository.save(p);
        });

        // Attach features
        linkPlanFeature(consultorio, "historia_clinica");
        linkPlanFeature(consultorio, "agenda_compartida");
        linkPlanFeature(consultorio, "bot_whatsapp");
        linkPlanFeature(consultorio, "mp_split");
        linkPlanFeature(consultorio, "google_meet");
        linkPlanFeature(consultorio, "reportes");

        linkPlanFeature(clinico, "historia_clinica");
        linkPlanFeature(clinico, "agenda_compartida");
        linkPlanFeature(clinico, "bot_whatsapp");
        linkPlanFeature(clinico, "mp_split");
        linkPlanFeature(clinico, "google_meet");
        linkPlanFeature(clinico, "reportes");
        linkPlanFeature(clinico, "recetas_electronicas");

        linkPlanFeature(equipo, "historia_clinica");
        linkPlanFeature(equipo, "agenda_compartida");
        linkPlanFeature(equipo, "bot_whatsapp");
        linkPlanFeature(equipo, "mp_split");
        linkPlanFeature(equipo, "google_meet");
        linkPlanFeature(equipo, "reportes");
        linkPlanFeature(equipo, "recetas_electronicas");

        log.info("Subscription plans initialized: consultorio ($149.500), clinico ($225.000), equipo ($382.500)");
    }

    private void linkPlanFeature(Plan plan, String featureKey) {
        if (!planFeatureRepository.existsByPlanIdAndFeatureKey(plan.getId(), featureKey)) {
            planFeatureRepository.save(PlanFeature.builder()
                    .plan(plan)
                    .featureKey(featureKey)
                    .build());
        }
    }

    public List<Plan> getAllActivePlans() {
        return planRepository.findByIsActiveTrue();
    }

    public List<Plan> getAllPlans() {
        return planRepository.findAll();
    }

    public Optional<Plan> findByCode(String code) {
        return planRepository.findByCode(code);
    }

    public Optional<Plan> findById(Long id) {
        return planRepository.findById(id);
    }

    public List<String> getFeatureKeysForPlan(Long planId) {
        return planFeatureRepository.findByPlanId(planId).stream()
                .map(PlanFeature::getFeatureKey)
                .toList();
    }

    @Transactional
    public Plan updatePlan(Long id, BigDecimal priceArs, BigDecimal priceUsdRef, String name, String description, Boolean isActive) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Plan con ID " + id + " no encontrado"));

        if (priceArs != null) {
            plan.setPriceArs(priceArs);
        }
        if (priceUsdRef != null) {
            plan.setPriceUsdRef(priceUsdRef);
        }
        if (name != null && !name.trim().isEmpty()) {
            plan.setName(name.trim());
        }
        if (description != null) {
            plan.setDescription(description.trim());
        }
        if (isActive != null) {
            plan.setIsActive(isActive);
        }

        log.info("Plan {} ({}) actualizado por admin: precio ARS {}, USD ref {}", plan.getCode(), plan.getName(), plan.getPriceArs(), plan.getPriceUsdRef());
        return planRepository.save(plan);
    }
}
