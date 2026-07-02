package com.tranqui.app.model.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {
    private int sessionsToday;
    private String sessionsTodayChange;
    private BigDecimal earningsThisWeek;
    private String earningsThisWeekChange;
    private int activePatients;
    private String activePatientsChange;
    private int noShowsThisMonth;
    private String noShowsChange;
}
