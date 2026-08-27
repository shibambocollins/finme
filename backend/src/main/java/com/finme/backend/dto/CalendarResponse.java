package com.finme.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One month's spend, one entry per calendar day (including zero-spend days, so the frontend can
 * render a full grid without having to fill gaps itself).
 *
 * @param month "2026-07"
 * @param days  every day of that month in order, each carrying that day's total spend
 */
public record CalendarResponse(String month, List<CalendarDayResponse> days) {

    public record CalendarDayResponse(LocalDate date, BigDecimal total) {
    }
}
