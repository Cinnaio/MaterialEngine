package com.github.cinnaio.materiaengine.feature;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public enum HarvestPeriod {
    ALL, TODAY, WEEK;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public Window window(LocalDate today) {
        return switch (this) {
            case ALL -> new Window("", "");
            case TODAY -> new Window(today.toString(), today.toString());
            case WEEK -> new Window(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), today.toString());
        };
    }

    public record Window(String from, String through) {
        boolean includes(String day) {
            return from.isEmpty() ? day.isEmpty() : !day.isEmpty() && day.compareTo(from) >= 0 && day.compareTo(through) <= 0;
        }
    }
}
