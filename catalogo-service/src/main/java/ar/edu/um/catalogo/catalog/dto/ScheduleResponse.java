package ar.edu.um.catalogo.catalog.dto;

import ar.edu.um.catalogo.catalog.WeeklySchedule;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleResponse(
    Long id,
    DayOfWeek dayOfWeek,
    @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
    @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
    int slotDurationMinutes,
    boolean enabled
) {
    public static ScheduleResponse from(WeeklySchedule s) {
        return new ScheduleResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(),
            s.getSlotDurationMinutes(), s.isEnabled());
    }
}
