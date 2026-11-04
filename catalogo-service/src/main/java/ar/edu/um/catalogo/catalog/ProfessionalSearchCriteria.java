package ar.edu.um.catalogo.catalog;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Filtros ya validados de la busqueda de profesionales. */
public record ProfessionalSearchCriteria(
    Long categoryId,
    String name,
    Boolean enabled,
    DayOfWeek availableDay,
    LocalTime availableFrom,
    LocalTime availableTo
) {}
