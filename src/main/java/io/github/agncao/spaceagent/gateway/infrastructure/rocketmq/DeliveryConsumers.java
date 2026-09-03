package io.github.agncao.spaceagent.gateway.infrastructure.rocketmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.Message;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

@Configuration
public class DeliveryConsumers {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public DeliveryConsumers(JdbcClient jdbc, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Bean
    Consumer<Message<String>> deliveryConsumer() {
        return message -> {
            JsonNode envelope = read(message.getPayload());
            String kind = envelope.path("kind").asText();
            String executionId = envelope.path("execution_id").asText();
            String worker = jdbc.sql("SELECT worker FROM task_delivery WHERE execution_id=:id")
                    .param("id", executionId).query(String.class).single();
            String serviceName = jdbc.sql("SELECT service_name FROM agent_manifest WHERE worker=:worker")
                    .param("worker", worker).query(String.class).single();
            RestClient client = restClientBuilder.baseUrl("http://" + serviceName).build();
            if ("TASK".equals(kind)) {
                client.post().uri("/internal/v1/tasks")
                        .header("X-Execution-Id", executionId)
                        .body(envelope.path("submission"))
                        .retrieve().toBodilessEntity();
                jdbc.sql("UPDATE task_delivery SET status='DISPATCHED' WHERE execution_id=:id")
                        .param("id", executionId).update();
            } else if ("TASK_COMMAND".equals(kind)) {
                client.post().uri("/internal/v1/tasks/{id}/commands", executionId)
                        .body(envelope.path("payload"))
                        .retrieve().toBodilessEntity();
            } else if ("ACTION_RESULT".equals(kind)) {
                String actionId = envelope.path("payload").path("action_id").asText();
                client.post().uri("/internal/v1/tasks/{id}/actions/{actionId}/result", executionId, actionId)
                        .body(envelope.path("payload"))
                        .retrieve().toBodilessEntity();
            }
        };
    }

    @Bean
    Consumer<Message<String>> callbackConsumer() {
        return message -> {
            JsonNode envelope = read(message.getPayload());
            String executionId = envelope.path("execution_id").asText();
            DeliveryRoute route = jdbc.sql("""
                    SELECT requester_id, run_id, step_id FROM task_delivery WHERE execution_id=:id
                    """)
                    .param("id", executionId)
                    .query((rs, rowNum) -> new DeliveryRoute(
                            rs.getString("requester_id"), rs.getString("run_id"), rs.getString("step_id")))
                    .single();
            RestClient client = restClientBuilder.baseUrl("http://" + route.requesterId()).build();
            if ("ACTION_REQUEST".equals(envelope.path("kind").asText())) {
                client.post().uri("/api/v1/internal/action-requests")
                        .header("X-Run-Id", route.runId())
                        .header("X-Step-Id", route.stepId())
                        .body(envelope.path("request"))
                        .retrieve().toBodilessEntity();
            } else {
                client.post().uri("/api/v1/internal/task-events")
                        .header("X-Run-Id", route.runId())
                        .header("X-Step-Id", route.stepId())
                        .body(envelope)
                        .retrieve().toBodilessEntity();
            }
        };
    }

    private record DeliveryRoute(String requesterId, String runId, String stepId) {
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid gateway message", exception);
        }
    }
}
