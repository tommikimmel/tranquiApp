package com.tranqui.app.config;

import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.TurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time-effective backfill (runs every startup, no-op once already applied) for the built-in
 * "receta-fuera" servicio and any Turno booked through it before TarifaMedico.esReceta /
 * Turno.esReceta existed — those rows sit at the columns' DB default (false), which would make
 * them show the "Enviar por mail" flow instead of "Generar receta →" (see TurnoService#esReceta,
 * DocumentActions.tsx). Mirrors TarifaAgendaBackfillRunner's exact pattern.
 */
@Component
public class RecetaFlagBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecetaFlagBackfillRunner.class);

    @Autowired
    private TarifaMedicoRepository tarifaMedicoRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Override
    public void run(String... args) {
        List<TarifaMedico> tarifasACorregir = tarifaMedicoRepository.findByServicioIdAndEsReceta("receta-fuera", false);
        if (!tarifasACorregir.isEmpty()) {
            for (TarifaMedico t : tarifasACorregir) {
                t.setEsReceta(true);
            }
            tarifaMedicoRepository.saveAll(tarifasACorregir);
            log.info("Backfill de esReceta: {} tarifa(s) 'receta-fuera' marcadas como esReceta=true.", tarifasACorregir.size());
        }

        List<Turno> turnosACorregir = turnoRepository.findLegacyRecetasSinFlag();
        if (!turnosACorregir.isEmpty()) {
            for (Turno t : turnosACorregir) {
                t.setEsReceta(true);
            }
            turnoRepository.saveAll(turnosACorregir);
            log.info("Backfill de esReceta: {} turno(s) de receta marcados como esReceta=true.", turnosACorregir.size());
        }
    }
}
