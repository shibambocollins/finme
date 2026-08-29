package com.finme.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CalendarResponse(String month, List<CalendarDayResponse> days) {

    public record CalendarDayResponse(LocalDate date, BigDecimal total) {
    }
}
