package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.Map;

public record TaskSubmission(
        String protocolVersion,
        String requesterId,
        String runId,
        String stepId,
        String worker,
        String task,
        String originalIntent,
        String idempotencyKey,
        Map<String, Object> context) {
}
