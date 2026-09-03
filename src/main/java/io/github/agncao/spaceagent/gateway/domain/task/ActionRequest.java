package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.Map;

public record ActionRequest(
        String protocolVersion,
        String actionId,
        String executionId,
        String action,
        String namespace,
        String toolFunc,
        Map<String, Object> arguments,
        String idempotencyKey) {
}
