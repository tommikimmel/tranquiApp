package com.tranqui.app.config;

import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.repository.TarifaMedicoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time-effective backfill (runs every startup, no-op once already applied) for médicos who
 * already had receta/certificado/informe tarifa rows before TarifaMedico.requiereAgenda existed —
 * those rows were created with the column's DB default (true), which would keep blocking the
 * médico's agenda exactly like a normal consultation. Flips them to false so these pure document
 * services stop reserving a slot, matching what new médicos get from MedicoService.DEFAULT_TARIFFS.
 */
@Component
public class TarifaAgendaBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TarifaAgendaBackfillRunner.class);
    private static final List<String> SERVICIOS_SIN_AGENDA = List.of("receta-fuera", "certificado", "informe-apto");

    @Autowired
    private TarifaMedicoRepository tarifaMedicoRepository;

    @Override
    public void run(String... args) {
        List<TarifaMedico> aCorregir = tarifaMedicoRepository.findByServicioIdInAndRequiereAgenda(SERVICIOS_SIN_AGENDA, true);
        if (aCorregir.isEmpty()) {
            return;
        }
        for (TarifaMedico t : aCorregir) {
            t.setRequiereAgenda(false);
        }
        tarifaMedicoRepository.saveAll(aCorregir);
        log.info("Backfill de agenda: {} tarifa(s) de servicios documentales marcadas como requiereAgenda=false.", aCorregir.size());
    }
}
