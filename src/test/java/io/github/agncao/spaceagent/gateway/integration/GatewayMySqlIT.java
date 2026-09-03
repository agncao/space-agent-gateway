package io.github.agncao.spaceagent.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.agncao.spaceagent.gateway.application.PersistentGatewayService;
import io.github.agncao.spaceagent.gateway.domain.agent.AgentManifest;
import io.github.agncao.spaceagent.gateway.domain.task.TaskAccepted;
import io.github.agncao.spaceagent.gateway.domain.task.ActionRequest;
import io.github.agncao.spaceagent.gateway.domain.task.ActionResult;
import io.github.agncao.spaceagent.gateway.domain.task.TaskSubmission;
import io.github.agncao.spaceagent.gateway.infrastructure.rocketmq.OutboxRelay;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.cloud.stream.function.StreamBridge;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers(disabledWithoutDocker = true)
class GatewayMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("space_agent_gateway")
            .withUsername("gateway")
            .withPassword("gateway-test");

    private static PersistentGatewayService service;
    private static JdbcClient jdbc;

    @BeforeAll
    static void configureGateway() {
        DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndAddModules()
                .build();
        service = new PersistentGatewayService(jdbc, mapper);
        service.register(new AgentManifest(
                "analysis-agent", "space-agent-analysis", "0.1.0", "v1", "分析",
                List.of("scene.opened", "entity.resolved"),
                List.of("analysis.illumination.completed"), List.of(),
                List.of("analysis.analyze_entity_data")));
    }

    @BeforeEach
    void clearTaskData() {
        jdbc.sql("DELETE FROM outbox_event").update();
        jdbc.sql("DELETE FROM task_command").update();
        jdbc.sql("DELETE FROM task_action").update();
        jdbc.sql("DELETE FROM task_event").update();
        jdbc.sql("DELETE FROM task_delivery").update();
    }

    @Test
    void persistsIdempotentTasksForMultipleRequestersAndOutbox() {
        TaskSubmission firstRequest = submission("orchestrator-a", "run-a", "run-a:step-1:1");
        TaskAccepted first = service.submit(firstRequest);
        TaskAccepted duplicate = service.submit(firstRequest);
        TaskAccepted secondRequester = service.submit(submission("orchestrator-b", "run-b", "run-b:step-1:1"));

        assertThat(duplicate.executionId()).isEqualTo(first.executionId());
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(secondRequester.executionId()).isNotEqualTo(first.executionId());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM task_delivery").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM outbox_event").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void movesRepeatedRocketMqPublicationFailureToDeadLetter() {
        TaskAccepted accepted = service.submit(submission("orchestrator-retry", "run-retry", "run-retry:step-1:1"));
        StreamBridge streamBridge = mock(StreamBridge.class);
        OutboxRelay relay = new OutboxRelay(jdbc, streamBridge, 2);

        relay.publishPending();
        jdbc.sql("UPDATE outbox_event SET next_attempt_at=CURRENT_TIMESTAMP(6) WHERE aggregate_id=:executionId")
                .param("executionId", accepted.executionId())
                .update();
        relay.publishPending();

        assertThat(jdbc.sql("SELECT status FROM outbox_event WHERE aggregate_id=:executionId")
                .param("executionId", accepted.executionId())
                .query(String.class)
                .single()).isEqualTo("DEAD_LETTER");
    }

    @Test
    void duplicateActionResultDoesNotCreateDuplicateDelivery() {
        TaskAccepted accepted = service.submit(submission("orchestrator-action", "run-action", "run-action:step-1:1"));
        ActionRequest request = new ActionRequest(
                "v1", "action-stable", accepted.executionId(), "analysis.analyze_entity_data",
                "data_analyse_tools", "analyzeEntityData", Map.of("entityId", "entity-1"),
                accepted.executionId() + ":action-stable");
        ActionResult result = new ActionResult(
                "v1", "action-stable", accepted.executionId(), "success", Map.of("success", true), null);

        service.requestAction(request);
        service.actionResult(result);
        service.actionResult(result);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=:executionId AND aggregate_type='ACTION_RESULT'")
                .param("executionId", accepted.executionId())
                .query(Integer.class)
                .single()).isEqualTo(1);
    }

    private static TaskSubmission submission(String requester, String runId, String idempotencyKey) {
        return new TaskSubmission(
                "v1", requester, runId, "step-1", "analysis-agent", "分析当前实体的光照数据",
                "分析它的光照数据", idempotencyKey,
                Map.of("facts", List.of("scene.opened", "entity.resolved")));
    }
}
