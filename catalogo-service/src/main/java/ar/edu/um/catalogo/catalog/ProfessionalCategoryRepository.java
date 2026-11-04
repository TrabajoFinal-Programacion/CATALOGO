package ar.edu.um.catalogo.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalCategoryRepository extends JpaRepository<ProfessionalCategory, Long> {

    List<ProfessionalCategory> findAllByOrderByNameAsc();

    List<ProfessionalCategory> findAllByEnabledOrderByNameAsc(boolean enabled);

    @Modifying
    @Query("delete from ProfessionalCategory c where c.id not in :ids")
    int deleteByIdNotIn(@Param("ids") Collection<Long> ids);
}
