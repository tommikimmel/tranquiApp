package com.tranqui.pagos.web;

import com.tranqui.pagos.datos.CuentasMp;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint interno para la resincronización de cuentas de Mercado Pago
 * vía gateway con JWT de servicio (Fase 9, I3c).
 */
@RestController
@RequestMapping("/internal/pagos")
public class PagosInternoController {

    private final CuentasMp cuentas;

    public PagosInternoController(CuentasMp cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/cuentas-mp")
    public ResponseEntity<List<CuentasMp.CuentaMpResumenDto>> cuentasMp(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio
    ) {
        if (pagina < 0 || tamanio < 1 || tamanio > 1000) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(cuentas.paginadas(pagina, tamanio));
    }
}
