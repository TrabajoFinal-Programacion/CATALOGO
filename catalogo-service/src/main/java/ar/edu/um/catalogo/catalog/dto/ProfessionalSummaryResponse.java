package ar.edu.um.catalogo.catalog.dto;

import ar.edu.um.catalogo.catalog.Professional;
import ar.edu.um.catalogo.catalog.ProfessionalCategory;

public record ProfessionalSummaryResponse(Long id, String firstName, String lastName, boolean enabled,
                                          Long categoryId, CategoryRef category) {

    public static ProfessionalSummaryResponse from(Professional p, ProfessionalCategory category) {
        return new ProfessionalSummaryResponse(p.getId(), p.getFirstName(), p.getLastName(), p.isEnabled(),
            p.getCategoryId(), CategoryRef.from(category));
    }
}
