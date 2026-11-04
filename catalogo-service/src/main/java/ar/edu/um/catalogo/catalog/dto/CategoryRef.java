package ar.edu.um.catalogo.catalog.dto;

import ar.edu.um.catalogo.catalog.ProfessionalCategory;

/** Categoria resumida dentro de un profesional. */
public record CategoryRef(Long id, String name, boolean enabled) {

    public static CategoryRef from(ProfessionalCategory c) {
        return c == null ? null : new CategoryRef(c.getId(), c.getName(), c.isEnabled());
    }
}
