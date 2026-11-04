package ar.edu.um.catalogo.catalog;

import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/** Franja semanal de atencion de un profesional. Copia local; el ID es el de la catedra. */
@Entity
@Table(name = "weekly_schedule")
public class WeeklySchedule {

    @Id
    private Long id;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected WeeklySchedule() {}

    public static WeeklySchedule from(WeeklyScheduleDto dto) {
        WeeklySchedule s = new WeeklySchedule();
        s.id = dto.id();
        s.professionalId = dto.professionalId();
        s.dayOfWeek = dto.dayOfWeek();
        s.startTime = dto.startTime();
        s.endTime = dto.endTime();
        s.slotDurationMinutes = dto.slotDurationMinutes();
        s.enabled = dto.enabled();
        s.createdAt = dto.createdAt();
        s.updatedAt = dto.updatedAt();
        return s;
    }

    public Long getId() { return id; }
    public Long getProfessionalId() { return professionalId; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getSlotDurationMinutes() { return slotDurationMinutes; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
