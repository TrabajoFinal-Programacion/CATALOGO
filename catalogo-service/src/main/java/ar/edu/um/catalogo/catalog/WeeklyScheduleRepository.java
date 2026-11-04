package ar.edu.um.catalogo.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule, Long> {

    /** Sin orden: day_of_week se guarda como texto y el orden alfabetico no es el de la semana. */
    List<WeeklySchedule> findAllByProfessionalId(Long professionalId);

    @Modifying
    @Query("delete from WeeklySchedule s where s.id not in :ids")
    int deleteByIdNotIn(@Param("ids") Collection<Long> ids);
}
