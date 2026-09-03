package io.github.agncao.spaceagent.gateway.domain.task;

import java.time.Instant;

public record TaskAccepted(String protocolVersion, String executionId, Instant acceptedAt, boolean duplicate) {
    public TaskAccepted(String executionId, boolean duplicate) {
        this("v1", executionId, Instant.now(), duplicate);
    }
}
