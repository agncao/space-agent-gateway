package io.github.agncao.spaceagent.gateway.domain.task;

import io.github.agncao.spaceagent.contracts.v1.TaskAccepted;
import io.github.agncao.spaceagent.contracts.v1.TaskSubmission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class TaskLedger {
    private final Supplier<String> executionIdSupplier;
    private final Map<String, TaskAccepted> tasks = new LinkedHashMap<>();

    public TaskLedger(Supplier<String> executionIdSupplier) {
        this.executionIdSupplier = executionIdSupplier;
    }

    public TaskAccepted accept(TaskSubmission submission) {
        TaskAccepted existing = tasks.get(submission.idempotencyKey());
        if (existing != null) {
            return new TaskAccepted(
                    existing.protocolVersion(), existing.executionId(), existing.acceptedAt(), true);
        }
        TaskAccepted accepted = new TaskAccepted("v1", executionIdSupplier.get(), java.time.Instant.now(), false);
        tasks.put(submission.idempotencyKey(), accepted);
        return accepted;
    }

    public int size() {
        return tasks.size();
    }
}
