package ar.edu.um.catalogo.sync;

import static ar.edu.um.catalogo.CatalogFixtures.professional;
import static ar.edu.um.catalogo.CatalogFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.edu.um.catalogo.CatalogFixtures;
import ar.edu.um.catalogo.IntegrationTest;
import ar.edu.um.catalogo.catalog.ProfessionalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Notificaciones CatalogUpdated por Kafka real: actualizacion incremental disparada por el evento,
 * procesamiento idempotente de un mensaje duplicado y tolerancia a mensajes invalidos.
 */
class CatalogUpdatedListenerIntegrationTest extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired CatalogApplier applier;
    @Autowired SyncStateService stateService;
    @Autowired ProcessedEventRepository processedEvents;
    @Autowired ProfessionalRepository professionals;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    CatalogFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CatalogFixtures(redis, objectMapper, jdbc);
        fixtures.reset();
        applier.applySnapshot(snapshot(5));
    }

    @Test
    void catalogUpdatedTriggersIncrementalSynchronization() throws Exception {
        publishVersion6();

        send("evt-v6-a", 6);

        await().atMost(TIMEOUT).until(() -> Long.valueOf(6).equals(stateService.localVersion()));
        assertThat(professionals.findById(15L).orElseThrow().getLastName()).isEqualTo("Gomez Perez");
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById("evt-v6-a"));
    }

    /**
     * Contrato 18.1: el mismo eventId llega dos veces. La segunda entrega no repite efectos.
     * Para demostrarlo, entre ambas entregas se publica la version 7 en Redis: si el duplicado
     * disparara una sincronizacion, la version local pasaria a 7. Debe quedar en 6.
     */
    @Test
    void duplicatedEventIsProcessedOnlyOnce() throws Exception {
        publishVersion6();
        send("evt-dup", 6);
        await().atMost(TIMEOUT).until(() -> processedEvents.existsById("evt-dup"));
        assertThat(stateService.localVersion()).isEqualTo(6);

        fixtures.publishVersions(7, 1);
        fixtures.publishChanges(7, List.of(), List.of(16L), List.of());
        fixtures.publishEntities(List.of(), List.of(professional(16, 2, "Bruno", "Diaz Lopez", true)), List.of());
        send("evt-dup", 6);

        await().atMost(TIMEOUT).until(() ->
            processedEvents.findById("evt-dup").map(ProcessedEvent::getDuplicateCount).orElse(0) == 1);
        assertThat(stateService.localVersion()).isEqualTo(6);
        assertThat(professionals.findById(16L).orElseThrow().getLastName()).isEqualTo("Diaz");
    }

    @Test
    void invalidMessagesAreSkippedWithoutBlockingTheTopic() throws Exception {
        publishVersion6();

        kafka.send(CATALOG_TOPIC, "x", "{no es json").get();
        kafka.send(CATALOG_TOPIC, "x", "{\"eventType\":\"OtroEvento\",\"eventId\":\"otro-1\"}").get();
        send("evt-after-garbage", 6);

        await().atMost(TIMEOUT).until(() -> Long.valueOf(6).equals(stateService.localVersion()));
        assertThat(processedEvents.existsById("otro-1")).isFalse();
    }

    @Test
    void eventForAnAlreadyAppliedVersionIsRecordedWithoutSynchronizing() throws Exception {
        send("evt-old", 4);

        await().atMost(TIMEOUT).until(() -> processedEvents.existsById("evt-old"));
        assertThat(stateService.localVersion()).isEqualTo(5);
    }

    private void publishVersion6() {
        fixtures.publishVersions(6, 1);
        fixtures.publishChanges(6, List.of(), List.of(15L), List.of());
        fixtures.publishEntities(List.of(), List.of(professional(15, 1, "Ana", "Gomez Perez", true)), List.of());
    }

    private void send(String eventId, long newVersion) throws Exception {
        String json = """
            {"eventId":"%s","eventType":"CatalogUpdated","newVersion":%d,
             "occurredAt":"2026-09-10T13:44:59Z","source":"MANUAL","schemaVersion":1,"campoNuevo":"se ignora"}
            """.formatted(eventId, newVersion);
        kafka.send(CATALOG_TOPIC, String.valueOf(newVersion), json).get();
    }
}
