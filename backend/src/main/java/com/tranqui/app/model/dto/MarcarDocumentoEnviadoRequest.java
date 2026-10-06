package com.tranqui.app.model.dto;

import lombok.*;

// Optional body for TurnoController#marcarDocumentoEnviado — both fields are optional so a
// médico can still mark a document as sent without attaching anything (e.g. they sent it
// through their own email, the original flow before uploads existed).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcarDocumentoEnviadoRequest {
    private String archivoData; // base64 data: URI (e.g. "data:application/pdf;base64,...")
    private String archivoNombre; // original filename, shown to the patient in "Mis Documentos"
}
