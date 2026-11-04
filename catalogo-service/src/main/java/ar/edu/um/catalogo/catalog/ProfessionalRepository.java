package ar.edu.um.catalogo.catalog;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalRepository extends JpaRepository<Professional, Long>, JpaSpecificationExecutor<Professional> {

    @Modifying
    @Query("delete from Professional p where p.id not in :ids")
    int deleteByIdNotIn(@Param("ids") Collection<Long> ids);
}
