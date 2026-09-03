package io.github.agncao.spaceagent.gateway.domain.task;

import java.util.Map;

public record TaskCommand(
        String protocolVersion,
        String commandId,
        String executionId,
        String type,
        String resumeHandle,
        String userInput,
        Map<String, Object> payload) {
}
