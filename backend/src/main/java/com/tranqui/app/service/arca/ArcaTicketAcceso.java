package com.tranqui.app.service.arca;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArcaTicketAcceso {
    private String token;
    private String sign;
    private LocalDateTime generationTime;
    private LocalDateTime expirationTime;

    public boolean isValid() {
        if (token == null || sign == null || expirationTime == null) {
            return false;
        }
        // Valid if current time is at least 30 minutes before expiration
        return LocalDateTime.now().plusMinutes(30).isBefore(expirationTime);
    }
}
