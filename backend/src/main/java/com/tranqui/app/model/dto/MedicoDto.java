package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicoDto {
    private Long id;
    private String name; // maps to name in frontend
    private String email;
    private String initials;
    private String degree; // maps to degree in frontend
    private String specialty; // maps to specialty in frontend
    private String matricula;
    private String cuit;
    private BigDecimal price; // maps to price in frontend
    private List<String> tags;
    private String color;
    private List<TarifaDto> tariffs;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TarifaDto {
        private String id; // particular, primera, osde, etc.
        private String label;
        private BigDecimal price;
        private boolean enabled;
    }
}
