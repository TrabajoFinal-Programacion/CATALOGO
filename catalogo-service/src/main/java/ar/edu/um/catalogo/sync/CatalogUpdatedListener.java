package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.catedra.dto.CatalogUpdatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume CatalogUpdated de catedra.catalog.{groupId}.
 *
 * Idempotencia en dos niveles:
 * 1. eventId: un evento ya procesado no repite efectos (contrato 18.1); solo se cuenta el duplicado.
 * 2. version: aunque llegue un evento nuevo, si la version local ya es >= newVersion no hay nada que hacer,
 *    y la sincronizacion en si es idempotente porque compara versiones.
 *
 * El evento se registra como procesado DESPUES de sincronizar. Si el servicio se cae en el medio,
 * Kafka lo vuelve a entregar y la sincronizacion repetida no cambia el resultado.
 * El offset se confirma al terminar el metodo sin excepcion (ack-mode record).
 */
@Component
public class CatalogUpdatedListener {

    private static final Logger log = LoggerFactory.getLogger(CatalogUpdatedListener.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final SyncStateService stateService;
    private final CatalogSyncService syncService;
    private final Clock clock;

    public CatalogUpdatedListener(ObjectMapper objectMapper, ProcessedEventRepository processedEvents,
                                  SyncStateService stateService, CatalogSyncService syncService, Clock clock) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.stateService = stateService;
        this.syncService = syncService;
        this.clock = clock;
    }

    @KafkaListener(
        id = "catalog-updates",
        topics = "${app.catedra.kafka-catalog-topic}",
        groupId = "${app.catedra.kafka-consumer-group}")
    public void onMessage(ConsumerRecord<String, String> record) {
        CatalogUpdatedEvent event = parse(record);
        if (event == null) {
            return; // mensaje invalido: se registra y se confirma para no bloquear el topic
        }
        if (processedEvents.existsById(event.eventId())) {
            processedEvents.registerDuplicate(event.eventId(), clock.instant());
            log.info("CatalogUpdated duplicado ignorado (eventId={}, version={})", event.eventId(), event.newVersion());
            return;
        }

        Long local = stateService.localVersion();
        if (local == null || event.newVersion() > local) {
            // Si falla, la excepcion llega al DefaultErrorHandler: reintentos acotados y el evento
            // no queda marcado como procesado.
            syncService.synchronize();
        } else {
            log.debug("CatalogUpdated version {} ya aplicada (local={})", event.newVersion(), local);
        }
        processedEvents.insertIfAbsent(event.eventId(), CatalogUpdatedEvent.TYPE, clock.instant());
    }

    private CatalogUpdatedEvent parse(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            log.warn("Mensaje vacio en {} offset {}", record.topic(), record.offset());
            return null;
        }
        try {
            CatalogUpdatedEvent event = objectMapper.readValue(record.value(), CatalogUpdatedEvent.class);
            if (!CatalogUpdatedEvent.TYPE.equals(event.eventType())) {
                log.info("Evento ignorado: tipo {} en {} offset {}", event.eventType(), record.topic(), record.offset());
                return null;
            }
            if (event.eventId() == null || event.eventId().isBlank() || event.newVersion() == null) {
                log.warn("CatalogUpdated sin eventId o newVersion en {} offset {}", record.topic(), record.offset());
                return null;
            }
            if (event.schemaVersion() != null && event.schemaVersion() != 1) {
                log.warn("CatalogUpdated con schemaVersion {} no soportada; se procesa como notificacion",
                    event.schemaVersion());
            }
            return event;
        } catch (JsonProcessingException e) {
            log.warn("JSON invalido en {} offset {}: {}", record.topic(), record.offset(), e.getOriginalMessage());
            return null;
        }
    }
}
