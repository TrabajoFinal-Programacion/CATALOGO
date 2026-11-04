package ar.edu.um.catalogo.catalog.dto;

import ar.edu.um.catalogo.catalog.ProfessionalCategory;

public record CategoryResponse(Long id, String name, String description, boolean enabled) {

    public static CategoryResponse from(ProfessionalCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isEnabled());
    }
}
