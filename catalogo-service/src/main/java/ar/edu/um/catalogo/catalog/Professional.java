package ar.edu.um.catalogo.catalog;

import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Profesional. Copia local; el ID es el de la catedra. */
@Entity
@Table(name = "professional")
public class Professional {

    @Id
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Professional() {}

    public static Professional from(ProfessionalDto dto) {
        Professional p = new Professional();
        p.id = dto.id();
        p.categoryId = dto.categoryId();
        p.firstName = dto.firstName();
        p.lastName = dto.lastName();
        p.enabled = dto.enabled();
        p.createdAt = dto.createdAt();
        p.updatedAt = dto.updatedAt();
        return p;
    }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
