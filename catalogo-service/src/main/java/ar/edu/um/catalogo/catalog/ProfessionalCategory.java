package ar.edu.um.catalogo.catalog;

import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Categoria profesional. Copia local; el ID es el de la catedra. */
@Entity
@Table(name = "professional_category")
public class ProfessionalCategory {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ProfessionalCategory() {}

    public static ProfessionalCategory from(CategoryDto dto) {
        ProfessionalCategory c = new ProfessionalCategory();
        c.id = dto.id();
        c.name = dto.name();
        c.description = dto.description();
        c.enabled = dto.enabled();
        c.createdAt = dto.createdAt();
        c.updatedAt = dto.updatedAt();
        return c;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
