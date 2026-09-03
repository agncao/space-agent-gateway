package io.github.agncao.spaceagent.gateway.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.agncao.spaceagent.gateway.domain.agent.AgentManifest;
import io.github.agncao.spaceagent.gateway.domain.policy.GatewayPolicyException;
import io.github.agncao.spaceagent.gateway.domain.policy.TaskEventPolicy;
import io.github.agncao.spaceagent.gateway.domain.task.TaskAccepted;
import io.github.agncao.spaceagent.gateway.domain.task.ActionRequest;
import io.github.agncao.spaceagent.gateway.domain.task.ActionResult;
import io.github.agncao.spaceagent.gateway.domain.task.TaskCommand;
import io.github.agncao.spaceagent.gateway.domain.task.TaskEvent;
import io.github.agncao.spaceagent.gateway.domain.task.TaskSubmission;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PersistentGatewayService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final TaskEventPolicy eventPolicy = new TaskEventPolicy();

    public PersistentGatewayService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void register(AgentManifest manifest) {
        requireV1(manifest.protocolVersion());
        String json = json(manifest);
        jdbc.sql("""
                INSERT INTO agent_manifest(worker, service_name, agent_version, protocol_version, manifest_json)
                VALUES (:worker, :service, :version, :protocol, :json)
                ON DUPLICATE KEY UPDATE service_name=VALUES(service_name), agent_version=VALUES(agent_version),
                  protocol_version=VALUES(protocol_version), manifest_json=VALUES(manifest_json), updated_at=CURRENT_TIMESTAMP(6)
                """)
                .param("worker", manifest.worker())
                .param("service", manifest.serviceName())
                .param("version", manifest.agentVersion())
                .param("protocol", manifest.protocolVersion())
                .param("json", json)
                .update();
    }

    public List<AgentManifest> catalog() {
        return jdbc.sql("SELECT manifest_json FROM agent_manifest ORDER BY worker")
                .query(String.class)
                .list()
                .stream()
                .map(value -> fromJson(value, AgentManifest.class))
                .toList();
    }

    @Transactional
    public TaskAccepted submit(TaskSubmission submission) {
        requireV1(submission.protocolVersion());
        ensureWorkerRegistered(submission.worker());
        Instant acceptedAt = Instant.now();
        String executionId = "exec-" + UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO task_delivery(execution_id, idempotency_key, requester_id, run_id, step_id, worker, status, payload_json)
                    VALUES (:executionId, :idempotencyKey, :requesterId, :runId, :stepId, :worker, 'ACCEPTED', :payload)
                    """)
                    .param("executionId", executionId)
                    .param("idempotencyKey", submission.idempotencyKey())
                    .param("requesterId", submission.requesterId())
                    .param("runId", submission.runId())
                    .param("stepId", submission.stepId())
                    .param("worker", submission.worker())
                    .param("payload", json(submission))
                    .update();
            appendOutbox("TASK", executionId, "space-agent-delivery-v1", json(Map.of(
                    "kind", "TASK", "execution_id", executionId, "submission", submission)));
            return new TaskAccepted("v1", executionId, acceptedAt, false);
        } catch (DuplicateKeyException duplicate) {
            return jdbc.sql("SELECT execution_id, accepted_at FROM task_delivery WHERE idempotency_key=:key")
                    .param("key", submission.idempotencyKey())
                    .query((rs, rowNum) -> new TaskAccepted(
                            "v1", rs.getString("execution_id"), rs.getTimestamp("accepted_at").toInstant(), true))
                    .single();
        }
    }

    @Transactional
    public void acceptEvent(TaskEvent event) {
        requireV1(event.protocolVersion());
        if (jdbc.sql("SELECT COUNT(*) FROM task_event WHERE event_id=:eventId")
                .param("eventId", event.eventId()).query(Integer.class).single() > 0) {
            return;
        }
        Map<String, Object> delivery = task(event.executionId());
        if (!event.worker().equals(delivery.get("worker"))) {
            throw new GatewayPolicyException("CONTRACT_VIOLATION: event worker does not own execution");
        }
        long lastSequence = jdbc.sql("SELECT COALESCE(MAX(event_seq), 0) FROM task_event WHERE execution_id=:executionId")
                .param("executionId", event.executionId())
                .query(Long.class)
                .single();
        eventPolicy.validateSequence(lastSequence, event);
        AgentManifest manifest = manifestFor(event.worker());
        eventPolicy.validateEffects(event, manifest.provides());
        eventPolicy.validateInvalidates(event, manifest.invalidates());
        jdbc.sql("""
                INSERT INTO task_event(event_id, execution_id, event_seq, worker, payload_json)
                VALUES (:eventId, :executionId, :eventSeq, :worker, :payload)
                """)
                .param("eventId", event.eventId())
                .param("executionId", event.executionId())
                .param("eventSeq", event.eventSeq())
                .param("worker", event.worker())
                .param("payload", json(event))
                .update();
        jdbc.sql("UPDATE task_delivery SET status=:status, updated_at=CURRENT_TIMESTAMP(6) WHERE execution_id=:id")
                .param("status", deliveryStatus(event.type()))
                .param("id", event.executionId())
                .update();
        appendOutbox("TASK_EVENT", event.executionId(), "space-agent-callback-v1", json(event));
    }

    public Map<String, Object> task(String executionId) {
        return jdbc.sql("SELECT execution_id, requester_id, run_id, step_id, worker, status FROM task_delivery WHERE execution_id=:id")
                .param("id", executionId)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "execution_id", rs.getString("execution_id"),
                        "requester_id", rs.getString("requester_id"),
                        "run_id", rs.getString("run_id"),
                        "step_id", rs.getString("step_id"),
                        "worker", rs.getString("worker"),
                        "status", rs.getString("status")))
                .optional()
                .orElseThrow(() -> new GatewayPolicyException("TASK_NOT_FOUND: " + executionId));
    }

    @Transactional
    public void requestAction(ActionRequest request) {
        requireV1(request.protocolVersion());
        Map<String, Object> delivery = task(request.executionId());
        AgentManifest manifest = manifestFor((String) delivery.get("worker"));
        eventPolicy.validateAction(request.action(), manifest.allowedActions());
        try {
            jdbc.sql("""
                    INSERT INTO task_action(execution_id, action_id, status, payload_json)
                    VALUES (:executionId, :actionId, 'REQUESTED', :payload)
                    """)
                    .param("executionId", request.executionId())
                    .param("actionId", request.actionId())
                    .param("payload", json(request))
                    .update();
            appendOutbox("ACTION_REQUEST", request.executionId(), "space-agent-callback-v1", json(Map.of(
                    "kind", "ACTION_REQUEST", "execution_id", request.executionId(), "request", request)));
        } catch (DuplicateKeyException ignored) {
            // 同一 action_id 的网络重放已经持久化，不重复产生副作用。
        }
    }

    @Transactional
    public void command(TaskCommand command) {
        requireV1(command.protocolVersion());
        task(command.executionId());
        try {
            jdbc.sql("""
                    INSERT INTO task_command(command_id, execution_id, type, payload_json)
                    VALUES (:commandId, :executionId, :type, :payload)
                    """)
                    .param("commandId", command.commandId())
                    .param("executionId", command.executionId())
                    .param("type", command.type())
                    .param("payload", json(command))
                    .update();
            appendOutbox("TASK_COMMAND", command.executionId(), "space-agent-delivery-v1", json(Map.of(
                    "kind", "TASK_COMMAND", "execution_id", command.executionId(), "payload", command)));
        } catch (DuplicateKeyException ignored) {
            // command_id 是幂等键。
        }
    }

    @Transactional
    public void actionResult(ActionResult result) {
        requireV1(result.protocolVersion());
        int updated = jdbc.sql("""
                UPDATE task_action SET status=:status, payload_json=:payload
                WHERE execution_id=:executionId AND action_id=:actionId AND status='REQUESTED'
                """)
                .param("status", result.status().toUpperCase())
                .param("payload", json(result))
                .param("executionId", result.executionId())
                .param("actionId", result.actionId())
                .update();
        if (updated == 0) {
            int existing = jdbc.sql("SELECT COUNT(*) FROM task_action WHERE execution_id=:executionId AND action_id=:actionId")
                    .param("executionId", result.executionId())
                    .param("actionId", result.actionId())
                    .query(Integer.class)
                    .single();
            if (existing > 0) {
                return;
            }
            throw new GatewayPolicyException("TASK_NOT_FOUND: action " + result.actionId());
        }
        appendOutbox("ACTION_RESULT", result.executionId(), "space-agent-delivery-v1", json(Map.of(
                "kind", "ACTION_RESULT", "execution_id", result.executionId(), "payload", result)));
    }

    private void ensureWorkerRegistered(String worker) {
        if (jdbc.sql("SELECT COUNT(*) FROM agent_manifest WHERE worker=:worker")
                .param("worker", worker).query(Integer.class).single() == 0) {
            throw new GatewayPolicyException("WORKER_NOT_REGISTERED: " + worker);
        }
    }

    private AgentManifest manifestFor(String worker) {
        return jdbc.sql("SELECT manifest_json FROM agent_manifest WHERE worker=:worker")
                .param("worker", worker)
                .query(String.class)
                .optional()
                .map(value -> fromJson(value, AgentManifest.class))
                .orElseThrow(() -> new GatewayPolicyException("WORKER_NOT_REGISTERED: " + worker));
    }

    private void appendOutbox(String type, String aggregateId, String topic, String payload) {
        jdbc.sql("""
                INSERT INTO outbox_event(id, aggregate_type, aggregate_id, topic, payload_json, status)
                VALUES (:id, :type, :aggregateId, :topic, :payload, 'PENDING')
                """)
                .param("id", UUID.randomUUID().toString())
                .param("type", type)
                .param("aggregateId", aggregateId)
                .param("topic", topic)
                .param("payload", payload)
                .update();
    }

    private String deliveryStatus(String eventType) {
        return switch (eventType) {
            case "task.accepted" -> "ACCEPTED";
            case "task.started", "task.progress" -> "RUNNING";
            case "task.waiting_user" -> "WAITING_USER";
            case "task.waiting_dependency" -> "WAITING_DEPENDENCY";
            case "task.succeeded" -> "SUCCEEDED";
            case "task.failed" -> "FAILED";
            case "task.cancelled" -> "CANCELLED";
            default -> throw new GatewayPolicyException("CONTRACT_VIOLATION: unsupported event type " + eventType);
        };
    }

    private void requireV1(String protocolVersion) {
        if (!"v1".equals(protocolVersion)) {
            throw new GatewayPolicyException("CONTRACT_VIOLATION: unsupported protocol " + protocolVersion);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new GatewayPolicyException("CONTRACT_VIOLATION: " + exception.getMessage());
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new GatewayPolicyException("CONTRACT_VIOLATION: " + exception.getMessage());
        }
    }
}
