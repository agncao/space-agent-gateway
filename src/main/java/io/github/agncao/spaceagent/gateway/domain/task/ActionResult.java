package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.Map;

public record ActionResult(
        String protocolVersion,
        String actionId,
        String executionId,
        String status,
        Map<String, Object> result,
        String error) {
}
