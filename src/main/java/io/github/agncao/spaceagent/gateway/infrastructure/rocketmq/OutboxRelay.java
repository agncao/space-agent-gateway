package io.github.agncao.spaceagent.gateway.infrastructure.rocketmq;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {
    private final JdbcClient jdbc;
    private final StreamBridge streamBridge;
    private final int maxAttempts;

    public OutboxRelay(
            JdbcClient jdbc,
            StreamBridge streamBridge,
            @Value("${space.gateway.outbox-max-attempts:20}") int maxAttempts) {
        this.jdbc = jdbc;
        this.streamBridge = streamBridge;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${space.gateway.outbox-delay-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxRecord> records = jdbc.sql("""
                SELECT id, topic, payload_json FROM outbox_event
                WHERE status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP(6)
                ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
                """).query((rs, rowNum) -> new OutboxRecord(
                rs.getString("id"), rs.getString("topic"), rs.getString("payload_json"))).list();
        for (OutboxRecord record : records) {
            boolean sent;
            try {
                sent = streamBridge.send(record.topic(), record.payload());
            } catch (RuntimeException deliveryFailure) {
                sent = false;
            }
            if (sent) {
                jdbc.sql("UPDATE outbox_event SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP(6) WHERE id=:id")
                        .param("id", record.id()).update();
            } else {
                jdbc.sql("""
                        UPDATE outbox_event SET attempts=attempts+1,
                          status=CASE WHEN attempts+1 >= :maxAttempts THEN 'DEAD_LETTER' ELSE 'PENDING' END,
                          next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 5 SECOND) WHERE id=:id
                        """).param("maxAttempts", maxAttempts).param("id", record.id()).update();
            }
        }
    }

    private record OutboxRecord(String id, String topic, String payload) {
    }
}
