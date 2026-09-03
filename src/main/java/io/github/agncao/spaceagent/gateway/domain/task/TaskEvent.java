package io.github.agncao.spaceagent.gateway.domain.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskEvent(
        String protocolVersion,
        String eventId,
        long eventSeq,
        String executionId,
        String worker,
        String type,
        Instant emittedAt,
        StepResult result,
        Map<String, Object> payload) {
    public TaskEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public TaskEvent(String eventId, long eventSeq, String executionId, String worker, List<String> effects) {
        this(
                "v1",
                eventId,
                eventSeq,
                executionId,
                worker,
                "task.progress",
                Instant.now(),
                new StepResult("success", "TEST", "test", Map.of(), effects, List.of(), List.of(), List.of()),
                Map.of());
    }

    public List<String> effects() {
        return result == null ? List.of() : result.effects();
    }

    public List<String> invalidates() {
        return result == null ? List.of() : result.invalidates();
    }
}
